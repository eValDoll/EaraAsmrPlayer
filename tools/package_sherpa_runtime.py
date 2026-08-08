#!/usr/bin/env python3
"""Build the pinned arm64 sherpa-onnx runtime asset used by the Android app."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import tempfile
import urllib.request
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


VERSION = "1.13.2"
ASSET_NAME = f"sherpa-onnx-runtime-{VERSION}-android-arm64-v8a.zip"
UPSTREAM_AAR_URL = (
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/"
    f"v{VERSION}/sherpa-onnx-{VERSION}.aar"
)
UPSTREAM_AAR_SHA256 = "aa5505c0ec4f8bdaee5f214a64ba3012be64f2aecc022e82a64f33392b8dd245"
EXPECTED_ARCHIVE_SHA256 = "bfa564c5da27a7ab734d4c788cafd7c95c1e4934e02056be24358532d3d33c2e"
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
RUNTIME_LIBRARIES = {
    "arm64-v8a/libonnxruntime.so": {
        "source": "jni/arm64-v8a/libonnxruntime.so",
        "bytes": 25_831_632,
        "sha256": "4d2318b3849abb8862133d3068fc7e807ed8b2671cc6d83657fff2fcb9e1caad",
    },
    "arm64-v8a/libsherpa-onnx-jni.so": {
        "source": "jni/arm64-v8a/libsherpa-onnx-jni.so",
        "bytes": 4_623_192,
        "sha256": "fc072f201dc1923ee98b594eb61c796b538ef087f7f18d08dcfdf0565167a8bd",
    },
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_file(path: Path, expected_bytes: int, expected_sha256: str) -> None:
    actual_bytes = path.stat().st_size
    if actual_bytes != expected_bytes:
        raise ValueError(f"Unexpected size for {path}: {actual_bytes} != {expected_bytes}")
    actual_sha256 = sha256_file(path)
    if actual_sha256 != expected_sha256:
        raise ValueError(f"Unexpected SHA-256 for {path}: {actual_sha256}")


def download_upstream_aar(destination: Path) -> None:
    request = urllib.request.Request(UPSTREAM_AAR_URL, headers={"User-Agent": "Eara-runtime-packager"})
    with urllib.request.urlopen(request) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output, length=1024 * 1024)


def write_runtime_zip(aar_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_output = output_path.with_suffix(".zip.part")
    temporary_output.unlink(missing_ok=True)
    with ZipFile(aar_path) as aar, ZipFile(
        temporary_output,
        mode="w",
        compression=ZIP_DEFLATED,
        compresslevel=9,
        strict_timestamps=True,
    ) as output:
        for archive_name in sorted(RUNTIME_LIBRARIES):
            descriptor = RUNTIME_LIBRARIES[archive_name]
            data = aar.read(str(descriptor["source"]))
            if len(data) != descriptor["bytes"]:
                raise ValueError(f"Unexpected size for {descriptor['source']}")
            if hashlib.sha256(data).hexdigest() != descriptor["sha256"]:
                raise ValueError(f"Unexpected SHA-256 for {descriptor['source']}")
            info = ZipInfo(filename=archive_name, date_time=ZIP_TIMESTAMP)
            info.compress_type = ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100444 << 16
            output.writestr(info, data, compress_type=ZIP_DEFLATED, compresslevel=9)
    temporary_output.replace(output_path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", type=Path, help="Use an existing pinned upstream AAR")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("build/runtime-assets"),
        help="Directory for the runtime ZIP and manifest",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    with tempfile.TemporaryDirectory(prefix="eara-sherpa-runtime-") as temporary_dir:
        if args.aar is None:
            aar_path = Path(temporary_dir) / f"sherpa-onnx-{VERSION}.aar"
            download_upstream_aar(aar_path)
        else:
            aar_path = args.aar.resolve()
        require_file(aar_path, 56_655_608, UPSTREAM_AAR_SHA256)

        output_path = args.output_dir.resolve() / ASSET_NAME
        write_runtime_zip(aar_path, output_path)
        archive_sha256 = sha256_file(output_path)
        if EXPECTED_ARCHIVE_SHA256 and archive_sha256 != EXPECTED_ARCHIVE_SHA256:
            output_path.unlink(missing_ok=True)
            raise ValueError(
                "Runtime ZIP differs from the pinned release artifact: "
                f"{archive_sha256} != {EXPECTED_ARCHIVE_SHA256}"
            )

        manifest = {
            "asset": output_path.name,
            "bytes": output_path.stat().st_size,
            "sha256": archive_sha256,
            "upstream": UPSTREAM_AAR_URL,
            "libraries": RUNTIME_LIBRARIES,
        }
        manifest_path = output_path.with_suffix(".json")
        manifest_path.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
