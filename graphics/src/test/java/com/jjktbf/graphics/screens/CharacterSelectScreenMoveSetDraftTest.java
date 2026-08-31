package com.jjktbf.graphics.screens;

import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterSelectScreenMoveSetDraftTest {

    private static Move move(String id) {
        return new Move.Builder(id).name(id).category(MoveCategory.PHYSICAL).build();
    }

    private static CharacterData fighter() {
        CharacterData data = new CharacterData();
        data.id = "000001";
        data.name = "Fighter";
        return data;
    }

    @Test
    void fighterWithoutSavedChoiceDraftsTheRecommendation() {
        CharacterData data = fighter();
        data.moveIds = List.of("one", "two");
        List<Move> moves = List.of(move("one"), move("two"));

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, moves, moves);

        assertEquals(List.of("one", "two"), draft);
    }

    @Test
    void savedChoiceDraftsInSavedOrder() {
        CharacterData data = fighter();
        data.moveSetIds = List.of("two", "one");

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, List.of(move("one"), move("two"), move("three")),
            List.of(move("three")));

        assertEquals(List.of("two", "one"), draft);
    }

    @Test
    void savedIdsThatAreNoLongerLearnedAreDropped() {
        CharacterData data = fighter();
        data.moveSetIds = Arrays.asList("one", "ghost", null);

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, List.of(move("one")), List.of());

        assertEquals(List.of("one"), draft);
    }

    @Test
    void entirelyStaleSavedChoiceFallsBackToRecommendation() {
        CharacterData data = fighter();
        data.moveSetIds = List.of("ghost");

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, List.of(move("one"), move("two")), List.of(move("two")));

        assertEquals(List.of("two"), draft);
    }

    @Test
    void staleSavedChoiceFallsBackBeforeBuildingTheSetupDraft() {
        CharacterData data = fighter();
        data.combatAbility = 10;
        data.moveIds = List.of("one");
        data.moveSetIds = List.of("ghost");
        MoveRepository moves = new MoveRepository("unused");
        moves.add(MoveData.fromMove(move("one")));

        com.jjktbf.model.character.Character resolved =
            CharacterSelectScreen.resolveProfileCharacter(data, moves, null, null, null);
        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, resolved.getLearnedMoves(), resolved.getRecommendedMoveSet());

        assertEquals(List.of("one"), draft);
    }
}
