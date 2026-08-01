#!/usr/bin/env python3
"""Print the slowest frames from Android gfxinfo framestats."""

from __future__ import annotations

import argparse
import csv
import subprocess


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.asmr.player")
    parser.add_argument("--limit", type=int, default=25)
    args = parser.parse_args()

    output = subprocess.check_output(
        [
            "adb",
            "-s",
            args.serial,
            "shell",
            "dumpsys",
            "gfxinfo",
            args.package,
            "framestats",
        ],
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    profile = output.split("---PROFILEDATA---", maxsplit=1)[1]
    rows = list(csv.reader(profile.splitlines()))
    header = next(row[:-1] for row in rows if row and row[0] == "Flags")

    frames: list[dict[str, float]] = []
    for row in rows:
        values = row[:-1]
        if len(values) != len(header) or not values[0].isdigit():
            continue
        frame = dict(zip(header, map(int, values)))
        intended_vsync = frame["IntendedVsync"]
        frame_start = frame["FrameStartTime"]
        sync_queued = frame["SyncQueued"]
        frame_completed = frame["FrameCompleted"]
        frames.append(
            {
                "total": (frame_completed - intended_vsync) / 1_000_000,
                "start": (frame_start - intended_vsync) / 1_000_000,
                "main": (sync_queued - frame_start) / 1_000_000,
                "render": (frame_completed - sync_queued) / 1_000_000,
                "gpu": (frame["GpuCompleted"] - frame["SwapBuffers"])
                / 1_000_000,
                "flags": frame["Flags"],
                "id": frame["FrameTimelineVsyncId"],
            }
        )

    print(f"frames={len(frames)}")
    for frame in sorted(frames, key=lambda value: value["total"], reverse=True)[
        : args.limit
    ]:
        print(
            f"total={frame['total']:6.2f}ms "
            f"start={frame['start']:5.2f}ms "
            f"main={frame['main']:5.2f}ms "
            f"render={frame['render']:5.2f}ms "
            f"gpu={frame['gpu']:5.2f}ms "
            f"flags={int(frame['flags'])} id={int(frame['id'])}"
        )


if __name__ == "__main__":
    main()
