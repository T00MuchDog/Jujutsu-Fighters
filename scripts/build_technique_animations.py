#!/usr/bin/env python3
"""Export data-authored technique pixel cels. Requires Pillow; no downloaded art.

Each JSON document supplies reusable vector motifs and ordered animated art rows.
Combat IDs, creature anatomy, palettes, timing, and choreography belong in JSON,
not in this renderer. Existing animation packs/profiles are left intact.
"""

import argparse
import hashlib
import json
import math
from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw, ImageFont

from build_cursed_spirit_animations import primitive, interpolate as linear_interpolate

ROOT = Path(__file__).resolve().parents[1]
ART = Path(__file__).with_name("animation_art") / "techniques"
ASSETS = ROOT / "graphics/src/main/resources/assets"
MOTIFS = {}


def interpolate(value, t):
    """Optional unevenly spaced keys permit compression acceleration and held cels."""
    if not isinstance(value, dict):
        return linear_interpolate(value, t)
    keys = value["keys"]
    assert len(keys) >= 2 and all(a[0] < b[0] for a, b in zip(keys, keys[1:])), keys
    if t <= keys[0][0]:
        return keys[0][1]
    for (start, a), (end, b) in zip(keys, keys[1:]):
        if t <= end:
            return linear_interpolate([a, b], (t - start) / (end - start))
    return keys[-1][1]


@lru_cache(maxsize=None)
def motif(name):
    image = Image.new("RGBA", (96, 96))
    draw = ImageDraw.Draw(image)
    for shape in MOTIFS[name]:
        fill = shape.get("fill")
        outline = shape.get("outline")
        width = shape.get("width", 1)
        if "polygon" in shape:
            draw.polygon([tuple(p) for p in shape["polygon"]], fill=fill, outline=outline, width=width)
        elif "line" in shape:
            draw.line([tuple(p) for p in shape["line"]], fill=fill, width=width)
        elif "ellipse" in shape:
            draw.ellipse(shape["ellipse"], fill=fill, outline=outline, width=width)
        elif "rect" in shape:
            draw.rectangle(shape["rect"], fill=fill, outline=outline, width=width)
        else:
            raise ValueError(f"Unknown vector shape in {name}: {shape}")
    return image


@lru_cache(maxsize=None)
def asset(path):
    resolved = (ASSETS / path).resolve()
    if not resolved.is_relative_to(ASSETS.resolve()):
        raise ValueError(f"Unsafe source asset: {path}")
    with Image.open(resolved) as source:
        return source.convert("RGBA").resize((96, 96), Image.Resampling.NEAREST)


def render(spec, frame):
    count = spec.get("frames", 24)
    contact, index = divmod(frame, count)
    t = index / (count - 1)
    image = Image.new("RGBA", (96, 96))
    for row in spec["art"]:
        start, end = row.get("time", [0, 1])
        if not start < t < end:
            continue
        q = (t - start) / (end - start)
        if "image" in row:
            cel = asset(row["image"]).copy()
        elif row["kind"].startswith("art:"):
            cel = motif(row["kind"][4:]).copy()
        else:
            cel = primitive(row["kind"], row.get("palette", spec.get("palette", "bone")), q)
        if "colors" in row:
            replacements = {ImageColor.getcolor(a, "RGBA"): ImageColor.getcolor(b, "RGBA")
                            for a, b in row["colors"].items()}
            cel.putdata([replacements.get(pixel, pixel) for pixel in cel.getdata()])
        # Reveal from the shadow's footline rather than scaling an animal blob.
        reveal = row.get("reveal")
        if reveal:
            edge = round(96 * min(1, q / row.get("revealUntil", .55)))
            if edge < 96:
                ImageDraw.Draw(cel).rectangle((0, 0 if reveal == "up" else edge,
                                              95, 95 - edge if reveal == "up" else 95), fill=(0, 0, 0, 0))
        motion = min(1, q / row.get("motionUntil", 1))
        scale = interpolate(row.get("scale", 1), motion)
        size = tuple(max(1, round(96 * scale * interpolate(row.get(axis, 1), motion)))
                     for axis in ("scaleX", "scaleY"))
        cel = cel.resize(size, Image.Resampling.NEAREST)
        angle = interpolate(row.get("rotate", 0), motion) + contact * row.get("contactRotation", 0)
        cel = cel.rotate(angle, Image.Resampling.NEAREST, expand=True)
        if row.get("flip"):
            cel = cel.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        alpha = min(1, q / row.get("fadeIn", .08), (1-q) / row.get("fadeOut", .18)) * row.get("alpha", 1)
        cel.putalpha(cel.getchannel("A").point(lambda a: round(a * alpha)))
        x = interpolate(row.get("x", 48), motion) + contact * row.get("contactX", 0)
        y = interpolate(row.get("y", 48), motion)
        layer = Image.new("RGBA", (96, 96)) if "clipBelow" in row else image
        for k in range(row.get("repeat", 1)):
            a = k / row.get("repeat", 1) * math.tau + q * row.get("orbit", 0)
            radius = interpolate(row.get("radius", 0), q)
            instance = cel.rotate(-math.degrees(a), Image.Resampling.NEAREST, expand=True) if row.get("radial") else cel
            layer.alpha_composite(instance, (round(x + math.cos(a)*radius - instance.width/2),
                                             round(y + math.sin(a)*radius - instance.height/2)))
        if "clipBelow" in row:
            ImageDraw.Draw(layer).rectangle((0, row["clipBelow"], 95, 95), fill=(0, 0, 0, 0))
            image.alpha_composite(layer)
    return image.resize((192, 192), Image.Resampling.NEAREST)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=ASSETS / "animations")
    parser.add_argument("--review", type=Path, default=ROOT / "docs/animations/techniques")
    parser.add_argument("--check", action="store_true", help="audit coverage, export fidelity, grids, and references")
    args = parser.parse_args()
    specs = []
    for path in sorted(ART.glob("*.json")):
        document = json.loads(path.read_text())
        assert not MOTIFS.keys() & document.get("motifs", {}).keys(), f"Duplicate motif in {path}"
        MOTIFS.update(document.get("motifs", {}))
        specs.extend(document["effects"])
    ids = [s["id"] for s in specs]
    bindings = [m for s in specs for m in s.get("moveIds", [])]
    assert len(ids) == len(set(ids)), "Duplicate effect ID"
    assert len(bindings) == len(set(bindings)), "Duplicate move binding"
    moves = json.loads((ROOT / "data/moves/all_moves.json").read_text())
    # Coverage follows current technique ownership and shikigami move types, not stats/costs.
    expected = {m["id"] for m in moves if m.get("requiredTechniqueId") in
                {"Miracles", "Ratio", "Ten Shadows", "Cursed Speech", "Blood Manipulation"}
                or set(m.get("moveTypes") or []) == {"SHIKIGAMI"}}
    assert expected <= set(bindings), f"Missing technique/shikigami moves: {sorted(expected-set(bindings))}"
    assert set(bindings) <= {m["id"] for m in moves}, "Unknown move binding"
    pack = args.destination / "techniques"
    manifest = dict(schemaVersion=1, sheetOrder="row-major-top-left", frameWidth=192,
                    frameHeight=192, columns=6, effects=[])
    choreography_path = args.destination / "choreography.json"
    choreography = json.loads(choreography_path.read_text())
    boards = {}
    hashes = {}
    if not args.check:
        (pack / "sprites").mkdir(parents=True, exist_ok=True)
        args.review.mkdir(parents=True, exist_ok=True)
    for spec in specs:
        count = spec.get("frames", 24)
        n = count * spec.get("contacts", 1)
        frames = [render(spec, i) for i in range(n)]
        sheet = Image.new("RGBA", (1152, 192 * math.ceil(n/6)))
        for i, cel in enumerate(frames):
            sheet.paste(cel, ((i % 6)*192, (i//6)*192))
        e = {k: spec[k] for k in ("id", "name", "description", "moveIds", "placement", "role", "castEffect") if k in spec}
        e.update(sheet=f"sprites/{spec['id']}.png", frameCount=n, frameDurationMs=spec.get("frameDurationMs", 40),
                 loop=False, anchor=spec.get("anchor", [.5, .5]), impactFrames=[spec.get("impact", count//2) + i*count
                     for i in range(spec.get("contacts", 1))])
        manifest["effects"].append(e)
        path = pack / e["sheet"]
        if args.check:
            with Image.open(path) as saved:
                assert saved.mode == "RGBA" and saved.size == sheet.size, spec["id"]
                assert saved.tobytes() == sheet.tobytes(), f"Stale art: {spec['id']}"
            assert sheet.getchannel("A").getextrema() == (0, 255), spec["id"]
            digest = hashlib.sha256(sheet.tobytes()).hexdigest()
            # Identical authored compositions may intentionally share a visual (e.g. dog signs).
            assert digest not in hashes or hashes[digest] == spec["art"], f"Duplicate art: {spec['id']}"
            hashes[digest] = spec["art"]
            if "profile" in spec:
                assert choreography["profiles"][spec["id"]] == spec["profile"], f"Stale profile: {spec['id']}"
                assert choreography["effects"][spec["id"]] == spec["id"]
        else:
            sheet.save(path, optimize=True)
            if "profile" in spec:
                choreography["effects"][spec["id"]] = spec["id"]
                choreography["profiles"][spec["id"]] = spec["profile"]
        boards.setdefault(spec.get("family", "supporting-layers"), []).append((spec, frames))
    if args.check:
        assert json.loads((pack / "manifest.json").read_text()) == manifest, "Stale manifest"
        catalog = json.loads((args.destination / "catalog.json").read_text())
        assert catalog["packs"][0] == "techniques", "Technique bindings must precede generic examples"
        print(f"Validated {len(bindings)} moves, {len(specs)} sheets, all export pixels and profiles.")
        return
    (pack / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    choreography_path.write_text(json.dumps(choreography, indent=2) + "\n")
    font = ImageFont.load_default(size=14)
    for family, entries in boards.items():
        board = Image.new("RGB", (1080, 42 + len(entries)*154), "#17212b")
        draw = ImageDraw.Draw(board)
        draw.text((16, 12), family.upper() + " / WIND-UP - CONTACT - FOLLOW-THROUGH - RELEASE", font=font, fill="#f0e6cf")
        for i, (spec, frames) in enumerate(entries):
            y = 42 + i*154
            draw.rectangle((8, y, 1071, y+146), fill="#e0d8c8" if i % 2 == 0 else "#b9bfc1")
            label = spec.get("moveIds", ["LAYER"])[0] + "  " + spec["name"]
            draw.text((18, y+18), label, font=font, fill="#19232d")
            draw.text((18, y+44), spec["placement"].upper() + " / " + spec["role"], font=font, fill="#384750")
            count = spec.get("frames", 24)
            for j, idx in enumerate([int(count*.23), spec.get("impact", count//2), int(count*.72), int(count*.88)]):
                cel = frames[idx].resize((144, 144), Image.Resampling.NEAREST)
                board.paste(cel, (480+j*148, y), cel)
        board.save(args.review / f"{family}.png", optimize=True)
    print(f"Exported {len(bindings)} moves, {len(specs)-len(bindings)} reusable layers, {len(boards)} review boards.")


if __name__ == "__main__":
    main()
