#!/usr/bin/env python3
"""Stream a legacy atrace text dump and summarize app trace slices by thread/name."""

from __future__ import annotations

import argparse
import math
import re
from collections import defaultdict


TRACE_LINE = re.compile(
    r"^\s*(?P<thread>.+)-(?P<tid>\d+)\s+\(\s*(?P<tgid>\d+|-------)\)"
    r".*?\s(?P<timestamp>\d+\.\d+): tracing_mark_write: (?P<payload>.*)$"
)


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("trace")
    parser.add_argument("--tgid", required=True)
    parser.add_argument("--min-count", type=int, default=2)
    parser.add_argument("--top", type=int, default=80)
    parser.add_argument("--slowest", type=int, default=0)
    parser.add_argument("--slow-thread", default="")
    parser.add_argument("--start", type=float)
    parser.add_argument("--end", type=float)
    args = parser.parse_args()

    target = args.tgid
    stacks: dict[int, list[tuple[str, float, str]]] = defaultdict(list)
    durations: dict[tuple[str, str], list[float]] = defaultdict(list)
    events: list[tuple[float, float, float, str, str]] = []

    with open(args.trace, "r", encoding="utf-8", errors="replace") as trace:
        for line in trace:
            match = TRACE_LINE.match(line)
            if match is None:
                continue
            payload = match.group("payload")
            tid = int(match.group("tid"))
            timestamp = float(match.group("timestamp"))
            thread = match.group("thread").strip()
            if payload.startswith(f"B|{target}|"):
                stacks[tid].append((payload.split("|", 2)[2], timestamp, thread))
            elif payload == f"E|{target}" and stacks[tid]:
                name, started, started_thread = stacks[tid].pop()
                duration_ms = (timestamp - started) * 1_000
                inside_window = (
                    (args.start is None or started >= args.start) and
                    (args.end is None or started < args.end)
                )
                if duration_ms >= 0 and inside_window:
                    durations[(started_thread, name)].append(duration_ms)
                    events.append((duration_ms, started, timestamp, started_thread, name))

    rows = []
    for (thread, name), values in durations.items():
        if len(values) < args.min_count:
            continue
        rows.append(
            (
                percentile(values, 0.99),
                max(values),
                percentile(values, 0.90),
                len(values),
                thread,
                name,
            )
        )
    rows.sort(reverse=True)
    for p99, maximum, p90, count, thread, name in rows[: args.top]:
        print(
            f"thread={thread:<22} count={count:5d} p90={p90:8.3f}ms "
            f"p99={p99:8.3f}ms max={maximum:8.3f}ms slice={name}"
        )

    if args.slowest > 0:
        candidates = [
            event for event in events
            if not args.slow_thread or event[3] == args.slow_thread
        ]
        candidates.sort(reverse=True)
        for duration_ms, started, ended, thread, name in candidates[: args.slowest]:
            print(
                f"\nslow thread={thread} start={started:.6f} duration={duration_ms:.3f}ms "
                f"slice={name}"
            )
            nested = [
                event for event in events
                if event[3] == thread and event[1] >= started and event[2] <= ended and
                event[0] >= 0.05 and event != (duration_ms, started, ended, thread, name)
            ]
            nested.sort(key=lambda event: (event[1], -event[0]))
            for child_duration, child_started, _, _, child_name in nested:
                offset_ms = (child_started - started) * 1_000
                print(
                    f"  +{offset_ms:8.3f}ms {child_duration:8.3f}ms {child_name}"
                )


if __name__ == "__main__":
    main()
