"""Align stereo WAV captures to a reference and measure digital fidelity.

Requires NumPy and SciPy. Inputs must have the same sample rate; this tool never
resamples or mixes channels. --reference-start locates a trimmed reference in
the original track. Playback captures do not measure the earphone output.
"""

import argparse
import json
from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import correlate


def read_stereo(path):
    rate, raw = wavfile.read(path)
    if raw.ndim != 2 or raw.shape[1] != 2:
        raise ValueError(f"{path}: expected stereo WAV")
    if np.issubdtype(raw.dtype, np.signedinteger):
        scale = float(2 ** (np.iinfo(raw.dtype).bits - 1))
    elif np.issubdtype(raw.dtype, np.floating):
        scale = 1.0
    else:
        raise ValueError(f"{path}: unsupported sample type {raw.dtype}")
    audio = raw.astype(np.float64) / scale
    if not np.isfinite(audio).all():
        raise ValueError(f"{path}: non-finite samples")
    return rate, audio


def window(audio, start, count):
    if start < 0 or start + count > len(audio) or count <= 0:
        raise ValueError("Requested interval lies outside the WAV")
    return audio[start : start + count]


def align(reference, capture, rate, start):
    # Match the stronger channel to avoid aligning quantization noise on a
    # nearly silent side of a binaural recording.
    template = window(capture, start, 3 * rate)
    channel = int(np.argmax(np.sum(template * template, axis=0)))
    template = template[:, channel]
    energy = np.dot(template, template)
    if energy < 1e-12 or len(reference) < len(template):
        raise ValueError("Alignment needs a non-silent 3-second window")
    signal = reference[:, channel]
    cumulative = np.concatenate(([0.0], np.cumsum(signal * signal)))
    rolling_energy = np.maximum(cumulative[len(template) :] - cumulative[: -len(template)], 0)
    denominator = np.sqrt(rolling_energy * energy)
    score = np.full(len(denominator), -np.inf)
    np.divide(
        correlate(signal, template, mode="valid", method="fft"),
        denominator,
        out=score,
        where=denominator > 1e-12,
    )
    position = int(np.argmax(score))
    if score[position] < 0.5:
        raise ValueError("No reliable alignment; check source, speed and capture")
    return position - start, float(score[position])


def db(value):
    return 20 * np.log10(np.maximum(value, 1e-15))


def metrics(reference, capture):
    energy = np.sum(reference * reference, axis=0)
    if np.any(energy < 1e-12) or np.any(np.sum(capture * capture, axis=0) < 1e-12):
        raise ValueError("Both channels need non-zero energy for fidelity metrics")
    gain = np.sum(reference * capture, axis=0) / energy
    ref_rms = db(np.sqrt(np.mean(reference * reference, axis=0)))
    capture_rms = db(np.sqrt(np.mean(capture * capture, axis=0)))
    residual = capture - reference * gain
    return {
        "gain": gain.tolist(),
        "gainDb": db(np.abs(gain)).tolist(),
        "correlation": [float(np.corrcoef(reference[:, ch], capture[:, ch])[0, 1]) for ch in range(2)],
        "referenceRmsDbFS": ref_rms.tolist(),
        "captureRmsDbFS": capture_rms.tolist(),
        "residualRmsDbFS": db(np.sqrt(np.mean(residual * residual, axis=0))).tolist(),
        "referenceRightOverLeftDb": float(ref_rms[1] - ref_rms[0]),
        "captureRightOverLeftDb": float(capture_rms[1] - capture_rms[0]),
        "channelSeparationChangeDb": float((capture_rms[1] - capture_rms[0]) - (ref_rms[1] - ref_rms[0])),
        "mixMatrix": np.linalg.lstsq(reference, capture, rcond=None)[0].tolist(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--reference-start", type=float, default=0)
    parser.add_argument("--capture", type=Path, action="append", required=True)
    parser.add_argument("--capture-start", type=float, default=6)
    parser.add_argument("--duration", type=float, default=16)
    parser.add_argument("--common-source-start", type=float)
    parser.add_argument("--common-duration", type=float, default=14)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() in {path.resolve() for path in [args.reference, *args.capture]}:
        parser.error("Output must not overwrite an input WAV")
    rate, reference = read_stereo(args.reference)
    start = round(args.capture_start * rate)
    count = round(args.duration * rate)
    aligned = []
    results = []
    for path in args.capture:
        capture_rate, capture = read_stereo(path)
        if capture_rate != rate:
            raise ValueError(f"{path}: sample rate differs from reference")
        lag, confidence = align(reference, capture, rate, start)
        result = metrics(window(reference, start + lag, count), window(capture, start, count))
        result.update({
            "file": path.name,
            "lagSamples": lag,
            "alignmentCorrelation": confidence,
            "alignedSourceStartSec": args.reference_start + (start + lag) / rate,
            "durationSec": count / rate,
        })
        results.append(result)
        aligned.append((path.name, capture, lag))
    report = {"sampleRate": rate, "channelOrder": ["left", "right"], "captures": results}
    if args.common_source_start is not None:
        common_start = round((args.common_source_start - args.reference_start) * rate)
        common_count = round(args.common_duration * rate)
        window(reference, common_start, common_count)
        first_name, first, first_lag = aligned[0]
        baseline = window(first, common_start - first_lag, common_count)
        comparisons = []
        for name, capture, lag in aligned[1:]:
            segment = window(capture, common_start - lag, common_count)
            comparisons.append({
                "first": first_name,
                "second": name,
                "sourceStartSec": args.common_source_start,
                "durationSec": common_count / rate,
                "frames": common_count,
                "samples": int(baseline.size),
                "differentSamples": int(np.count_nonzero(segment != baseline)),
                "maxAbsoluteDifferenceFS": float(np.max(np.abs(segment - baseline))),
                "bitIdentical": bool(np.array_equal(segment, baseline)),
            })
        report["captureComparisons"] = comparisons
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    print(f"Compared {len(results)} capture(s) at {rate} Hz; results: {args.output}")


if __name__ == "__main__":
    main()
