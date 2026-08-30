package com.jjktbf.controller;

import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartAIScoringTest {

    @Test
    void blockUsefulnessIsZeroWhenItCoversNoOpponentAttack() {
        BattleCombatant physicalOpp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("punch", 20, 10));
        OpponentIntel intel = OpponentIntel.forOpponent(physicalOpp);

        // A [CURSED_ENERGY]-only block cannot stop a purely physical attack.
        assertEquals(0.0, SmartAIScoring.blockUsefulness(
            AIFixtures.block("ceBlock", List.of("CURSED_ENERGY")), intel));
    }

    @Test
    void blockUsefulnessIsPositiveWhenItCoversAnOpponentAttack() {
        BattleCombatant physicalOpp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("punch", 20, 10));
        OpponentIntel intel = OpponentIntel.forOpponent(physicalOpp);

        assertTrue(SmartAIScoring.blockUsefulness(
            AIFixtures.block("phyBlock", List.of("PHYSICAL")), intel) > 0.0);
    }

    @Test
    void lowPotencyBlockIsRatedBelowOneThatCanContest() {
        Move potentAttack = AIFixtures.attack("heavy", 20, 10,
            java.util.Set.of(com.jjktbf.model.move.MoveTag.PHYSICAL,
                com.jjktbf.model.move.MoveTag.ATTACK, com.jjktbf.model.move.MoveTag.MELEE),
            false, 5); // potency 5
        BattleCombatant opp = AIFixtures.sorcerer("opp", potentAttack);
        OpponentIntel intel = OpponentIntel.forOpponent(opp);
        assertEquals(5, intel.maxAttackPotency);

        double weak = SmartAIScoring.blockUsefulness(
            AIFixtures.block("weak", List.of("PHYSICAL"), 1, 50), intel);
        double strong = SmartAIScoring.blockUsefulness(
            AIFixtures.block("strong", List.of("PHYSICAL"), 5, 50), intel);

        assertTrue(strong > weak, "a block that can contest the potency scores higher");
    }

    @Test
    void overReinforcedBlockIsDeWeightedVsAPhysicalOnlyOpponent() {
        BattleCombatant physicalOpp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("punch", 20, 10));
        OpponentIntel intel = OpponentIntel.forOpponent(physicalOpp);
        assertTrue(intel.physicalOnly);

        double minimal = SmartAIScoring.blockUsefulness(
            AIFixtures.block("phy", List.of("PHYSICAL")), intel);
        double reinforced = SmartAIScoring.blockUsefulness(
            AIFixtures.block("reinforced", List.of("PHYSICAL", "CURSED_ENERGY")), intel);

        assertTrue(minimal > reinforced,
            "vs a physical-only opponent, the minimal physical block is preferred over the reinforced one");
    }

    @Test
    void reinforcementAttackIsBoostedWhenOpponentBlocksArePhysicalOnly() {
        BattleCombatant physicalBlocker = AIFixtures.sorcerer("opp",
            AIFixtures.block("phy", List.of("PHYSICAL")),
            AIFixtures.meleeAttack("punch", 20, 10));
        OpponentIntel intel = OpponentIntel.forOpponent(physicalBlocker);
        assertTrue(intel.blocksPhysicalOnly);

        Move ceAttack = AIFixtures.ceAttack("ce", 20, 10);
        Move physical = AIFixtures.meleeAttack("phy", 20, 10);

        assertEquals(SmartAIScoring.REINFORCEMENT_BYPASS_BONUS,
            SmartAIScoring.reinforcementAttackMultiplier(ceAttack, intel));
        assertEquals(1.0,
            SmartAIScoring.reinforcementAttackMultiplier(physical, intel),
            "a physical attack gets no reinforcement bypass bonus");
    }

    @Test
    void reinforcementAttackIsNotBoostedWhenOpponentCanBlockCe() {
        BattleCombatant ceBlocker = AIFixtures.sorcerer("opp",
            AIFixtures.block("ce", List.of("PHYSICAL", "CURSED_ENERGY")),
            AIFixtures.meleeAttack("punch", 20, 10));
        OpponentIntel intel = OpponentIntel.forOpponent(ceBlocker);
        assertFalse(intel.blocksPhysicalOnly);

        assertEquals(1.0, SmartAIScoring.reinforcementAttackMultiplier(
            AIFixtures.ceAttack("ce", 20, 10), intel));
    }

    @Test
    void meleeAttackIsPenalisedByCommittedMeleeDodges() {
        Move meleeDodge = AIFixtures.dodge("mDodge", "MELEE");
        BattleCombatant opp = AIFixtures.sorcerer("opp", meleeDodge, AIFixtures.meleeAttack("p", 20, 10));
        AIFixtures.commitTimeline(opp, 60, meleeDodge);
        OpponentIntel intel = OpponentIntel.forOpponent(opp);
        assertEquals(1, intel.committedMeleeDodge);

        double meleeMult = SmartAIScoring.dodgeExposureMultiplier(AIFixtures.meleeAttack("m", 20, 10), intel);
        double rangedMult = SmartAIScoring.dodgeExposureMultiplier(AIFixtures.rangedAttack("r", 20, 10), intel);

        assertTrue(meleeMult < 1.0);
        assertTrue(rangedMult > meleeMult, "ranged is unaffected by a melee-only dodge");
    }

    @Test
    void reinforcementRequiresBothPhysicalAndCursedEnergy() {
        // "Reinforcement" = a PHYSICAL + CURSED_ENERGY strike, not any CE move.
        assertTrue(SmartAIScoring.isReinforcement(AIFixtures.ceAttack("rein", 20, 10)));
        assertFalse(SmartAIScoring.isReinforcement(AIFixtures.meleeAttack("phy", 20, 10)));
        assertFalse(SmartAIScoring.isReinforcement(AIFixtures.pureCeAttack("pureCe", 20, 10)));
    }

    @Test
    void effectMultiplierRewardsEffectRows() {
        assertEquals(SmartAIScoring.EFFECT_BONUS,
            SmartAIScoring.effectMultiplier(AIFixtures.meleeAttackWithEffect("e", 20, 10)));
        assertEquals(1.0,
            SmartAIScoring.effectMultiplier(AIFixtures.meleeAttack("plain", 20, 10)));
    }

    @Test
    void guardBreakOpponentDeValuesBlocks() {
        Move blk = AIFixtures.block("blk", List.of("PHYSICAL"));
        BattleCombatant gbOpp = AIFixtures.sorcerer("opp",
            AIFixtures.guardBreakAttack("gb", 20, 10));
        BattleCombatant plainOpp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("p", 20, 10));

        double vsGuardBreak = SmartAIScoring.defenseValue(blk, OpponentIntel.forOpponent(gbOpp));
        double vsPlain = SmartAIScoring.defenseValue(blk, OpponentIntel.forOpponent(plainOpp));

        assertTrue(vsGuardBreak < vsPlain, "a block is worth less when the attacker can guard-break");
    }

    @Test
    void weightedRandomPickFollowsTheWeights() {
        Move a = AIFixtures.meleeAttack("A", 10, 5);
        Move b = AIFixtures.meleeAttack("B", 10, 5);
        int bCount = 0;
        SeededRandomSource rng = new SeededRandomSource(7L);
        for (int i = 0; i < 400; i++) {
            Move picked = SmartAIScoring.weightedRandomPick(List.of(a, b), List.of(1.0, 3.0), rng);
            if (picked == b) bCount++;
        }
        // B is weighted 3x; expect roughly 300/400. Allow a wide band to avoid flakiness.
        assertTrue(bCount > 240 && bCount < 360, "B should dominate but not always: " + bCount);
    }

    @Test
    void weightedRandomTargetFavoursTargetsThatTakeMoreDamageWithoutGuaranteeingThem() {
        Move attack = AIFixtures.meleeAttack("attack", 100, 10);
        BattleCombatant attacker = AIFixtures.sorcerer("attacker", attack);
        BattleCombatant fragile = AIFixtures.sorcerer("fragile", true,
            new com.jjktbf.model.character.CharacterStats.Builder()
                .vitality(100).speed(80).combatAbility(80).strength(80).durability(40).build(),
            null);
        BattleCombatant durable = AIFixtures.sorcerer("durable", true,
            new com.jjktbf.model.character.CharacterStats.Builder()
                .vitality(100).speed(80).combatAbility(80).strength(80).durability(100).build(),
            null);
        assertTrue(SmartAIScoring.estimatedDamage(attack, attacker, fragile)
            > SmartAIScoring.estimatedDamage(attack, attacker, durable));

        int fragileCount = 0;
        int durableCount = 0;
        SeededRandomSource rng = new SeededRandomSource(19L);
        for (int i = 0; i < 500; i++) {
            BattleCombatant chosen = SmartAIScoring.weightedRandomTarget(
                attack, attacker, List.of(fragile, durable), rng);
            if (chosen == fragile) fragileCount++;
            if (chosen == durable) durableCount++;
        }

        assertTrue(fragileCount > durableCount,
            "the higher-damage target should be selected more often");
        assertTrue(durableCount > 0,
            "damage weighting must remain random rather than forcing the best target");
    }

    @Test
    void estimatedDamageIncludesElementalStatusMultipliers() {
        Move electric = elementalAttack("electric", MoveTag.ELECTRIC);
        Move melee = elementalAttack("melee", MoveTag.MELEE);
        BattleCombatant attacker = AIFixtures.sorcerer("attacker", electric, melee);
        BattleCombatant target = AIFixtures.sorcerer("target");

        int normalElectric = SmartAIScoring.estimatedDamage(electric, attacker, target);
        target.addStatusEffect(new StatusEffect(StatusEffectType.WET, 1, 0.0));
        int wetElectric = SmartAIScoring.estimatedDamage(electric, attacker, target);
        assertTrue(Math.abs(wetElectric - normalElectric * 2) <= 1);

        int normalMelee = SmartAIScoring.estimatedDamage(melee, attacker, target);
        attacker.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));
        int burnedMelee = SmartAIScoring.estimatedDamage(melee, attacker, target);
        assertTrue(Math.abs(burnedMelee * 2 - normalMelee) <= 1);
    }

    @Test
    void guaranteedKillOpeningIsRandomOnlyAmongLethalMoves() {
        Move nonLethal = AIFixtures.meleeAttack("non-lethal", 1, 10);
        Move lethalA = AIFixtures.meleeAttack("lethal-a", 300, 10);
        Move lethalB = AIFixtures.rangedAttack("lethal-b", 300, 10);
        BattleCombatant ai = AIFixtures.sorcerer("ai", nonLethal, lethalA, lethalB);
        BattleCombatant enemy = AIFixtures.sorcerer("enemy");
        enemy.receiveDamage(enemy.getCurrentHp() - 20);
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(enemy)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(ai)));
        SeededRandomSource rng = new SeededRandomSource(23L);
        int lethalACount = 0;
        int lethalBCount = 0;

        for (int i = 0; i < 200; i++) {
            BattlePlan original = new BattlePlan(ai.getMaxApBar(), ai.getCurrentCe(), 60);
            original.place(nonLethal, 1, ai.computeMoveCeCost(nonLethal));
            BattlePlan promoted = SmartAIScoring.promoteGuaranteedKillOpening(
                state, ai, original, rng);
            com.jjktbf.model.combat.ActionSegment opening = promoted.allSegments().stream()
                .min(java.util.Comparator.comparingInt(
                    com.jjktbf.model.combat.ActionSegment::getFireTick))
                .orElseThrow();

            assertFalse(opening.getMove() == nonLethal,
                "a non-lethal move must never remain the opening when a lethal move fits");
            assertEquals(List.of(enemy.getInstanceId()), opening.getTargets());
            if (opening.getMove() == lethalA) lethalACount++;
            if (opening.getMove() == lethalB) lethalBCount++;
        }

        assertTrue(lethalACount > 60 && lethalACount < 140,
            "lethal A should be chosen uniformly at random: " + lethalACount);
        assertTrue(lethalBCount > 60 && lethalBCount < 140,
            "lethal B should be chosen uniformly at random: " + lethalBCount);
    }

    private static Move elementalAttack(String id, MoveTag elementalTag) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.PHYSICAL))
            .hitComponents(List.of(new HitComponent(
                100, Set.of(MoveTag.PHYSICAL, elementalTag), 0, false, true)))
            .apCost(10)
            .unleashPoint(1)
            .build();
    }

    @Test
    void dispatcherPromotesAndTargetsGuaranteedKillAsTheFirstMove() {
        Move nonLethal = AIFixtures.meleeAttack("non-lethal", 1, 10);
        Move lethal = AIFixtures.meleeAttack("lethal", 300, 10);
        BattleCombatant ai = AIFixtures.sorcerer("000003", nonLethal, lethal);
        BattleCombatant enemy = AIFixtures.sorcerer("enemy");
        enemy.receiveDamage(enemy.getCurrentHp() - 20);
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(enemy)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(ai)));

        TeamBattlePlan teamPlan = new ArchetypeAIStrategy().selectTeamPlan(
            state, state.enemyTeam().active(), new SeededRandomSource(31L));
        BattlePlan plan = teamPlan.get(ai.getInstanceId());
        com.jjktbf.model.combat.ActionSegment opening = plan.allSegments().stream()
            .min(java.util.Comparator.comparingInt(
                com.jjktbf.model.combat.ActionSegment::getFireTick))
            .orElseThrow();

        assertTrue(opening.getMove() == lethal);
        assertEquals(List.of(enemy.getInstanceId()), opening.getTargets());
        assertTrue(plan.allSegments().stream()
            .filter(segment -> segment != opening)
            .allMatch(segment -> segment.getFireTick() > opening.getFireTick()));
    }
}
