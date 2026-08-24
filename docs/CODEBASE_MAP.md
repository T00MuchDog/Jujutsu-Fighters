# JJK TBF Codebase Map

> Fast routing guide for coding agents. Use this to find the **smallest relevant
> area** before exploring source. It is intentionally not a complete class list.
>
> Verified against `main` on 2026-08-25. Live source wins if this becomes stale.

## 1. Architecture at a Glance

```text
project-root/
├── core/       Game/domain rules, battle engine, AI, repositories,
│               shared multiplayer engine + protocol DTOs
├── graphics/   LibGDX desktop client, screens, battle UI, editors,
│               audio, client HTTP/WebSocket networking
├── server/     Javalin server, auth, challenges, active matches,
│               canonical content, DB/persistence, WebSocket transport
├── data/       Canonical authored JSON content (not a Maven module)
├── pom.xml     Maven reactor + authoritative application revision
├── GLOSSARY.txt
├── MULTIPLAYER_ARCHITECTURE.md
├── MULTIPLAYER.md
├── AUDIO.md
└── RELEASE.md
```

Dependency direction:

```text
graphics ──► core ◄── server
```

**Core owns game rules. Graphics owns presentation. Server owns multiplayer
authority. Do not create LibGDX dependencies in core.**

---

## 2. Fast Task Router

| Task | Start here |
|---|---|
| Existing move damage/cost/tags/timing | `data/moves/all_moves.json` |
| New move field/schema | `model/move/MoveData.java` → `Move.java` |
| Damage formula | `model/combat/DamageCalculator.java` |
| Move power formula | `model/combat/PowerCalculator.java` |
| CE efficiency/output/cost scaling | `model/combat/CeEfficiencyCalculator.java` |
| Resolution ordering, hits, blocks, statuses, Black Flash, victory | `model/combat/CombatResolver.java` |
| Runtime HP/CE/status/combatant state | `BattleCombatant.java`, `BattleState.java` |
| AP/timeline/plan mechanics | `BattlePlan`, `TeamBattlePlan`, `Timeline`, `ActionSegment` |
| Move availability/locks | `MoveAvailability.java` |
| Targeting | `MoveTargeting.java`, `MoveTargetSelection.java`, move targeting enums |
| Generic ability behavior | `AbilityActivationEngine` + character ability classes |
| Cursed Speech / Miracles / Ratio / Ten Shadows / New Shadow Style | `model/character/coded/` |
| Existing character stats/content | `data/characters/all_characters.json` |
| Stat system/scaling | `CharacterData`, `CharacterStats`, `CombatStats`, `StatKey`, `StatScale` |
| Move-slot budgets | `SlotBudgetEnforcer.java` |
| Technique/skill tree | `model/technique/` + `data/techniques/all_techniques.json` |
| Technique mastery | `model/progression/` |
| Cursed tools | `model/weapon/` + `data/tools/` |
| AI behavior | `controller/*AIStrategy.java` |
| Local battle loop | `controller/BattleController.java` |
| Battle planning UI | `graphics/.../ui/battle/PlanningPanel.java` |
| Move cards | `graphics/.../ui/battle/MoveCardView.java` |
| HUD/status bars/meters | `graphics/.../ui/CombatantPanel.java`, `StatusBar.java`, meter class |
| Mac/Windows battle geometry | `graphics/.../ui/profile/` + battle-layout JSON |
| Battle integration/render lifecycle | relevant methods in `BattleScreen.java` |
| Main navigation/screens | `JJKGame.java` + relevant `screens/*Screen.java` |
| Move editor | `MoveEditorScreen.java` (+ `MoveData` if schema changes) |
| Character editor | `CharacterEditorScreen.java` (+ `CharacterData` if schema changes) |
| Ability editor | `AbilityEditorScreen.java` (+ ability DTOs if schema changes) |
| Technique editor | `TechniqueEditorScreen.java`, `TechniqueTreeRepositorySync.java` |
| Client HTTP/challenges | `graphics/.../multiplayer/ChallengeService`, `HttpApiClient` |
| Client socket/reconnect | `MatchWebSocketClient`, `MultiplayerMatchService` |
| Shared network JSON/wire shape | `core/.../multiplayer/protocol/` |
| Authoritative multiplayer battle | `HeadlessBattleSession.java` |
| Active server match lifecycle | `server/.../match/MatchManager.java` |
| Challenge host/join/accept/reject | `server/.../challenge/ChallengeService.java` |
| Guest auth | `server/.../auth/` |
| Canonical online content | `server/.../content/ContentCatalog.java` |
| DB/schema/persistence | `server/.../db/`, feature repository, Flyway migrations |
| Audio | `graphics/.../audio/` + `AUDIO.md` |
| Desktop launch/platform | `GraphicsMain.java`, `graphics/.../launch/` |
| Packaging/release | root `pom.xml`, `RELEASE.md`, `release.sh`, `packaging/` |

### Investigation rule

```text
exact task
  ↓
primary owner from table
  ↓
exact symbol/method/data entry
  ↓
direct callers/callees/helpers/tests only
  ↓
implement
```

Do **not** do repository-wide exploration unless the ownership genuinely cannot
be determined. Cross modules only when a direct dependency proves they matter.

---

# CORE

Root:

```text
core/src/main/java/com/jjktbf/
├── AppPaths.java
├── controller/
├── model/
├── multiplayer/
└── view/
```

## 3. Local Battle Orchestration

Primary owner:

```text
core/.../controller/BattleController.java
```

`BattleController`:
- drives `PLANNING → RESOLUTION → ROUND_END → ...`,
- uses `BattleView` for player interaction/presentation,
- uses `AIStrategy` for AI plans,
- uses `CombatResolver` for battle resolution,
- does not own rendering or damage math.

Current local loop:

```text
PLANNING
  CombatResolver.processRoundStart
  BattleView.promptTeamBattlePlan      [human]
  AIStrategy.selectTeamPlan            [AI]
  BattlePlan → Timeline
        ↓
RESOLUTION
  CombatResolver.beginResolution
  CombatResolver.resolveTick loop
        ↓
ROUND_END
  CombatResolver.processRoundEnd
        ↓
next round / battle over
```

AI lives in:

```text
core/.../controller/
```

Key entry points:
`AIStrategy`, `ArchetypeAIStrategy` (default dispatcher),
`GreedyAIStrategy`, `AggressiveSorcererAIStrategy`,
`PassiveSorcererAIStrategy`, `ShikigamiAIStrategy`,
`TenShadowsAIStrategy`, `CursedSpeechAIStrategy`, `SmartAIScoring`.

---

## 4. Combat Engine

Package:

```text
core/.../model/combat/
```

### Central owners

- `CombatResolver` — tick/round resolution and mechanic interactions.
- `BattleState` — whole-battle mutable state, teams, phase, winner.
- `BattleCombatant` — per-combatant runtime state/resources/statuses.
- `BattleTeam` / `BattleTeamId` — team structure.
- `BattleFormat` — configured roster format.
- `CombatEvent` — core/local battle events.

### Planning/timeline

- `BattlePlan` — one combatant's plan.
- `TeamBattlePlan` — atomic team plan.
- `ActionSegment` — scheduled action.
- `Timeline` — resolution timeline representation.

### Narrow rule owners

- `DamageCalculator` — damage math.
- `PowerCalculator` — move power math.
- `CeEfficiencyCalculator` — CE efficiency/output/cost math.
- `MoveAvailability` — usability/lock checks.
- `MoveTargeting` / `MoveTargetSelection` — target rules/selection.
- `AbilityActivationEngine` — generic runtime ability activation.
- `SummonStatScaler` — summoned stat scaling.
- `SummonUpkeepScaler` — summon upkeep scaling.
- `TargetExchangeRegistry` — target redirection/exchange.
- `RandomSource` / `SeededRandomSource` — deterministic injected randomness.

**Do not open `CombatResolver` first for a narrow calculator task.**

---

## 5. Move Model

Package:

```text
core/.../model/move/
```

Key ownership:

- `MoveData` — authored/serialized move schema.
- `Move` — runtime/domain move + move-owned helpers.
- `MoveRepository` — move persistence/load/save.
- `HitComponent` — hit components.
- `MoveEffectData` / `MoveEffectTrigger` — authored effects.
- `MoveTag`, `MoveCategory`, `MoveType` — classification.
- `AoeType`, `AttackLaunchMode` — attack behavior metadata.
- `DefenseType`, `DefenseTiming`, `DefenseTargeting`, `BlockStyle`, `DodgeScope` — defense metadata.
- `StatusEffect`, `StatusEffectType`, `StatusEffectMessages` — statuses.

Rule:

```text
existing value change → JSON first
new expressible field → MoveData → Move → runtime consumer → editor
```

---

## 6. Character, Stats, Abilities

Package:

```text
core/.../model/character/
```

Character/content:
`Character`, `CharacterData`, `CharacterRepository`, `CharacterType`,
character subclasses, `Equipment`.

Stats:
`CharacterStats`, `CombatStats`, `StatKey`, `BattleStatKey`,
`StatScale`, `StatTier`.

Other important owners:
- `SlotBudgetEnforcer` — move-slot budget rules.
- `Ability`, `AbilityData`, `AbilityRepository`
- `AbilityApplicator`, `AbilityResolver`
- `AbilityCondition*`, `AbilityEffect*`

Use `StatKey` for stat aliases/name mapping; do not duplicate name-string logic.

### Bespoke abilities

```text
core/.../model/character/coded/
```

Contains dedicated logic for:
- Cursed Speech
- Miracles
- New Shadow Style
- Ratio
- Ten Shadows
- shikigami move runtime

Plus `CodedAbilityRegistry`, runtime/state/binding helpers.

**If a bug names one of these abilities, start with its named coded class.**

---

## 7. Techniques, Progression, Tools

Technique definitions:

```text
core/.../model/technique/
```

Key:
`InnateTechnique`, `InnateTechniqueData`, `TechniqueRepository`,
`TechniqueSkillTree`, skill-tree node/prerequisite DTOs.

Mastery:

```text
core/.../model/progression/
```

Key:
`TechniqueMasteryFormula`, `TechniqueMasteryResolver`,
progression data/registry classes.

Cursed tools:

```text
core/.../model/weapon/
```

Key:
`CursedToolData`, `CursedToolRepository`, `WeaponType`.

---

## 8. Persistence and View Boundary

Shared repository behavior:

```text
core/.../model/repo/BaseRepository.java
```

If all entity repositories share a persistence bug, start here.

Application/user-data paths:

```text
core/.../AppPaths.java
```

Use only for filesystem/data-root/per-user persistence concerns.

Presentation contract:

```text
core/.../view/BattleView.java
```

`BattleView` is the core-to-local-presentation boundary. Core must not know
about `BattleScreen` or LibGDX.

---

# DATA

## 9. Canonical Authored Content

```text
data/
├── moves/all_moves.json
├── characters/all_characters.json
├── abilities/all_abilities.json
├── techniques/all_techniques.json
├── tools/
└── keyword_descriptions.json
```

**Data-first rule:** if an existing entity can already express the requested
change, edit/search the JSON before Java.

Java is needed when:
- schema cannot express it,
- parsing/conversion is wrong,
- runtime behavior/validation changes,
- an editor needs a new field.

---

# GRAPHICS

Root:

```text
graphics/src/main/java/com/jjktbf/graphics/
├── GraphicsMain.java
├── JJKGame.java
├── AssetLoader.java
├── BattleSpriteScaleConfig.java
├── audio/
├── launch/
├── multiplayer/
├── screens/
└── ui/
```

## 10. Entry, Navigation, Screens

- `GraphicsMain` — desktop/LWJGL launcher.
- `JJKGame` — application owner, screen navigation/lifecycle.
- `launch/DesktopLaunchOptions` — desktop launch options.
- `launch/DesktopPlatform` — platform detection.

Main screens include:
`MainMenuScreen`, `BattleFormatScreen`, `CharacterSelectScreen`,
`BattleScreen`, and multiplayer screens.

Typical local flow:

```text
GraphicsMain → JJKGame → menu/format/character selection
             → BattleScreen ↔ BattleController
```

### `BattleScreen` rule

`BattleScreen.java` is very large (~254 KB).

For a UI change:
1. inspect the smaller `ui/battle` or `ui/profile` owner,
2. search `BattleScreen` for the exact component/method,
3. read only relevant surrounding methods.

Read broad `BattleScreen` context only for BattleView integration,
animation/event playback, or screen-level lifecycle.

---

## 11. Battle UI

Package:

```text
graphics/.../ui/battle/
```

- `PlanningPanel` — planning/queue/timeline interaction.
- `TeamPlanningPanel` — multi-combatant/team planning composition.
- `MoveCardView` — move cards/list presentation.
- `ActionSegmentView` — action segment view.
- `TimelineBar` — timeline bar.
- `BattleUiAssets` — battle-specific UI assets.
- `WindowsBattleCanvas` — Windows battle canvas concerns.

General HUD/UI:

```text
graphics/.../ui/
```

Useful owners:
`CombatantPanel`, `StatusBar`, `AbilityStateMeter`,
`MiraclesMeter`, `RatioMeter`.

---

## 12. Mac/Windows Battle Layout

Package:

```text
graphics/.../ui/profile/
```

- `BattleUiLayout`
- `BattleUiLayoutStore`
- `UiProfile`

Tracked layout files:

```text
graphics/src/main/resources/assets/ui/battle-layouts/mac.json
graphics/src/main/resources/assets/ui/battle-layouts/windows.json
```

For platform geometry, start with the relevant JSON + `BattleUiLayout`.
Do not change game rules.

---

## 13. Editors

```text
graphics/.../screens/editors/
```

- `MoveEditorScreen`
- `CharacterEditorScreen`
- `AbilityEditorScreen`
- `TechniqueEditorScreen`
- `CursedToolEditorScreen`
- `TechniqueTreeRepositorySync`

Pattern for a new persisted editor field:

```text
core *Data DTO
  → runtime/domain conversion if needed
  → relevant EditorScreen
  → data compatibility/default
  → tests
```

For an existing field's layout/control only, stay in the editor/UI layer.

---

## 14. Client Multiplayer

```text
graphics/.../multiplayer/
```

HTTP/account/challenges:
- `HttpApiClient`
- `MultiplayerApi`
- `ChallengeService`
- `GuestAccountService`
- `GuestCredentialsStore`
- `ClientNetworkConfig`

Match/socket:
- `MatchWebSocketClient`
- `MatchSocket`
- `MultiplayerMatchService`
- `MultiplayerSession`
- `MultiplayerPlanDraft`
- `TargetListSupport`

Routing:

```text
HTTP bug       → relevant service → HttpApiClient
socket/reconnect → MatchWebSocketClient
match state/commands → MultiplayerMatchService
client plan draft → MultiplayerPlanDraft
wire DTO mismatch → core/multiplayer/protocol
screen-only bug → relevant Multiplayer*Screen
```

---

## 15. Audio

```text
graphics/.../audio/
```

- `GameAudio`
- `BattleAudioRouter`
- `AudioSettings`
- `AudioChannel`
- `MusicTrack`
- `SoundCue`

Also consult `AUDIO.md`.

---

# SHARED MULTIPLAYER CORE

## 16. Headless Authoritative Engine

```text
core/.../multiplayer/engine/
├── HeadlessBattleSession.java
└── MatchParticipant.java
```

`HeadlessBattleSession` owns multiplayer battle-session logic:
- canonical plan validation,
- command/state-version handling within the session,
- conversion of stable intent to battle plans,
- authoritative core resolution,
- multiplayer snapshots/round progression.

It **reuses** the core combat engine; it is not a second rules engine.

Authority path:

```text
client ActionCommand
  → server MatchManager
  → HeadlessBattleSession
  → BattlePlan/BattleState/CombatResolver
  → authoritative protocol state/events
  → clients
```

If local and multiplayer share the same wrong numerical result, fix the narrow
core rule owner. If only multiplayer validation/phase/state is wrong, start in
`HeadlessBattleSession`.

---

## 17. Protocol DTOs

```text
core/.../multiplayer/protocol/
```

Important families:
- commands: `ActionCommand`, `CommandResult`, `CommandType`, `SubmitPlanPayload`
- plans: `PlanPlacement`, `PlanState`, `PlanBoard`
- match: `MatchState`, `MatchSetup`, `MatchStatus`, `BattlePhase`
- player/combatant: `PlayerState`, `CharacterState`, `MoveState`, `StatusEffectState`
- events: `BattleEventState`, `BattleEventType`, `ActionSegmentState`
- transport: `SocketMessage`, `MessageType`
- compatibility: `ProtocolVersion`
- challenge/guest/session DTOs

Wire-format change:

```text
protocol DTO
  → server producer/consumer
  → graphics producer/consumer
  → serialization/integration tests
```

Do not serialize mutable `BattleState`, `BattleCombatant`, `CombatEvent`, or
`Move` directly as network state.

---

# SERVER

Root:

```text
server/src/main/java/com/jjktbf/server/
├── ServerMain.java
├── MultiplayerServer.java
├── auth/
├── challenge/
├── config/
├── content/
├── db/
├── match/
├── service/
└── transport/
```

## 18. Entry/Wiring

- `ServerMain` — executable entry.
- `MultiplayerServer` — Javalin/routes/WebSocket/server wiring.

Use for endpoint wiring/startup, not combat math.

---

## 19. Authentication

```text
server/.../auth/
```

- `GuestAuthService`
- `GuestRepository`
- `GuestSessionRecord`

Start here for guest/token/session issues.

---

## 20. Challenges

```text
server/.../challenge/
```

Key:
- `ChallengeService`
- `ChallengeRepository`
- `ChallengeRecord`
- `MatchRepository`
- `AcceptedMatchSetup`
- `AcceptedMatchParticipant`
- `RosterCodec`

Use for host/list/join/accept/reject/withdraw/cancel and challenge→match setup.

Note there are **two** `ChallengeService` classes:
- graphics = client service
- server = authoritative business logic

---

## 21. Active Matches

```text
server/.../match/
```

- `MatchManager`
- `ActiveMatch`
- `MatchConnection`
- `MatchPersistenceRepository`
- `MatchResultType`

`MatchManager` owns active server match lifecycle:
connection/disconnection, per-match synchronization, command routing,
broadcast/state updates, reconnect/timeout/terminal integration.

Do not put numerical combat rules here.

---

## 22. Canonical Content, DB, Transport

Canonical online content:

```text
server/.../content/ContentCatalog.java
```

Use for server canonical character/content lookup and selectable roster validation.

Database:

```text
server/.../db/Database.java
```

Feature-specific persistence also lives beside its feature repository.
Inspect Flyway migrations when persisted schema changes.

Transport:

```text
server/.../transport/JavalinMatchConnection.java
```

Service errors:

```text
server/.../service/ServiceErrorCode.java
server/.../service/ServiceException.java
```

---

# IMPORTANT EXECUTION FLOWS

## 23. Content → Runtime

```text
JSON
  → Repository.load()
  → *Data DTO
  → domain object
  → BattleCombatant/runtime systems
```

Use this to decide whether a request is data-only, schema/conversion, or runtime.

## 24. Local Battle

```text
GraphicsMain
 → JJKGame
 → selection screens
 → BattleScreen / BattleController
 → TeamBattlePlan
 → BattlePlan / Timeline
 → CombatResolver
 → CombatEvent
 → BattleView
 → BattleScreen
```

Wrong result = inspect core.
Correct result but wrong display = inspect graphics.

## 25. Multiplayer Challenge

```text
Multiplayer*Screen
 → graphics ChallengeService
 → HttpApiClient
 → MultiplayerServer route
 → server ChallengeService
 → repositories / accepted match setup
```

Challenge-list/accept bugs normally do not require combat-engine exploration.

## 26. Multiplayer Battle

```text
BattleScreen / planning UI
 → MultiplayerPlanDraft
 → MultiplayerMatchService
 → MatchWebSocketClient
 → MultiplayerServer
 → MatchManager
 → HeadlessBattleSession
 → core combat engine
 → MatchState/BattleEventState
 → client playback
```

Client submits intent; server owns validation, resources, randomness and outcomes.

---

# LARGE-FILE WARNINGS

## 27. Search These Files; Do Not Automatically Read Them Whole

Approximate current sizes:

```text
BattleScreen.java                    ~254 KB
MoveEditorScreen.java                ~164 KB
CombatResolver.java                  ~119 KB
CharacterEditorScreen.java            ~92 KB
CharacterSelectScreen.java            ~84 KB
PlanningPanel.java                    ~81 KB
AbilityActivationEngine.java          ~77 KB
HeadlessBattleSession.java            ~74 KB
Move.java                             ~69 KB
BattleCombatant.java                  ~68 KB
MoveData.java                         ~60 KB
```

Preferred approach:

```text
exact symbol/string search
 → relevant method
 → local surrounding context
 → direct dependencies only
```

Not:

```text
read giant file
 → summarize entire subsystem
 → inspect every dependency
 → finally locate feature
```

---

# COMMON CHANGE RECIPES

## 28. Existing Move

```text
exact name/ID in all_moves.json
 → edit existing fields
 → targeted mechanic test
```

Only open Java if current schema cannot express the request.

## 29. New Move Property

```text
MoveData
 → Move
 → narrow runtime consumer
 → MoveEditorScreen if authorable
 → data/default compatibility
 → tests
```

## 30. Stat or Scaling Change

```text
CharacterData / StatKey / CharacterStats / CombatStats / StatScale
 → direct runtime consumer
 → CharacterEditorScreen if authorable
 → tests
```

## 31. Damage or CE Formula

```text
DamageCalculator
or
CeEfficiencyCalculator
 → direct callers only
 → tests
```

Widen into `CombatResolver` only when order/integration matters.

## 32. Bespoke Ability

```text
model/character/coded/<NamedAbility>
 → its direct registry/runtime hook
 → generic ability/combat engine only if required
 → tests
```

## 33. Platform UI Geometry

```text
mac.json OR windows.json
 → BattleUiLayout
 → specific small UI component
 → relevant BattleScreen method only if required
```

## 34. Protocol Change

```text
protocol DTO
 → ProtocolVersion if compatibility changes
 → server consumer/producer
 → graphics consumer/producer
 → integration tests
```

## 35. Multiplayer Combat Bug

Ask first:

```text
Does local battle have the same bug?
```

Yes:
```text
fix narrow core rule owner
 → verify HeadlessBattleSession integration
```

No:
```text
HeadlessBattleSession
 → MatchManager only if lifecycle/routing is implicated
```

---

# TESTS AND DOCS

## 36. Tests

Typical roots:

```text
core/src/test/java/
graphics/src/test/java/
server/src/test/java/
```

After locating the implementation, search tests by exact:
- class name,
- mechanic name,
- enum/event/error code.

Run narrow tests first; broaden when justified.

Full verification:

```bash
mvn clean verify
```

## 37. Targeted Documentation

- `GLOSSARY.txt` — canonical game terminology.
- `MULTIPLAYER_ARCHITECTURE.md` — protocol/authority/lifecycle architecture.
- `MULTIPLAYER.md` — multiplayer setup/operation.
- `AUDIO.md` — audio architecture/assets.
- `RELEASE.md` — packaging/release.
- `README.md` — high-level build/architecture.

Do not read all docs for every task.

---

# ARCHITECTURAL GUARDRAILS

## 38. Preserve These

1. `core` owns game rules; `graphics` owns presentation.
2. Dependency direction remains `graphics -> core <- server`.
3. `BattleController` orchestrates local battles; it does not render or own damage math.
4. `BattleView` is the local core/presentation contract.
5. Multiplayer uses `HeadlessBattleSession` + existing core combat rules, not copied rules.
6. Server is authoritative for multiplayer validation/resources/random outcomes.
7. Network state uses explicit DTOs in `core.multiplayer.protocol`.
8. Existing content remains data-driven when schema already supports it.
9. Shared repository behavior belongs in `BaseRepository`.
10. Slot-budget rules belong in `SlotBudgetEnforcer`.
11. Stat aliases/name mapping belong in `StatKey`.
12. Platform battle geometry belongs in layout/profile resources.
13. `GLOSSARY.txt` is canonical terminology.
14. Prefer one behavior owner over duplicated checks across modules.
15. Preserve injected/deterministic randomness in battle resolution.

---

# CODING-AGENT OPERATING RULE

## 39. Default Investigation Procedure

For each task:

```text
1. Classify the task with §2.
2. Open/search the primary owner only.
3. Trace the concrete execution path.
4. Inspect direct helper/DTO/test dependencies.
5. Identify the minimum file set.
6. Implement.
7. Run targeted tests.
8. Expand scope only when evidence requires it.
```

Do not attempt to understand the whole repository before a localized change.

Update this map only when subsystem ownership, major entry points, or execution
flows change—not for every new move, character, helper, or minor refactor.
