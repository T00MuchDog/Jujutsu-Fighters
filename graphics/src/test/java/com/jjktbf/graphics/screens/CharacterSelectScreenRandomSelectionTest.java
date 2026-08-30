package com.jjktbf.graphics.screens;

import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MovePool;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterSelectScreenRandomSelectionTest {

    private static CharacterData character(String id, String name) {
        CharacterData data = new CharacterData();
        data.id = id;
        data.name = name;
        return data;
    }

    @Test
    void randomCandidateNeverReturnsFighterAlreadyPickedForSide() {
        CharacterData a = character("000001", "A");
        CharacterData b = character("000002", "B");
        CharacterData c = character("000003", "C");
        List<CharacterData> roster = List.of(a, b, c);

        for (int seed = 0; seed < 200; seed++) {
            CharacterData pick = CharacterSelectScreen.randomCandidate(
                roster, List.of(b), new Random(seed));
            assertNotNull(pick);
            assertTrue(pick == a || pick == c, "picked already-taken fighter at seed " + seed);
        }
    }

    @Test
    void randomCandidateIsDeterministicPerSeed() {
        List<CharacterData> roster = List.of(
            character("000001", "A"),
            character("000002", "B"),
            character("000003", "C"));

        assertEquals(
            CharacterSelectScreen.randomCandidate(roster, List.of(), new Random(42)),
            CharacterSelectScreen.randomCandidate(roster, List.of(), new Random(42)));
    }

    @Test
    void randomCandidateCanReturnEveryUnpickedFighter() {
        CharacterData a = character("000001", "A");
        CharacterData b = character("000002", "B");
        CharacterData c = character("000003", "C");
        List<CharacterData> roster = List.of(a, b, c);

        Set<CharacterData> seen = new HashSet<>();
        for (int seed = 0; seed < 500 && seen.size() < 3; seed++) {
            seen.add(CharacterSelectScreen.randomCandidate(
                roster, List.of(), new Random(seed)));
        }
        assertEquals(Set.of(a, b, c), seen);
    }

    @Test
    void randomCandidateReturnsNullWhenEveryFighterIsPicked() {
        CharacterData a = character("000001", "A");
        assertNull(CharacterSelectScreen.randomCandidate(
            List.of(a), List.of(a), new Random(1)));
    }

    private static Move move(String id, MovePool pool, boolean free) {
        return new Move.Builder(id)
            .category(MoveCategory.PHYSICAL)
            .pool(pool)
            .freeMove(free)
            .build();
    }

    @Test
    void randomMoveSetAlwaysEquipsFreeMovesAndRespectsPoolBudgets() {
        Move freeCombat = move("m1", MovePool.COMBAT_ARTS, true);
        Move freeJujutsu = move("m2", MovePool.JUJUTSU_ARTS, true);
        Move combatA = move("m3", MovePool.COMBAT_ARTS, false);
        Move combatB = move("m4", MovePool.COMBAT_ARTS, false);
        Move jujutsuA = move("m5", MovePool.JUJUTSU_ARTS, false);
        List<Move> learned = List.of(freeCombat, freeJujutsu, combatA, combatB, jujutsuA);

        for (int seed = 0; seed < 200; seed++) {
            List<String> equipped = CharacterSelectScreen.randomMoveSetIds(
                learned, m -> !m.isFreeMove(), 1, 1, new Random(seed));

            // Free moves are always equipped.
            assertTrue(equipped.contains("m1"), "free combat move missing at seed " + seed);
            assertTrue(equipped.contains("m2"), "free jujutsu move missing at seed " + seed);

            // Slot-budgeting moves: at most one per pool here.
            long combatPicked = equipped.stream()
                .filter(id -> id.equals("m3") || id.equals("m4")).count();
            long jujutsuPicked = equipped.stream()
                .filter(id -> id.equals("m5")).count();
            assertTrue(combatPicked <= 1, "combat budget exceeded at seed " + seed);
            assertTrue(jujutsuPicked <= 1, "jujutsu budget exceeded at seed " + seed);

            // Equipped ids are unique and retain learned order.
            assertEquals(equipped.stream().distinct().toList(), equipped);
            List<String> learnedOrder = learned.stream().map(Move::getId).toList();
            List<String> equippedInLearnedOrder =
                learnedOrder.stream().filter(equipped::contains).toList();
            assertEquals(equippedInLearnedOrder, equipped, "equipped order breaks learned order");
        }
    }

    @Test
    void randomMoveSetWithZeroBudgetsEquipsOnlyFreeMoves() {
        List<Move> learned = List.of(
            move("m1", MovePool.COMBAT_ARTS, true),
            move("m2", MovePool.JUJUTSU_ARTS, false));

        assertEquals(List.of("m1"), CharacterSelectScreen.randomMoveSetIds(
            learned, m -> !m.isFreeMove(), 0, 0, new Random(3)));
    }

    @Test
    void randomMoveSetFillsBudgetsWhenEnoughMovesExist() {
        List<Move> learned = List.of(
            move("m1", MovePool.COMBAT_ARTS, false),
            move("m2", MovePool.COMBAT_ARTS, false),
            move("m3", MovePool.JUJUTSU_ARTS, false));

        for (int seed = 0; seed < 200; seed++) {
            List<String> equipped = CharacterSelectScreen.randomMoveSetIds(
                learned, m -> !m.isFreeMove(), 2, 1, new Random(seed));
            assertEquals(3, equipped.size(), "budgets not filled at seed " + seed);
        }
    }

    @Test
    void randomMoveSetIsDeterministicPerSeed() {
        List<Move> learned = List.of(
            move("m1", MovePool.COMBAT_ARTS, false),
            move("m2", MovePool.JUJUTSU_ARTS, false),
            move("m3", MovePool.COMBAT_ARTS, false));

        assertEquals(
            CharacterSelectScreen.randomMoveSetIds(learned, m -> !m.isFreeMove(), 1, 1, new Random(9)),
            CharacterSelectScreen.randomMoveSetIds(learned, m -> !m.isFreeMove(), 1, 1, new Random(9)));
    }
}
