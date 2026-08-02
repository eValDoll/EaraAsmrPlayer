#!/usr/bin/env python3
"""Measure repeated local album enter/exit presentation cadence during playback."""

from __future__ import annotations

import argparse
import math
import re
import subprocess
import time


def adb(serial: str, *args: str) -> str:
    return subprocess.check_output(
        ["adb", "-s", serial, *args],
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def percentile(values: list[float], percentile_value: float) -> float:
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * percentile_value) - 1)
    return ordered[index]


def find_main_activity_layer(serial: str, package_name: str) -> str:
    output = adb(serial, "shell", "dumpsys", "SurfaceFlinger", "--list")
    pattern = re.compile(
        rf"{re.escape(package_name)}/{re.escape(package_name)}\.MainActivity#\d+"
    )
    candidates = list(dict.fromkeys(pattern.findall(output)))
    for layer in reversed(candidates):
        latency = adb(
            serial,
            "shell",
            "dumpsys",
            "SurfaceFlinger",
            "--latency",
            layer,
        )
        if len(latency.splitlines()) > 1:
            return layer
    raise RuntimeError("No active MainActivity buffer layer was found")


def assert_playing(serial: str, package_name: str) -> None:
    output = adb(serial, "shell", "dumpsys", "media_session")
    pattern = re.compile(
        rf"package={re.escape(package_name)}[\s\S]{{0,2000}}?"
        r"state=PlaybackState \{state=PLAYING\(3\)"
    )
    if not pattern.search(output):
        raise RuntimeError("Target playback session is not PLAYING")


def clear_latency(serial: str, layer: str) -> None:
    adb(
        serial,
        "shell",
        "dumpsys",
        "SurfaceFlinger",
        "--latency-clear",
        layer,
    )


def read_intervals(serial: str, layer: str) -> tuple[float, list[float]]:
    output = adb(
        serial,
        "shell",
        "dumpsys",
        "SurfaceFlinger",
        "--latency",
        layer,
    )
    lines = output.splitlines()
    refresh_ms = int(lines[0]) / 1_000_000
    present_times = sorted(
        {
            int(columns[1])
            for line in lines[1:]
            if len(columns := line.split()) == 3
            and 0 < int(columns[1]) < 9_000_000_000_000_000_000
        }
    )
    intervals = [
        (current - previous) / 1_000_000
        for previous, current in zip(present_times, present_times[1:])
        if current > previous
    ]
    if not intervals:
        raise RuntimeError("No presentation intervals were captured")
    return refresh_ms, intervals


def print_result(
    label: str,
    round_index: int,
    refresh_ms: float,
    intervals: list[float],
    show_misses: bool,
) -> None:
    missed_intervals = [
        (index, interval)
        for index, interval in enumerate(intervals)
        if interval > refresh_ms * 1.5
    ]
    cadence = [max(1, round(interval / refresh_ms)) * refresh_ms for interval in intervals]
    print(
        f"round={round_index:02d} phase={label:<5} frames={len(intervals):3d} "
        f"p99={percentile(intervals, 0.99):.3f}ms "
        f"cadenceP99={percentile(cadence, 0.99):.3f}ms "
        f"max={max(intervals):.3f}ms missed={len(missed_intervals)}"
    )
    if show_misses and missed_intervals:
        details = ", ".join(
            f"{index}:{interval:.3f}ms" for index, interval in missed_intervals
        )
        print(f"  missedIntervals={details}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.asmr.player")
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--tap-x", type=int, default=159)
    parser.add_argument("--tap-y", type=int, default=646)
    parser.add_argument("--sample-ms", type=int, default=800)
    parser.add_argument(
        "--cooldown-ms",
        type=int,
        default=1_500,
        help="Idle time between independent enter/exit samples",
    )
    parser.add_argument("--show-misses", action="store_true")
    args = parser.parse_args()

    assert_playing(args.serial, args.package)
    layer = find_main_activity_layer(args.serial, args.package)
    print(f"layer={layer}")
    samples: dict[str, list[float]] = {"enter": [], "exit": []}
    for round_index in range(1, args.rounds + 1):
        clear_latency(args.serial, layer)
        adb(
            args.serial,
            "shell",
            "input",
            "tap",
            str(args.tap_x),
            str(args.tap_y),
        )
        time.sleep(args.sample_ms / 1_000)
        refresh_ms, intervals = read_intervals(args.serial, layer)
        samples["enter"].extend(intervals)
        print_result("enter", round_index, refresh_ms, intervals, args.show_misses)

        clear_latency(args.serial, layer)
        adb(args.serial, "shell", "input", "keyevent", "4")
        time.sleep(args.sample_ms / 1_000)
        refresh_ms, intervals = read_intervals(args.serial, layer)
        samples["exit"].extend(intervals)
        print_result("exit", round_index, refresh_ms, intervals, args.show_misses)
        assert_playing(args.serial, args.package)
        if round_index < args.rounds and args.cooldown_ms > 0:
            time.sleep(args.cooldown_ms / 1_000)

    for label, intervals in samples.items():
        missed = sum(interval > refresh_ms * 1.5 for interval in intervals)
        cadence = [max(1, round(interval / refresh_ms)) * refresh_ms for interval in intervals]
        print(
            f"aggregate phase={label:<5} frames={len(intervals):4d} "
            f"p99={percentile(intervals, 0.99):.3f}ms "
            f"cadenceP99={percentile(cadence, 0.99):.3f}ms "
            f"max={max(intervals):.3f}ms missed={missed}"
        )


if __name__ == "__main__":
    main()
