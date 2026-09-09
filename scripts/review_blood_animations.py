#!/usr/bin/env python3
"""Review paired studies through the production technique renderer, not another exporter.

PNG boards and GIFs are review-only. The selected authored JSON remains the sole
source of runtime sheets; --check audits its material, continuity and timing.
"""
import argparse
import copy
import json
from pathlib import Path

from PIL import Image, ImageDraw

import build_technique_animations as exporter

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/animations/blood-manipulation"
IDS = ("ct-convergence", "ct-pressure-core", "ct-piercing-stream", "ct-piercing-blood")
BLOOD = {"#24090e", "#590b16", "#930d1d", "#bd2030", "#e34b4b"}
STUDIES = {
    "A-laminar": {"orbit": .85, "drops": 5, "colors": {}},
    "B-rotational": {"orbit": 1.7, "drops": 7,
        "colors": {"#930d1d": "#800d1c", "#bd2030": "#af1b2a", "#e34b4b": "#d73c42"}},
    "C-restrained": {"orbit": .3, "drops": 3, "colors": {}},
}


def load(study):
    doc = json.loads((exporter.ART / "blood-ratio.json").read_text())
    exporter.MOTIFS.clear()
    exporter.MOTIFS.update(doc["motifs"])
    exporter.motif.cache_clear()
    specs = {s["id"]: copy.deepcopy(s) for s in doc["effects"] if s["id"] in IDS}
    for row in specs[IDS[0]]["art"]:
        if row["kind"] == "art:wet-blood-drop":
            row.update(orbit=study["orbit"], repeat=study["drops"])
    for spec in specs.values():
        for row in spec["art"]:
            row["colors"] = study["colors"]
    return specs


def frames(spec):
    return [exporter.render(spec, i) for i in range(spec["frames"])]


def blood_area(cel, colors):
    palette = {tuple(bytes.fromhex(c[1:])) for c in colors}
    return sum(p[:3] in palette and p[3] == 255 for p in cel.resize((96, 96), Image.Resampling.NEAREST).getdata())


def validate(specs, rendered, study):
    colors = {study["colors"].get(c, c) for c in BLOOD}
    all_colors = colors | {"#35272b", "#79626a", "#b59080", "#d1af98"}
    palette = {tuple(bytes.fromhex(c[1:])) for c in all_colors}
    for cels in rendered.values():
        for cel in cels:
            assert cel.mode == "RGBA" and cel.size == (192, 192)
            assert set(cel.getchannel("A").getdata()) <= {0, 255}, "Partial alpha or smoothing"
            assert all(p[:3] in palette for p in cel.getdata() if p[3]), "Unplanned palette color"
            assert cel.tobytes() == cel.resize((96, 96), Image.Resampling.NEAREST).resize(
                (192, 192), Image.Resampling.NEAREST).tobytes(), "Non-integer logical pixels"
    convergence, core, stream, impact = (rendered[i] for i in IDS)
    # Isolate the shared blood crop, not the deliberately different hand arrangements.
    blood_rgb = {tuple(bytes.fromhex(c[1:])) for c in colors}
    def sphere_pixels(cel):
        return [p if p[:3] in blood_rgb else (0, 0, 0, 0)
                for p in cel.crop((88, 88, 104, 104)).getdata()]
    assert sphere_pixels(convergence[19]) == sphere_pixels(core[4])
    assert all(convergence[i].crop((88, 88, 104, 104)).tobytes() ==
               convergence[19].crop((88, 88, 104, 104)).tobytes() for i in range(16, 23))
    areas = [blood_area(cel, colors) for cel in convergence]
    assert areas[0] > areas[5] > areas[10] > areas[15] > areas[19] > 0, areas
    assert all(a >= b for a, b in zip(areas, areas[1:])), areas
    assert not convergence[-1].getbbox(), "Finite preparation must end"
    assert all(blood_area(c, colors) <= areas[19] for c in core[:5]), "Source re-compresses loose blood"
    for cel in stream:
        assert cel.getbbox() == (0, 92, 192, 100), "Stream must fill width and stay four logical pixels thick"
    assert all(not c.getbbox() for c in impact[:6]), "Contact before release"
    assert impact[6].getbbox() and not impact[-1].getbbox()
    profile = specs[IDS[3]]["profile"]
    assert profile["impactSeconds"] == profile["layers"][1]["startSeconds"] == .24
    assert profile["size"] == profile["layers"][0]["size"] == specs[IDS[0]]["profile"]["size"]
    assert not specs[IDS[0]]["profile"].get("target")
    assert not specs[IDS[0]]["profile"].get("layers")
    assert abs(exporter.interpolate({"keys": [[0, 8], [.4, 6], [1, 0]]}, .7) - 3) < 1e-9
    return areas


def composite(specs, rendered, time, piercing):
    """Neutral-distance review, no simulated fighter movement or claim of live testing."""
    canvas = Image.new("RGB", (640, 224), "#c3beb3")
    draw = ImageDraw.Draw(canvas)
    draw.line((96, 120, 544, 120), fill="#aaa89f")
    draw.text((18, 12), "PIERCING BLOOD / LOADED - SNAP - PUNCTURE" if piercing else
              "CONVERGENCE / GATHER - COMPRESS - HOLD", fill="#30292d")
    if piercing:
        # At 192px per fighter-height the beam cel is .72H, not the source's .9H.
        layers = [(IDS[2], .24, .12, (96, 51), (448, 138)),
                  (IDS[1], 0, .32, (10, 34), (173, 173)),
                  (IDS[3], 0, .72, (458, 34), (173, 173))]
    else:
        layers = [(IDS[0], 0, 1.2, (234, 34), (173, 173))]
    for id_, start, duration, position, size in layers:
        if start <= time < start + duration:
            cels = rendered[id_]
            cel = cels[min(len(cels)-1, int((time-start) / duration * len(cels)))]
            cel = cel.resize(size, Image.Resampling.NEAREST)
            canvas.paste(cel, position, cel)
    return canvas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        OUTPUT.mkdir(parents=True, exist_ok=True)
    metrics = {}
    for name, study in STUDIES.items():
        specs = load(study)
        rendered = {id_: frames(spec) for id_, spec in specs.items()}
        areas = validate(specs, rendered, study)
        metrics[name] = {"bloodAreaByConvergenceFrame": areas,
                         "sphereDiameterLogicalPixels": 8,
                         "streamThicknessFighterHeights": 4 / 96 * .72}
        if args.check:
            continue
        board = Image.new("RGB", (1152, 864), "#c3beb3")
        draw = ImageDraw.Draw(board)
        draw.text((16, 12), name + " / production renderer / 2x nearest-neighbour", fill="#30292d")
        selections = ([0, 5, 10, 15, 19, 22], [0, 2, 4, 5, 6, 7], [0, 1, 2], [5, 6, 7, 9, 11, 14])
        for row, (id_, indices) in enumerate(zip(IDS, selections)):
            y = 40 + row * 206
            draw.text((12, y), id_, fill="#30292d")
            for col, idx in enumerate(indices):
                cel = rendered[id_][idx]
                board.paste(cel, (col*192, y+14), cel)
                draw.text((col*192+12, y+24), str(idx), fill="#30292d")
        board.save(OUTPUT / f"{name}.png")
        continuity = Image.new("RGB", (768, 430), "#c3beb3")
        d = ImageDraw.Draw(continuity)
        for i, (id_, idx, label) in enumerate(((IDS[0], 19, "CONVERGENCE / held product"),
                                              (IDS[1], 4, "PIERCING / loaded product"))):
            d.text((i*384+16, 15), label, fill="#30292d")
            cel = rendered[id_][idx].resize((384, 384), Image.Resampling.NEAREST)
            continuity.paste(cel, (i*384, 40), cel)
        continuity.save(OUTPUT / f"{name}-continuity.png")
        # Includes a gap: continuity does not depend on consecutive use.
        previews = [composite(specs, rendered, i*.02, False) for i in range(60)]
        previews += [Image.new("RGB", (640, 224), "#c3beb3")] * 20
        previews += [composite(specs, rendered, i*.02, True) for i in range(36)]
        previews[0].save(OUTPUT / f"{name}-pair.gif", save_all=True,
                         append_images=previews[1:], duration=20, loop=0, disposal=2)
    if not args.check:
        (OUTPUT / "metrics.json").write_text(json.dumps(metrics, indent=2) + "\n")
    print("PASS: three paired studies; exact sphere continuity, decreasing blood area, palette, binary alpha, "
          "2x pixels, finite preparation, four-pixel full-width stream and synchronized contact")


if __name__ == "__main__":
    main()
