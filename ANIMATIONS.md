# Battle Animations

This is a playback/integration system, not an animation editor. It reads exported
transparent PNG sprite sheets and JSON timing/choreography. It does not consume
GIF previews, video, `.anm`, or animation-generator source code.

## Locations

Bundled animations live in `graphics/src/main/resources/assets/animations/`:

- `catalog.json`: ordered list of pack directories to load.
- `<pack>/manifest.json`: sheet layout, frame timing, effect IDs and move bindings.
- `<pack>/sprites/`: exported PNG sprite sheets.
- `<pack>/overlays/`: optional synchronized reinforcement sheets.
- `choreography.json`: background/fighter tracks, timed extra layers, and profiles.

For an installed game, place overrides in an `animations/` directory in the launch
working directory, or set `-Djjktbf.animationsDir=/absolute/path/to/animations`.
Lookup is explicit directory, then working-directory overrides, then bundled
classpath resources. A pack's sheet paths are relative to its own manifest, so
copy the complete runtime pack when overriding that manifest. Catalog and
choreography overrides are complete files, not JSON patches. Assets/configuration
reload at the next battle entry; no Java rebuild is needed for external changes.

Resources under `src/main/resources` are included automatically in the desktop
JAR and packaged applications. No reference to the original external animation
project is required at runtime.

## Add A Move Animation

1. Put your exported sheet at `animations/custom/sprites/my-effect.png`.
2. Create `animations/custom/manifest.json` using the example below.
3. Create `animations/catalog.json` with `{"schemaVersion":1,"packs":["pixel-fx","custom"]}`.
4. Use the move's stable `id` from the move editor/data in `moveIds`. Do not use its display name.
5. Optionally override `choreography.json` to add/bind a profile, then start another battle.

Example for a 4-column sheet of six 96x96 frames (384x192 PNG):

```json
{
  "schemaVersion": 1,
  "sheetOrder": "row-major-top-left",
  "frameWidth": 96,
  "frameHeight": 96,
  "columns": 4,
  "effects": [
    {
      "id": "my-effect",
      "sheet": "sprites/my-effect.png",
      "frameCount": 6,
      "frameDurationMs": 80,
      "loop": false,
      "anchor": [0.5, 0.5],
      "placement": "target",
      "role": "attack",
      "impactFrames": [2],
      "moveIds": ["YOUR_MOVE_ID"]
    }
  ]
}
```

Frames are top-left row-major, without padding. Non-square frames are supported.
Filtering is nearest-neighbor; use normal transparent RGBA PNGs, not opaque GIF
previews. Frame duration is milliseconds. IDs must be unique across loaded packs;
move/event bindings should have one owner (first catalog pack wins across packs).
Missing/invalid assets are logged with the `BattleAnimation` tag and do not stop
combat. Invalid catalogs fall back to the existing generic battle visuals.

The existing export can be imported/reimported without its generators or previews:

```bash
python3 scripts/import_animation_pack.py /path/to/jjktbf-pixel-fx --with-audio
```

The importer retains credits, reports move bindings, and copies runtime assets
only. It never deletes unrelated files. `--destination` chooses another pack root.
The imported WAVs are retained, but playback currently uses the existing
`GameAudio`/`BattleAudioRouter` cues at the event impact rather than playing a
multi-contact WAV for every isolated contact.

## Choreography

`choreography.json` defines reusable `profiles`. `effects` maps an effect ID to a
profile; `roles` provides fallback profiles; `events` can bind a profile directly
to a semantic event (for example `MOVE_DODGED`) without needing a sheet.

Example standalone configuration:

```json
{
  "schemaVersion": 1,
  "effects": {"my-effect": "my-motion"},
  "profiles": {
    "my-motion": {
      "durationSeconds": 0.8,
      "impactSeconds": 0.25,
      "size": 1.6,
      "source": [
        {"at": 0},
        {"at": 0.25, "x": 0.25, "easing": "smooth"},
        {"at": 1, "easing": "smooth"}
      ],
      "target": [
        {"at": 0},
        {"at": 0.4, "x": 0.12, "rotation": -5},
        {"at": 1, "easing": "smooth"}
      ],
      "background": [
        {"at": 0},
        {"at": 0.25, "red": 0.2, "green": 0.3, "blue": 0.5},
        {"at": 1, "easing": "smooth"}
      ],
      "layers": [
        {
          "effect": "cursed-energy-burst",
          "placement": "target",
          "plane": "behind",
          "startSeconds": 0.25,
          "durationSeconds": 0.4,
          "size": 1.8,
          "transform": [{"at": 0, "alpha": 0}, {"at": 0.2}, {"at": 1, "alpha": 0}]
        }
      ]
    }
  }
}
```

- `durationSeconds`: primary visual duration; omitted/zero uses the selected sheet clip's duration.
- `impactSeconds`: when the displayed combat event/HP/audio can advance; omitted uses the clip's impact marker.
- `size`: effect width in fighter sprite heights; default 1.6. For beams it is
  the full cel's thickness in average source/target sprite heights, independent
  of the distance between fighters.
- `source`, `target`, `background`: independent transform keyframe arrays.
- `at`: normalized position 0..1 over the complete timeline; extra layers use their own lifetime.
- `x`, `y`: displacement in sprite heights for fighters; viewport width/height fractions for the background.
- `scaleX`, `scaleY`, `rotation`: scaling and rotation in degrees, around a fighter's footline or the background center.
- `alpha`, `red`, `green`, `blue`: 0..1 opacity/tint; omitted channels default to 1.
- `easing`: `linear`, `smooth`, or `step`, on the arriving keyframe.
- `layers`: extra effects with their own start, duration, placement, size, and transform track.
- `plane`: `background` (over the backdrop), `behind` (behind fighters), or `front` (above fighters, below HUD).
- `placement`: `source`, `target`, `beam`, or `projectile` for primary effects
  and extra layers; extra layers also support `screen`. Screen layers cover the
  viewport at size 1.

Beam cels face right and fill the horizontal tile edge to edge. They are stretched
between the current transformed fighter centers, with independent thickness and
rotation toward the actual target. Projectile cels also face right; their center
travels source-to-target over the layer's lifetime. These placements rotate rather
than flip for right-to-left attacks, work between different rows, follow relayout,
and skip safely when either endpoint is absent or both centers coincide. Layer
`x`/`y` remain sprite-height offsets, not path-progress controls. A projectile layer
should end just before the target contact; use a separate target sheet for the
impact. An inward-flowing beam is authored with inward-flowing cels, as in Absorb.

Extra layers extend the complete timeline if necessary. Non-looping sheets stretch
their frame timing to the configured layer lifetime; looping sheets repeat at
their authored rate until that lifetime ends. A static replacement background can
be registered as a one-frame effect and used in a `background`/`screen` layer,
with alpha keyframes for a crossfade. Positive fighter X follows the source's
facing direction, so the same choreography mirrors when the opponent acts.
Missing pose fields default to identity at each keyframe, not the preceding value.
Return the final keyframe to identity for a smooth exit; playback always restores
the unmodified layout at completion/skip/exit, even if you omit that final keyframe.

## Event Timing

Both battle modes use `BattleAnimationPlayer`. The local controller waits for a
render-thread start fence, then the impact marker; the online event cursor holds
the same authoritative event until that marker. The remainder plays before the
next event. Combat itself is still calculated exclusively by core/the server.

- `role: "attack"`: plays on `DAMAGE_DEALT`/`DAMAGE_IGNORED` using the actual recipient.
- `role: "targeted"`: activation/attempt on `MOVE_TARGETED`, once per resolved
  target after firing and target exchange. This covers non-damaging hostile utility
  and zero-power contact moves; it does not assert a hit or successful status.
- `role: "guard"`: collision on the incoming block/parry event, using its defensive
  move ID. The guard and incoming attack reach their impact markers together.
- Other roles at `placement: "source"`: activation on `MOVE_FIRED`.
- Other roles at `placement: "target"`: ally-defense activation on `DEFENSE_GRANTED`, when the actual ally is known.
- `eventTypes` in a pack: explicit event-bound effects such as `BLACK_FLASH`.
- `events` in choreography: reusable successful dodge, miss, block, and parry movement.

Attack `MOVE_FIRED` deliberately does not play a complete impact sheet: it has no
resolved target, and the attack might miss, be dodged, or be interrupted. Each
actual hit chooses its `componentIndex` slice, split halfway between adjacent
`impactFrames`. Missing indices use the first contact; extra authored move hits
reuse the final available contact. Reduced blocks animate at their subsequent
damage event rather than duplicating the same contact. AOE recipients each get
their own resolved hit. This does not change authored AP/hit delays.

Reinforcement overlays use the nullable `reinforced` execution snapshot carried
by local and online events, never eligibility or guessed plan state. Older events
without that optional field render uncoated. Overlay timing/grid must match the
base sheet and the overlay is drawn behind it, with the same frame and transform.
The live protocol is now v27, including resolved per-target activation events,
because network readers reject unknown fields and event enum values;
deploy the updated client and server together. Older clients are rejected by the
compatibility handshake rather than failing midway through event decoding.

## Supplied Coverage

The imported pack binds 44 existing move IDs, including all 31 Sorcerer moves in
the supplied export, technique examples, and shadow summons. It also binds the
Black Flash event. Sidestep, Evasive Dash, and Out of the way have distinct fighter
motion; energy/domain/Black Flash profiles animate the backdrop.

Fire and reverse-healing sheets are intentionally unbound templates because the
export has no corresponding authored move IDs. Eight status-loop sheets are
available to compose as timed layers, but are **not automatically attached to
status application/expiry**: current combat events have no typed status identity,
and reading log text or final round snapshots would produce incorrect lifetimes.
No interactive animation editor or status/combat rule changes are included.

### Cursed Spirit Pack

`cursed-spirits` adds **49 distinct move animations**: all 28 shared Cursed Spirit
moves, 10 Disaster Plants moves, and 11 Idle Transfiguration moves, including
non-default loadout moves. It does not replace the sorcerer pack or edit combat
content. Eighteen unbound supporting sheets provide composable charges, traveling
projectiles, beam streams, restoration particles, pollen, and domain scenery.

The new cels are original 96px pixel geometry exported at 192px with nearest
sampling. Family palettes are mint/ink for shared spirit energy, bark/leaf/rose
for Hanami, and flesh/ivory/violet for Mahito. Every move has its own sheet and
profile, not only a renamed or recolored generic effect. Multi-contact sheets
isolate Barrage, Focused Roots, and Wooden Ball contacts. Guards render on the
actual protected fighter, including ally-conferred Wooden Bulwark.

Sprite tracks include lunges, compression, recoil, shrinking, winged lift, and
root trips. Flower Field and Self-Embodiment add battlefield layers; beams and
projectiles span actual fighter positions. Transformations and scenery are
transient presentation and restore on completion/skip/exit, not persistent form,
domain, or status replacements. Existing form/domain systems still own gameplay.

Reproducible art source and review boards:

- `scripts/animation_art/cursed_spirits.json`: per-move compositions and profiles.
- `scripts/build_cursed_spirit_animations.py`: reusable pixel primitive exporter.
- `docs/animations/cursed-spirits/`: shared, Hanami, and Mahito cel storyboards.
- `cursed-spirits/CREDITS.md`: provenance and source-library reference credit.

With Pillow installed, export or audit from the repository root:

```bash
python3 scripts/build_cursed_spirit_animations.py
python3 scripts/build_cursed_spirit_animations.py --check
```

Export regenerates the `cursed-spirits` sheets/manifest and `cs-` profiles in the
global choreography file, preserving other packs and profiles. Edit the authoring
JSON for reproducible changes. `--destination` selects an alternate animations
root; `--review` selects another storyboard directory. `--check` validates current
spirit-type/technique coverage, unique bindings/sheets, RGBA transparency and grids
without pinning editable move costs, powers, effect rows, or character stats.

## Implementation And Verification

- `graphics/.../animation/BattleEffectPack.java`: validated manifests, sheets, contact clips, texture ownership.
- `BattleChoreography.java`: profile schema and pure keyframe interpolation.
- `BattleAnimationPlayer.java`: shared event routing, timeline, layers, source/target lookup.
- `BattleScreen.java`: local/online pacing and battlefield draw hooks.
- `CombatantPanel.drawSprite`: visual transforms without changing HUD bounds or combat state.

Tests construct their own moves/packs and mock GL for frame/transform/lifetime
checks. A bundled-asset smoke test decodes the real PNGs against their declared
grids. Mock GL tests are not a substitute for a live desktop/multiplayer playtest.

```bash
mvn -pl graphics -am test '-Dtest=BattleEffectPackTest,BattleChoreographyTest,BattleAnimationPlayerTest,ProtocolJsonTest,HeadlessBattleSessionTest' -Dsurefire.failIfNoSpecifiedTests=false
```
