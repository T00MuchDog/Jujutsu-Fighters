# Shared Gameplay UI

Battle and Character Select use the former Windows design on all hosts. There is
no OS/profile switch in either screen or in the single/team planning panels.
The temporary Windows-on-Mac flag has been removed.

## Ownership

| Owner | Responsibility |
| --- | --- |
| `ui/UiScaleSystem` | 2560 x 1440 gameplay reference, uniform scale, expanded logical dimensions, small-text sizing |
| `ui/battle/BattleCanvas` | Battle safe-region transforms, input conversion, full-window surface partitions |
| `screens/CharacterSelectScreen` | One roster/profile/stats/technique/move-set/drawer layout; extra height for roster and technique information |
| `screens/BattleScreen` | One persistent execution/log/planning composition in all battle phases |
| `ui/battle/PlanningPanel`, `TeamPlanningPanel` | Shared planning controls and matching input/scissor transforms |
| `ui/battle/ActionSegmentView` | Move labels fitted to both the width and height of queued/selected action cards |
| `ui/profile/BattleUiLayout`, `BattleUiLayoutStore` | One source/bundled metrics resource: `assets/ui/battle-layouts/shared.json` |
| `AssetLoader` | Separate `gameplayFont*` typography, shared textures, unchanged shell fonts/skin |
| `JJKGame` | Loads the shared battle resource independently of launch profile |

The old battle `mac.json` and `windows.json` resources are replaced by `shared.json`.
Obsolete metrics for the removed exclusive planner/classic execution layout were
removed rather than retaining settings that no longer affect rendering. Layouts
are source/bundled resources, not user-persisted settings. Authoring mode reads the
shared source file; packaged games read the bundled resource.

## Responsive Layout

The baseline scale is `min(viewportWidth / 2560, viewportHeight / 1440)` in GLFW
logical window coordinates. Elements are rendered directly, not via an enlarged
screen bitmap, and X/Y scaling is uniform.

- Character Select uses an `ExtendViewport`: the minimum logical area is the
  reference design, with extra width/height exposed to the actual panel layout.
- Roster and detail widths follow the available width. Extra vertical space
  increases roster visibility and technique-information space, not portrait size.
- Body text receives a bounded readability adjustment toward a 9-logical-pixel
  cap height on small windows (maximum 1.6x). At the reference resolution it is
  unchanged. Wrapping, value spacing, and header controls account for that change.
- Battle keeps its canonical fighter/HUD formation geometry in a safe region.
  The planner and field share a bottom anchor; extra height expands the background
  above the formation. This keeps foreground back sprites and baseplates cropped
  at the planner divider instead of exposing their flat bottoms on taller windows.
- Execution background and log surfaces extend to the available edges. The
  background uses uniform cover scaling/cropping, not aspect-ratio stretching.
  Persistent domain backdrops cover that same expanded execution surface.
  Extra horizontal space extends the log/background and planner chrome while
  keeping battle controls in the central safe region.
- Rendering, pointer coordinates, and scissors share the same canvas transforms.
  Backing-buffer scaling is applied once for high-DPI scissors/viewports.

The learned-moves drawer remains an intentional overlay with the existing
open/close and drag/drop interactions. Its toggle has reserved header space,
including when the six-slot team tray is present.

## Retained Platform Behavior

`GraphicsMain`, `DesktopPlatform`, and `display/` retain real host responsibilities:
macOS Cocoa fullscreen/first-thread startup, Windows borderless resolution policy
and DWM handling, display settings, and LibGDX logical high-DPI coordinates.

Menus, editors, and other unrelated screens retain their existing `UiProfile`
policies. `--ui-profile=MAC|WINDOWS` and `jjktbf.ui.profile` now select those shell
presentations only; they cannot select an alternate Battle or Character Select.
Removing the experiment restores the normal Mac shell default without restoring
the removed Mac gameplay layouts.

## Validation

The graphics test suite covers geometry, viewport round trips, roster selection,
planning drag/drop and locking, team planning, battle presentation state, and
unrelated graphics components. The opt-in `SharedUiPreview` renders the production
screens to real OpenGL framebuffers at exact dimensions, even when they exceed the
host desktop. Fixtures are constructed in code and do not load/save editable game
content. This is a presentation harness, not an end-to-end battle simulation.

Matrix: 1366 x 768, 1920 x 1080, 2560 x 1440, 1512 x 982, 2000 x 1243, 2560 x 1600, and
3440 x 1440. Each includes Character Select, six-slot selection, the open learned
drawer, and planning/locked execution with 1, 3, and 4 fighters per side.

Validated on macOS on 2026-09-08 after the foreground-anchor fix: package succeeded;
all 464 graphics tests passed and all 70 rendered image pairs were identical between
shell profiles. The MacBook-size 2x backing-buffer pass also completed without GL
errors. Regression checks now assert foreground footing at the planner divider
across aspect ratios and actual resource-text vertex bounds at normal/enlarged
font scales. Preview fixtures include a cropped back sprite and a long resource
name; the screenshot-size and MacBook battle captures were visually inspected.

From the repository root on macOS:

```bash
mvn -pl graphics -am -Dtest='com.jjktbf.graphics.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false package
java -XstartOnFirstThread -Djjktbf.data.root=graphics/target/shared-ui-preview-data -cp 'graphics/target/test-classes:graphics/target/classes:core/target/classes:graphics/target/graphics-1.4.1.jar' com.jjktbf.graphics.screens.SharedUiPreview --ui-profile=MAC
java -XstartOnFirstThread -Djjktbf.data.root=graphics/target/shared-ui-preview-data -cp 'graphics/target/test-classes:graphics/target/classes:core/target/classes:graphics/target/graphics-1.4.1.jar' com.jjktbf.graphics.screens.SharedUiPreview --ui-profile=WINDOWS
```

Run MAC first, then WINDOWS: the latter asserts byte-identical PNGs against the
MAC run. Output is `graphics/target/shared-ui-preview/{mac,windows}/`.
Add `-Djjktbf.preview.hdpi=2` before `-cp` to render the MacBook logical size into a
3024 x 1964 backing buffer; output uses a `-hdpi2` suffix. The harness asserts no
OpenGL errors. Screenshots are intended for visual inspection in addition to
automated geometry/input checks, not as golden images tied to editable content.

Native Windows runtime/fullscreen/DPI-driver behavior is not verified by an
offscreen macOS run. Those native policies were left unchanged. Arbitrary
user-authored content lengths and smaller-than-supported windows are not a
guaranteed visual matrix; existing clipping, wrapping, ellipsis, and scrolling
remain in place for overflow.
