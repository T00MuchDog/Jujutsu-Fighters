#!/usr/bin/env python3
"""Import the runtime portion of a JJKTBF pixel-effects pack.

The pack is an authored export, so this importer deliberately does not run its
generator or copy its preview/tooling files.  It copies only paths named by the
manifest that are valid runtime assets: base sprite sheets, independent
reinforcement overlays, the manifest, and credits.  WAV cues can be included
with ``--with-audio``.

Existing files are replaced only when they are one of the selected files.  No
destination cleanup or deletion is performed, making repeated imports safe.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path, PurePosixPath
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DESTINATION = ROOT / "graphics/src/main/resources/assets/animations/pixel-fx"
DEFAULT_MOVES = ROOT / "data/moves/all_moves.json"


class ImportError(ValueError):
    """Raised when a pack does not satisfy the import contract."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Import runtime pixel-FX sheets and report manifest bindings."
    )
    parser.add_argument(
        "source",
        type=Path,
        help="exported pack directory",
    )
    parser.add_argument(
        "-d",
        "--destination",
        type=Path,
        default=DEFAULT_DESTINATION,
        help=f"import destination (default: {DEFAULT_DESTINATION})",
    )
    parser.add_argument(
        "--moves",
        type=Path,
        default=DEFAULT_MOVES,
        help=f"current move JSON used for binding validation (default: {DEFAULT_MOVES})",
    )
    parser.add_argument(
        "--with-audio",
        action="store_true",
        help="also copy manifest-referenced WAV cues under audio/",
    )
    return parser.parse_args()


def _relative_source_file(source: Path, raw: object, label: str) -> tuple[str, Path]:
    """Validate a manifest path and resolve it inside *source*.

    Manifest paths use POSIX separators.  Rejecting backslashes as well avoids
    a Windows-style traversal becoming an ordinary filename on this platform.
    Resolution also protects against a source symlink that points outside the
    supplied pack.
    """

    if not isinstance(raw, str) or not raw:
        raise ImportError(f"{label} must be a non-empty relative path")
    if "\\" in raw or ":" in raw or "\0" in raw:
        raise ImportError(f"{label} must use POSIX relative paths: {raw!r}")
    relative = PurePosixPath(raw)
    if relative.is_absolute() or ".." in relative.parts or "." in relative.parts:
        raise ImportError(f"{label} contains an unsafe path: {raw!r}")
    source_root = source.resolve()
    resolved = (source / Path(*relative.parts)).resolve()
    try:
        resolved.relative_to(source_root)
    except ValueError as exc:
        raise ImportError(f"{label} escapes the source pack: {raw!r}") from exc
    if not resolved.is_file():
        raise ImportError(f"{label} does not exist: {raw}")
    return raw, resolved


def _check_manifest(source: Path) -> tuple[dict, list[tuple[str, Path]]]:
    manifest_path = source / "manifest.json"
    if not manifest_path.is_file():
        raise ImportError(f"missing manifest: {manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ImportError(f"cannot read manifest: {exc}") from exc
    if not isinstance(manifest, dict) or manifest.get("schemaVersion") != 1:
        raise ImportError("manifest schemaVersion must be exactly 1")
    effects = manifest.get("effects")
    if not isinstance(effects, list):
        raise ImportError("manifest effects must be an array")

    seen_ids: set[str] = set()
    runtime: list[tuple[str, Path]] = []
    for index, effect in enumerate(effects):
        if not isinstance(effect, dict):
            raise ImportError(f"effects[{index}] must be an object")
        effect_id = effect.get("id")
        if not isinstance(effect_id, str) or not effect_id:
            raise ImportError(f"effects[{index}] has no usable id")
        if effect_id in seen_ids:
            raise ImportError(f"duplicate effect id: {effect_id}")
        seen_ids.add(effect_id)

        reinforcement = effect.get("reinforcement")
        if reinforcement is not None:
            if not isinstance(reinforcement, dict):
                raise ImportError(f"{effect_id}.reinforcement must be an object")

        sheet = effect.get("sheet")
        if not isinstance(sheet, str) or not sheet.startswith("sprites/"):
            raise ImportError(f"{effect_id}.sheet must be a runtime sprites/*.png path")
        if not sheet.lower().endswith(".png"):
            raise ImportError(f"{effect_id}.sheet must be a PNG sheet: {sheet}")
        _, sheet_path = _relative_source_file(source, sheet, f"{effect_id}.sheet")
        runtime.append((sheet, sheet_path))

        if reinforcement is not None and "sheet" in reinforcement:
            overlay = reinforcement["sheet"]
            if not isinstance(overlay, str) or not overlay.startswith("overlays/"):
                raise ImportError(
                    f"{effect_id}.reinforcement.sheet must be an overlays/*.png path"
                )
            if not overlay.lower().endswith(".png"):
                raise ImportError(
                    f"{effect_id}.reinforcement.sheet must be a PNG sheet: {overlay}"
                )
            _, overlay_path = _relative_source_file(
                source, overlay, f"{effect_id}.reinforcement.sheet"
            )
            runtime.append((overlay, overlay_path))

    return manifest, runtime


def _check_credits(source: Path) -> Path:
    credits = source / "CREDITS.md"
    if not credits.is_file():
        raise ImportError(f"missing required credits/license file: {credits}")
    text = credits.read_text(encoding="utf-8")
    if not text.strip():
        raise ImportError("CREDITS.md is empty")
    if not re.search(r"license|licen[cs]e|permission|reuse|rights", text, re.I):
        raise ImportError("CREDITS.md must contain license, permission, reuse, or rights information")
    return credits


def _copy(source_file: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, destination)


def _import_files(
    source: Path,
    destination: Path,
    runtime: Iterable[tuple[str, Path]],
    credits: Path,
    with_audio: bool,
    manifest: dict,
) -> list[str]:
    destination = destination.resolve()
    if destination.exists() and not destination.is_dir():
        raise ImportError(f"destination is not a directory: {destination}")
    destination.mkdir(parents=True, exist_ok=True)

    selected: dict[str, Path] = {
        "manifest.json": source / "manifest.json",
        "CREDITS.md": credits,
    }
    for relative, path in runtime:
        selected[relative] = path

    if with_audio:
        for effect in manifest["effects"]:
            if "sound" not in effect:
                continue
            sound, sound_path = _relative_source_file(
                source, effect["sound"], f"{effect['id']}.sound"
            )
            if not sound.startswith("audio/") or not sound.lower().endswith(".wav"):
                raise ImportError(
                    f"{effect['id']}.sound must be an audio/*.wav path: {sound}"
                )
            selected[sound] = sound_path

    copied: list[str] = []
    for relative, source_file in sorted(selected.items()):
        target = destination / Path(*PurePosixPath(relative).parts)
        if not target.resolve().is_relative_to(destination):
            raise ImportError(f"destination symlink escapes animation directory: {relative}")
        _copy(source_file, target)
        copied.append(relative)
    return copied


def _binding_report(manifest: dict, moves_path: Path) -> None:
    try:
        moves = json.loads(moves_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ImportError(f"cannot read move data: {exc}") from exc
    if not isinstance(moves, list):
        raise ImportError("move data must be an array")
    move_by_id = {str(move.get("id")): move for move in moves if isinstance(move, dict)}

    print("\nMove bindings:")
    valid = 0
    invalid = 0
    unbound: list[str] = []
    for effect in manifest["effects"]:
        effect_id = effect["id"]
        move_ids = effect.get("moveIds", [])
        if not move_ids:
            unbound.append(effect_id)
            continue
        for move_id in move_ids:
            move_id = str(move_id)
            move = move_by_id.get(move_id)
            if move is None:
                invalid += 1
                print(f"  INVALID  {effect_id} -> {move_id}")
            else:
                valid += 1
                print(f"  valid    {effect_id} -> {move_id} ({move.get('name', '<unnamed>')})")
    print(f"  totals: {valid} valid, {invalid} invalid")
    print(f"  unbound effects: {', '.join(unbound) if unbound else 'none'}")

    print("\nAttack effect impact frames:")
    for effect in manifest["effects"]:
        if effect.get("role") != "attack":
            continue
        frames = effect.get("impactFrames", [])
        move_ids = [str(move_id) for move_id in effect.get("moveIds", [])]
        hit_details = []
        for move_id in move_ids:
            move = move_by_id.get(move_id)
            if move is None:
                hit_details.append(f"{move_id}:missing")
                continue
            components = move.get("hitComponents", [])
            delays = [component.get("delayTicks") for component in components]
            hit_details.append(f"{move_id}:hits={len(components)},delays={delays}")
        kind = "multi-hit" if len(frames) > 1 else "single-hit/visual"
        details = "; ".join(hit_details) if hit_details else "unbound"
        print(
            f"  {effect['id']}: impactFrames={frames}; {kind}; "
            f"moveIds={move_ids}; authored={details}"
        )


def main() -> int:
    args = parse_args()
    source = args.source.expanduser()
    if not source.is_dir():
        print(f"error: source is not a directory: {source}", file=sys.stderr)
        return 2
    try:
        manifest, runtime = _check_manifest(source)
        credits = _check_credits(source)
        copied = _import_files(
            source,
            args.destination.expanduser(),
            runtime,
            credits,
            args.with_audio,
            manifest,
        )
        _binding_report(manifest, args.moves.expanduser())
    except (ImportError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    print(
        f"\nImported {len(copied)} files into "
        f"{args.destination.expanduser().resolve()} (audio={'yes' if args.with_audio else 'no'})."
    )
    print("No destination files were deleted; previews, composites, generators, and frames were excluded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
