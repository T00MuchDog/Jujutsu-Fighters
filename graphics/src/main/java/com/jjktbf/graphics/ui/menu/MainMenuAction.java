package com.jjktbf.graphics.ui.menu;

import com.jjktbf.graphics.JJKGame;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Shared route catalog. Optional entries are filtered before either UI lays out. */
public enum MainMenuAction {
    SINGLE_PLAYER("SINGLE PLAYER", "PLAYER VS CPU", "Choose your fighters. Outsmart the opponent.", false, JJKGame::showSinglePlayerBattle),
    MULTIPLAYER("MULTIPLAYER", "ONLINE CHALLENGES", "Host a challenge or find an opponent online.", false, JJKGame::showMultiplayerMenu),
    AUTHOR_BATTLE("AUTHOR BATTLE", "CONTROL BOTH SIDES", "Direct both teams in a local battle.", false, JJKGame::showAuthorBattle),
    CHARACTER_EDITOR("CHARACTER EDITOR", "ROSTER", "Fighters, stats and loadouts.", true, JJKGame::showCharacterEditor),
    MOVE_EDITOR("MOVE EDITOR", "COMBAT", "Moves and ordered effect compositions.", true, JJKGame::showMoveEditor),
    ABILITY_EDITOR("ABILITY EDITOR", "ABILITIES", "Active and passive effects.", true, JJKGame::showAbilityEditor),
    TECHNIQUE_EDITOR("TECHNIQUE EDITOR", "TECHNIQUES", "Techniques and progression trees.", true, JJKGame::showTechniqueEditor),
    DOMAIN_EDITOR("DOMAIN EDITOR", "DOMAINS", "Domain definitions and properties.", true, JJKGame::showDomainEditor),
    CURSED_TOOL_EDITOR("CURSED TOOL EDITOR", "EQUIPMENT", "Cursed tools and weapon properties.", true, JJKGame::showCursedToolEditor);

    public final String title;
    public final String category;
    public final String description;
    public final boolean editor;
    private final Consumer<JJKGame> route;

    MainMenuAction(String title, String category, String description, boolean editor, Consumer<JJKGame> route) {
        this.title = title;
        this.category = category;
        this.description = description;
        this.editor = editor;
        this.route = route;
    }

    public void invoke(JJKGame game) {
        if (this != AUTHOR_BATTLE || game.isAuthorBattleAvailable()) route.accept(game);
    }

    public static List<MainMenuAction> available(boolean authorBattle) {
        return Arrays.stream(values()).filter(a -> a != AUTHOR_BATTLE || authorBattle).toList();
    }

    public static List<MainMenuAction> modes(boolean authorBattle) {
        return available(authorBattle).stream().filter(a -> !a.editor).toList();
    }

    public static List<MainMenuAction> editors() {
        return Arrays.stream(values()).filter(a -> a.editor).toList();
    }
}
