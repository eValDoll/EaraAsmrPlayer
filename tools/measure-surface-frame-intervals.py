#!/usr/bin/env python3
"""Measure actual app-buffer presentation intervals from SurfaceFlinger."""

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
    layer_list = adb(serial, "shell", "dumpsys", "SurfaceFlinger", "--list")
    pattern = re.compile(
        rf"{re.escape(package_name)}/{re.escape(package_name)}\.MainActivity#\d+"
    )
    candidates = list(dict.fromkeys(pattern.findall(layer_list)))
    for layer in reversed(candidates):
        output = adb(
            serial,
            "shell",
            "dumpsys",
            "SurfaceFlinger",
            "--latency",
            layer,
        )
        if len(output.splitlines()) > 1:
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


def read_present_times(serial: str, layer: str) -> tuple[int, list[int]]:
    output = adb(
        serial,
        "shell",
        "dumpsys",
        "SurfaceFlinger",
        "--latency",
        layer,
    )
    lines = output.splitlines()
    refresh_period = int(lines[0].strip())
    present_times: list[int] = []
    for line in lines[1:]:
        columns = line.split()
        if len(columns) != 3:
            continue
        actual_present_time = int(columns[1])
        if 0 < actual_present_time < 9_000_000_000_000_000_000:
            present_times.append(actual_present_time)
    return refresh_period, sorted(set(present_times))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.asmr.player")
    parser.add_argument("--rounds", type=int, default=1)
    parser.add_argument("--swipe-duration-ms", type=int, default=180)
    parser.add_argument("--sample-ms", type=int, default=1_000)
    args = parser.parse_args()

    assert_playing(args.serial, args.package)
    layer = find_main_activity_layer(args.serial, args.package)
    print(f"layer={layer}")
    for round_index in range(args.rounds):
        adb(
            args.serial,
            "shell",
            "dumpsys",
            "SurfaceFlinger",
            "--latency-clear",
            layer,
        )
        forward = round_index % 2 == 0
        start_y, end_y = (2_100, 450) if forward else (450, 2_100)
        adb(
            args.serial,
            "shell",
            "input",
            "swipe",
            "600",
            str(start_y),
            "600",
            str(end_y),
            str(args.swipe_duration_ms),
        )
        time.sleep(args.sample_ms / 1_000)
        refresh_period, present_times = read_present_times(args.serial, layer)
        intervals = [
            (current - previous) / 1_000_000
            for previous, current in zip(present_times, present_times[1:])
            if current > previous
        ]
        if not intervals:
            raise RuntimeError(f"No presentation intervals captured in round {round_index + 1}")
        refresh_ms = refresh_period / 1_000_000
        missed = sum(interval > refresh_ms * 1.5 for interval in intervals)
        cadence = [max(1, round(interval / refresh_ms)) * refresh_ms for interval in intervals]
        print(
            f"round={round_index + 1:02d} frames={len(intervals):3d} "
            f"p50={percentile(intervals, 0.50):.3f}ms "
            f"p90={percentile(intervals, 0.90):.3f}ms "
            f"p95={percentile(intervals, 0.95):.3f}ms "
            f"p99={percentile(intervals, 0.99):.3f}ms "
            f"cadenceP99={percentile(cadence, 0.99):.3f}ms "
            f"max={max(intervals):.3f}ms missed={missed}"
        )
        assert_playing(args.serial, args.package)


if __name__ == "__main__":
    main()
