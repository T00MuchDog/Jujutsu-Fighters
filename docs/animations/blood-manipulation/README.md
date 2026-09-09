# Convergence / Piercing Blood

Selected study: **C-restrained**, revised with clearer joined fingers, rounded
liquid, a one-frame directional pressure deformation, and a compact puncture.
Original pixel artwork; no reference pixels are copied into the game.

## Review Assets

- [Selected paired animation](C-restrained-pair.gif): preparation, idle gap, later shot.
- [Selected cel board](C-restrained.png): source, stream, and target sheets.
- [Shared sphere comparison](C-restrained-continuity.png): Convergence frame 19 beside source frame 4.
- [A: laminar pair](A-laminar-pair.gif), [board](A-laminar.png), [continuity](A-laminar-continuity.png).
- [B: rotational pair](B-rotational-pair.gif), [board](B-rotational.png), [continuity](B-rotational-continuity.png).
- [Measured blood area](metrics.json): every frame, measured at logical resolution.

The three studies are linked pairs, not independently selected moves. A uses five
inward-curving droplets; B uses seven with stronger rotation and darker highlights;
C uses three with only 0.3 radians of curvature. All use the same pressure/load/
release timing and internally consistent material. The final anatomy and liquid
silhouette corrections were applied to all retained studies for fair comparison.
Only C is installed in the runtime pack. The review script applies study parameters
in memory and renders through the production exporter; it is not a second runtime
exporter or animation system.

## Repository Inspection

Inspected `ANIMATIONS.md`, current `data/moves/all_moves.json`, resource definitions,
bounded-resource transactions, Blood Manipulation AI/tests, technique manifest,
global choreography, Blood/Ratio authoring, exporter, current sheets/review boards,
and `BattleAnimationPlayer` / `BattleEffectPack` / `BattleChoreography` draw/timing.

At inspection, Convergence `000097` converted one BLOOD_SUPPLY to one COMPRESSION
on start. Piercing Blood `000098` spent one COMPRESSION on start. Resource changes
are atomic, chronologically validated, and independent of VFX lifetimes. The AI
can bank compression for later use. No mechanics, authored move values, AI,
targeting, AP timing, protocol, or server logic were edited for this work.
Pre-existing user changes in core/data/UI were left untouched.

The old four sheets all used 24 192px cels, 6 columns, 40ms declared frame time,
authored at 96px. Convergence's profile stretched playback to 1.2s at size 1.5;
Piercing used 1.1s, size 1.4, impact 0.6s, a 0.5s source charge at size 0.8,
and a beam from 0.5 to 0.86s at size 0.7. Both orb layers used generic primitives;
the target used a star and slash. The stream contained a pale red continuous line.
Those four effects are replaced, not rebound to different move IDs.

## Reference Research

Primary visual evidence: the user's first four supplied images for Convergence
and following six for Piercing Blood, plus anime frame sequences from the GIFs
below. Manga panels supplied by the user establish opposed palms, joined firing
hands, and narrow directional penetration. A colored manga panel is useful for
shape but is not treated as an official palette source.

| Source | Observed design evidence |
|---|---|
| [Official episode 19 page](https://jujutsukaisen.jp/episodes/19.php) | Kamo/Hanami episode context; gallery is not a clear technique close-up |
| [Official episode 37 page](https://jujutsukaisen.jp/episodes/37.php) | Choso/Yuji context; official stills 37-10 and 37-11 corroborate braced hands and scene rim lighting |
| [Kamo Convergence anime still](https://static.wikia.nocookie.net/jujutsu-kaisen/images/8/8a/Convergence_%28Anime%29.png/revision/latest?cb=20210219194405) | Opposed palms, elongated wet red drops |
| [Kamo Piercing Blood anime GIF](https://static.wikia.nocookie.net/jujutsu-kaisen/images/b/b7/Piercing_Blood_%28Anime%29.gif/revision/latest?cb=20210219195348) | Hand arrangement, sharp release, linear stream, localized contact on Hanami |
| [Choso Convergence anime GIF](https://static.wikia.nocookie.net/jujutsu-kaisen/images/3/3e/Choso_using_Convergence_%28Anime%29.gif/revision/latest?cb=20231019235024) | Blood-cell montage, shrinking isolated red sphere, bright specular/cyan rim, joined palms |
| [Choso Piercing Blood anime GIF](https://static.wikia.nocookie.net/jujutsu-kaisen/images/8/89/Choso_using_Piercing_Blood_on_Yuji_%28Anime%29.gif/revision/latest?cb=20231019235200) | Braced firing, narrow connection, perspective-enlarged leading tip, arm contact |
| [Convergence reference index](https://jujutsu-kaisen.fandom.com/wiki/Convergence) | Secondary source indexing manga chapter 46 / anime episode 19 |
| [Piercing Blood reference index](https://jujutsu-kaisen.fandom.com/wiki/Piercing_Blood) | Secondary explanation of release after compression; chapter 101 Choso/Yuji sequence |

This was still/GIF/frame-sequence research, not a claim of watching full episodes
or frame-timing analysis of licensed full-length footage. Official-game evidence
was unnecessary because direct anime/manga evidence was available.

## Shared Material

| Use | Color |
|---|---|
| Deep blood outline | `#24090e` |
| Dense shadow | `#590b16` |
| Crimson liquid | `#930d1d` |
| Wet surface red | `#bd2030` |
| Small specular accent | `#e34b4b` |

The compressed sphere is defined once as `compressed-blood`: an 8x8 logical-pixel
circle with 52 opaque pixels, a one-pixel dark rim, asymmetric crimson shading,
and a two-pixel upper-left wet accent. It has no floating satellite drops and no
internal flashing during the hold. Both effects use the exact same motif without
resizing it, at the same runtime size 0.9. Thus its diameter is 0.075 fighter
heights in either move. The review check compares blood pixels exactly, masking
only the intentionally different neighboring skin pixels.

The cyan halo/white specular close-up in Choso's anime is acknowledged, not copied:
the compact game-scale design keeps a red wet highlight and dark rim to preserve
the requested liquid identity without a luminous magic core. Kamo's darker liquid
rendering guides the shared palette. Neutral hand colors are separate from blood.

## Convergence

| Property | Final |
|---|---|
| Move / effect | `000097` / `ct-convergence` |
| Role / placement | Utility / source, activation on MOVE_FIRED |
| Frames | 24 x 50ms = 1.2s |
| Sheet | 1152x768 RGBA; 6 columns x 4 rows; 192x192 frames |
| Authoring | 96x96 logical cels, 2x nearest-neighbour export |
| Size | 0.9 fighter heights, center anchor `[0.5,0.5]` |
| Presentation marker | Frame 16, 0.8s, at the final sphere, not a damage event |

Blood is already visibly present on frame 0. Opposed palms move inward as three
wet droplets curve toward a rounded, drooping liquid mass. Uneven motion keys
accelerate compression, eliminating loose drops before the held sphere. Visible
blood area decreases monotonically: 865 -> 684 -> 340 -> 76 -> 52 logical pixels
at frames 0/5/10/15/19, about a 94% reduction. Frames 16-22 hold the same sphere
for 350ms; frame 23 is transparent. No outward burst, target track, background
flash, or permanent orb is attached. The source has only 2% inward body tension
and returns to identity.

## Piercing Blood

| Property | Final |
|---|---|
| Move / primary effect | `000098` / `ct-piercing-blood` |
| Role / placement | Attack / target, on resolved contact |
| Primary | 18 x 40ms = 0.72s; 1152x576 sheet, 6x3 |
| Source | `ct-pressure-core`, 8 x 40ms = 0.32s; 1152x384 sheet, 6x2 with unused cells transparent |
| Stream | `ct-piercing-stream`, 3 x 40ms = 0.12s; 1152x192 sheet, three occupied cells |
| Contact | Primary frame 6 at 0.24s; simultaneous full-distance stream onset |
| Sizes | Primary/source 0.9; beam cel thickness 0.72 average fighter heights |

All frames are 192x192 RGBA, authored at 96x96. Source cels 0-4 recall the already
prepared sphere, moving it only three logical pixels into position. Cel 5 briefly
elongates it toward the firing aperture from 0.20 to 0.24s. At 0.24s the sphere
is consumed visually, the full stream connects, and the target puncture starts.
The source hands remain braced through 0.32s. There is no gathering or shrinking
charge sequence in this move.

The beam occupies logical rows 46-49, with two crimson inner rows and dark red
edges. Its visible thickness is `4 / 96 * 0.72 = 0.03` average fighter heights,
approximately 6 pixels for a 200px fighter, independent of separation. Sparse red
surface streaks move forward across three cels; there is no white center, glow,
electricity, fire, or crawling extension. Art fills each occupied cel edge to edge;
runtime controls length and rotation from transformed source to actual recipient.

The target gets a roughly six-pixel puncture and small forward liquid fragments,
not a radial explosion. Peak target recoil is 0.16 fighter heights and -6 degrees
40ms after contact. Source recoil is only -0.025 heights. Both tracks remain
neutral through contact and return to identity; skipping/exit also uses existing
runtime cleanup. All three VFX layers render on the existing front plane.

## Seven Critic Lenses

The first review scored A 6.3/10, B 5.8/10, C 6.5/10 overall. These deliberately
modest scores reflected angular liquid, fin-like firing hands, and a chevron
contact, not a failure of the narrow-stream or shared-sphere concept. C was
selected for the clearest inward pressure and least orbital-magic character.

Final lead review below is subjective art direction, not an objective canon test.
Scores apply to the revised selected pair and inspected generated frames.

| Canon dimension: Convergence | Score / 10 | Difference or evidence |
|---|---|---|
| Hand/palm relationship | 7 | Opposed closing palms; abstract detached VFX hands rather than animated character arms |
| Compression | 9 | Monotonic shrinking liquid and centripetal drops; no film-style cellular montage |
| Final sphere | 9 | Tiny readable held circle, then exact recall in the source layer |
| Material | 8 | Rounded pooling and red wet highlight; fewer surface details than anime close-ups |
| Color | 8 | Crimson/shadow red; deliberately omits Choso's cyan halo and white specular |
| Recognizability | 8 | Preparation, palms, and compressed blood read together |

| Canon dimension: Piercing | Score / 10 | Difference or evidence |
|---|---|---|
| Firing pose | 6 | Overlapping fingers around aperture; existing character's full-body pose is not replaced |
| Sphere continuity | 10 | Same motif, palette, size, highlight, and pixel mask |
| Stream thickness | 9 | Four logical rows, 3% fighter height; no widening attack body |
| Blood material | 8 | Dark-red edges and crimson fluid, restrained wet texture |
| Speed | 9 | Full connection at release rather than visible travel |
| Impact | 8 | Tiny puncture and directional fragments; no persistent wounds or literal exit-hole simulation |
| Recognizability | 8 | Prepared sphere, braced hands, abrupt narrow shot |

1. **Canon fidelity:** major differences are abstract hands over existing poses,
   no cinematic camera/cell montage, no anime-specific glow, no perspective-big
   projectile tip, and no permanent wound. These are explicit game-scale choices.
2. **Generic anime VFX detector:** removed the star, slash, pale stream core and
   repeating orb charge. No runes, sparks, aura, electrical arcs, flame, bloom,
   widening cone, or explosion remain. Kept only reference-supported blood and
   hand shapes. Rejected adding a cyan pixel suggested by the critic because the
   shared small-scale material is clearer with the requested compact red palette.
3. **Compression reviewer:** exact edits were a rounded 32px liquid body with a
   drooping lobe, three drops instead of five/seven, 0.3-radian curvature instead
   of 0.85/1.7, accelerated inward keys, and a 350ms stable final hold. Every
   frame's measured blood area is non-increasing.
4. **Velocity/penetration reviewer:** replaced the star-chevron with a six-pixel
   puncture and short forward splinters. Added one 40ms directional deformation
   before the instantaneous shot. Power is conveyed by fast target recoil,
   not increasing VFX radius.
5. **Continuity reviewer:** side-by-side final/load frames share exact sphere
   pixels; runtime source sizes are equal. The two finite effects remain coherent
   across intervening events; stored resource state does not spawn a persistent
   decoration. Loading translates, never recompresses, the sphere.
6. **Pixel-art reviewer:** binary alpha, nine intentional opaque colors including
   skin, integer 2x export, nearest filtering, and coherent silhouettes. Small
   isolated dots are authored liquid fragments, not unplanned noise. Rotated/
   stretched runtime beam texels naturally differ from square source texels;
   runtime uses nearest sampling without smoothing.
7. **Integration reviewer:** actual GL captures establish correct owners,
   mirrored source/impact, source-center aperture, actual-target beam endpoints,
   distance-independent thickness, contact timing, and restored transforms. Local
   hand cels stay horizontally mirrored while the beam rotates to different rows;
   this is an aperture-centered hand abstraction, not a skeletal aiming system.

## Validation

Executed successfully:

```bash
python3 scripts/build_technique_animations.py --check
python3 scripts/review_blood_animations.py --check
mvn -pl graphics -am test '-Dtest=BattleEffectPackTest,BattleChoreographyTest,BattleAnimationPlayerTest,BoundedResourceEffectTest,HeadlessBattleSessionTest' -Dsurefire.failIfNoSpecifiedTests=false
```

The production exporter audits 55 move bindings, 66 distinct sheets, decoded RGBA
pixels against regenerated output, grids, impact markers via manifest equality,
profile equality, and catalog precedence. The paired review checks binary alpha,
palette, logical pixel scale, exact sphere continuity, all-frame decreasing blood
area, finite preparation, no premature target contact, equal source sizes, and
full-width four-row streams. Maven: **84 tests passed** (52 core, 32 graphics).
Pillow reports existing `getdata()` deprecation warnings; no export failure.

`BloodManipulationAIStrategyTest` was also attempted during initial inspection;
it was blocked loading the pre-existing dirty move/status data (`Sleep` validation),
not by an animation change. The bounded-resource and authoritative-session fixture
tests pass and do not pin editor-authored content values.

### Real Desktop Battle

`BloodManipulationBattlePreview` is an opt-in real LWJGL window with the actual
`BattleScreen`, resolver, blocking event path, PNG capture, and in-code resource/
move fixtures. It creates an isolated empty catalog root before startup rather
than depending on or rewriting the user's editable game data. Existing shaded
JAR is used only for dependencies/native libraries; current classes/resources
come first on the classpath.

```bash
mvn -pl graphics -am test-compile -DskipTests
java -XstartOnFirstThread \
  -cp 'graphics/target/test-classes:graphics/target/classes:core/target/classes:graphics/target/graphics-1.4.1.jar' \
  com.jjktbf.graphics.animation.BloodManipulationBattlePreview \
  --windowed --width=1280 --height=720 --ui-profile=mac
```

If the shaded JAR does not exist, build it first with
`mvn -pl graphics -am package -DskipTests`. Omit `-XstartOnFirstThread` off macOS
and use the host classpath separator. Output defaults to
`graphics/target/blood-manipulation-preview/`; override `jjktbf.preview.output`.

Completed all four scenarios on macOS real GL:

- Left-to-right and right-to-left at normal diagonal centers, distance 1020.201 canvas units.
- Both directions at short level centers, distance 467.750 canvas units, unchanged sprite sizes.
- Each scenario converts blood, advances three empty rounds, then consumes stored compression.
- Convergence captures at 0.15/0.55/0.90s; Piercing at 0.12/0.24/0.245/0.29/0.40s.
- Neutral opponent during Convergence; correct source and resolved recipient bindings.
- Neutral poses through 0.24s; target snap and restrained source recoil afterward.
- Resource preservation between moves; resource spend before firing; no extra blood spend.
- Natural completion, identity restoration, no finite/persistent orb left after the move or on hide.

The first harness run encountered a legitimate seeded miss on its third shot.
The harness now uses guaranteed successful random rolls and asserts resolved
contact before expecting a VFX capture. This changes only test fixtures.

Screenshots were visually inspected for the sphere hold/recall, full narrow beam,
both directions, compact contact, and different separations. The game's existing
damage blink hides the target in some post-contact captures, notably 0.29s;
numeric live-pose assertions independently verify the recoil during that blink.
The preview uses existing Miwa sprite assets as neutral test stand-ins, not new
Kamo/Choso character art.

### Remaining Limits

- This is scripted local real-GL validation, not a manual full battle or live network match.
- Diagonal and level placements probe row geometry; no multi-member team/row-selection UI test was run.
- Audio, every possible fighter sprite/palette, and other operating systems were not playtested.
- Hands are finite technique illustrations over the current sprite, not character-specific pose replacements; detached hands remain the main fidelity compromise.
- Source hands/target fragment art mirror horizontally; only the beam aims dynamically. The beam starts at the shared center aperture and follows live fighter transforms.
- No claims of perfect canon reproduction or a new persistent Compression architecture.

## Files

Runtime changes are limited to four technique PNGs, their entries in
`techniques/manifest.json`, and the two profiles in `choreography.json`.
Authoring changes are in `blood-ratio.json`; reusable exporter extensions are
motion keys and per-effect frame duration. `review_blood_animations.py`, this
directory, the refreshed technique review boards, and the opt-in Java preview
provide reproducibility and validation. `ANIMATIONS.md` links this workflow.
