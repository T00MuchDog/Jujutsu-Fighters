#!/usr/bin/env python3
"""One production-art review board, not a live-GL playtest. Requires Pillow."""
import argparse
import json

from PIL import Image, ImageDraw

import build_technique_animations as exporter


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="audit without writing the review board")
    args = parser.parse_args()
    art = json.loads((exporter.ART / "ten-shadows-summon-art.json").read_text())
    exporter.MOTIFS.update(art["motifs"])
    exporter.motif.cache_clear()
    effects = json.loads((exporter.ART / "ten-shadows.json").read_text())["effects"]
    summons = [s for s in effects if s.get("family") == "shadow-summons"]
    base, = art["effects"]
    profile = summons[0]["profile"]
    layer, = profile["layers"]
    assert layer["effect"] == base["id"] and layer["placement"] == "source-feet"
    assert layer["plane"] == "front" and layer.get("startSeconds", 0) == 0
    assert base["placement"] == "source-feet" and not base.get("moveIds")
    duration = base["frames"] * base["frameDurationMs"] / 1000
    assert profile["durationSeconds"] == layer["durationSeconds"] == duration
    assert not any(profile.get(k) for k in ("source", "target", "background"))

    # Discover coverage from current authored mechanics, never from editable costs or stats.
    moves = json.loads((exporter.ROOT / "data/moves/all_moves.json").read_text())
    expected = {m["id"] for m in moves if m.get("requiredTechniqueId") == "Ten Shadows"
                and any(e.get("type") == "SUMMON_CHARACTER" for e in m.get("effects", []))}
    bindings = [m for s in summons for m in s["moveIds"]]
    assert set(bindings) == expected and len(bindings) == len(expected)
    manifest = json.loads((exporter.ASSETS / "animations/techniques/manifest.json").read_text())
    by_move = {m: e["id"] for e in manifest["effects"] for m in e.get("moveIds", [])}
    for s in summons:
        assert s["profile"] == profile, f"Different summon choreography: {s['id']}"
        assert s["frames"] == base["frames"] and s["frameDurationMs"] == base["frameDurationMs"]
        assert s["impact"] == base["impact"]
        assert s["impact"] * s["frameDurationMs"] / 1000 == profile["impactSeconds"]
        assert s["placement"] == "source" and s["role"] == "utility"
        assert all(by_move[m] == s["id"] for m in s["moveIds"])
        assert all(r["kind"].startswith("art:sign-") and not r.get("flip") for r in s["art"])

    rendered = {}
    for spec in [base, *summons]:
        cels = [exporter.render(spec, i) for i in range(spec["frames"])]
        assert not cels[0].getbbox() and not cels[-1].getbbox(), "VFX must release completely"
        for cel in cels:
            assert cel.mode == "RGBA" and cel.size == (192, 192)
            assert cel.tobytes() == cel.resize((96, 96), Image.Resampling.NEAREST).resize(
                (192, 192), Image.Resampling.NEAREST).tobytes(), "Smoothed logical pixels"
            if spec is base:
                assert all(max(rgb[:3]) <= 64 for _, rgb in cel.getcolors(192 * 192) if rgb[3]), "Bright ink"
        rendered[spec["id"]] = cels
    ink = rendered[base["id"]]
    peak = base["impact"]
    assert any(rendered[s["id"]][1].getbbox() for s in summons) and not ink[1].getbbox()
    assert ink[peak].getbbox()[1] < ink[5].getbbox()[1], "Ink must rise from the pool"
    assert ink[-3].getbbox()[1] > ink[peak].getbbox()[1], "Ink must settle quickly"
    print(f"PASS: {len(summons)} summon bindings; one dark foot layer; identical timing; "
          "sign-only primary art; nearest-neighbour RGBA; finite release.")
    if args.check:
        return

    # 96px fighter tile, default Megumi scale, matching source-center / visible-foot anchors.
    sprite = Image.open(exporter.ASSETS / "sprites/characters/megumi_frontsprite.png").convert("RGBA")
    sprite = sprite.resize((96, 96), Image.Resampling.NEAREST)
    bounds = sprite.getbbox()
    ink_size = round((bounds[3] - bounds[1]) * layer["size"])
    hand_size = round(96 * profile["size"])
    samples = [(1, False), (5, False), (10, False), (peak, False), (21, False), (23, False), (peak, True)]
    board = Image.new("RGB", (1260, 54 + len(summons) * 154), "#17212b")
    draw = ImageDraw.Draw(board)
    draw.text((12, 9), "TEN SHADOWS / 96px fighter tile / production cels, source-center hands, visible-foot ink", fill="#e0d8c8")
    for j, (frame, mirrored) in enumerate(samples):
        label = "MIRRORED PEAK" if mirrored else f"{frame * base['frameDurationMs']}ms"
        draw.text((252 + j * 144, 32), label, fill="#e0d8c8")
    for i, spec in enumerate(summons):
        top = 54 + i * 154
        draw.rectangle((0, top, 1259, top + 153), fill="#b9bfc1" if i % 2 else "#e0d8c8")
        draw.text((12, top + 40), spec["moveIds"][0], fill="#19232d")
        draw.text((12, top + 60), spec["name"], fill="#19232d")
        for j, (frame, mirrored) in enumerate(samples):
            cx, sy = 312 + j * 144, top + 22
            fighter = sprite.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if mirrored else sprite
            board.paste(fighter, (cx - 48, sy), fighter)
            cel = ink[frame].resize((ink_size, ink_size), Image.Resampling.NEAREST)
            ink_top = round(sy + bounds[3] - (1 - base["anchor"][1]) * ink_size)
            board.paste(cel, (cx - ink_size // 2, ink_top), cel)
            cel = rendered[spec["id"]][frame].resize((hand_size, hand_size), Image.Resampling.NEAREST)
            if mirrored:
                cel = cel.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            board.paste(cel, (cx - hand_size // 2, sy + 48 - hand_size // 2), cel)
    board.save(exporter.ROOT / "docs/animations/techniques/shadow-summons-composite.png", optimize=True)


if __name__ == "__main__":
    main()
