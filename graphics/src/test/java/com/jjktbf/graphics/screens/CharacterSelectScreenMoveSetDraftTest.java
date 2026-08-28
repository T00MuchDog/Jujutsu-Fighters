package com.jjktbf.graphics.screens;

import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void fighterWithoutSavedChoiceDraftsAnEmptyMoveSet() {
        CharacterData data = fighter();
        data.moveIds = List.of("one", "two");

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, List.of(move("one"), move("two")));

        assertTrue(draft.isEmpty());
    }

    @Test
    void savedChoiceDraftsInSavedOrder() {
        CharacterData data = fighter();
        data.moveSetIds = List.of("two", "one");

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, List.of(move("one"), move("two"), move("three")));

        assertEquals(List.of("two", "one"), draft);
    }

    @Test
    void savedIdsThatAreNoLongerLearnedAreDropped() {
        CharacterData data = fighter();
        data.moveSetIds = Arrays.asList("one", "ghost", null);

        List<String> draft = CharacterSelectScreen.seedMoveSetDraft(
            data, List.of(move("one")));

        assertEquals(List.of("one"), draft);
    }
}
