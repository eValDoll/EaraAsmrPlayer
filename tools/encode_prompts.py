#!/usr/bin/env python3
"""Encode plaintext LLM prompts into a repository-committed obfuscated resource.

Workflow:
    - Edit plaintext prompts under the gitignored `prompts/` directory (UTF-8).
    - Run this script to regenerate:
        app/src/main/java/com/asmr/player/subtitle/TranslationPromptsEncoded.kt
    - The generated file is committed to the repo and contains NO plaintext:
      each prompt is XOR-ed with a key and then Base64 encoded.

The XOR key must stay in sync with `TranslationPromptsCodec` in
app/src/main/java/com/asmr/player/subtitle/TranslationPrompts.kt.
"""
import base64
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROMPTS_DIR = ROOT / "prompts"
OUTPUT_FILE = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "asmr"
    / "player"
    / "subtitle"
    / "TranslationPromptsEncoded.kt"
)

# Must match TranslationPromptsCodec.SUBTITLE_PROMPTS_XOR_KEY.
XOR_KEY = "EaraAsmrPlayer.SubtitlePrompt.V1"

CHUNK_LEN = 96


def xor_bytes(data: bytes, key: bytes) -> bytes:
    return bytes(b ^ key[i % len(key)] for i, b in enumerate(data))


def encode(text: str) -> str:
    return base64.b64encode(xor_bytes(text.encode("utf-8"), XOR_KEY.encode("utf-8"))).decode("ascii")


def main() -> int:
    if not PROMPTS_DIR.is_dir():
        print(f"error: {PROMPTS_DIR} not found", file=sys.stderr)
        return 1
    files = sorted(PROMPTS_DIR.glob("*.txt"))
    if not files:
        print(f"error: no *.txt files under {PROMPTS_DIR}", file=sys.stderr)
        return 1

    entries = []
    for path in files:
        key = path.stem
        text = path.read_text(encoding="utf-8")
        # Remove trailing line breaks so editors can keep POSIX newline endings.
        text = text.rstrip("\r\n")
        encoded = encode(text)
        # Self-check: round trip must reproduce the original text.
        decoded = base64.b64decode(encoded)
        unxored = xor_bytes(decoded, XOR_KEY.encode("utf-8")).decode("utf-8")
        if unxored != text:
            print(f"error: round-trip check failed for {path.name}", file=sys.stderr)
            return 1
        entries.append((key, encoded))

    lines = []
    lines.append("// GENERATED FILE - DO NOT EDIT BY HAND.")
    lines.append("// Regenerate with: python tools/encode_prompts.py")
    lines.append("// Source plaintext prompts live in the gitignored `prompts/` directory (UTF-8).")
    lines.append("// Values are XOR + Base64 obfuscated; see TranslationPromptsCodec.")
    lines.append("package com.asmr.player.subtitle")
    lines.append("")
    lines.append("internal object TranslationPromptsEncoded {")
    lines.append("    /**")
    lines.append("     * Encoded prompt entries, one per line: <key>=<base64>. No plaintext.")
    lines.append("     */")
    lines.append("    internal const val ENCODED_LINES: String =")
    for index, (key, encoded) in enumerate(entries):
        payload = f"{key}={encoded}"
        parts = [payload[i : i + CHUNK_LEN] for i in range(0, len(payload), CHUNK_LEN)]
        for part in parts:
            lines.append(f'        "{part}" +')
        lines.append('        "\\n"' + ("" if index == len(entries) - 1 else " +"))
    lines.append("}")
    lines.append("")

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUTPUT_FILE.relative_to(ROOT)}")
    print(f"  entries: {len(entries)}")
    for key, encoded in entries:
        print(f"    {key}: {len(encoded)} base64 chars")
    return 0


if __name__ == "__main__":
    sys.exit(main())
