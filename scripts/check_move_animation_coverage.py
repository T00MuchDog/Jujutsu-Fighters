#!/usr/bin/env python3
"""Check current move coverage, sheet paths and animation references.

Bindings follow catalog order (first pack wins). PNG decoding and playback are
covered by the graphics module's bundled-asset smoke tests.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ANIMATIONS = ROOT / "graphics/src/main/resources/assets/animations"
DEFAULT_MOVES = ROOT / "data/moves/all_moves.json"


def read_json(path, label):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ValueError(f"cannot read {label} {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"malformed JSON in {label} {path}: {exc}") from exc


def audit(animations, moves_path):
    if not animations.is_dir():
        raise ValueError(f"animations path is not a directory: {animations}")
    if not moves_path.is_file():
        raise ValueError(f"moves path is not a file: {moves_path}")
    data = read_json(moves_path, "move data")
    if not isinstance(data, list):
        raise ValueError("move data must be an array")
    moves = {}
    for index, move in enumerate(data):
        move_id = move.get("id") if isinstance(move, dict) else None
        if not isinstance(move_id, str) or not move_id:
            raise ValueError(f"move[{index}] must have a non-empty string id")
        if move_id in moves:
            raise ValueError(f"duplicate move id: {move_id}")
        moves[move_id] = move
    catalog = read_json(animations / "catalog.json", "catalog")
    packs = catalog.get("packs") if isinstance(catalog, dict) else None
    if not isinstance(packs, list):
        raise ValueError("catalog must be an object with a packs array")
    errors, effects, bindings, casts = [], {}, [], []
    for pack_index, pack in enumerate(packs):
        if not isinstance(pack, str) or not pack:
            raise ValueError(f"catalog packs[{pack_index}] must be a non-empty string")
        manifest_path = animations / pack / "manifest.json"
        if not manifest_path.is_file():
            raise ValueError(f"manifest path does not exist: {manifest_path}")
        manifest = read_json(manifest_path, f"manifest for pack {pack!r}")
        entries = manifest.get("effects") if isinstance(manifest, dict) else None
        if not isinstance(entries, list):
            raise ValueError(f"manifest for pack {pack!r} must have an effects array")
        pack_bindings = set()
        for index, effect in enumerate(entries):
            effect_id = effect.get("id") if isinstance(effect, dict) else None
            if not isinstance(effect_id, str) or not effect_id:
                raise ValueError(f"{pack}/effects[{index}] must have a non-empty string id")
            if effect_id in effects:
                errors.append(f"duplicate effect id: {effect_id}")
            else:
                effects[effect_id] = (pack, effect)
            sheet = effect.get("sheet")
            if not isinstance(sheet, str) or not sheet:
                raise ValueError(f"{pack}/{effect_id}.sheet must be a non-empty string")
            if not (manifest_path.parent / sheet).is_file():
                errors.append(f"missing sheet file: {pack}/{sheet}")
            reinforcement = effect.get("reinforcement")
            if reinforcement is not None:
                overlay = reinforcement.get("sheet") if isinstance(reinforcement, dict) else None
                if not isinstance(overlay, str) or not overlay:
                    raise ValueError(f"{pack}/{effect_id}.reinforcement must have a sheet")
                if not (manifest_path.parent / overlay).is_file():
                    errors.append(f"missing reinforcement sheet file: {pack}/{overlay}")
            if "castEffect" in effect:
                casts.append((pack, effect_id, effect["castEffect"]))
            move_ids = effect.get("moveIds", [])
            if not isinstance(move_ids, list):
                raise ValueError(f"{pack}/{effect_id}.moveIds must be an array")
            for move_id in move_ids:
                if not isinstance(move_id, str) or not move_id:
                    raise ValueError(f"{pack}/{effect_id}.moveIds must contain strings")
                if move_id in pack_bindings:
                    errors.append(f"duplicate move binding within pack {pack}: {move_id}")
                pack_bindings.add(move_id)
                if move_id not in moves:
                    errors.append(f"unknown move reference: {pack}/{effect_id} -> {move_id}")
                bindings.append((pack, effect_id, move_id))
    for pack, effect_id, cast_id in casts:
        target = effects.get(cast_id) if isinstance(cast_id, str) else None
        if target is None:
            errors.append(f"dangling castEffect: {pack}/{effect_id} -> {cast_id!r}")
        elif not str(target[1].get("placement", "")).startswith("source"):
            errors.append(f"castEffect is not a source effect: {cast_id}")
    choreography = read_json(animations / "choreography.json", "choreography")
    profiles = choreography.get("profiles") if isinstance(choreography, dict) else None
    if not isinstance(profiles, dict):
        raise ValueError("choreography must be an object with profiles")
    for profile_id, profile in profiles.items():
        if not isinstance(profile, dict):
            raise ValueError(f"choreography profile {profile_id!r} must be an object")
        layers = profile.get("layers", [])
        if not isinstance(layers, list):
            raise ValueError(f"choreography profile {profile_id!r}.layers must be an array")
        for index, layer in enumerate(layers):
            layer_id = layer.get("effect") if isinstance(layer, dict) else None
            if not isinstance(layer_id, str) or layer_id not in effects:
                errors.append(f"dangling choreography layer effect: {profile_id}[{index}] -> {layer_id!r}")
    for field in ("effects", "events", "roles"):
        mapping = choreography.get(field, {})
        if not isinstance(mapping, dict):
            raise ValueError(f"choreography {field} must be an object")
        for key, profile_id in mapping.items():
            if not isinstance(profile_id, str) or profile_id not in profiles:
                errors.append(f"choreography {field} binding {key!r} names unknown profile {profile_id!r}")
            if field == "effects" and key not in effects:
                errors.append(f"dangling choreography effect reference: {key}")
    effective, shadowed = {}, 0
    for pack, effect_id, move_id in bindings:
        if move_id in effective:
            shadowed += 1
        else:
            effective[move_id] = (pack, effect_id)
    missing = [(move_id, move.get("name", "<unnamed>")) for move_id, move in moves.items()
               if move_id not in effective]
    covered = len(moves) - len(missing)
    percent = 100 * covered / len(moves) if moves else 0.0
    print(f"Total moves: {len(moves)}")
    print(f"Effective coverage: {covered}/{len(moves)} ({percent:.1f}%)")
    print("Missing moves:")
    for move_id, name in missing:
        print(f"  {move_id}: {name}")
    if not missing:
        print("  none")
    print(f"Shadowed binding count: {shadowed}")
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    return 1 if missing or errors else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--animations", type=Path, default=DEFAULT_ANIMATIONS)
    parser.add_argument("--moves", type=Path, default=DEFAULT_MOVES)
    parsed = parser.parse_args()
    try:
        return audit(parsed.animations, parsed.moves)
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
