#!/usr/bin/env python3
"""Generate deterministic, review-only simple-domain pixel animation candidates.

The art is intentionally small and compositional: a floor ellipse is painted in
logical 96px space, then exported at 2x with nearest-neighbour sampling.  The
script has no dependency on authored game data and never edits runtime JSON or
Java.  Running it with no arguments writes all three candidate reviews under
``docs/animations/simple-domain``.  ``--export`` is opt-in and writes only the
four selected runtime sheets.
"""

from __future__ import annotations

import argparse
import hashlib
import math
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
REVIEW_ROOT = ROOT / "docs/animations/simple-domain"
RUNTIME_ROOT = ROOT / "graphics/src/main/resources/assets/animations/simple-domain/sprites"

LOGICAL_SIZE = 96
SCALE = 2
EXPORT_SIZE = LOGICAL_SIZE * SCALE
FIELD_FRAMES = 48
ACTIVATION_FRAMES = 24
FIELD_MS = 80
ACTIVATION_MS = 50
TRANSPARENT = (0, 0, 0, 0)

# Limited opaque palette; no blurred or semitransparent edge pixels.
PALETTE = {
    "outline": (25, 57, 61, 255),       # dark desaturated teal
    "cyan": (66, 151, 157, 255),        # muted cyan
    "pale": (143, 211, 209, 255),       # pale cyan
    "ivory": (230, 245, 242, 255),      # cool near-white, not a warm spell ring
    "floor-blue": (35, 125, 187, 255),
    "floor-light": (48, 156, 207, 255),
}

CANDIDATES = ("quiet-chalk", "double-lip", "low-veil")


def ellipse_box(rx: float, ry: float, grow: float = 0.0) -> tuple[int, int, int, int]:
    """Return a deterministic integer Pillow box centred at (48, 48)."""
    return (
        round(48 - rx - grow),
        round(48 - ry - grow),
        round(48 + rx + grow),
        round(48 + ry + grow),
    )


def ellipse(draw: ImageDraw.ImageDraw, rx: float, ry: float, colour: str, grow: float = 0.0) -> None:
    draw.ellipse(ellipse_box(rx, ry, grow), outline=PALETTE[colour], width=1)


def ring_sector(
    draw: ImageDraw.ImageDraw,
    rx: float,
    ry: float,
    angle: float,
    half_width: float,
    colour: str,
) -> None:
    """Paint a short stepped highlight on an ellipse without antialiasing."""
    points = []
    for index in range(5):
        theta = angle - half_width + (2 * half_width * index / 4)
        points.append((round(48 + math.cos(theta) * (rx - 0.25)),
                       round(48 + math.sin(theta) * (ry - 0.25))))
    draw.line(points, fill=PALETTE[colour], width=1)


def draw_ring(image: Image.Image, candidate: str, rx: float, ry: float, phase: float) -> None:
    """Draw one candidate's fixed-radius floor language on a logical cel."""
    draw = ImageDraw.Draw(image)

    # A dark one-pixel supporting edge remains visible around every candidate.
    ellipse(draw, rx, ry, "outline", grow=1)
    if candidate == "quiet-chalk":
        ellipse(draw, rx, ry, "ivory")
        # Unevenly spaced catches avoid a fourfold-symmetric, visibly faster sub-loop.
        for offset, width in ((0, 0.055), (2.2, 0.035)):
            ring_sector(draw, rx, ry, phase + offset, width, "pale")
    else:
        # The two close contours deliberately read as a low floor ring, never a dome.
        ellipse(draw, rx, ry, "cyan")
        ellipse(draw, max(1, rx - 2), max(1, ry - 1), "ivory")
        for index in range(4):
            ring_sector(draw, rx, ry, phase + index * math.tau / 4, 0.105, "pale" if candidate == "low-veil" else "ivory")

        if candidate == "low-veil":
            # Tiny vertical wisps sit only at the left/right extrema.  They are
            # attached to the rim and never become floating particles.
            length = 2 + (round((phase / math.tau) * 12) % 2)
            for x in (round(48 - rx), round(48 + rx)):
                draw.line((x, 48 - length // 2, x, 48 + length // 2), fill=PALETTE["pale"], width=1)


def expansion_radii(frame: int) -> tuple[float, float]:
    """Activation timing: tight indication, eased growth, then exact persistence."""
    if frame <= 3:
        return ((9, 2), (11, 2), (14, 3), (18, 4))[frame]
    if frame <= 10:
        t = (frame - 4) / 6
        eased = 1 - (1 - t) ** 2
        return 18 + (40 - 18) * eased, 4 + (13 - 4) * eased
    return 40, 13


def blue_field(kind: str, frame: int) -> tuple[Image.Image, Image.Image]:
    """Blue ground plane with swept, pointed wavelets orbiting its boundary."""
    rx, ry = expansion_radii(frame) if kind == "activation" else (40, 13)
    # Finish at loop cel zero; the brief matching tail overlaps the persistent fade.
    seconds = min(0, (frame - 22) * ACTIVATION_MS / 1000) if kind == "activation" else frame * FIELD_MS / 1000
    phase = seconds * math.tau / (FIELD_FRAMES * FIELD_MS / 1000)
    back = Image.new("RGBA", (LOGICAL_SIZE, LOGICAL_SIZE), TRANSPARENT)
    floor = ImageDraw.Draw(back)
    floor.ellipse(ellipse_box(rx, ry, 1), fill=PALETTE["outline"])
    floor.ellipse(ellipse_box(rx, ry), fill=PALETTE["floor-blue"])
    floor.ellipse(ellipse_box(max(1, rx - 3), max(1, ry - 2)), fill=PALETTE["floor-light"])

    rim = Image.new("RGBA", back.size, TRANSPARENT)
    ellipse(ImageDraw.Draw(rim), rx, ry, "ivory")
    rear_rim, front = split_halves(rim)
    back.alpha_composite(rear_rim)
    # Each crest leans in the travel direction, rather than radiating like spokes.
    crest = ((-.36, 0), (-.24, .18), (-.09, .35), (.04, 1),
             (-.01, .34), (.20, .70), (.15, .14), (.36, 0), (.08, -.10), (-.18, -.10))
    for offset, height in ((0, 5), (.95, 4), (2.10, 6), (3.20, 4), (4.25, 5), (5.35, 3)):
        angle = phase + offset
        growth = rx / 40

        def point(along: float, rise: float) -> tuple[int, int]:
            lift = rise * height * growth
            theta = angle + along
            return (round(48 + math.cos(theta) * (rx + max(0, lift) * .35)),
                    round(48 + math.sin(theta) * (ry + max(0, lift) * .12) - lift))

        # Sort the whole wave by its root on the ground, not by its elevated tip.
        draw = ImageDraw.Draw(back if math.sin(angle) < 0 else front)
        draw.polygon([point(t, rise) for t, rise in crest], fill=PALETTE["pale"], outline=PALETTE["outline"])
        draw.line([point(-.24, .18), point(-.09, .35), point(.04, 1)], fill=PALETTE["ivory"], width=1)
        draw.line([point(-.01, .34), point(.20, .70)], fill=PALETTE["ivory"], width=1)
    return back, front


def render_logical(candidate: str, kind: str, frame: int) -> Image.Image:
    """Render one 96x96 full cel before depth splitting."""
    if candidate == "quiet-chalk":
        back, front = blue_field(kind, frame)
        return Image.alpha_composite(back, front)
    image = Image.new("RGBA", (LOGICAL_SIZE, LOGICAL_SIZE), TRANSPARENT)
    if kind == "activation":
        rx, ry = expansion_radii(frame)
        # Once settled, activation cels are byte-for-byte field frame zero.
        phase = 0.0 if frame >= 10 else frame * 0.07
    elif kind == "field":
        rx, ry = 40, 13
        phase = frame * math.tau / FIELD_FRAMES
    else:
        raise ValueError(f"Unknown animation kind: {kind}")
    draw_ring(image, candidate, rx, ry, phase)
    return image


def split_halves(logical: Image.Image) -> tuple[Image.Image, Image.Image]:
    """Return disjoint transparent back (y<48) and front (y>=48) cels."""
    back = Image.new("RGBA", logical.size, TRANSPARENT)
    front = Image.new("RGBA", logical.size, TRANSPARENT)
    back.paste(logical.crop((0, 0, LOGICAL_SIZE, 48)), (0, 0))
    front.paste(logical.crop((0, 48, LOGICAL_SIZE, LOGICAL_SIZE)), (0, 48))
    return back, front


def export_scale(image: Image.Image) -> Image.Image:
    return image.resize((EXPORT_SIZE, EXPORT_SIZE), Image.Resampling.NEAREST)


def render_frames(candidate: str, kind: str) -> tuple[list[Image.Image], list[Image.Image]]:
    count = ACTIVATION_FRAMES if kind == "activation" else FIELD_FRAMES
    backs, fronts = [], []
    for frame in range(count):
        back, front = blue_field(kind, frame) if candidate == "quiet-chalk" else split_halves(render_logical(candidate, kind, frame))
        backs.append(export_scale(back))
        fronts.append(export_scale(front))
    return backs, fronts


def make_sheet(frames: Iterable[Image.Image]) -> Image.Image:
    frames = list(frames)
    sheet = Image.new("RGBA", (EXPORT_SIZE * 8, EXPORT_SIZE * math.ceil(len(frames) / 8)), TRANSPARENT)
    for index, frame in enumerate(frames):
        sheet.paste(frame, ((index % 8) * EXPORT_SIZE, (index // 8) * EXPORT_SIZE))
    return sheet


def generate_sheets(candidate: str) -> dict[str, Image.Image]:
    activation_back, activation_front = render_frames(candidate, "activation")
    field_back, field_front = render_frames(candidate, "field")
    return {
        "activation-front": make_sheet(activation_front),
        "activation-back": make_sheet(activation_back),
        "field-front": make_sheet(field_front),
        "field-back": make_sheet(field_back),
    }


def save_candidate_sheets(candidate: str, destination: Path) -> dict[str, Image.Image]:
    folder = destination / candidate
    folder.mkdir(parents=True, exist_ok=True)
    sheets = generate_sheets(candidate)
    for name, sheet in sheets.items():
        sheet.save(folder / f"{name}.png", optimize=True)
    return sheets


def silhouette(tile: Image.Image, colour: tuple[int, int, int, int]) -> None:
    """Draw a neutral pixel humanoid for review boards only, with feet at y=48."""
    draw = ImageDraw.Draw(tile)
    def box(values: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
        return tuple(value * SCALE for value in values)  # type: ignore[return-value]
    draw.ellipse(box((43, 17, 53, 27)), fill=colour)
    draw.polygon([(x * SCALE, y * SCALE) for x, y in (
        (42, 29), (35, 33), (31, 39), (27, 42), (28, 45), (34, 43),
        (39, 38), (40, 45), (37, 48), (44, 48), (48, 42), (52, 48),
        (59, 48), (56, 44), (57, 38), (62, 43), (68, 45), (69, 42),
        (62, 38), (57, 32), (52, 29),
    )], fill=colour)


def contact_tile(back: Image.Image, front: Image.Image, background: tuple[int, int, int, int], figure: tuple[int, int, int, int]) -> Image.Image:
    tile = Image.new("RGBA", (EXPORT_SIZE, EXPORT_SIZE), background)
    tile.alpha_composite(back)
    person = Image.new("RGBA", tile.size, TRANSPARENT)
    silhouette(person, figure)
    tile.alpha_composite(person)
    tile.alpha_composite(front)
    return tile


def make_contact_board(candidate: str, destination: Path, light: bool) -> Path:
    folder = destination / candidate
    activation_back, activation_front = render_frames(candidate, "activation")
    field_back, field_front = render_frames(candidate, "field")
    activation_indices = (0, 3, 4, 6, 8, 10, 16, 23)
    field_indices = (0, 6, 12, 18, 24, 30, 36, 47)
    background = (244, 241, 224, 255) if light else (13, 29, 32, 255)
    figure = (30, 49, 52, 255) if light else (228, 231, 207, 255)
    ink = (27, 53, 56, 255) if light else (229, 235, 211, 255)
    muted_ink = (79, 107, 106, 255) if light else (138, 170, 164, 255)
    board = Image.new("RGBA", (EXPORT_SIZE * 8, 42 + 2 * (EXPORT_SIZE + 28)), background)
    draw = ImageDraw.Draw(board)
    font = ImageFont.load_default()
    title = ("BLUE FIELD / ROTATING RIM WAVES" if candidate == "quiet-chalk"
             else candidate.upper().replace("-", " ")) + " / NO AA GEOMETRY"
    draw.text((10, 8), title, fill=ink, font=font)
    for row, (label, indices, backs, fronts) in enumerate((
        ("ACTIVATION  24 x 50ms", activation_indices, activation_back, activation_front),
        (f"MAINTENANCE  {FIELD_FRAMES} x {FIELD_MS}ms", field_indices, field_back, field_front),
    )):
        y = 42 + row * (EXPORT_SIZE + 28)
        draw.text((10, y + 5), label, fill=muted_ink, font=font)
        for column, index in enumerate(indices):
            tile = contact_tile(backs[index], fronts[index], background, figure)
            x = column * EXPORT_SIZE
            board.paste(tile, (x, y + 20))
            draw.rectangle((x, y + 20, x + EXPORT_SIZE - 1, y + 20 + EXPORT_SIZE - 1), outline=muted_ink, width=1)
            draw.text((x + 5, y + 24), f"{index:02d}", fill=ink, font=font)
    path = folder / ("contact-light.png" if light else "contact-dark.png")
    board.save(path, optimize=True)
    return path


def make_loop_preview(candidate: str, destination: Path) -> None:
    frames = []
    for kind in ("activation", "field", "field", "field"):
        backs, fronts = render_frames(candidate, kind)
        frames.extend(contact_tile(back, front, (13, 29, 32, 255), (228, 231, 207, 255)).convert("RGB")
                      for back, front in zip(backs, fronts))
    frames[0].save(destination / candidate / "lifecycle.gif", save_all=True,
                   append_images=frames[1:], loop=0,
                   duration=[ACTIVATION_MS] * ACTIVATION_FRAMES + [FIELD_MS] * FIELD_FRAMES * 3)


def check_sheet(sheet: Image.Image, expected_rows: int) -> None:
    assert sheet.mode == "RGBA"
    assert sheet.size == (EXPORT_SIZE * 8, EXPORT_SIZE * expected_rows), sheet.size
    alpha_values = {colour for _, colour in sheet.getchannel("A").getcolors(256)}
    assert alpha_values <= {0, 255}, sorted(alpha_values)
    # Every exported logical pixel must be a uniform nearest-neighbour 2x2.
    pixels = sheet.load()
    for y in range(0, sheet.height, 2):
        for x in range(0, sheet.width, 2):
            value = pixels[x, y]
            assert pixels[x + 1, y] == value and pixels[x, y + 1] == value and pixels[x + 1, y + 1] == value
    opaque = {colour for _, colour in sheet.getcolors(sheet.width * sheet.height) if colour[3] == 255}
    assert opaque <= set(PALETTE.values()), opaque


def reconstruct(sheet_back: Image.Image, sheet_front: Image.Image, frame: int) -> Image.Image:
    x = (frame % 8) * EXPORT_SIZE
    y = (frame // 8) * EXPORT_SIZE
    result = Image.new("RGBA", (EXPORT_SIZE, EXPORT_SIZE), TRANSPARENT)
    result.alpha_composite(sheet_back.crop((x, y, x + EXPORT_SIZE, y + EXPORT_SIZE)))
    result.alpha_composite(sheet_front.crop((x, y, x + EXPORT_SIZE, y + EXPORT_SIZE)))
    return result


def check_determinism() -> None:
    """Validate the default quiet-chalk runtime contract, with or without export."""
    expected = {
        "activation-front": (3, ACTIVATION_FRAMES),
        "activation-back": (3, ACTIVATION_FRAMES),
        "field-front": (6, FIELD_FRAMES),
        "field-back": (6, FIELD_FRAMES),
    }
    sheets: dict[str, Image.Image] = {}
    generated = generate_sheets("quiet-chalk")
    for name, (rows, _) in expected.items():
        path = RUNTIME_ROOT / f"{name}.png"
        assert path.exists(), f"Missing runtime sheet: {path}"
        sheet = Image.open(path).convert("RGBA")
        assert sheet.tobytes() == generated[name].tobytes(), f"Stale runtime sheet: {path}"
        check_sheet(sheet, rows)
        sheets[name] = sheet
    activation = reconstruct(sheets["activation-back"], sheets["activation-front"], ACTIVATION_FRAMES - 1)
    field = reconstruct(sheets["field-back"], sheets["field-front"], 0)
    assert activation.tobytes() == field.tobytes(), "activation final frame differs from field frame zero"
    loop = [reconstruct(sheets["field-back"], sheets["field-front"], frame).tobytes()
            for frame in range(FIELD_FRAMES)]
    assert len(set(loop)) == FIELD_FRAMES, "maintained loop has an unintended shorter repeating period"
    for frame in range(FIELD_FRAMES):
        back, front = blue_field("field", frame)
        assert back.getpixel((48, 48)) == PALETTE["floor-light"], "blue base must remain filled"
        assert front.getpixel((48, 48))[3] == 0, "floor must not paint over the fighter"
        assert PALETTE["floor-blue"] not in {c for _, c in front.getcolors(LOGICAL_SIZE ** 2)}
        assert PALETTE["floor-light"] not in {c for _, c in front.getcolors(LOGICAL_SIZE ** 2)}
        for side in (back, front):
            assert side.getbbox() is not None
            left, top, right, bottom = side.getbbox()
            assert left > 0 and top > 0 and right < LOGICAL_SIZE and bottom < LOGICAL_SIZE, "clipped wave crest"
    # The seam must be another ordinary rotation step, not a reset/jump.
    cels = [render_logical("quiet-chalk", "field", i).tobytes() for i in range(FIELD_FRAMES)]
    changes = [sum(a[j:j+4] != b[j:j+4] for j in range(0, len(a), 4))
               for a, b in zip(cels, cels[1:] + cels[:1])]
    assert changes[-1] <= max(changes[:-1]), "loop seam is larger than an ordinary step"
    digest = hashlib.sha256(b"".join(sheets[name].tobytes() for name in expected)).hexdigest()
    print(f"Checked quiet-chalk: 1536px-wide sheets, binary alpha, uniform 2x2 pixels, final==field0; digest {digest[:16]}")


def export_runtime(candidate: str, sheets: dict[str, Image.Image]) -> None:
    RUNTIME_ROOT.mkdir(parents=True, exist_ok=True)
    for name, sheet in sheets.items():
        sheet.save(RUNTIME_ROOT / f"{name}.png", optimize=True)
    # These superseded generated sheets no longer have catalog entries.
    old_sprites = RUNTIME_ROOT.parents[1] / "pixel-fx/sprites"
    for name in ("simple-domain.png", "new-shadow-style-simple-domain.png"):
        (old_sprites / name).unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", choices=CANDIDATES, help="generate/export one candidate; default is all review candidates")
    parser.add_argument("--export", action="store_true", help="also write the selected candidate's four runtime PNG sheets")
    parser.add_argument("--check", action="store_true", help="check quiet-chalk runtime sheets, or their deterministic in-memory equivalent")
    args = parser.parse_args()
    if args.check:
        check_determinism()
        return

    # Export without an explicit candidate means the deterministic default,
    # while ordinary generation intentionally produces all three reviews.
    candidates = (args.candidate,) if args.candidate else (("quiet-chalk",) if args.export else CANDIDATES)
    for candidate in candidates:
        sheets = save_candidate_sheets(candidate, REVIEW_ROOT)
        dark = make_contact_board(candidate, REVIEW_ROOT, light=False)
        light = make_contact_board(candidate, REVIEW_ROOT, light=True)
        make_loop_preview(candidate, REVIEW_ROOT)
        print(f"{candidate}: {dark} ; {light}")
        if args.export:
            export_runtime(candidate, sheets)
            print(f"exported runtime sprites: {RUNTIME_ROOT}")


if __name__ == "__main__":
    main()
