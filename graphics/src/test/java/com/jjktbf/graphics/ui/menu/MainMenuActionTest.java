package com.jjktbf.graphics.ui.menu;

import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.GameAudio;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.launch.DesktopPlatform;
import com.jjktbf.graphics.ui.profile.UiProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuActionTest {
    @ParameterizedTest
    @EnumSource(UiProfile.class)
    void catalogContainsOnlyTheOptionalAuthorEntryWhenPermissionAllows(UiProfile profile) {
        assertFalse(MainMenuAction.modes(false).contains(MainMenuAction.AUTHOR_BATTLE));
        assertTrue(MainMenuAction.modes(true).contains(MainMenuAction.AUTHOR_BATTLE));
        assertEquals(6, MainMenuAction.editors().size());
        assertEquals(2, MainMenuAction.modes(false).size());
        assertEquals(3, MainMenuAction.modes(true).size());
    }

    @ParameterizedTest
    @EnumSource(UiProfile.class)
    void everyCatalogEntryInvokesTheMatchingGameRoute(UiProfile profile) {
        RecordingGame game = new RecordingGame(profile, true);

        for (MainMenuAction action : MainMenuAction.available(true)) {
            game.routes.clear();
            action.invoke(game);
            assertEquals(List.of(action.name()), game.routes,
                () -> profile + " route for " + action);
        }
    }

    @ParameterizedTest
    @EnumSource(UiProfile.class)
    void authorBattleGuardBlocksAStaleOrForgedOptionalAction(UiProfile profile) {
        RecordingGame game = new RecordingGame(profile, false);

        MainMenuAction.AUTHOR_BATTLE.invoke(game);

        assertTrue(game.routes.isEmpty());
    }

    private static final class RecordingGame extends JJKGame {
        private final boolean authorAvailable;
        private final List<String> routes = new ArrayList<>();

        RecordingGame(UiProfile profile, boolean authorAvailable) {
            super(new DesktopLaunchOptions(
                profile == UiProfile.WINDOWS ? DesktopPlatform.WINDOWS : DesktopPlatform.OTHER,
                profile, true, profile.defaultReferenceWidth(), profile.defaultReferenceHeight()));
            this.authorAvailable = authorAvailable;
        }

        @Override public boolean isAuthorBattleAvailable() { return authorAvailable; }
        @Override public GameAudio audio() { return null; }
        @Override public void showSinglePlayerBattle() { routes.add(MainMenuAction.SINGLE_PLAYER.name()); }
        @Override public void showMultiplayerMenu() { routes.add(MainMenuAction.MULTIPLAYER.name()); }
        @Override public void showAuthorBattle() { routes.add(MainMenuAction.AUTHOR_BATTLE.name()); }
        @Override public void showCharacterEditor() { routes.add(MainMenuAction.CHARACTER_EDITOR.name()); }
        @Override public void showMoveEditor() { routes.add(MainMenuAction.MOVE_EDITOR.name()); }
        @Override public void showAbilityEditor() { routes.add(MainMenuAction.ABILITY_EDITOR.name()); }
        @Override public void showTechniqueEditor() { routes.add(MainMenuAction.TECHNIQUE_EDITOR.name()); }
        @Override public void showDomainEditor() { routes.add(MainMenuAction.DOMAIN_EDITOR.name()); }
        @Override public void showCursedToolEditor() { routes.add(MainMenuAction.CURSED_TOOL_EDITOR.name()); }
    }
}
