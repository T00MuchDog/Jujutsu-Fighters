# Complete move animation coverage

The 2026-09-10 audit compared all 140 current move IDs with the ordered runtime
catalog. It found five unbound moves; all five now have dedicated animations in
the existing `techniques` pack. Thirteen legacy example bindings are intentionally
shadowed by earlier technique-specific bindings.

## Art and reference decisions

| Move | Visual treatment | Reference / consistency |
| --- | --- | --- |
| `000085` Gorilla Mode | Three cores exchange emphasis, the gorilla crest expands, blue shoulder energy spreads, and the fighter broadens its stance. | Panda switches to his brother's power-oriented core. The black/white crest and brown knuckle palette follow the bundled Panda/Gorilla fighter sprites. Core colors are illustrative identifiers. |
| `000086` Gorilla Pummel | Three separately timed, differently angled fur-backed punches with blue CE edging, sharp contact stars, and individual lunges/recoils. | The authored move describes three CE-reinforced Gorilla strikes. Contact isolation follows Barrage and other existing multi-hit animations. |
| `000087` Unblockable Drumming Beat | One planted punch followed by narrow pressure rings traveling through the body and diminishing lateral vibration. | Gorilla strikes resonate through the recipient even through a guard. The continuing internal shock is distinct from Pummel's short external impacts. |
| `000088` Return to Panda Core | Reverse core transfer, inward-folding shoulder energy, Panda crest and a relaxed stance. | The previously retained Panda core resumes control; this is a core switch, so the visual uses neither healing particles nor an RCT aura. |
| `000151` Collapse | A 7:3 ruler marks masonry, a punch fractures the wall, then heavy debris lands at the delayed resolved contact. | Nanami uses Ratio on surrounding structures. The ruler and red weak-point marker are the same motifs as his existing Ratio moves. Concrete uses neutral gray/ivory; debris falls under gravity. |

Reference reading:

- [Panda — Jujutsu and Gorilla Mode](https://jujutsu-kaisen.fandom.com/wiki/Panda#Jujutsu):
  core switching, Gorilla physiology and Unblockable Drumming Beat's guard-penetrating
  vibration (Kyoto Goodwill battle with Mechamaru).
- [Collapse](https://jujutsu-kaisen.fandom.com/wiki/Collapse): Nanami marks an area's
  weak points and punches a wall, causing it to crumble onto Mahito; chapter 23 /
  episode 11.
- Local appearance references: `panda_frontsprite.png` and
  `pandaGorilla_frontsprite.png` under `assets/sprites/characters/`.

All six new sheets are original geometric pixel art authored in the two JSON
files below. No web images or animation frames were copied. They use the existing
96px logical canvas, 192px nearest-neighbor export, transparent RGBA sheets,
outlined shapes, restrained palettes, and reusable choreography tracks.

## Timing and runtime binding

| Effect | Event / placement | Timing |
| --- | --- | --- |
| `ct-gorilla-mode` | `MOVE_FIRED` / source | 24 × 40ms; marker 480ms |
| `ct-gorilla-pummel` | resolved damage / target | 54 × 30ms; contact slices `[0,18)`, `[18,36)`, `[36,54)`; local marker 270ms each |
| `ct-drumming-beat` | resolved damage / target | 30 × 30ms; marker 360ms |
| `ct-panda-core-return` | `MOVE_FIRED` / source | 24 × 40ms; marker 480ms |
| `ct-collapse-structure` | Collapse's `castEffect`, `MOVE_FIRED` / source | 24 × 40ms; marker 400ms |
| `ct-collapse` | resolved damage / actual recipient | 30 × 40ms; marker 560ms |

The structural cast runs once when Collapse fires. Debris belongs to the later
resolved contact, so the source cast cannot show the target taking an early hit.
Core/server event timing and current authored hit delays remain authoritative.
Source and recipient motion mirror through the shared animation player.

## Reproduce and inspect

Authoring sources:

- `scripts/animation_art/techniques/panda.json`
- `scripts/animation_art/techniques/ratio-collapse.json`
- Reused `ratio-ruler` motif from `blood-ratio.json`.

From the repository root, with Pillow installed:

```bash
python3 scripts/build_technique_animations.py
python3 scripts/build_technique_animations.py --check
python3 scripts/check_move_animation_coverage.py
mvn -pl graphics -am test '-Dtest=BattleEffectPackTest,BattleChoreographyTest,BattleAnimationPlayerTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Review boards show wind-up, contact, follow-through and release:

- [Panda](panda.png)
- [Ratio, including Collapse's debris and structural cast](ratio.png)

The export check compares every exported pixel and profile with the authoring data.
The catalog audit checks all **current** move IDs, not fixed content values, and
exits nonzero for missing bindings, unknown moves, broken sheet paths or dangling
references. `--moves` and `--animations` can audit other content/asset directories.
The animation tests include dynamic decoding/drawing of all bundled sheets under
mock GL. The boards were visually inspected; a live desktop battle playtest is
still a separate manual check.
