# JJK TBF UX Audit and Improvement Report

Date: 2026-08-31

## Purpose

This report audits JJK TBF against six practical UX goals:

- **Efficiency:** minimize clicks, repeated setup, waiting, and avoidable work.
- **Low cognitive load:** make choices, state, consequences, and next steps easy to understand.
- **Feedback and recovery:** explain failures, preserve context, and make actions reversible.
- **Accessibility:** support keyboard, controller, readable text, non-color cues, reduced motion, and audio-independent play.
- **Flexibility:** support different input, display, audio, setup, and play preferences.
- **Immersion:** keep the player focused on the fight instead of UI ambiguity or technical state.

## Scope and Method

The audit covered every player-facing screen in `graphics`, the local battle architecture plus a runtime pass through first-round resolution, multiplayer setup and recovery paths, settings, display/audio behavior, and all six in-game editors.

Runtime validation used the current `graphics-1.4.9.jar` with a clean temporary data root and the Mac UI profile at 1600x900. The exercised path was:

```text
Main menu
  -> battle format
  -> random player and CPU selection
  -> battle introduction
  -> timeline planning
  -> round resolution
  -> abort to menu
  -> character editor
  -> multiplayer menu
```

The remaining flows were traced through their screen code, direct services, focused tests, layout profiles, and resources. A two-client online match, Windows runtime pass, controller hardware pass, assistive-technology pass, and full battle-to-results runtime pass remain validation work. Findings that depend on those passes are explicitly phrased as validation recommendations rather than confirmed visual defects.

## Priority Scale

| Priority | Meaning |
|---|---|
| **P0** | Blocks or seriously impairs a core journey, accessibility, data safety, or recovery. |
| **P1** | Material recurring friction or confusion in a primary journey. |
| **P2** | Valuable quality, flexibility, or immersion improvement after core journeys are sound. |

## Executive Priorities

The highest-value sequence is:

1. Make setup and battle fully operable by keyboard and controller, with visible focus.
2. Add a first-run tutorial and contextual planner help inside the game.
3. Give every fighter a recommended move set and add a true quick-start path.
4. Explain disabled moves, show targets/statuses, and add undo to planning.
5. Replace destructive Back/Escape behavior with explicit labels, confirmations, and resumable context.
6. Add replay, rematch, change-team, and multiplayer-resume paths.
7. Add text/UI scale, high contrast, reduced motion, captions, remapping, and complete audio/display settings.
8. Protect editor data with warnings, undo, atomic multi-file changes, and draft recovery.

## Journey Map

```text
Launch
  -> Main menu
       -> Settings
       -> Single player
            -> Format and stat mode
            -> Character and move-set selection
            -> Battle introduction
            -> Planning
            -> Resolution
            -> Next round / battle result
       -> Multiplayer
            -> Guest identity
            -> Host or browse challenge
            -> Approval and roster setup
            -> Waiting
            -> Online battle
            -> Reconnect / terminal result
       -> Authoring tools
            -> Character / Move / Ability / Technique / Domain / Tool editor
```

## Global, Onboarding, Settings, and Accessibility

| ID | Priority | Improvement | Area and UX goals | How to accomplish it | Evidence |
|---|---|---|---|---|---|
| G01 | P0 | Create one semantic input system for the whole game. | Accessibility, flexibility, consistency | Replace direct `Input.Keys` checks with actions such as Navigate, Confirm, Back, Add, Remove, Lock, Playback, and Pause. Add keyboard and controller defaults, remapping, conflict detection, reset, and persisted bindings. Keep mouse and drag-and-drop as optional accelerators. | Hardcoded input is spread across `MainMenuScreen.java:199-215`, `BattleFormatScreen.java:136-153`, `CharacterSelectScreen.java:415-455`, `EditorScreenBase.java:418-481`, and `TeamPlanningPanel.java:647-672`. |
| G02 | P0 | Add a consistent focus model and visible focus treatment. | Accessibility, cognitive load | Define initial focus and deterministic Tab/Shift+Tab, arrow, and D-pad order for screens, lists, dialogs, sliders, cards, and buttons. Restore focus after dialogs and async updates. Use a high-contrast outline that is distinct from hover and selected state. | Several screens clear focus on entry; menu/format reuse hover styling for keyboard state (`MainMenuScreen.java:689-711`, `BattleFormatScreen.java:317-340`), while multiplayer has no general traversal (`MultiplayerScreenBase.java:57-81`). |
| G03 | P0 | Put the tutorial and glossary inside the game. | Learnability, cognitive load, accessibility | Add a short first-run interactive tutorial for AP, CE, Offense/Defense, unleash timing, placement, targeting, removing actions, and Lock In. Add replayable Help and Glossary entries from the main menu and planner. Make keyword definitions focusable/clickable rather than hover-only. | Startup goes directly to the menu (`JJKGame.java:261-266`); the main menu has no Help entry (`MainMenuScreen.java:160-174`); keyword help is mouse-hover driven (`KeywordLabel.java:29-45`). The current tutorial exists only in `README.md:56-100` and `GLOSSARY.txt`. |
| G04 | P0 | Expose Settings as a normal, labeled, keyboard-reachable action. | Accessibility, discoverability, efficiency | Add `SETTINGS` to the main navigation or label the gear and include it in focus order. Add a shortcut and controller action. Label the close control `CLOSE SETTINGS` or provide a tooltip. | Settings is intentionally pointer-only (`MainMenuScreen.java:143-150`), uses an image-only button (`MainMenuScreen.java:307-319`), and is absent from keyboard handling (`MainMenuScreen.java:199-215`). |
| G05 | P1 | Make the main menu play-first and move authoring tools under a Tools area. | Cognitive load, efficiency, immersion | Make Single Player the primary action, followed by Multiplayer, Continue/Resume, Settings, Help, and Quit. Put all editors under `TOOLS / AUTHORING`, and hide them in player builds if they are not intended for ordinary players. | Seven editor actions compete with play actions in `MainMenuScreen.java:160-174`. Runtime validation showed the editor list dominates the screen and pushes ordinary navigation into an oversized scrolling menu. |
| G06 | P1 | Fix the clipped main-menu title and validate every supported viewport. | Accessibility, polish, immersion | Reserve title/header height before sizing the scrollable command area, account for window chrome/safe insets, and add screenshot tests at minimum, reference, and high-DPI sizes for both profiles. | At the documented 1600x900 windowed launch, runtime validation showed `JJK TURN BASED FIGHTER` clipped by the top window edge. Responsive scaling is handled around `MainMenuScreen.java:536-553` but does not protect this header. |
| G07 | P0 | Add a persisted text and UI scale setting. | Accessibility, flexibility | Offer at least 100%, 125%, 150%, and 200% modes. Reflow layouts, enlarge hit targets and dialogs, preserve wrapping/scrolling, and establish minimum readable font sizes instead of shrinking content to fit. | UI scale is profile-based (`UiProfile.java:5-20`), fonts use fixed logical sizes (`AssetLoader.java:153-167`), and move-card text may shrink to 30% (`MoveCardView.java:426-462`). |
| G08 | P0 | Add high-contrast and color-vision-friendly presentation. | Accessibility, feedback | Audit palette tokens against contrast targets. Add a high-contrast theme. Pair red/yellow/green states with text, icons, patterns, and shape changes. Never make muted or disabled text unreadably faint. | HP and stat state use red/yellow/green thresholds (`StatusBar.java:164-173`, `CharacterSelectScreen.java:117-120`); source palette combinations in `PixelSkin.java:48-55,487-494` include likely low-contrast states. |
| G09 | P1 | Add reduced-motion, reduced-flashing, captions, and audio-independent cues. | Accessibility, flexibility | Add `Reduce motion and flashing`, disable damage flicker/entrance growth/silhouette fades, and offer instant transitions. Caption important battle audio and ensure every sound cue has a visible equivalent. | Motion is implemented in `CombatantPanel.java:167-176,215-240` and `StatusBar.java:68-89`; there is no reduced-motion or caption preference. The battle log is a useful existing visual fallback. |
| G10 | P1 | Expose the audio controls the backend already supports. | Flexibility, efficiency, accessibility | Add master volume, mute, music, UI SFX, and battle SFX controls, with numeric values, test sounds, and reset defaults. Keep the current simple mode available if desired. | `AudioSettings.java:6-11` and `GameAudio.java:126-136` support these channels, but Settings exposes only Music and combined Effects (`MainMenuScreen.java:373-390`). |
| G11 | P1 | Complete in-game display settings. | Flexibility, accessibility, recovery | Add windowed, borderless, and fullscreen modes; monitor and resolution selection; window size/position persistence; UI scale; and a timed safe rollback after mode changes. Treat Linux/Other as a tested profile rather than silently inheriting Windows assumptions. | Launch options own fullscreen/window mode (`DesktopLaunchOptions.java:15-18,41-60`); only a Windows resolution ID is persisted (`DisplaySettingsStore.java:16-18,46-70`); Other defaults to the Windows profile (`DesktopPlatform.java:20-21`). |
| G12 | P1 | Extract user-facing text for localization and long-string resilience. | Accessibility, flexibility | Move strings to resource bundles, add language selection and fallback, test pseudolocalization and non-Latin glyph coverage, and avoid fixed layouts that assume short English labels. | User-facing strings are hardcoded throughout `MainMenuScreen.java`, `BattleFormatScreen.java`, `CharacterSelectScreen.java`, multiplayer screens, and editors. Only one font family is loaded (`AssetLoader.java:153-167`). |
| G13 | P1 | Restore or intentionally replace menu music. | Immersion, polish | Add `assets/audio/music/menu.ogg`, point the catalog to an existing track, or explicitly design silence. Make missing required music fail packaging tests instead of silently disappearing. | `MusicTrack.java:6-10` registers `menu.ogg`, but the asset is absent; `GameAudio.java:178-199` silently skips missing music and `AudioCatalogTest.java:42-47` checks packaged effects rather than music files. |
| G14 | P2 | Carry the battle's visual identity into menus and setup. | Immersion, cognitive continuity | Reuse the strong pixel-art battle language through restrained backgrounds, fighter art, transitions, and consistent panels. Avoid decorative motion that competes with readability. Keep setup state visually tied to the fighters being configured. | Runtime validation showed a polished battle scene but largely flat light-blue menu/setup backgrounds. The shared screen color is documented in `README.md:395-403`. |
| G15 | P0 | Provide semantic UI output for screen readers and accessible narration. | Accessibility, flexibility | Build a semantic model for focused control role, label, value, disabled reason, and current game state rather than exposing only pixels. Connect it to platform accessibility APIs where feasible and provide an optional spoken-navigation/text-mode fallback for the custom battle canvas. Announce focus, resource changes, targets, statuses, timers, errors, and confirmations without requiring pointer hover. | Most gameplay presentation is custom LibGDX rendering and pointer hit-testing (`PlanningPanel.java:1535-1839`, `BattleScreen.java:736-805`), so keyboard focus alone does not expose meaningful roles or state to assistive technology. This requires a dedicated runtime validation pass. |

## Local Battle Setup

| ID | Priority | Improvement | Area and UX goals | How to accomplish it | Evidence |
|---|---|---|---|---|---|
| S02 | P1 | Add a true Quick Battle path and setup presets. | Efficiency, flexibility | From the main menu, offer Quick 1v1 using the last setup or recommended random fighters. Persist recent format, stat mode, teams, and per-match loadouts. Revalidate stale content before launch. | The normal first battle requires format, player fighter/loadout, and CPU fighter/loadout selection. `README.md:56-75` documents the multi-step path. |
| S03 | P0 | Make move-set customization keyboard/controller operable. | Accessibility, flexibility | Make learned moves and slots focusable. Add Confirm/A to add, Delete/X to remove, left/right or modifier+arrows to reorder, and visible `REMOVE`, `CLEAR`, `AUTO-FILL`, and `RESET` actions. | Character selection keys cover only Up, Down, Enter, Escape (`CharacterSelectScreen.java:432-455`); customization is pointer-based (`CharacterSelectScreen.java:2085-2168`), and removal is right-click-only (`CharacterSelectScreen.java:2101-2111`). |
| S04 | P0 | Make reorder feedback match the result. | Feedback, trust, error prevention | Use the calculated insertion index when dropping a move and test before/between/after insertion. If order is intentionally fixed, remove the insertion marker and do not imply drag reordering. | An insertion marker is drawn (`CharacterSelectScreen.java:1889-1896`), but drop calls append-only `addMoveToSet` (`CharacterSelectScreen.java:1800-1805,2159-2166`). |
| S05 | P1 | Show the selected/default battle format clearly. | Cognitive load, accessibility | Start with 1v1 visibly selected, initialize keyboard selection to it, and preserve a distinct selected state independent of hover/focus. A first right/down action should move from 1v1 to 2v2. | Internal defaults are not visually selected (`BattleFormatScreen.java:52-57,164-172`); the first positive keyboard movement can select 6v6 (`BattleFormatScreen.java:190-206`). Runtime validation confirmed no visible default. |
| S06 | P1 | Explain each format before selection. | Cognitive load, informed choice | Add concise descriptions: roster size, active/reserve count, expected setup effort, and team-order behavior. Explain Standard versus Equalized without requiring external docs. | The screen shows only `1V1`, `2V2`, `6V6` (`BattleFormatScreen.java:113-123`); active/reserve rules appear only later (`CharacterSelectScreen.java:801-806`). Runtime cards contain large unused space suited to these descriptions. |
| S07 | P1 | Separate Back, Undo Pick, and Change Format. | Recovery, consistency | Add visible `UNDO LAST PICK` and `BACK TO FORMAT`. Escape should follow the parent route; leaving configured picks should confirm and preserve a draft. | Escape undoes a pick until none remain, then exits (`CharacterSelectScreen.java:450-454,661-680`), while local exit routes to the main menu rather than format (`CharacterSelectScreen.java:292-306`). |
| S08 | P1 | Add a final battle review and explicit Start action. | Error prevention, confidence | Show format, stat mode, both teams, active/reserve order, and move-set summaries. Provide `START BATTLE`, `BACK TO EDIT`, and `RANDOMIZE`. Keep direct launch only as an optional quick-start preference. | The final CPU selection immediately launches (`CharacterSelectScreen.java:624-637`); runtime validation confirmed that selecting Random for the CPU transitions straight to the battle. |
| S09 | P1 | Explain rejected or unavailable selections in context. | Feedback, recovery, accessibility | Disable impossible rows and show inline messages such as `Already selected`, `No unused fighters`, or `Move set required`. Keep denied audio only as supplemental feedback. | Duplicate and invalid random picks can produce only a denied sound (`CharacterSelectScreen.java:578-595`); existing picks are mainly dimmed (`CharacterSelectScreen.java:877-883`). |
| S10 | P1 | Separate per-battle loadouts from saved character data. | User control, recovery | Label persisted edits explicitly. Offer `USE FOR THIS BATTLE` and `SAVE AS CHARACTER DEFAULT`, show dirty state, and provide reset/undo. Avoid silently authoring game data from ordinary setup. | Setup mutates and saves `character.moveSetIds` immediately (`CharacterSelectScreen.java:1771-1777`) under the less explicit label `LEARNED MOVES: CUSTOMIZE`. |
| S11 | P1 | Show battle construction and load failures. | Feedback, recovery | Display `LOADING BATTLE`, disable input until ready, and surface a recoverable dialog with `RETURN TO REVIEW` and expandable diagnostics if loading fails. | The battle screen is shown before background repository/controller construction completes (`JJKGame.java:764-805,885-939`). |
| S12 | P2 | Improve roster navigation for growing content. | Efficiency, flexibility | Add search, filter by character type/technique, favorites, and recently used fighters. Preserve scroll and selection when returning from review. Keep Random pinned at the top. | Runtime validation showed a single long vertical list; editor catalog search already demonstrates a reusable search pattern in `EditorScreenBase.java:625-634`. |

## Battle Planning, Playback, and Results

Completed: **B06** now displays compact, duration-labeled statuses inside every
execution HUD and in the macOS planning workspace, with hover inspection for
effect, remaining duration, stacks, and restrictions. Local battles also show
the source, removal chance, and upkeep when the combat model provides them.

| ID | Priority | Improvement | Area and UX goals | How to accomplish it | Evidence |
|---|---|---|---|---|---|
| B01 | P0 | Make every battle action keyboard/controller operable. | Accessibility, flexibility | Add focus regions for cards, offense timeline, defense timeline, target menu, team tabs, Lock In, playback, Next Round, and result actions. Support arrows/D-pad, Confirm/A, Back/B, Remove/Delete/X, shoulder-page switching, and visible control hints. | `PlanningInputProcessor` is mouse/touch/scroll-only (`PlanningPanel.java:1535-1839`); team keys are limited (`TeamPlanningPanel.java:647-672`); action buttons are pointer-polled (`BattleScreen.java:736-805`). |
| B02 | P0 | Explain why a move or placement is unavailable. | Feedback, cognitive load, recovery | Replace boolean validation with a reason-bearing result. Show required versus remaining AP/CE, timeline conflicts, move caps, status restrictions, and target requirements on hover, focus, and attempted use. | Cards use `plan.canPlace(...)` and generic disabled styling (`PlanningPanel.java:967-979`, `MoveCardView.java:286-318`); failed placement often gives only a denied sound (`PlanningPanel.java:1689-1703`). |
| B03 | P0 | Add undo/redo and safe removal. | Recovery, error prevention | Snapshot place, remove, relocate, target, and switch edits. Add visible Undo/Redo, explicit segment Remove, and AP/CE refund feedback. Dragging outside a board should cancel by default rather than delete. | Right-click and drag-out remove actions (`PlanningPanel.java:1554-1563,1697-1703`) and there is no undo state. |
| B04 | P0 | Add explicit target completion and keep invalid target menus open. | Error prevention, feedback | Show required count, selected chips, ordered roles, and a visible `CONFIRM TARGETS`. Prevent closing on incomplete selection and focus the next required target. | Multi-target menus have no Done action (`PlanningPanel.java:1241-1271`); outside click closes them (`PlanningPanel.java:1339-1357`), with invalidity potentially deferred to Lock In (`PlanningPanel.java:1359-1377`). |
| B06 | P0 | Add inspectable combat statuses and durations. | Decision support, cognitive load | Render compact status icons by each HUD. On focus/hover/click, show name, effect, source, duration, stacks, and restrictions using text and shape as well as color. Keep inspection available during planning and playback. | Completed with shared status badges and inspection in `StatusEffectStrip`, execution-HUD integration in `CombatantPanel`/`BattleScreen`, and macOS planner integration in `PlanningPanel`. |
| B07 | P1 | Teach timeline and unleash semantics in the planner. | Learnability, cognitive load | Add first-use coach marks, a persistent legend, useful tick numbers, `Starts / Fires / Ends` preview, and a ghost segment before placement. Explain click-to-first-fit versus drag-to-time. | Timelines show labels and dots but no tick numbers (`PlanningPanel.java:1014-1015`, `TimelineBar.java:82-96`); unleash uses an unexplained dark card dot (`MoveCardView.java:332-344,367-416`). Runtime validation confirmed no on-screen planning instructions. |
| B08 | P1 | Keep short timeline actions identifiable and easy to select. | Accessibility, plan review | Render an icon/number badge for narrow segments, show the full name on focus, enforce a minimum hit area independent of visual width, and offer a parallel textual queue list. | Labels disappear below 18 pixels (`ActionSegmentView.java:62-66`), while segment width is proportional to AP (`TimelineBar.java:64-70`). |
| B09 | P1 | Add a persistent plan summary and efficient repeat actions. | Efficiency, plan review | Add `CLEAR PLAN`, `REPEAT LAST PLAN`, and a compact ordered action list. Revalidate copied plans against current AP, CE, statuses, targets, and timeline rules before applying them. | Current planning optimizes first placement by card click (`PlanningPanel.java:1676-1688`) but provides no clear-all, copy-previous, or summary workflow. |
| B10 | P0 | Show team-wide planning progress at all times. | Cognitive load, feedback, efficiency | Add one tab/card per controlled fighter with `UNPLANNED`, `DRAFT`, `READY`, and `SWITCHING`, AP/CE remaining, target warnings, and `NEXT INCOMPLETE`. Keep names visible on every platform. | Team planning is paged (`TeamPlanningPanel.java:153-165,196-218`); confirmation silently waits for every page (`TeamPlanningPanel.java:256-261`), and Windows may show only page count (`TeamPlanningPanel.java:422-451`). |
| B11 | P1 | Make reserve switching an explicit, accessible team action. | Discoverability, accessibility, error prevention | Add a labeled reserve/team button, keyboard/controller navigation for reserve cards, a current-to-reserve replacement summary, and an explicit confirmation of the round cost. | Switching is opened by a button or `S` (`TeamPlanningPanel.java:657-661,703-708`); the modal consumes keys but reserve cards are not keyboard navigable (`TeamPlanningPanel.java:649-656`). |
| B12 | P0 | Make multiplayer time pressure prominent and predictable. | Feedback, accessibility | Place countdown by Lock In, use normal/warning/critical states with non-color cues, announce 10/5/1 seconds visually and optionally audibly, and preserve the submitted draft after timeout. | Multiplayer auto-lock starts near the deadline (`BattleScreen.java:3427-3454`); countdown is a single yellow text treatment (`BattleScreen.java:1289-1311`). |
| B13 | P1 | Complete playback controls with labels, pause, step, and input parity. | Flexibility, comprehension, accessibility | Label controls `2x SPEED`, `SKIP ROUND`, `PAUSE`, and `STEP`. Add tooltips and keyboard/controller bindings. Preserve an event summary after skip. Add optional auto-advance after a configurable review delay. | Current controls are icon-only and pointer-driven (`BattleUiAssets.java:73-76,125-130`, `BattleScreen.java:747-760`); fast-forward and skip exist (`BattleScreen.java:1561-1596`), but pause is empty (`BattleScreen.java:714-715`). |
| B14 | P1 | Put battle/network errors in the active workflow. | Feedback, recovery | Add a persistent banner and planner-level error region with next action: reconnect, retry, edit plan, or return safely. Focus the affected action/page. Keep technical detail expandable. | Connection, incomplete-state, and submission errors are appended to the battle log (`BattleScreen.java:2944-2963,3357-3370`) rather than placed beside the failed control. |
| B15 | P1 | Add a complete result screen. | Efficiency, recovery, immersion | Provide `REMATCH`, `CHANGE TEAMS`, `RETURN TO MULTIPLAYER`, and `MAIN MENU`, with mouse/keyboard/controller support. Preserve the last setup and show a concise result summary. | Battle over currently shows only text and `ESC: MAIN MENU` (`BattleScreen.java:2013-2044`). |
| B16 | P0 | Confirm active-battle abort and state its consequence. | Error prevention, recovery | Replace raw Escape with a pause/leave overlay. Use `RESUME`, `SETTINGS`, and `ABANDON BATTLE`; for online matches explain possible forfeit/timeout consequences. | Escape immediately aborts a live battle and routes away (`BattleScreen.java:661-703`). Runtime validation confirmed no confirmation. |
| B17 | P1 | Make critical resources readable without color and allow numeric opponent values. | Accessibility, decision support | Pair HP/CE with text/icons/patterns, add an optional exact-values setting for opponents where design rules allow it, and set a physical minimum font size for compact HUDs. | Status bars rely on color transitions (`StatusBar.java:164-173`); enemy numeric values are hidden (`BattleScreen.java:5469-5475`). |
| B18 | P1 | Add a stable move-details panel instead of over-shrinking cards. | Accessibility, cognitive load | Keep cards scannable with stable AP, CE, role, timing, power, and accuracy badges. Show full description, keywords, restrictions, and availability reasons in a focusable details pane. | Card text can shrink to 30% and descriptions ellipsize (`MoveCardView.java:426-462`); keyword help is hover-only (`PlanningPanel.java:1146-1185`). Runtime validation showed extremely small description text on dense cards such as Cover Ears. |
| B19 | P2 | Disambiguate mirror matches in HUDs and logs. | Cognitive load, feedback, immersion | Prefix or badge combatants as Player/CPU or team/slot, use stable team colors plus icons, and keep those identifiers in logs and target menus. | Runtime validation produced Panda versus Panda; log lines used the same full name for both actors, making attack/block attribution unnecessarily difficult. |
| B20 | P2 | Expose hidden roster overflow. | Feedback, flexibility | Show `4 of 6 visible`, add an inspectable roster tray, and keep active/reserve/field order consistent across HUD, planner, and battlefield. | Local and online visual rosters are capped at four (`BattleScreen.java:2241-2245,5526-5529,6046-6051`). |
| B21 | P2 | Add a replayable round history. | Comprehension, flexibility, immersion | Preserve each resolved round's event list and presentation inputs, then allow replay at 1x/2x, pause, step, and log-event navigation without mutating authoritative battle state. Keep a textual round history available even when animation replay is not possible. | Current playback can fast-forward or skip (`BattleScreen.java:1561-1596`), but there is no way to revisit a completed sequence after advancing. |
| B22 | P1 | Add safe local battle suspend and crash recovery. | Recovery, flexibility, efficiency | Autosave an explicit versioned local-battle snapshot only at stable phase boundaries such as planning start and round end. Add `CONTINUE BATTLE` on the main menu, validate content/version compatibility, and offer a clear discard path. Do not serialize mutable runtime objects directly; define a dedicated save DTO and reconstruction path. | Active local battle state is lost when Escape returns to the menu (`BattleScreen.java:661-703`), and the main menu has no Continue action (`MainMenuScreen.java:160-174`). |

## Multiplayer

| ID | Priority | Improvement | Area and UX goals | How to accomplish it | Evidence |
|---|---|---|---|---|---|
| M01 | P0 | Add keyboard/controller navigation to every multiplayer screen. | Accessibility, flexibility | Centralize focus order, arrows, Tab, Confirm, and Back in `MultiplayerScreenBase`. Restore focus after async refresh and skip disabled controls. Use a visible focus ring. | The base handles only Escape/Back (`MultiplayerScreenBase.java:57-67`); buttons are wired through mouse clicks (`MultiplayerScreenBase.java:201-221`). |
| M02 | P0 | Replace ambiguous Back actions with explicit, confirmed consequences. | Error prevention, recovery | Use `CANCEL CHALLENGE`, `WITHDRAW REQUEST`, `LEAVE MATCH`, and `STOP RECOVERY`. Confirm destructive actions and preserve a non-destructive route where possible. | Back can cancel a challenge (`HostChallengeScreen.java:442-452`), withdraw a request (`ChallengeBrowserScreen.java:522-580`), or clear match recovery (`MultiplayerDisconnectedScreen.java:236-244`). |
| M03 | P0 | Add a persistent Resume Match / Continue Setup action. | Recovery, efficiency | If `MultiplayerSession` has an accepted or active match, show opponent, format, current stage, and one resume action on the multiplayer menu. Preserve route and roster drafts across accidental navigation and restart where possible. | Multiplayer menu offers only Host/Search/Retry/Back (`MultiplayerMenuScreen.java:31-60`); recovery is hidden inside reopening host/browser routes (`HostChallengeScreen.java:131-160`, `ChallengeBrowserScreen.java:123-193`). |
| M04 | P0 | Prevent roster submission from racing navigation. | Data consistency, recovery | Disable Back while submission is in flight, or confirm and keep the match resumable. Reconcile the final server result even if the screen generation changes. | Roster submit is asynchronous (`MultiplayerRosterWaitingScreen.java:97-113`), while Back is not stored/disabled during submission (`MultiplayerRosterWaitingScreen.java:56-64`). |
| M05 | P0 | Expose reconnect immediately and align it with server grace time. | Recovery, feedback | Show reconnect state, remaining grace time, attempt count, and `RECONNECT NOW` during automatic retries. Keep total backoff/connect time inside the server grace period. Distinguish local transport, opponent grace, and terminal match state. | Client retries can include 1/2/4/8/8-second delays plus connect time (`ClientNetworkConfig.java:22-27,82-90`), while the server grace period is documented as 30 seconds (`MULTIPLAYER.md:101-103,150-151`). |
| M06 | P0 | Preserve or clearly explain guest identity during recovery. | Trust, recovery | Do not silently rotate credentials while an active match is being recovered. Detect token expiry and explain that a new identity cannot own the old match. Add a safe re-auth/recovery path if the server supports it. | Invalid stored credentials are cleared and recreated (`GuestAccountService.java:95-115`); reconnect revalidates identity before fetching setup (`MultiplayerDisconnectedScreen.java:141-170`). |
| M07 | P1 | Explain guest identity and let users manage it. | Trust, flexibility | On first multiplayer entry, explain persistence and visibility. Add rename/new-identity controls with warnings about pending matches. Show expiry/session status without exposing the token. | Identity is created automatically (`GuestAccountService.java:43-90`) and shown only as `GUEST: name` (`MultiplayerMenuScreen.java:100-105`), despite service support for selected display names. |
| M08 | P1 | Show the requester before host approval. | Informed choice, safety | Display privacy-safe requester name, requested fighters, request age, and format. Replace `YES/NO` with `ACCEPT REQUEST/DECLINE REQUEST` and show a compact confirmation. | Host UI omits requester context (`HostChallengeScreen.java:76-106,245-274`), while requester ID, fighters, and timestamp exist in `ChallengeSummary.java:17-33`. |
| M09 | P1 | Auto-refresh the challenge browser and show freshness/expiry. | Efficiency, feedback | Add low-frequency auto-refresh, `Last updated`, relative age, expiry countdown, and immediate stale-row removal after a failed join. Keep manual Refresh. | Browser loads once and manually refreshes (`ChallengeBrowserScreen.java:196-231,502-510`); `expiresAt` exists in `ChallengeSummary.java:28-33` but is not shown. |
| M10 | P1 | Make every waiting state show health, elapsed time, and next event. | Feedback, reduced anxiety | Show waiting-since, last successful poll, next check, opponent/requester state, and distinct Connecting/Waiting/Reconnecting/Expired states. Use a persistent status banner rather than only the battle log. | Host, join approval, and roster screens use indefinite status text (`HostChallengeScreen.java:255-264`, `ChallengeBrowserScreen.java:342-350`, `MultiplayerRosterWaitingScreen.java:90-94`). |
| M11 | P1 | Rewrite technical errors around recovery actions. | Cognitive load, recovery | Lead with human copy and one primary action. Move codes such as `HTTP_500` or `MATCH_NOT_FOUND` into expandable details/logs. Standardize terminology and capitalization. | Error formatter exposes codes directly (`MultiplayerScreenBase.java:248-262`); disconnected recovery hardcodes technical code text (`MultiplayerDisconnectedScreen.java:175-182`). |
| M12 | P1 | Confirm replacing an existing hosted challenge. | Error prevention, trust | Before cancel-and-recreate, show the existing versus proposed format/stat mode and whether a request is pending. Offer Keep Existing or Replace. | A mismatched recoverable challenge can be canceled and replaced automatically (`HostChallengeScreen.java:179-199`). |
| M13 | P1 | Show opponent and roster progress during match setup. | Feedback, immersion | Display opponent name, local roster locked state, opponent selection state, and whether the opponent roster is intentionally hidden. Transition automatically and visibly when both are ready. | Roster waiting shows format/local fighters and generic waiting (`MultiplayerRosterWaitingScreen.java:49-95`), while opponent identity and rosters exist in `MatchSetup.java:15-30`. |
| M14 | P2 | Hide protocol metadata unless it is actionable. | Cognitive load | Keep host, format, stat mode, age, expiry, and compatibility prominent. Move game/protocol versions to Details and explain incompatibility in player language. | Challenge rows display game and protocol versions (`ChallengeBrowserScreen.java:267-280`) even though incompatible rows are already filtered (`ChallengeBrowserScreen.java:234-243`). |
| M15 | P2 | Add private/direct challenges and browser filters. | Flexibility, efficiency, safety | Support private invite codes or friend links, plus filters for format/stat mode/region or latency if available. Keep public challenge browsing as the default simple flow. | The current UI is explicitly centered on public challenges (`MultiplayerMenuScreen`, `HostChallengeScreen`, `ChallengeBrowserScreen`); runtime validation showed only Host Challenge and Search Challenges. |

## Authoring Tools

The editors are power-user tools, but they are part of the shipped UI and can modify persistent game content. Their highest-priority UX work is therefore data safety and efficient repeated authoring rather than player onboarding.

Explicit editor coverage:

| Editor | Primary recommendations |
|---|---|
| Move | Destructive normalization warnings, transactional references, progressive disclosure, inline validation, undo, and richer catalog search. |
| Character | Visible assignment eligibility reasons, keyboard assignment, derived-stat clarity, technique-tree guidance, and preserved drafts. |
| Ability | Source/category change warnings, clearer condition/effect composition, inline validation, and direct navigation to references. |
| Technique | Explicit skill-tree tools, graph validation, zoom/fit/minimap, keyboard operation, and cross-editor links. |
| Domain | Anti-domain mode-change warnings, field-local validation, explicit technique association, and progressive disclosure of programs. |
| Cursed Tool | Direct links to assigned content, dependency impact previews, keyboard operation, and atomic cross-repository updates. |

| ID | Priority | Improvement | Area and UX goals | How to accomplish it | Evidence |
|---|---|---|---|---|---|
| E01 | P0 | Protect dirty drafts when deleting records. | Data safety, recovery | Before Delete, detect dirty state and offer Save Then Delete, Delete Record and Draft, or Cancel. State exactly what unsaved work will be lost. | Delete acts on the selected record (`EditorScreenBase.java:1095-1119`); the confirmation only says deletion is irreversible (`EditorScreenBase.java:1261-1276`). |
| E02 | P0 | Warn before mode changes clear hidden data. | Data safety, trust | Show a field-level impact summary before destructive source/category/tag/mode switches. Preserve inactive draft values where feasible, and expose `Clear inactive settings` explicitly rather than normalizing silently. | Move normalization clears inactive fields (`MoveEditorScreen.java:3494-3570`); ability source/category changes clear data (`AbilityEditorScreen.java:443-495,785-805`); anti-domain changes reset several values (`DomainEditorScreen.java:212-231`). |
| E03 | P0 | Make multi-repository delete/update operations transactional. | Data integrity, recovery | Add preflight validation, dependency impact preview, temporary files, atomic replacement, backups, and rollback. Report affected records and files before confirmation. | Cross-repository resequencing/saves occur in `MoveEditorScreen.java:793-840`, `CharacterEditorScreen.java:487-510`, `AbilityEditorScreen.java:535-580`, `DomainEditorScreen.java:166-198`, and `CursedToolEditorScreen.java:143-180`. |
| E04 | P0 | Add draft recovery and better save workflows. | Efficiency, recovery | Support Save and Continue, Save and New, Save As/Copy, autosaved crash drafts, and restore-last-selection/search/scroll. Keep editor instances/context instead of recreating them on every entry where safe. | Save rebuilds the draft (`EditorScreenBase.java:1153-1204`); editor navigation recreates screens (`JJKGame.java:459-492`). Runtime validation showed no record selected on entry despite a populated catalog. |
| E05 | P1 | Add direct cross-editor navigation with a return stack. | Efficiency, cognitive load | Make references clickable: Open Move, Open Technique, Open Ability, etc. Preserve the originating draft in memory and return to the exact record/field. | Hints instruct users to use another editor (`MoveEditorScreen.java:1104-1114,3018-3020`); tool assignments are read-only (`CursedToolEditorScreen.java:225-263`); current navigation returns through the main menu. |
| E06 | P1 | Show unavailable assignment candidates and reasons. | Feedback, efficiency | Add `Show unavailable`, reason badges, filters, and `Open/Fix reference`. Explain technique, stat, weapon, grant, tree, or invalid-definition constraints rather than hiding entries. | Character assignment filters candidates (`CharacterEditorScreen.java:1373-1388`) and may return null for every failure (`CharacterEditorScreen.java:1650-1658`). |
| E07 | P0 | Make assignment workflows keyboard operable. | Accessibility, efficiency | Use focusable selectable rows/cards. Add arrows, Confirm to assign, Delete to unassign, modifier+arrows to reorder, multi-select where useful, and visible focus/state text. Keep drag-and-drop optional. | `AssignmentPanel.java:204-294` and `MoveAssignmentPanel.java:253-339` rely on click and drag listeners without keyboard commands. |
| E08 | P1 | Reduce large-form cognitive load with progressive disclosure. | Cognitive load, efficiency | Add Basic/Advanced modes, collapsible sections, a sticky section outline, breadcrumbs in nested dialogs, completion/error counts, summaries, and `Jump to first error`. Keep Save/dirty status visible while scrolling. | Move, ability, condition/effect, and domain editors contain deep conditional forms (`MoveEditorScreen.java:925-1223,2377-2491`, `ConditionTreeEditor.java:71-139`, `EffectListEditor.java:238-359`, `DomainEditorScreen.java:292-329`). |
| E09 | P0 | Add undo/redo for draft mutations. | Recovery, error prevention | Record edits to effects, conditions, prerequisites, tags, assignment, and graph operations. Add Undo/Redo and a temporary recovery toast after destructive row removal. | Complex rows are removed immediately in `EffectListEditor.java:223-229`, `ConditionListEditor.java:101-106`, `ConditionTreeEditor.java:114-130`, and `SkillTreeCanvas.java:532-540`. |
| E10 | P1 | Expand catalog search and filtering. | Efficiency, flexibility | Search ID, name, tags, category, source, technique, weapon type, and references. Add filter chips, recent items, and `N of M` results. | Base search checks only `listLabel` (`EditorScreenBase.java:625-634`); several editors provide name-only labels, while assignment search already indexes richer text (`MoveAssignmentPanel.java:147-153`). |
| E11 | P0 | Validate inline and localize errors to fields/sections. | Error prevention, recovery, accessibility | Validate as fields are committed, show inline text/icons, section counts, and a persistent multi-error summary. Focus/scroll to the first invalid field. Do not distinguish status only by color. | Most validation occurs at Save; errors use one bottom status label (`EditorScreenBase.java:1248-1254`), and the `error` parameter currently does not alter its treatment. |
| E12 | P1 | Make numeric edit acceptance explicit. | Feedback, data quality | Parse on blur/Enter, show invalid intermediate state, explain range/clamping, and visibly restore prior values when rejected. Use one shared numeric field behavior. | Shared integer and editor decimal fields can clamp or retain stale values silently (`EditorScreenBase.java:1422-1449`, `DomainEditorScreen.java:383-407`, `ConditionTreeEditor.java:263-397`). |
| E13 | P1 | Complete and document desktop shortcuts. | Efficiency, accessibility | Add platform-aware Command/Ctrl shortcuts for Save, New, Copy, Delete, Undo, Redo, search, and Save-and-New. Show them in labels/tooltips and add a shortcut reference overlay. | Save checks only `CONTROL_LEFT` (`EditorScreenBase.java:477-481`); toolbar labels do not show shortcuts. |
| E14 | P1 | Give skill-tree editing an explicit toolset. | Discoverability, flexibility | Add Select/Move/Connect/Delete tools, keyboard node navigation, zoom, fit-to-content, minimap, connection removal controls, and graph validation/cycle warnings. | Important actions depend on left-drag/right-click (`SkillTreeCanvas.java:181-191,295-342,872-880`); the canvas has a fixed 1800 minimum width (`SkillTreeCanvas.java:51-55`). |
| E15 | P1 | Make editor layout responsive and preserve full text. | Accessibility, flexibility | Add breakpoints that stack assignment columns, resizable master/detail splitters, expandable descriptions, intentional horizontal scrolling, and tooltips for clipped labels. Test long authored names at minimum supported size and 200% UI scale. | Fixed columns/nodes/heights occur in `AssignmentPanel.java:133-139`, `MoveAssignmentPanel.java:31,210`, and `SkillTreeCanvas.java:51-55`; some descriptions are clipped (`SkillTreeCanvas.java:854-869`). Runtime validation also showed small catalog and toolbar text at 1600x900. |
| E16 | P1 | Preview dependency impact before delete or ID resequencing. | Trust, cognitive load | Show exactly which characters, techniques, tools, abilities, moves, and files will change; distinguish blockers from automatic rewrites; allow exporting a backup before commit. | Delete paths sequentially save affected repositories (`MoveEditorScreen.java:833-835`, `CharacterEditorScreen.java:503-505`, `DomainEditorScreen.java:191-193`, `CursedToolEditorScreen.java:173-176`) without a comprehensive pre-commit impact summary. |

## Positive Patterns to Preserve

- Character Random selection avoids duplicates and builds legal move sets (`CharacterSelectScreen.java:506-575`).
- Clicking a move card places it in the first valid timeline position, providing an efficient novice path (`PlanningPanel.java:1676-1688`).
- Invalid relocation restores the original action instead of losing it (`PlanningPanel.java:1689-1696`).
- Target validation covers ally/enemy relationships, multi-target caps, ordered pairs, and summon-only rules (`PlanningPanel.java:466-515,1379-1393`).
- Team plans submit atomically only after all controlled fighters are ready (`TeamPlanningPanel.java:256-273`).
- Battle presentation has strong fighter art, background, HUD hierarchy, event pacing, and audio routing.
- Multiplayer networking is asynchronous, guards stale callbacks, stops polling on hidden screens, and treats the server as authoritative.
- Guest credentials are stored atomically with restrictive permissions (`GuestCredentialsStore.java:62-109`).
- Editors use a consistent master-detail shell, drafts, dirty indicators, copy/new flows, deep-copy safeguards, and contextual hints.
- Battle layout profiles are validated and tested separately for Mac and Windows.

## Recommended Delivery Plan

### Phase 1: Complete Core Journeys

- Implement semantic input, focus, keyboard/controller planning, and accessible multiplayer/editor actions.
- Add recommended move sets, Quick Battle, format defaults/descriptions, and final setup review.
- Add planner availability reasons, explicit targets, status inspection, undo, and team progress.
- Confirm destructive exits and add rematch, round replay, local Continue, and multiplayer resume paths.
- Protect editor drafts and transactional multi-repository operations.

### Phase 2: Teach and Explain

- Add first-run battle tutorial, contextual planner legend, Help, and in-game Glossary.
- Add inline setup, network, planner, and editor validation/error guidance.
- Add challenge freshness, waiting health, requester/opponent context, and reconnect countdowns.

### Phase 3: Accessibility and Flexibility

- Add remapping, controller settings, UI/text scale, high contrast, reduced motion, captions, and full audio controls.
- Add semantic accessibility output, accessible narration/text fallback, and platform assistive-technology validation.
- Add complete display modes and responsive tests.
- Extract strings and pseudolocalize layouts.

### Phase 4: Efficiency and Immersion

- Add recent/preset setups, repeat plan, deeper search/filtering, direct editor links, and private challenges.
- Carry the battle visual language into navigation and setup.
- Restore menu music and refine transitions without compromising reduced-motion behavior.

## Validation Matrix

The following should be required before considering the audit closed:

| Validation | Required scenarios |
|---|---|
| Keyboard-only | First launch, settings, 1v1 setup, move-set editing, complete battle, multiplayer host/join/recovery, editor CRUD. |
| Controller-only | Same core play path, target selection, team pages, reserve switch, playback, result actions. |
| Assistive technology | Control roles/labels/values, focus announcements, battle-state narration, target/status/timer updates, errors, dialogs, and text-mode fallback with the display hidden. |
| Visual accessibility | High contrast, three common color-vision simulations, 100%-200% UI scale, muted audio, reduced motion. |
| Resolution/platform | Mac profile; Windows profile at 1366x768, 1920x1080, 2560x1440; resized window; high-DPI; Other/Linux smoke pass. |
| Battle complexity | 1v1, multi-fighter team, multi-target move, reserve switch, status-heavy round, summon, transformation, mirror match, long move names. |
| Multiplayer recovery | Empty browser, stale challenge, rejected join, host replacement, roster submit race, disconnect during every phase, token expiry, grace-period timeout, terminal reconnect. |
| Editor safety | Dirty delete, mode normalization, invalid numeric input, cross-repository failure injection, undo/redo, crash draft recovery, long labels, 200% scale. |
| Usability | At least five new-player sessions completing the first round without reading the repository README, followed by experienced-player timing/click-count tests. |

## Success Measures

- A new player can start a recommended 1v1 battle from the main menu in no more than three decisions.
- Every gameplay and multiplayer action is available by mouse, keyboard, and controller.
- No Back/Escape action silently deletes setup, draft, challenge, recovery, or battle context.
- Every disabled move and failed submission provides a visible reason and recovery action.
- A player can inspect every status and queued target without reconstructing state from the log.
- A rematch requires one action; changing teams returns to preserved setup rather than the main menu.
- A local battle can be safely suspended at a stable phase and resumed after restart without changing its outcome.
- UI remains readable and operable at 200% scale and supported minimum resolutions.
- Editor operations do not lose dirty work or leave repositories partially updated after failure.
