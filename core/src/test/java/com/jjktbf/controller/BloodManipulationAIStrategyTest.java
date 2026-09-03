package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.jjktbf.controller.BloodManipulationAIStrategy.bloodSpendAllowed;
import static com.jjktbf.controller.BloodManipulationAIStrategy.wantsConversion;
import static com.jjktbf.controller.BloodManipulationAIStrategy.wantsEscalation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Noritoshi Kamo (Blood Manipulation) archetype: the stance and
 * blood-economy decision rules, cautious versus aggressive round shapes, the
 * loaded lethal finisher, and dispatcher routing.
 */
class BloodManipulationAIStrategyTest {

    private final BloodManipulationAIStrategy strategy = new BloodManipulationAIStrategy();
    private final List<Move> canonical = loadMoves();

    // --- Decision rules -------------------------------------------------------

    @Test
    void escalationWaitsForATrigger() {
        assertFalse(wantsEscalation(true, 5, 0.9, 0.9, false),
            "no re-escalation while the surge window is already open");
        assertFalse(wantsEscalation(false, 0, 0.1, 0.1, true),
            "no escalation without blood to spend");
        assertFalse(wantsEscalation(false, 5, 0.9, 0.9, false),
            "a healthy duel stays cautious");
        assertTrue(wantsEscalation(false, 5, 0.30, 0.9, false),
            "enemy in execution range");
        assertTrue(wantsEscalation(false, 5, 0.9, 0.30, false),
            "cornered");
        assertTrue(wantsEscalation(false, 5, 0.9, 0.9, true),
            "a loaded lethal finisher warrants the surge");
    }

    @Test
    void conversionNeedsHeadroomBloodAndAQuietBoard() {
        assertTrue(wantsConversion(0, 2, 5, false, 0), "quiet: bank idle blood");
        assertTrue(wantsConversion(0, 2, 5, false, 1), "one committed attack is still quiet");
        assertFalse(wantsConversion(0, 2, 5, false, 2), "under pressure: keep acting");
        assertTrue(wantsConversion(1, 2, 1, true, 5), "aggressive banks freely");
        assertFalse(wantsConversion(2, 2, 5, true, 0), "compression at capacity");
        assertFalse(wantsConversion(0, 2, 0, true, 0), "no blood in hand");
        assertFalse(wantsConversion(0, 0, 5, true, 0), "no compression capacity");
    }

    @Test
    void cautiousBloodSpendsKeepTheFinisherReserve() {
        assertTrue(bloodSpendAllowed(false, 5, 0), "deep supply: free to spend");
        assertTrue(bloodSpendAllowed(false, 2, 1), "blood + compression still reaches convert-and-fire");
        assertFalse(bloodSpendAllowed(false, 2, 0), "would strand the finisher");
        assertFalse(bloodSpendAllowed(false, 1, 1), "one ammunition short");
        assertFalse(bloodSpendAllowed(false, 0, 2), "no blood at all");
        assertTrue(bloodSpendAllowed(true, 1, 0), "aggressive spends freely");
        assertFalse(bloodSpendAllowed(true, 0, 0));
    }

    // --- Cautious round shape -------------------------------------------------

    @Test
    void cautiousRoundOneBanksCompressionAndKeepsADefenseLayer() {
        BattleCombatant kamo = kamo(kit());
        BattleState state = state(kamo, AIFixtures.lowCeSorcererEnemy("e"));

        BattlePlan plan = strategy.buildPlan(state, kamo, new SeededRandomSource(1L));

        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().getId().equals("000097")),
            "banks idle blood into compression while the board is quiet");
        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().hasTag("ATTACK")),
            "still threatens");
        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().isActiveDefense()),
            "cautious Kamo keeps a defense layer");
        assertFalse(plan.allSegments().stream().anyMatch(s -> s.getMove().getId().equals("000094")),
            "no Flowing Red Scale in a healthy duel");
    }

    @Test
    void cautiousKamoHoldsBothBloodAndLoadedCompressionWhenAmmunitionIsShort() {
        BattleCombatant kamo = kamo(kit());
        kamo.transactBoundedResources("BLOOD_SUPPLY", 3, null, 0); // blood 5 -> 2
        BattleState state = state(kamo, AIFixtures.lowCeSorcererEnemy("e"));

        BattlePlan plan = strategy.buildPlan(state, kamo, new SeededRandomSource(1L));

        // Convergence converts one blood (ammunition is preserved, not spent), so
        // after it blood=1/compression=1: no blood attack may follow, and the
        // loaded compression is held for a kill or the surge window.
        Set<String> freeAttacks = Set.of("000000", "000092");
        assertTrue(plan.allSegments().stream()
                .filter(s -> s.getMove().hasTag("ATTACK"))
                .allMatch(s -> freeAttacks.contains(s.getMove().getId())),
            "only free attacks while the finisher reserve is short");
    }

    // --- Aggressive round shape -----------------------------------------------

    @Test
    void surgeWindowSpendsBloodAndShedsDefenseLayers() {
        BattleCombatant kamo = kamo(move("000096"), move("000001")); // Slicing Exorcism + Basic Block
        kamo.transactBoundedResources("BLOOD_SUPPLY", 4, null, 0); // blood 5 -> 1
        kamo.addRuntimeAbilityEffect(flowingRedScaleSpeedRow(), 1, BattleState.Phase.RESOLUTION);
        BattleState state = state(kamo, AIFixtures.lowCeSorcererEnemy("e"));

        BattlePlan plan = strategy.buildPlan(state, kamo, new SeededRandomSource(1L));

        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().getId().equals("000096")),
            "inside the surge window the last blood is fair game");
        long defenses = plan.allSegments().stream()
            .filter(s -> s.getMove().isActiveDefense()).count();
        assertTrue(defenses <= 1, "aggressive Kamo keeps at most one defense layer");
    }

    @Test
    void executionRangeOpensFlowingRedScale() {
        BattleCombatant kamo = kamo(kit());
        BattleCombatant enemy = AIFixtures.lowCeSorcererEnemy("e");
        enemy.applyDamage(Math.max(0, enemy.getCurrentHp() * 4 / 5)); // ~20% HP remains
        BattleState state = state(kamo, enemy);

        BattlePlan plan = strategy.buildPlan(state, kamo, new SeededRandomSource(1L));

        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().getId().equals("000094")),
            "enemy in execution range: open Flowing Red Scale");
    }

    // --- Loaded lethal finisher -----------------------------------------------

    @Test
    void loadedLethalPiercingBloodFiresFirst() {
        BattleCombatant kamo = kamo(kit());
        kamo.transactBoundedResources("BLOOD_SUPPLY", 1, "COMPRESSION", 1); // compression 0 -> 1
        BattleCombatant enemy = AIFixtures.lowCeSorcererEnemy("e");
        enemy.applyDamage(Math.max(0, enemy.getCurrentHp() - 10)); // 10 HP remains
        BattleState state = state(kamo, enemy);

        BattlePlan plan = strategy.buildPlan(state, kamo, new SeededRandomSource(1L));

        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().getId().equals("000098")),
            "a loaded lethal Piercing Blood is committed");
        ActionSegment first = plan.allSegments().stream()
            .filter(s -> s.getMove().hasTag("ATTACK"))
            .min(Comparator.comparingInt(ActionSegment::getStartTick))
            .orElseThrow();
        assertEquals("000098", first.getMove().getId(),
            "the kill does not wait for other attacks");
    }

    // --- Routing --------------------------------------------------------------

    @Test
    void dispatcherRoutesBloodManipulationSorcerer() {
        ArchetypeAIStrategy dispatcher = new ArchetypeAIStrategy();
        BattleCombatant kamo = kamo(kit());
        BattleState state = state(kamo, AIFixtures.lowCeSorcererEnemy("e"));

        TeamBattlePlan teamPlan = dispatcher.selectTeamPlan(
            state, state.playerTeam().active(), new SeededRandomSource(1L));
        BattlePlan plan = teamPlan.get(kamo.getInstanceId());

        assertTrue(plan.allSegments().stream().anyMatch(s -> s.getMove().getId().equals("000097")),
            "the Blood Manipulation brain banks compression through the dispatcher");
    }

    // --- helpers --------------------------------------------------------------

    /** The canonical Kamo Goodwill-Event kit (basic layer + bow + blood techniques). */
    private Move[] kit() {
        return new Move[] {
            move("000000"), move("000001"), move("000012"), move("000019"),
            move("000027"), move("000028"), move("000092"), move("000093"),
            move("000094"), move("000095"), move("000096"), move("000097"), move("000098")
        };
    }

    /** A Blood Manipulation sorcerer with the kit's resource economy defined. */
    private static BattleCombatant kamo(Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).speed(85).combatAbility(90).strength(90).durability(80)
            .cursedEnergyReserves(300).cursedEnergyEfficiency(200).cursedEnergyOutput(200)
            .jujutsuSkill(150).cursedTechniqueMastery(150).build();
        SorcererCharacter c = new SorcererCharacter(
            "000020", "Kamo", stats, "Blood Manipulation", List.of(moves), List.of(),
            Equipment.base(WeaponType.BOW));
        BattleCombatant combatant = new BattleCombatant(c, List.of());
        combatant.defineBoundedResource("BLOOD_SUPPLY", "Blood Supply", 5, 5);
        combatant.defineBoundedResource("COMPRESSION", "Compression", 2, 0);
        return combatant;
    }

    /** The Flowing Red Scale speed row as it survives into the next planning phase. */
    private static AbilityEffectData flowingRedScaleSpeedRow() {
        AbilityEffectData effect = new AbilityEffectData();
        effect.type = AbilityEffectType.TIMED_STAT_MODIFIER.name();
        effect.stat = "speed";
        effect.statType = "CORE";
        effect.doubleValue = 0.2;
        effect.target = "SELF";
        effect.durationRounds = 1;
        return effect;
    }

    private Move move(String id) {
        return AIFixtures.canonicalMoveById(canonical, id);
    }

    private static BattleState state(BattleCombatant kamo, BattleCombatant enemy) {
        return new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(kamo)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(enemy)));
    }

    private static List<Move> loadMoves() {
        try {
            return AIFixtures.loadCanonicalMoves();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not load canonical moves", e);
        }
    }
}
