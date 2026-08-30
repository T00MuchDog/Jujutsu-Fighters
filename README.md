# Jujutsu Kaisen — Turn Based Fighter

## Play The Game

### What is it?

**Jujutsu Kaisen — Turn Based Fighter** is a fan-made tactical fighting game
inspired by *Jujutsu Kaisen*. Choose your fighters and their move sets, then plan
attacks and defenses on a timeline. Good timing, careful use of Cursed Energy,
and predicting your opponent matter as much as raw power.

The game includes local battles against the computer, online multiplayer, 1V1
fights, and larger team formats. If this is your first match, start with a 1V1
single-player battle.

### Download and install

You do **not** need Java, programming tools, a GitHub account, or a copy of this
repository. The official installers include everything required to play.

1. Open the **[latest GitHub Release](https://github.com/T00MuchDog/Jujutsu-Fighters/releases/latest)**.
2. Scroll down to **Assets**. If the files are hidden, click **Assets** to show them.
3. Download the installer for your computer:

| Your computer | File to download |
|---|---|
| Windows 10 or 11, 64-bit | `JujutsuKaisenFighter-<version>-windows-x64.msi` |
| Mac with an Apple M-series chip | `JujutsuKaisenFighter-<version>-macos-arm64.dmg` |
| Mac with an Intel processor | `JujutsuKaisenFighter-<version>-macos-x64.dmg` |

On a Mac, open **Apple menu > About This Mac** if you are unsure which version
you need. A line beginning with **Chip: Apple** means `arm64`; a line beginning
with **Processor: Intel** means `x64`.

Do not download the server `.jar`, `SHA256SUMS`, or the automatically generated
**Source code** files just to play the game.

#### Windows

1. Double-click the downloaded `.msi` file and follow the installation steps.
2. Open **Jujutsu Kaisen Fighter** from the Start menu.
3. The current installer is unsigned, so Windows may display **Windows protected
   your PC**. If you downloaded it from the official Releases page above, click
   **More info**, then **Run anyway**.

#### macOS

1. Double-click the downloaded `.dmg` file.
2. Drag **Jujutsu Kaisen Fighter** into the **Applications** folder.
3. Open the Applications folder, Control-click or right-click the game, choose
   **Open**, then choose **Open** again. This first-launch step is required because
   the current app is unsigned. Future launches can be opened normally.

There is not currently an official Linux installer.

### Your first battle

1. From the main menu, choose **Single Player**.
2. Choose **1V1**. Leave **Stat Mode** on **Standard** to use each fighter's
   normal stats; **Equalized** moves all fighters' stats halfway toward the same
   baseline while leaving their moves and abilities unchanged.
3. Choose your fighter. If it needs a move set, open **Learned Moves** and click
   move cards to add them; right-click a move in the move set to remove it. Then
   choose the computer's fighter and move set. **Random Fighter** automatically
   creates a legal move set when needed and is an easy way to get started.
4. On the battle screen, choose **Start Battle**.
5. Plan your round by clicking a move card to place it in the first available
   space, or drag it onto the **Offense** or **Defense** timeline to choose its
   timing. Drag a placed move to retime it. Right-click it, or drag it off the
   timeline, to remove it.
6. Choose **Lock In** when you are satisfied. In a team battle, plan and lock in
   every fighter and check each move's target.
7. Both sides' plans play out one timeline tick at a time. Review what happened,
   choose **Next Round**, and plan again. You win when the opposing team has no
   fighters left alive.

### Battle basics

- **HP (Health Points):** A fighter is defeated when this reaches zero.
- **AP (Action Points):** Your planning budget for the round. A move occupies a
  number of AP ticks on its timeline.
- **CE (Cursed Energy):** A limited resource spent by many powerful moves. CE is
  paid when a move begins, so do not queue more than you can afford.
- **Offense and Defense:** These are separate timelines, so an attack and a
  defensive move can cover the same ticks, but they share the fighter's AP and
  CE budgets.
- **Unleash:** The point inside a move where its effect happens. A move starting
  on tick 3 with Unleash 2 fires on tick 4. Compare fire ticks, not only where
  the move segments begin.
- **Block:** Reduces matching damage when the block is strong enough.
- **Parry:** Negates a matching hit and may stagger a melee attacker, but some
  attacks cannot be parried.
- **Dodge:** Gives a chance to avoid the covered attack completely.
- **Move cards:** Read each card's power, accuracy, AP, CE, Unleash, Potency,
  range, and description before placing it. Hover over highlighted terms in a
  card description to see a short explanation in game.

Begin with one affordable attack and one defense rather than spending every AP
and CE point. Try to make your defense active before the attack's fire tick, and
save some CE for later rounds.

### Learn more

- **[Full glossary and new-player tutorial](GLOSSARY.txt):** Detailed definitions
  for stats, move properties, status effects, timing, damage, Cursed Energy,
  abilities, domains, and other mechanics.
- **In-game move cards:** Hover over highlighted keywords for quick definitions,
  and read each move's full description because defensive coverage and special
  rules vary between moves.
- **[Latest release and release notes](https://github.com/T00MuchDog/Jujutsu-Fighters/releases/latest):**
  Download updates and see what changed.

---

## Developer Documentation

A turn-based combat game built in Java with a multi-module Maven architecture.
Characters, moves, and abilities are all data-driven (JSON) and authored through
the graphical editors inside the LibGDX front-end.

---

## Module Structure

```
JJKTBF/
├── core/      Shared domain model, deterministic headless battle engine, and multiplayer protocol DTOs.
├── graphics/  LibGDX desktop client. Contains local play, editors, async HTTP/WebSocket services,
│              and multiplayer screens. GraphicsMain is the desktop entry point.
├── server/    Javalin multiplayer server, guest auth, challenge/match managers, JDBC, and Flyway.
└── data/      JSON data files shared by all modules at runtime.
    ├── moves/all_moves.json
    ├── characters/all_characters.json
    ├── abilities/all_abilities.json
    └── techniques/all_techniques.json
```

---

## Build And Run

This is a Maven project, not a Gradle project. Run commands from the repository
root. Build the desktop fat JAR with:

```bash
mvn -Drevision=1.4.1 -pl graphics -am package
```

The resulting launcher is `graphics/target/graphics-1.4.1.jar`.

### Normal game

The launcher selects one UI profile at startup. macOS defaults to `MAC` and
Windows defaults to `WINDOWS`. The game, battle state, rules, networking, and
all other gameplay code remain shared.

Both profile files intentionally begin with the existing Mac-tuned geometry.
The Windows profile is independent and carries `2560 x 1440` target-resolution
metadata ready for a later redesign; normal rendering still uses the live window.

macOS (Author game, default `MAC` profile):
 
```bash
mvn -Drevision=1.4.1 -pl core,graphics -am clean verify
java -XstartOnFirstThread -Djjktbf.authoring=true -jar graphics/target/graphics-1.4.1.jar
```

Windows PowerShell or Command Prompt (Author game, default `WINDOWS` profile):

```bash
mvn "-Drevision=1.4.1" -pl core,graphics -am clean verify
java "-Djjktbf.authoring=true" -jar graphics/target/graphics-1.4.1.jar
```

The profile can be overridden independently of the host OS. For example, this
launches the normal game with the Windows presentation on a Mac:


Use `--windowed --width=1600 --height=900` when a windowed normal-game launch is
more convenient. The equivalent persistent JVM override is
`-Djjktbf.ui.profile=MAC` or `-Djjktbf.ui.profile=WINDOWS`.

> **macOS rule:** Every Java launch of the graphics JAR on macOS requires
> `-XstartOnFirstThread`. The packaged `.app`/`.dmg` supplies it automatically.
> Do not pass this option on Windows.

### Battle UI profiles

Battle layout values are edited manually in two independent tracked files:

The tracked profile files are:

```text
graphics/src/main/resources/assets/ui/battle-layouts/mac.json
graphics/src/main/resources/assets/ui/battle-layouts/windows.json
```

### Run multiplayer locally

The server runs with an embedded persistent H2 database by default:

```bash
mvn -Drevision=1.4.1 -pl server -am package
java -jar server/target/server-1.4.1.jar
```

Build the desktop client and launch two instances with separate guest profiles:

```bash
mvn -Drevision=1.4.1 -pl graphics -am package
java -XstartOnFirstThread -Djjktbf.data.root="$PWD/.local/client-a" -jar graphics/target/graphics-1.4.1.jar
java -XstartOnFirstThread -Djjktbf.data.root="$PWD/.local/client-b" -jar graphics/target/graphics-1.4.1.jar
```

The `-XstartOnFirstThread` option is macOS-only. PostgreSQL/Docker setup, non-macOS commands, production configuration, and the complete two-client flow are documented in **[`MULTIPLAYER.md`](MULTIPLAYER.md)**. The design and protocol are documented in **[`MULTIPLAYER_ARCHITECTURE.md`](MULTIPLAYER_ARCHITECTURE.md)**.

> **Where data lives:** On first launch the game copies its bundled default
> data into a per-user directory (`~/Library/Application Support/JujutsuKaisenFighter/`
> on macOS, `%APPDATA%\JujutsuKaisenFighter\` on Windows). The in-game editors
> read and write there, so edits persist while you use the same game version.
> When a newer release is launched, its bundled moves, abilities, characters,
> and techniques replace the local catalogs in full. The new release therefore
> becomes the authoritative game-data state for that profile.

### Run tests
```bash
mvn clean verify
```
Tests cover the original combat rules plus deterministic shared-engine commands,
serialization, guest authentication, challenge concurrency, match lifecycle,
client persistence/reconnect behavior, and real HTTP/WebSocket integration.

## Audio

The LibGDX client includes a centralized audio system for menu/battle music, UI
effects, and battle-scene effects. Registered files are optional, so audio can be
wired before final assets arrive. Existing drop-in paths, event mappings, mixer
settings, and the process for adding a new cue are documented in
**[`AUDIO.md`](AUDIO.md)**.

---

## Packaging & Releases

The game ships as self-contained installers with a bundled Java runtime —
players need no JDK, Maven, or source. See **[`RELEASE.md`](RELEASE.md)** for
the full process; the short version:

- The version lives in **one place**: `<revision>` in the root `pom.xml`.
- Local build for the current OS:
  ```bash
  ./release.sh 1.0.0          # or: ./release.sh 1.0.0 fast   (skips tests)
  ```
  Output: `dist/JujutsuKaisenFighter-<ver>-<os>-<arch>.dmg` (macOS) / `.msi` (Windows).
- Automated cross-platform release: push a tag `v1.0.0`. GitHub Actions builds
  macOS (arm64 + x64) and Windows (x64) installers plus the platform-independent
  multiplayer server JAR, then attaches them to a GitHub Release.
- Rebranding: replace `packaging/icon.png` with a 1024×1024 master icon and
  rebuild — no packaging config edits needed.
- Player data (saves, edits, settings) is stored outside the app, so upgrades
  never delete it.

### How to cut a new release

```bash
git commit -am "Release 1.4.8"

mvn -Drevision=1.4.8 clean verify

git tag v1.4.8

git push origin HEAD
git push origin v1.4.8
```

Pushing the tag triggers GitHub Actions, which builds macOS (arm64 + x64) and
Windows (x64) installers and attaches them to a new GitHub Release. Players
download them from the repo's **Releases** page; their saved data from earlier
versions carries over automatically.

Key files: `release.sh`, `packaging/package.sh`, `packaging/make-icons.py`,
`.github/workflows/release.yml`.

---

## Core Design Principles

These were established deliberately and should be maintained throughout development.

### 1. Loose coupling — changes touch the minimum number of files

The key test: if you add a new `DefenseType` or `StatusEffectType`,
how many files need to change?

The answer should be: **the enum itself + the one class that owns that behaviour**.

Current mechanisms that enforce this:
- `Move.applyBlockTo(int damage)` — block reduction logic lives on `Move`, not scattered across `DamageCalculator`, `Timeline`, and `CombatResolver`.
- `Move.blockActivationMessage()`, `Move.blockDisplayInfo()` — display strings live on `Move`.
- `MoveData.isPercentageBlock()`, `isFlatBlock()`, `isAnyBlock()` — string comparisons in the editor are compile-safe helper methods, not raw `"PERCENTAGE_BLOCK".equals(...)` literals.

### 2. Encapsulation — internals stay internal

- `BattleView` interface: the graphics layer implements this interface. The `core` module never knows which renderer is active.
- `AIStrategy` interface: AI logic is pluggable. `GreedyAIStrategy` is the default.
- `SlotBudgetEnforcer`: slot budget logic lives in one place in `core`. Both the domain (`Character.validateAndBuildMoveList`) and the graphical Character editor (`AssignmentPanel`) delegate to it.
- `StatKey` enum: stat name mapping (string aliases, display labels, read/write on `CharacterData`) lives in one place. `CharacterStats.getByName()` and `AbilityApplicator` both delegate to it. Adding a new stat means touching one enum.
- `BaseRepository<D>`: the three repositories (`MoveRepository`, `CharacterRepository`, `AbilityRepository`) share their load/save/CRUD/ID-resequence behaviour via this abstract base. Each subclass supplies only id accessors, a Jackson `TypeReference`, a seed hook, and an entity name.

### 3. Abstraction — callers don't need to know implementation details

Good example: `Timeline.hasActiveBlockAt(tick)` calls `move.isActiveBlock()` — it does not compare `DefenseType` enum values directly. If a third block type is added, only `Move` changes.

Bad pattern to avoid: raw string comparisons like `"PERCENTAGE_BLOCK".equals(md.defenseType)`. These are invisible to the compiler and silently break on rename. Always use the helper methods on `MoveData` instead.

### 4. The BattleView contract

`BattleController` only ever calls methods on `BattleView`. It never calls `System.out`, never references `BattleScreen`. This is what makes swapping renderers zero-cost to core.

When the graphics `BattleScreen` implements `BattleView`, it runs the controller on a background thread and uses `Gdx.app.postRunnable()` to push state updates back to the LibGDX render thread. `promptMoveSelection()` blocks the controller thread via a `volatile boolean` flag until the player confirms their queue.

---

## Glossary

**`GLOSSARY.txt` in the project root is the canonical reference for all game terms.**

Before writing any display text, editor labels, or code comments, check there first.
Key clarifications:

| Term | Meaning |
|---|---|
| **Defense** | A *computed combat stat* derived from Durability + CE Reserves. Applied *after* defensive moves. Not a raw base stat. |
| **DEFENSIVE** | A MoveTag applied to blocking moves (PERCENTAGE_BLOCK, FLAT_BLOCK). Applied *before* Defense. |
| **blockDamageReduction** | The percentage reduction property of a PERCENTAGE_BLOCK move. Not the same as Defense. |
| **PERCENTAGE_BLOCK** | DefenseType for percentage-based damage reduction (0–100%). |
| **FLAT_BLOCK** | DefenseType for flat damage subtraction. |
| **MOVE_BLOCK_REDUCED** | CombatEvent emitted when a block reduces but does not fully negate damage. |
| **isFreeMove** | A move that does not consume a move slot. Still respects prereqs and technique restrictions. |
| **requiredTechniqueId** | Stored as a plain string (e.g. "SHRINE") pending implementation of the Technique class. |
| **AP tick** | One unit on the AP timeline. "Game tick" and "AP tick" are both acceptable. |
| **Fire tick** | The tick at which a move's effect resolves. `fireTick = startTick + unleashPoint - 1`. |
| **Shikigami** | A summoned combatant (e.g. Divine Dogs, Mahoraga) — the JJK term used throughout the code. `CharacterType.SHIKIGAMI`; only shikigami definitions may be summoned by a move/ability summon effect. (Not "shinigami".) |
| **MoveType** | Authored move class used for learning eligibility. Cursed spirits learn only `CURSED_SPIRIT` moves; cursed corpses learn `SORCERER` and `SHIKIGAMI` moves. |
| **AoeType** | Authoritative AOE targeting shape for an AOE-tagged move: `MULTIPLE` (N targets), `ALL_ENEMIES`, or `ALL_OTHERS` (allies included). |
| **BattleFormat** | Roster-size format for a battle: `ONE_V_ONE` or `TWO_V_TWO`. The engine supports any team size; this captures the configured format. |

### Cursed spirits

`CharacterType.CURSED_SPIRIT` is a reusable character class rather than one
hardcoded fighter. Every cursed spirit automatically receives the canonical
physiology and exorcism abilities from `data/abilities/all_abilities.json`:

- Max CE is multiplied by 5 and CE regeneration becomes 1 CE per resolution tick.
- A cursed spirit waives a move's final CE cost when its authored base cost is at
  most `max(1, floor(raw ten-stat BST / 100))`.
- Fatal uncursed physical hits leave it at 1 HP. Cursed Energy, technique,
  cursed-tool, cursed-being, and non-hit damage can exorcise it normally.
- `Reconstitute` is granted automatically; the other cursed-spirit abilities and
  moves remain optional data-driven content.

The non-innate cursed-spirit move roster is stored at IDs `000110`–`000139`.
Every `CURSED_SPIRIT` move must explicitly carry the `CURSED_ENERGY` tag and a
real CE cost, including slot-free baseline moves. No starter cursed-spirit
character is shipped; the Character Editor can compose one from this package.

---

## Architecture: Data Flow

```
JSON file
    ↓ Repository.load()
DTO (MoveData / CharacterData / AbilityData)
    ↓ .toMove() / .toCharacter(moveRepo) / AbilityRepository
Domain object (Move / Character / Ability)   ← immutable
    ↓ BattleCombatant constructor
AbilityApplicator.apply()
    → effectiveStats (CharacterStats, ability-modified)
    → AbilityFlags (non-stat runtime effects)
    ↓ BattleController
BattleState + Timeline
    ↓ CombatResolver.resolveRound()
List<CombatEvent>
    ↓ BattleView.displayCombatEvents()
BattleScreen (render)
```

---


## Graphics Module Notes

- **Library**: LibGDX 1.13.5, LWJGL3 backend
- **Font**: Atlantis International (FreeType, generated at runtime from `assets/fonts/AtlantisInternational-jen0.ttf`)
- **Battle planner**: `BattleScreen` implements `BattleView`; the 150-dot planning UI uses runtime-generated nearest-neighbour pixel textures from `ui/battle/BattleUiAssets`.
- **Thread model**: `BattleController.runBattle()` runs on a daemon background thread (`battle-thread`). All LibGDX render-thread mutations go through `Gdx.app.postRunnable()`.
- **Editors (Scene2D)**: The move, character, ability, and technique editors are Scene2D Stage-based tools. UI textures are generated in code by `PixelSkin` using the battle planner's framed navy, parchment-card, and green-action visual language. CRUD editor chrome lives in `EditorScreenBase<D>`.
- **Screen background**: All screens clear to `#CDDCFA` (light blue). Button text turns bright yellow (`#FFE32E`) on hover to signal clickability.

---

## Java / Maven Notes

- Java 17 (source + target in both `<properties>` and `maven-compiler-plugin`)
- Jackson 2.15.2 (JSON serialization for all data DTOs)
- JUnit Jupiter 5.10.0 (test scope in all three modules)
- LibGDX 1.13.5 (graphics module only - `gdx`, `gdx-backend-lwjgl3`, `gdx-platform natives-desktop`, `gdx-freetype`, `gdx-freetype-platform natives-desktop`)
- The graphics module uses `maven-shade-plugin` to produce a fat JAR with all dependencies bundled.
