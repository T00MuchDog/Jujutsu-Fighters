# Simple Domain Visual Remake

## Repository Findings

Inspected the current animation guide, catalog, all three existing manifests,
choreography, domain-backdrop configuration, old Simple Domain sheets, domain/move/
character JSON, Self-Embodiment implementation, domain engine/events/snapshots,
BattleScreen and animation tests before integration.

| Identity | Current binding |
| --- | --- |
| Simple Domain | Move `000138` |
| New Shadow Style Simple Domain | Move `000026` |
| Both establish | Anti-domain definition `000000` |
| Shared activation | `simple-domain-establish` |
| Persistent layers | `simple-domain-field-back`, `simple-domain-field-front` |

Both moves use `ESTABLISH_DOMAIN` on fire. Miwa (`000008`) is the authored New
Shadow Style user. Character loadouts are editable and are not test fixtures.
The retired dome and katana-bound effects are removed from the manifest and
choreography, and their two obsolete PNGs removed by the exporter. Historical
unbound WAV files are not used by animation playback and have not been changed.

## Canonical Research

The research separates official episode identification from anime/manga imagery
hosted by a secondary reference site. Still images and the technique description
were inspected; this is not a claim to have watched complete licensed episodes
or verified exact shot-by-shot timing. No official game depiction was needed to
override the stronger anime/manga evidence.

| User | Reference | Observation / confidence |
| --- | --- | --- |
| Miwa | Anime episodes [15](https://jujutsukaisen.jp/episodes/15.php), [17](https://jujutsukaisen.jp/episodes/17.php); manga ch.35, ch.40 | Standing sword posture, bright cyan-white floor area; small circular area around user. The stated 2.21m radius is Miwa-specific, not a universal scale. |
| Todo | Anime [45](https://jujutsukaisen.jp/episodes/45.php); manga ch.130 | Low hand-seal posture over bright floor light inside Mahito's Domain. The attempted defense is too late; posture alone does not prove successful establishment. |
| Ui Ui | Anime [38](https://jujutsukaisen.jp/episodes/38.php); manga ch.102 | Upright hand seal; coffin/background geometry is not evidence of a universal Simple Domain wall. |
| Kusakabe | Manga ch.246, ch.254 | Sword stance and adaptable range; monochrome panels cannot establish a universal color. |
| Yuki | Manga ch.206 | Low posture over a plain white oval area inside Womb Profusion. No need for an enclosing dome. |

Direct visual references (research only):

- [Miwa anime still](https://static.wikia.nocookie.net/jujutsu-kaisen/images/7/71/Kasumi_prepares_her_Simple_Domain_%28Anime%29.png/revision/latest?cb=20210318005739)
- [Miwa activation GIF reference](https://static.wikia.nocookie.net/jujutsu-kaisen/images/5/55/New_Shadow_Style_Simple_Domain_%28Anime%29.gif/revision/latest?cb=20210123010638)
- [Todo anime still](https://static.wikia.nocookie.net/jujutsu-kaisen/images/c/cd/Aoi_Todo_attempts_to_use_Simple_Domain_%28Anime%29.png/revision/latest?cb=20231214223609)
- [Ui Ui anime still](https://static.wikia.nocookie.net/jujutsu-kaisen/images/d/d6/Ui_Ui_using_Simple_Domain_%28Anime%29.png/revision/latest?cb=20231026214302)
- [Kusakabe manga](https://static.wikia.nocookie.net/jujutsu-kaisen/images/9/99/Kusakabe%27s_Simple_Domain.png/revision/latest?cb=20231224160703)
- [Yuki manga](https://static.wikia.nocookie.net/jujutsu-kaisen/images/7/7e/Yuki_Tsukumo%27s_Simple_Domain.png/revision/latest?cb=20221205005324)
- [Secondary index and chapter citations](https://jujutsu-kaisen.fandom.com/wiki/Simple_Domain)

Shared visual invariants: a small, ground-level circular area centred on
the user; outward establishment; scenery outside remains intact. In a side view
the ground circle becomes an ellipse. Cyan-white is an anime-informed palette,
not an assertion of universal canonical color. Thin line weight, the exact
expansion timing and loop timing are deliberate pixel-game adaptations.
Sword poses, hand seals, vows and counterattacks belong to the user, not the
shared boundary. No runes, dome, vertical shell, particles, or invented lightning.

### User Reference Correction

The user's supplied Miwa anime still clearly shows a blue filled base and small,
pointed energy waves at the edge. The supplied manga panels also show swept,
spiky wave shapes tracing the circumference. These correct the first pass's
overly minimal empty ring. The selected art now includes both the blue floor
and low rim wavelets; their tips lean along the direction of travel, and six
unevenly spaced waves rotate around the owner. This is not a dome or a wall.

## Candidate Review

The initial candidates each included a 24-frame activation and 12-frame loop.
The selected `quiet-chalk` folder now contains the revised filled-blue design
with a smoother 48-frame loop; the other folders retain the earlier alternatives.
Each folder contains dark/light contact boards, split sheets and `lifecycle.gif`
(activation followed by three maintained cycles). Review considered both phases.

| Candidate | Canon /5 | Avoids Generic VFX /5 | Persistence /5 | Decision |
| --- | --- | --- | --- | --- |
| Quiet Chalk (initial review) | 4 | 5 | 5 | Selected foundation; subsequently corrected to include the user's blue fill and spiky rim waves. |
| Double Lip | 3 | 3 | 4 | Rejected: double contours approach a portal/emitter look. |
| Low Veil | 2 | 2 | 3 | Rejected: side wisps suggest an unsupported rising wall and add flicker. |

Separate canon/generic-VFX and persistence reviewers selected Quiet Chalk. The
final white was cooled from ivory, keeping a dark supporting outline for light
background readability. Review was based on contact boards and temporal code,
not a claim of continuous video observation. The integration review checked the
event/state path; live screenshots subsequently exposed transparent-sprite-padding
misalignment, fixed with reusable visible-content foot anchoring.

## Art And Timing

Activation: 24 frames at 50ms, 1.2s total. Tight floor indication frames 0-3,
rapid eased outward expansion frames 4-10, settled boundary frames 10-23. Frame
10 / 0.5s is the established-radius impact point. Waves continue moving while
the boundary settles. The final two cels match maintained cel zero; the field
fades in during the last 0.08s of activation for a continuous handoff.
It never shrinks or vanishes at the end.

Maintained field: 48 frames at 80ms, 3.84s looping. Constant radius, no pulsing
size or repeated activation. Six swept, pointed wavelets orbit the boundary.
96x96 logical cels, 192x192 export, eight columns, binary-alpha hard pixels.
The ellipse has logical radii 40x13, a dark one-pixel supporting outline, and a
two-tone blue filled interior. The entire floor draws behind the fighter. The
rim splits at logical y=48; each elevated wave is sorted by its ground root,
so foreground crests overlap naturally without putting blue fill over the owner.

Runtime tile width is 2.4 visible fighter heights; the boundary diameter is about
2 visible fighter heights (40/48 of the tile). This is a readability adaptation,
not a world-metre radius. Visible content excludes transparent canvas padding.
The anchor follows the rendered foot position, including pose displacement,
rotation of padded soles, temporary physical size and current layout/side/row.
Waist-cropped back sprites use the visible base as their ground anchor, rather
than inventing feet below the image. The existing HUD can occlude the front arc
at the lower battlefield edge; the rear boundary remains readable and never
paints over the log or resource cards.
The circle itself remains ground-oriented rather than rotating with the torso.

## Domain Integration

`DomainBackdropPlayer` retains the existing lifecycle/replay authority and adds
`placement: "owner-local"` definitions alongside the shipped default `backdrop`.
Each runtime instance retains both domain-definition ID and combatant-instance
owner ID. Owner-local instances never enter latest-backdrop competition.

`beginOpening` looks forward from `MOVE_FIRED` for the same owner's declaration
and successful `DOMAIN_COUNTER_ESTABLISHED` at the same tick (and online round).
The `domain` animation role only plays with that confirmation. Rejected, failed,
interrupted or merely declared moves cannot leave a maintained field. Parrying
with New Shadow Style does not replay establishment.

After confirmed opening, the field delays 1.12s while the finite clip establishes
and settles the boundary, then fades in over 0.08s underneath its matching tail.
Ground layers retain world orientation, including on the opponent side, to keep
the rotating waves aligned through the handoff. Authoritative direct
establishment without an opening can also restore it. The lifetime is never the
delay or animation clock: only the exact active domain instance controls it.
`DOMAIN_COLLAPSED` removes that instance immediately, regardless of cause.

Local round reconciliation copies `BattleState.domainBattlefield().activeDomains()`
with owner IDs. Online restoration uses `MatchState.domainBattlefield()` and
`DomainState.ownerInstanceId()`. Replay rewinds the final snapshot's establishment/
collapse events before consuming them in order. Skip reconciles immediately;
routine sync preserves loop phase. Finite-player clear does not clear domains.

Rear/front halves draw after the owner's plate, immediately around its sprite,
before all HUDs. This avoids plate occlusion and preserves per-combatant depth.
Textures are reused from the normal catalog, nearest-filtered, and disposed by
`BattleAnimationPlayer`; the domain player still owns full-scene textures.
No gameplay, protocol, move costs, vows, parry, counter, or collapse rules changed.

## Reproduce

```bash
python3 scripts/build_simple_domain_animations.py
python3 scripts/build_simple_domain_animations.py --export
python3 scripts/build_simple_domain_animations.py --check
mvn -pl graphics -am test '-Dtest=DomainBackdropPlayerTest,BattleAnimationPlayerTest,BattleEffectPackTest,BattleChoreographyTest,CombatantPanelTest,DomainBattlefieldTest,HeadlessBattleSessionTest,ProtocolJsonTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Opt-in real desktop preview, using the actual BattleScreen and core resolver:

```bash
mvn -pl graphics -am package -DskipTests
java -XstartOnFirstThread -Djjktbf.data.root=/path/to/isolated/preview-data -cp 'graphics/target/test-classes:graphics/target/graphics-1.4.1.jar' com.jjktbf.graphics.animation.SimpleDomainBattlePreview --windowed --width=1280 --height=720 --ui-profile=mac
```

On other platforms omit `-XstartOnFirstThread`; use the platform's classpath
separator. `--ui-profile=windows --width=1366 --height=768` exercises the Windows
canvas on the same desktop. Override `-Djjktbf.preview.output=...` to retain a
second set of captures. The launcher automates UI confirmations in test scope;
it uses in-code moves/domain definitions carrying the presentation binding IDs,
not mutable bundled gameplay values. It checks both activations, three later
rounds each, core-generated owner-hit collapse, hostile coexistence and cleanup.

Live validation used macOS LWJGL with Mac and Windows layout profiles. Screenshots
are emitted under `graphics/target/simple-domain-preview*`. This is scripted
BattleScreen/core integration, not a manual full-roster or two-client server
playtest. Multiplayer snapshot/replay and identity are covered by automated tests;
a live two-client reconnect session remains a validation limitation. The preview
also establishes independent fields on both owners, then collapses just one to
verify the opponent's field remains.

### Validation Record (2026-09-08)

- Full core/graphics test runs passed; current reports contain 906 core and 421
  graphics tests, with zero failures, errors or skips.
- The eight focused animation/domain/protocol classes account for 131 tests.
- Deterministic export audit passed: binary alpha, uniform nearest 2x pixels,
  correct grids and matching final activation/field-zero cel. The reference
  correction additionally checks 48 distinct loop cels, continuous blue fill,
  no floor pixels in the front layer, unclipped waves and a normal-sized seam step.
- The desktop package rebuilt successfully. A combined package invocation hit
  its command timeout; the separate packaging retry completed successfully.
- The final packaged-app preview passed at Mac 1280x720 and Windows-profile
  1366x768 on macOS, including both-owner and opponent-only captures.
- `git diff --check` passed. No gameplay content/rules were edited by this task.

Reference correction validation: the 49 animation/domain presentation tests and
export audit passed, and the desktop package rebuilt. The revised blue/wave art
was exercised in the real LWJGL battle screen using the Windows layout on macOS,
including both move bindings, later rounds, hostile scenery, independent owners
and collapse. Revised captures are in `graphics/target/simple-domain-preview-blue/`.
