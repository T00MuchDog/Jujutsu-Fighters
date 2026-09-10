#!/usr/bin/env python3
"""Audit move animation bindings and references in the runtime animation catalog.

Bindings follow the desktop loader's order: the first catalog pack wins when
several packs bind the same move.  This check intentionally inspects metadata
and file paths only; it does not decode PNG sheets.
"""

import argparse
import json
import sys
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ANIMATIONS = ROOT / "graphics/src/main/resources/assets/animations"
DEFAULT_MOVES = ROOT / "data/moves/all_moves.json"


class AuditError(ValueError):
    """Raised for an unreadable or structurally invalid input file."""


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--animations",
        type=Path,
        default=DEFAULT_ANIMATIONS,
        help=f"animation asset directory (default: {DEFAULT_ANIMATIONS})",
    )
    parser.add_argument(
        "--moves",
        type=Path,
        default=DEFAULT_MOVES,
        help=f"move JSON file (default: {DEFAULT_MOVES})",
    )
    return parser.parse_args()


def read_json(path: Path, label: str):
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise AuditError(f"cannot read {label} {path}: {exc}") from exc
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise AuditError(f"malformed JSON in {label} {path}: {exc}") from exc


def require_directory(path: Path, label: str):
    if not path.is_dir():
        if not path.exists():
            raise AuditError(f"{label} path does not exist: {path}")
        raise AuditError(f"{label} path is not a directory: {path}")


def require_file(path: Path, label: str):
    if not path.is_file():
        if not path.exists():
            raise AuditError(f"{label} path does not exist: {path}")
        raise AuditError(f"{label} path is not a file: {path}")


def safe_relative_path(raw, label: str):
    if not isinstance(raw, str) or not raw:
        raise AuditError(f"{label} must be a non-empty relative path")
    if "\\" in raw or ":" in raw or "\0" in raw:
        raise AuditError(f"{label} contains an unsafe path: {raw!r}")
    relative = PurePosixPath(raw)
    if relative.is_absolute() or any(part in ("", ".", "..") for part in relative.parts):
        raise AuditError(f"{label} contains an unsafe path: {raw!r}")
    return relative


def pack_path(animations: Path, raw, label: str):
    relative = safe_relative_path(raw, label)
    root = animations.resolve()
    path = (animations / Path(*relative.parts)).resolve()
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise AuditError(f"{label} escapes the animation directory: {raw!r}") from exc
    return path


def load_moves(path: Path):
    require_file(path, "moves")
    moves = read_json(path, "move data")
    if not isinstance(moves, list):
        raise AuditError("move data must be an array")

    by_id = {}
    errors = []
    for index, move in enumerate(moves):
        if not isinstance(move, dict):
            errors.append(f"move[{index}] must be an object")
            continue
        move_id = move.get("id")
        if not isinstance(move_id, str) or not move_id:
            errors.append(f"move[{index}] has no usable id")
            continue
        if move_id in by_id:
            errors.append(f"duplicate move id: {move_id}")
            continue
        by_id[move_id] = move
    if errors:
        raise AuditError("; ".join(errors))
    return by_id


def check_manifest(animations: Path, pack_name, manifest, moves, effect_by_id, cast_refs, errors):
    if not isinstance(manifest, dict):
        raise AuditError(f"manifest for pack {pack_name!r} must be an object")
    if manifest.get("schemaVersion") != 1:
        raise AuditError(f"manifest for pack {pack_name!r} has unsupported schemaVersion")
    effects = manifest.get("effects")
    if not isinstance(effects, list):
        raise AuditError(f"manifest for pack {pack_name!r} effects must be an array")

    seen_effects = set()
    bindings = {"moveIds": set(), "eventTypes": set(), "statusTypes": set()}
    pack_root = pack_path(animations, pack_name, "catalog pack")
    for index, effect in enumerate(effects):
        label = f"{pack_name}/effects[{index}]"
        if not isinstance(effect, dict):
            errors.append(f"{label} must be an object")
            continue
        effect_id = effect.get("id")
        if not isinstance(effect_id, str) or not effect_id:
            errors.append(f"{label} has no usable id")
            continue
        if effect_id in seen_effects:
            errors.append(f"duplicate effect id within pack {pack_name}: {effect_id}")
        seen_effects.add(effect_id)
        if effect_id in effect_by_id:
            errors.append(f"duplicate effect id: {effect_id}")
        else:
            effect_by_id[effect_id] = (pack_name, effect)

        if "castEffect" in effect:
            cast_refs.append((pack_name, effect_id, effect.get("castEffect")))

        sheet = effect.get("sheet")
        try:
            sheet_path = pack_root / Path(*safe_relative_path(sheet, f"{effect_id}.sheet").parts)
            sheet_path.relative_to(pack_root)
            if not sheet_path.is_file():
                errors.append(f"missing sheet file for {effect_id}: {pack_root / str(sheet)}")
        except AuditError as exc:
            errors.append(str(exc))

        reinforcement = effect.get("reinforcement")
        if reinforcement is not None:
            if not isinstance(reinforcement, dict):
                errors.append(f"{effect_id}.reinforcement must be an object")
            elif "sheet" in reinforcement:
                try:
                    overlay = safe_relative_path(reinforcement["sheet"], f"{effect_id}.reinforcement.sheet")
                    overlay_path = pack_root / Path(*overlay.parts)
                    overlay_path.relative_to(pack_root)
                    if not overlay_path.is_file():
                        errors.append(
                            f"missing sheet file for {effect_id} reinforcement: {overlay_path}"
                        )
                except AuditError as exc:
                    errors.append(str(exc))

        for field in bindings:
            values = effect.get(field, [])
            if not isinstance(values, list):
                errors.append(f"{effect_id}.{field} must be an array")
                continue
            local_values = set()
            for value in values:
                if not isinstance(value, str) or not value:
                    errors.append(f"{effect_id}.{field} must contain non-empty strings")
                    continue
                if value in local_values:
                    errors.append(f"duplicate {field} binding within pack {pack_name}: {value}")
                local_values.add(value)
                if value in bindings[field]:
                    errors.append(f"duplicate {field} binding within pack {pack_name}: {value}")
                bindings[field].add(value)

                if field == "moveIds" and value not in moves:
                    errors.append(f"unknown move reference in {pack_name}/{effect_id}: {value}")

    return bindings


def load_catalog(animations: Path, moves):
    catalog_path = animations / "catalog.json"
    require_file(catalog_path, "catalog")
    catalog = read_json(catalog_path, "catalog")
    if not isinstance(catalog, dict):
        raise AuditError("catalog must be an object")
    if catalog.get("schemaVersion") != 1:
        raise AuditError("catalog has unsupported schemaVersion")
    packs = catalog.get("packs")
    if not isinstance(packs, list):
        raise AuditError("catalog packs must be an array")

    errors = []
    effect_by_id = {}
    cast_refs = []
    pack_effects = []
    for index, pack_name in enumerate(packs):
        try:
            manifest_path = pack_path(animations, pack_name, f"catalog packs[{index}]") / "manifest.json"
        except AuditError as exc:
            raise exc
        require_file(manifest_path, f"manifest for pack {pack_name!r}")
        manifest = read_json(manifest_path, f"manifest for pack {pack_name!r}")
        check_manifest(animations, pack_name, manifest, moves, effect_by_id, cast_refs, errors)
        pack_effects.append((str(pack_name), manifest.get("effects", [])))
    for pack_name, effect_id, cast_effect in cast_refs:
        if not isinstance(cast_effect, str) or not cast_effect:
            errors.append(f"{pack_name}/{effect_id}.castEffect must be a non-empty string")
            continue
        target = effect_by_id.get(cast_effect)
        if target is None:
            errors.append(f"dangling castEffect in {pack_name}/{effect_id}: {cast_effect}")
        elif target[1].get("placement") != "source":
            errors.append(f"castEffect must reference a source effect: {cast_effect}")
    return pack_effects, effect_by_id, errors


def check_choreography(animations: Path, effect_by_id, errors):
    path = animations / "choreography.json"
    require_file(path, "choreography")
    choreography = read_json(path, "choreography")
    if not isinstance(choreography, dict):
        raise AuditError("choreography must be an object")
    if choreography.get("schemaVersion") != 1:
        raise AuditError("choreography has unsupported schemaVersion")
    profiles = choreography.get("profiles")
    if not isinstance(profiles, dict):
        raise AuditError("choreography profiles must be an object")

    for profile_name, profile in profiles.items():
        if not isinstance(profile, dict):
            errors.append(f"choreography profile {profile_name!r} must be an object")
            continue
        layers = profile.get("layers", [])
        if not isinstance(layers, list):
            errors.append(f"choreography profile {profile_name!r} layers must be an array")
            continue
        for index, layer in enumerate(layers):
            if not isinstance(layer, dict):
                errors.append(f"choreography profile {profile_name!r} layer[{index}] must be an object")
                continue
            effect = layer.get("effect")
            if not isinstance(effect, str) or not effect:
                errors.append(f"choreography profile {profile_name!r} layer[{index}] has no effect")
            elif effect not in effect_by_id:
                errors.append(
                    f"dangling choreography layer effect in {profile_name!r}: {effect}"
                )

    for field in ("effects", "events", "roles"):
        bindings = choreography.get(field, {})
        if not isinstance(bindings, dict):
            raise AuditError(f"choreography {field} must be an object")
        for key, profile_name in bindings.items():
            if not isinstance(profile_name, str) or profile_name not in profiles:
                errors.append(f"choreography {field} binding {key!r} names unknown profile {profile_name!r}")
            if field == "effects" and key not in effect_by_id:
                errors.append(f"dangling choreography effect reference: {key}")


def audit(animations: Path, moves_path: Path):
    moves = load_moves(moves_path)
    pack_effects, effect_by_id, errors = load_catalog(animations, moves)
    check_choreography(animations, effect_by_id, errors)

    effective = {}
    shadowed = 0
    for pack_name, effects in pack_effects:
        for effect in effects:
            if not isinstance(effect, dict):
                continue
            effect_id = effect.get("id", "<unnamed>")
            move_ids = effect.get("moveIds", [])
            if not isinstance(move_ids, list):
                continue
            for move_id in move_ids:
                if not isinstance(move_id, str):
                    continue
                previous = effective.get(move_id)
                if previous is not None:
                    if previous[0] != pack_name:
                        shadowed += 1
                    continue
                effective[move_id] = (pack_name, effect_id)

    missing = [(move_id, move.get("name", "<unnamed>"))
               for move_id, move in moves.items() if move_id not in effective]
    covered = len(moves) - len(missing)
    print(f"Total moves: {len(moves)}")
    print(f"Effective coverage: {covered}/{len(moves)} ({covered / len(moves) * 100:.1f}%)")
    print("Missing moves:")
    if missing:
        for move_id, name in missing:
            print(f"  {move_id}: {name}")
    else:
        print("  none")
    print(f"Shadowed binding count: {shadowed}")
    if errors:
        print("Errors:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
    return 1 if errors or missing else 0


def main():
    args = parse_args()
    try:
        require_directory(args.animations, "animations")
        return audit(args.animations, args.moves)
    except AuditError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
