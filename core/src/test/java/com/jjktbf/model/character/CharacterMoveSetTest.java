package com.jjktbf.model.character;

import com.jjktbf.AppPaths;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterMoveSetTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void learnedPoolIsUncappedWhileDefaultMoveSetUsesStatBudget() {
        List<Move> learned = List.of(move("one"), move("two"), move("three"));
        CharacterStats stats = new CharacterStats.Builder().combatAbility(10).build();

        Character character = new SorcererCharacter(
            "fighter", "Fighter", stats, null,
            learned, null, List.of(), Set.of(), Equipment.NONE);

        assertEquals(List.of("one", "two", "three"), ids(character.getLearnedMoves()));
        assertEquals(List.of("one", "two"), ids(character.getMoveSet()));
    }

    @Test
    void recommendationRemainsAvailableForAnExplicitlyEmptyLoadout() {
        List<Move> learned = List.of(move("one"), move("two"), move("three"));
        CharacterStats stats = new CharacterStats.Builder().combatAbility(10).build();
        Character character = new SorcererCharacter(
            "fighter", "Fighter", stats, null,
            learned, List.of(), List.of(), Set.of(), Equipment.NONE);

        assertEquals(List.of(), ids(character.getMoveSet()));
        assertEquals(List.of("one", "two"), ids(character.getRecommendedMoveSet()));
    }

    @Test
    void explicitMoveSetKeepsLearnedOrderAndRejectsAnOverfilledPool() {
        List<Move> learned = List.of(move("one"), move("two"), move("three"));
        CharacterStats stats = new CharacterStats.Builder().combatAbility(10).build();
        Character character = new SorcererCharacter(
            "fighter", "Fighter", stats, null,
            learned, null, List.of(), Set.of(), Equipment.NONE);

        Character reordered = character.withMoveSet(List.of("two", "one"));

        assertEquals(List.of("one", "two"), ids(reordered.getMoveSet()));
        assertEquals(List.of("one", "two", "three"), ids(reordered.getLearnedMoves()));
        CharacterData saved = CharacterData.fromCharacter(reordered);
        assertEquals(List.of("one", "two", "three"), saved.moveIds);
        assertEquals(List.of("one", "two"), saved.moveSetIds);
        assertThrows(IllegalArgumentException.class,
            () -> character.withMoveSet(List.of("one", "two", "three")));
        assertThrows(IllegalArgumentException.class,
            () -> character.withMoveSet(List.of("one", "one")));
        assertThrows(IllegalArgumentException.class,
            () -> character.withMoveSet(List.of("not-learned")));
    }

    @Test
    void characterDataLoadsSavedDefaultAndAllowsAnExplicitBattleOverride() {
        MoveRepository moves = new MoveRepository("unused");
        moves.add(MoveData.fromMove(move("one")));
        moves.add(MoveData.fromMove(move("two")));
        moves.add(MoveData.fromMove(move("three")));
        CharacterData data = new CharacterData();
        data.id = "fighter";
        data.name = "Fighter";
        data.combatAbility = 10;
        data.moveIds = List.of("one", "two", "three");
        data.moveSetIds = List.of("three", "one");

        assertEquals(List.of("one", "three"), ids(data.toCharacter(moves).getMoveSet()));
        assertEquals(List.of("two"), ids(data.toCharacter(
            moves, null, null, null, List.of("two")).getMoveSet()));
    }

    @Test
    void characterRepositoryPersistsSavedMoveSet() throws Exception {
        String previousRoot = System.getProperty(AppPaths.DATA_ROOT_SYSTEM_PROPERTY);
        try {
            System.setProperty(AppPaths.DATA_ROOT_SYSTEM_PROPERTY,
                temporaryDirectory.toString());
            CharacterData data = new CharacterData();
            data.id = "fighter";
            data.name = "Fighter";
            data.moveIds = List.of("one", "two", "three");
            data.moveSetIds = List.of("one", "three");
            CharacterRepository characters = new CharacterRepository("data/characters");
            characters.add(data);
            characters.save();

            CharacterRepository reloaded = new CharacterRepository("data/characters");
            reloaded.load();

            assertEquals(List.of("one", "three"),
                reloaded.getAll().get(0).moveSetIds);
        } finally {
            if (previousRoot == null) {
                System.clearProperty(AppPaths.DATA_ROOT_SYSTEM_PROPERTY);
            } else {
                System.setProperty(AppPaths.DATA_ROOT_SYSTEM_PROPERTY, previousRoot);
            }
        }
    }

    private static Move move(String id) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .build();
    }

    private static List<String> ids(List<Move> moves) {
        return moves.stream().map(Move::getId).toList();
    }
}
