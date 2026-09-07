#!/usr/bin/env python3
"""Build the Self-Embodiment of Perfection domain backdrop.

This is deliberately an offline, deterministic painter rather than a texture
download.  The large shapes are assembled from tapered arm polygons and
individual hooked finger paths so that the repeating chains remain readable
at game resolution.  Run without arguments to write the canonical asset, or
use ``--output`` to inspect an alternate render.
"""

from __future__ import annotations

import argparse
import math
import random
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image, ImageDraw, ImageFilter


WIDTH, HEIGHT = 1920, 1080
SCALE = 2
SEED = 0x5E1F0F


Color = tuple[int, int, int, int]
Point = tuple[float, float]


def add(a: Point, b: Point) -> Point:
    return a[0] + b[0], a[1] + b[1]


def sub(a: Point, b: Point) -> Point:
    return a[0] - b[0], a[1] - b[1]


def mul(a: Point, amount: float) -> Point:
    return a[0] * amount, a[1] * amount


def unit(a: Point) -> Point:
    length = math.hypot(a[0], a[1]) or 1.0
    return a[0] / length, a[1] / length


def perp(a: Point) -> Point:
    return -a[1], a[0]


def lerp(a: Point, b: Point, t: float) -> Point:
    return add(a, mul(sub(b, a), t))


def curve(points: Sequence[Point], closed: bool = False) -> list[Point]:
    """Catmull-Rom contours give the skin and curled digits continuous outlines."""
    controls = [points[-1], *points, points[0], points[1]] if closed else [points[0], *points, points[-1]]
    result = []
    for i in range(1, len(controls) - 2):
        a, b, c, d = controls[i - 1:i + 3]
        for step in range(10):
            t = step / 10
            result.append(tuple(.5 * (2 * b[k] + (-a[k] + c[k]) * t
                + (2 * a[k] - 5 * b[k] + 4 * c[k] - d[k]) * t * t
                + (-a[k] + 3 * b[k] - 3 * c[k] + d[k]) * t ** 3) for k in range(2)))
    if not closed:
        result.append(points[-1])
    return result


def oriented_ellipse(center: Point, radius_a: float, radius_b: float, angle: float, count: int = 18) -> list[Point]:
    """Return points for an ellipse in world coordinates."""
    ca, sa = math.cos(angle), math.sin(angle)
    points: list[Point] = []
    for index in range(count):
        theta = math.tau * index / count
        x, y = radius_a * math.cos(theta), radius_b * math.sin(theta)
        points.append((center[0] + x * ca - y * sa, center[1] + x * sa + y * ca))
    return points


class Painter:
    """Small logical-coordinate wrapper around a supersampled Pillow canvas."""

    def __init__(self, image: Image.Image):
        self.image = image
        self.draw = ImageDraw.Draw(image, "RGBA")

    @staticmethod
    def points(points: Iterable[Point]) -> list[tuple[int, int]]:
        return [(round(x * SCALE), round(y * SCALE)) for x, y in points]

    def polygon(self, points: Iterable[Point], fill: Color) -> None:
        self.draw.polygon(self.points(points), fill=fill)

    def line(self, points: Iterable[Point], fill: Color, width: float, joint: str = "curve") -> None:
        self.draw.line(self.points(points), fill=fill, width=max(1, round(width * SCALE)), joint=joint)

    def ellipse(self, center: Point, radius_a: float, radius_b: float, angle: float, fill: Color) -> None:
        self.polygon(oriented_ellipse(center, radius_a, radius_b, angle), fill)


def rgba(rgb: tuple[int, int, int], alpha: int) -> Color:
    return rgb[0], rgb[1], rgb[2], max(0, min(255, alpha))


def arm_sections(a: Point, b: Point, width: float) -> tuple[list[Point], list[Point], list[Point], list[Point]]:
    """Make a gently organic tapered polygon plus its local shade planes."""
    direction = unit(sub(b, a))
    normal = perp(direction)
    # A wrist, a full sinewy belly, a shallow elbow plane, and the next wrist.
    ts = (0.0, 0.12, 0.30, 0.48, 0.62, 0.80, 1.0)
    widths = (0.55, 0.66, 0.97, 0.90, 0.61, 0.85, 0.50)
    centers = [add(lerp(a, b, t), mul(normal, math.sin(t * math.tau) * width * .12)) for t in ts]
    left = [add(c, mul(normal, width * factor / 2)) for c, factor in zip(centers, widths)]
    right = [add(c, mul(normal, -width * factor / 2)) for c, factor in zip(centers, widths)]
    silhouette = curve(left + list(reversed(right)), True)
    # Broad cel-shade plane, highlight ridge, and an underside strip.
    shade = [add(c, mul(normal, -width * factor * 0.40)) for c, factor in zip(centers[1:], widths[1:])]
    shade += [add(c, mul(normal, -width * factor * 0.05)) for c, factor in zip(reversed(centers[1:]), reversed(widths[1:]))]
    highlight = [add(c, mul(normal, width * factor * 0.34)) for c, factor in zip(centers[1:4], widths[1:4])]
    highlight += [add(c, mul(normal, width * factor * 0.10)) for c, factor in zip(reversed(centers[1:4]), reversed(widths[1:4]))]
    tendon = [add(c, mul(normal, width * factor * 0.16)) for c, factor in zip(centers, widths)]
    return silhouette, curve(shade, True), curve(highlight, True), curve(tendon)


def draw_stitches(p: Painter, a: Point, b: Point, width: float, rng: random.Random, alpha: int) -> None:
    """Add sparse black stitch seams at irregular wrist/arm intervals."""
    direction = unit(sub(b, a))
    normal = perp(direction)
    for t in (0.14, 0.64, 0.90):
        if rng.random() > 0.25:
            center = lerp(a, b, t)
            seam_len = width * rng.uniform(0.52, 0.68)
            p.line(
                [add(center, mul(normal, -seam_len / 2)), add(center, mul(normal, seam_len / 2))],
                rgba((13, 10, 21), alpha),
                max(0.7, width * 0.018),
            )
            # Tiny offset bars keep the seams feeling sewn into the skin.
            if width > 18:
                for offset in (-.40, -.19, .05, .27, .43):
                    stitch_center = add(center, mul(normal, seam_len * offset))
                    p.line(
                        [add(stitch_center, mul(direction, -width * .06)), add(stitch_center, mul(direction, width * .055))],
                        rgba((9, 7, 15), alpha),
                        max(.7, width * .018),
                    )


def draw_forearm(p: Painter, a: Point, b: Point, width: float, alpha: int, rng: random.Random, detail: bool = True) -> None:
    silhouette, shade, highlight, tendon = arm_sections(a, b, width)
    p.polygon(silhouette, rgba((90, 83, 104), alpha))
    p.polygon(shade, rgba((28, 23, 43), round(alpha * 0.75)))
    p.polygon(highlight, rgba((137, 121, 143), round(alpha * 0.28)))
    p.line(tendon[5:-10], rgba((154, 138, 156), round(alpha * 0.22)), max(.6, width * 0.014))

    # A nearly black contour on the lower edge gives the forms a cel-shaded cutout.
    direction = unit(sub(b, a))
    normal = perp(direction)
    contour = [add(lerp(a, b, t), mul(normal, -width * f * 0.48)) for t, f in zip((0.05, 0.25, 0.62, 0.87, 0.97), (0.60, 0.91, 1.04, 0.88, 0.54))]
    p.line(curve(contour), rgba((16, 13, 27), round(alpha * 0.60)), max(.8, width * 0.018))
    if detail:
        draw_stitches(p, a, b, width, rng, round(alpha * 0.76))


def draw_finger(p: Painter, path: Sequence[Point], width: float, alpha: int) -> None:
    path = curve(path)
    p.line(path, rgba((17, 13, 25), alpha), width * .165)
    p.line(path, rgba((107, 96, 116), alpha), width * .125)
    p.ellipse(path[-1], width * .063, width * .057, 0, rgba((107, 96, 116), alpha))
    p.line(path[7:18], rgba((152, 134, 151), round(alpha * .35)), width * .028)


def draw_grip(p: Painter, center: Point, direction: Point, width: float, alpha: int, rng: random.Random) -> None:
    """Draw one palm with four individually hooked fingers and a thumb.

    The grip is oriented along the chain axis.  Fingers start on the palm's
    near side, cross the next forearm, curl back, and end in visible rounded
    tips; short dark separators prevent the hand reading as a generic knot.
    """
    d = unit(direction)
    n = perp(d)
    def local(x: float, y: float) -> Point:
        return add(center, add(mul(n, width * x), mul(d, width * y)))

    palm = [local(x, y) for x, y in [(-.22, -.68), (.20, -.66), (.28, -.30),
        (.21, -.06), (.08, .21), (-.20, .36), (-.39, .21), (-.42, -.13)]]
    p.polygon(curve(palm, True), rgba((86, 76, 97), alpha))
    p.polygon(curve([local(x, y) for x, y in [(-.22, -.60), (-.06, -.58),
        (-.04, -.22), (-.15, .16), (-.32, .19), (-.34, -.18)]], True), rgba((130, 114, 136), round(alpha * .35)))

    # Parallel curled fingers lie across the following wrist, not in a knot.
    for index in range(4):
        y = -.17 + index * .155
        reach = (.27, .34, .32, .24)[index]
        path = [local(-.30, y - .055), local(-.13, y), local(.13, y + .03),
            local(reach, y + .095), local(reach - .015, y + .17), local(reach - .13, y + .175)]
        draw_finger(p, path, width, alpha)
        p.line([local(.10, y + .003), local(.08, y + .05)], rgba((36, 27, 44), alpha), max(.6, width * .012))

    thumb = curve([local(.18, -.49), local(.34, -.31), local(.37, -.12), local(.23, .02), local(.05, .07)])
    p.line(thumb, rgba((20, 15, 30), alpha), width * .20)
    p.line(thumb, rgba((113, 98, 119), alpha), width * .155)
    p.ellipse(thumb[-1], width * .07, width * .075, 0, rgba((126, 110, 131), alpha))

    # Wrist seam where the hand joins its incoming arm.
    seam_center = add(center, mul(d, -width * 0.56))
    p.line(
        [add(seam_center, mul(n, -width * 0.29)), add(seam_center, mul(n, width * 0.29))],
        rgba((13, 10, 21), round(alpha * 0.82)),
        max(2.0, width * 0.026),
    )


def draw_chain(p: Painter, points: Sequence[Point], width: float, alpha: int, rng: random.Random, detail: bool = True) -> None:
    segments: list[tuple[Point, Point]] = []
    for index in range(len(points) - 1):
        a, b = points[index], points[index + 1]
        jitter = rng.uniform(-width * 0.07, width * 0.07)
        direction = unit(sub(b, a))
        normal = perp(direction)
        aa, bb = add(a, mul(normal, jitter)), add(b, mul(normal, -jitter * 0.55))
        segments.append((aa, bb))
        draw_forearm(p, aa, bb, width * rng.uniform(0.82, 1.08), alpha, rng, detail)

    # Put every palm over every forearm.  Painting a forearm after the prior
    # joint's hand would bury the hooked fingers and make the links look like
    # blunt rivets rather than one hand gripping the next wrist.
    for index, (_, bb) in enumerate(segments):
        if index + 1 < len(segments):
            outgoing = unit(sub(segments[index + 1][1], bb))
        else:
            outgoing = unit(sub(bb, segments[index][0]))
        draw_grip(p, bb, outgoing, width * rng.uniform(0.80, 1.02), alpha, rng)


def make_base(rng: random.Random) -> Image.Image:
    """Create opaque near-black aubergine with restrained haze and grain."""
    base = Image.new("RGBA", (WIDTH, HEIGHT))
    pixels = base.load()
    for y in range(HEIGHT):
        v = y / HEIGHT
        for x in range(WIDTH):
            u = x / WIDTH
            edge = ((u - 0.5) ** 2 + (v - 0.48) ** 2) ** 0.5
            grain = rng.randrange(-3, 4)
            pixels[x, y] = (
                max(3, 10 + round(4 * (1 - v) - 7 * edge) + grain),
                max(2, 6 + round(3 * (1 - v) - 4 * edge) + grain // 2),
                max(7, 17 + round(7 * (1 - v) - 9 * edge) + grain),
                255,
            )

    base = base.resize((WIDTH * SCALE, HEIGHT * SCALE), Image.Resampling.BICUBIC)
    haze = Image.new("RGBA", base.size, (0, 0, 0, 0))
    hp = Painter(haze)
    hp.ellipse((960, 485), 620, 430, 0.0, (54, 41, 74, 25))
    hp.ellipse((950, 760), 530, 300, 0.0, (25, 17, 39, 30))
    hp.ellipse((170, 220), 360, 460, -0.35, (65, 49, 77, 22))
    haze = haze.filter(ImageFilter.GaussianBlur(85 * SCALE))
    base = Image.alpha_composite(base, haze)
    return base.convert("RGB")


def add_atmosphere(canvas: Image.Image, rng: random.Random) -> None:
    haze = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    p = Painter(haze)
    # A dim central opening is intentional: the lower/center battle silhouettes
    # should remain readable beneath the domain's hand canopy.
    p.ellipse((960, 475), 570, 325, 0.0, (93, 73, 111, 12))
    p.ellipse((965, 830), 430, 180, 0.0, (2, 1, 7, 55))
    haze = haze.filter(ImageFilter.GaussianBlur(120 * SCALE))
    canvas.paste(haze, (0, 0), haze)


def render() -> Image.Image:
    rng = random.Random(SEED)
    canvas = make_base(rng)
    p = Painter(canvas)

    # Far links are narrow and subdued.  Their paths stay mostly above and
    # beside the central opening, suggesting many receding layers without
    # turning the fighter area into visual noise.
    far = [
        ([(-100, 260), (210, 190), (530, 270), (830, 185), (1140, 300), (1480, 210), (1810, 310), (2130, 240)], 35, 70),
        ([(-80, 920), (210, 765), (470, 620), (770, 580), (1090, 610), (1370, 470), (1700, 440), (2010, 340)], 27, 48),
        ([(70, -90), (410, 110), (740, 270), (1050, 425), (1370, 525), (1680, 700), (1990, 810)], 31, 63),
        ([(-180, 92), (70, 160), (290, 266), (500, 356), (730, 440)], 34, 54),
        ([(560, -130), (680, 92), (780, 282), (845, 455)], 30, 48),
        ([(1510, -130), (1435, 80), (1360, 270), (1280, 432)], 32, 55),
        ([(2050, 105), (1810, 170), (1610, 280), (1450, 388)], 37, 58),
        ([(30, 1130), (110, 930), (182, 760), (252, 592)], 31, 50),
        ([(1900, 1140), (1840, 930), (1770, 770), (1700, 614)], 30, 48),
        ([(1220, 80), (1260, 205), (1248, 330), (1190, 448)], 25, 40),
    ]
    for index, (path, arm_width, arm_alpha) in enumerate(far):
        draw_chain(p, path, arm_width, arm_alpha, random.Random(SEED + index * 31), detail=False)

    # Mid-depth diagonals establish the repeated self-grasping rhythm behind
    # the foreground edges.  The gaps through the middle are preserved.
    middle = [
        ([(-180, -80), (80, 100), (330, 286), (570, 450)], 67, 112),
        ([(2140, -60), (1860, 120), (1630, 285), (1430, 452)], 64, 104),
        ([(880, -150), (930, 62), (980, 245), (1010, 420)], 54, 92),
        ([(1035, -120), (1080, 78), (1105, 260), (1120, 415)], 46, 78),
        ([(-110, 840), (100, 730), (270, 622), (430, 500)], 57, 94),
        ([(2050, 840), (1840, 730), (1670, 620), (1510, 500)], 57, 94),
    ]
    for index, (path, arm_width, arm_alpha) in enumerate(middle):
        draw_chain(p, path, arm_width, arm_alpha, random.Random(SEED + 101 + index * 43), detail=True)

    # Strong foreground chains are cropped by the frame, as if the void is
    # surrounded by grasping limbs.  They frame rather than cross the lower
    # center, leaving a clean stage for the fighters.
    foreground = [
        ([(-150, -170), (40, 72), (108, 300), (72, 528), (145, 760), (84, 1008), (-30, 1190)], 132, 210),
        ([(275, -150), (350, 84), (300, 310), (390, 528)], 105, 179),
        ([(2070, -170), (1882, 76), (1818, 304), (1870, 525), (1796, 768), (1860, 1020), (1995, 1180)], 136, 214),
        ([(1615, -160), (1564, 86), (1638, 304), (1575, 510)], 106, 177),
        ([(-110, 1110), (165, 1005), (405, 932)], 116, 174),
        ([(2030, 1118), (1770, 1010), (1535, 930)], 112, 172),
    ]
    for index, (path, arm_width, arm_alpha) in enumerate(foreground):
        draw_chain(p, path, arm_width * .70, arm_alpha, random.Random(SEED + 211 + index * 59), detail=True)

    # Subtle vignette keeps attention toward the opening while retaining arm
    # detail at the perimeter.
    vignette = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    vp = Painter(vignette)
    vp.ellipse((960, 540), 1120, 670, 0.0, (0, 0, 0, 0))
    # Four broad translucent edge planes avoid a visible geometric ring.
    vp.polygon([(0, 0), (520, 0), (320, 1080), (0, 1080)], (0, 0, 0, 35))
    vp.polygon([(1920, 0), (1400, 0), (1610, 1080), (1920, 1080)], (0, 0, 0, 35))
    vp.polygon([(0, 0), (1920, 0), (1920, 90), (0, 110)], (0, 0, 0, 20))
    vignette = vignette.filter(ImageFilter.GaussianBlur(90 * SCALE))
    canvas.paste(vignette, (0, 0), vignette)
    add_atmosphere(canvas, rng)
    return canvas.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS).convert("RGBA")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    default_output = Path(__file__).resolve().parents[1] / "graphics/src/main/resources/assets/animations/domain-backdrops/self-embodiment.png"
    parser.add_argument("--output", type=Path, default=default_output, help="PNG output path (default: canonical asset path)")
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image = render()
    image.save(args.output, format="PNG", optimize=True)
    print(f"wrote {args.output} ({image.width}x{image.height}, {image.mode}) seed={SEED}")


if __name__ == "__main__":
    main()
