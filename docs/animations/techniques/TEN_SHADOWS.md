# Ten Shadows Summoning

All eight current normal summon moves use the same liquid-shadow layer and timing.
Command, attack, rescue, desummon, and actual entity-entrance animations are unchanged.
No ritual-type summon move currently appears in the Ten Shadows move tree.

| Move ID | Effect | Hand Reference |
| --- | --- | --- |
| `000030` | `ct-white-dog` | Divine Dogs |
| `000031` | `ct-black-dog` | Divine Dogs |
| `000035` | `ct-summon-nue` | Nue |
| `000036` | `ct-summon-toad` | Toad |
| `000037` | `ct-summon-serpent` | Great Serpent |
| `000038` | `ct-summon-totality` | Divine Dogs, explicitly authorized |
| `000039` | `ct-summon-winged-toads` | Nue then Toad, explicitly authorized |
| `000040` | `ct-summon-elephant` | Max Elephant |

## Reference And Art

The authoritative reference is the user-supplied, wikiHow-labelled nine-sign
illustration in this task (Divine Dogs through Mahoraga). Only its hand poses were
used, not its backgrounds, text, clothing or colors. Pixel geometry is authored in
`scripts/animation_art/techniques/ten-shadows-summon-art.json`, using the existing
four-color hand palette from the blood-animation art. This is a hand-authored
pixel adaptation, not an embedded crop of the reference.

The image does not separately label White Dog, Black Dog, Totality or the fusion.
The user explicitly authorized the Divine Dogs sign for all three dog variants
and a Nue-to-Toad sequence for the fusion. Rabbit Escape, Piercing Ox, Round Deer
and Mahoraga have no current summon moves here and were not added as content.

Reference landmarks retained for the focused visual review:

- Divine Dogs: leftward stacked fingers, overlapping bent fingers, upright and angled digits.
- Nue: crossed wrists, four fanned fingers per side, overlapping upright thumbs.
- Toad: opposed bent fingers, thumb tips meeting below an open center.
- Great Serpent: overlapping diagonally aligned hands, four close fingers and raised rear thumb.
- Max Elephant: horizontal layered hands, bent upper fingers and lower right-pointing fingers.

Hands are authored in the supplied orientation without per-art flipping. Existing
source-effect playback deliberately mirrors the complete sign for the opposite
side. The abstract foot layer keeps its world orientation, as other ground VFX do.

## Composition And Timing

`ten-shadows.json` retains the eight move/effect IDs. Each primary sheet contains
only a hand sign; each choreography profile layers the same unbound
`ct-ten-shadows-rise` sheet at `source-feet`. There is no creature art in either
component. Creature motifs and the older shadow pool remain available unchanged
to attacks, commands and other existing effects.

- 24 frames at 50ms, 1.2 seconds total for every summon, including Max Elephant and fusion.
- Hand sign starts first; pool begins around 150ms, spreads, then raises rounded ink columns.
- Common impact marker at 800ms; hands stay readable at the peak and clear before the end.
- Ink sinks immediately after the peak, leaving an empty final frame at 1150ms.
- 96x96 logical cels, 192x192 exported RGBA, 6-column 1152x768 sheets, nearest-neighbour only.
- Near-black ink with sparse dark blue-grey highlights; opacity fades do not soften pixel edges.
- Hands occupy a 0.95 fighter-height tile; the ground tile is 1.55 visible fighter heights.
- Ground anchor is at logical y=82, keeping the pool at the summoner's actual soles.

Only the exporter needed two small accommodations: honoring an authored anchor
and allowing identical pixels for explicitly identical art compositions (the dog
signs). No runtime Java, protocol, combat data or summon lifecycle changes.

## Export And Review

```bash
python3 scripts/build_technique_animations.py
python3 scripts/build_technique_animations.py --check
python3 scripts/review_ten_shadows_animations.py
python3 scripts/review_ten_shadows_animations.py --check
```

`shadow-summons.png` shows the hands, `shadow-summoning-base.png` shows the common
ink, and `shadow-summons-composite.png` shows their combination over a 96px fighter
tile, including the opposite-side sign mirror. This is an offline scale/anchor
review using production cels, not a live desktop or multiplayer playtest.

The audit discovers summon coverage from current Ten Shadows `SUMMON_CHARACTER`
effects, checks bindings, common layer/timing, sign-only art, dark ink, exact 2x
pixels and finite release. It does not pin editable stats, AP, costs or character
definitions. Export fidelity is checked across the whole technique pack.
