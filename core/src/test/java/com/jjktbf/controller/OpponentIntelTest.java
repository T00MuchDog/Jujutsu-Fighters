package com.jjktbf.controller;

import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpponentIntelTest {

    @Test
    void physicalOnlyWhenArsenalHasNoCursedEnergy() {
        BattleCombatant opp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("punch", 20, 10),
            AIFixtures.rangedAttack("kick", 20, 10));

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertTrue(intel.physicalOnly, "no CE attack => physical-only arsenal");
        assertFalse(intel.hasCursedEnergy);
        assertEquals(2, intel.attacks.size());
    }

    @Test
    void authoredReinforcementRemainsPhysicalOnlyUntilExecuted() {
        BattleCombatant opp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("punch", 20, 10),
            AIFixtures.ceAttack("ceFist", 30, 10));

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertTrue(intel.physicalOnly);
        assertFalse(intel.hasCursedEnergy);
    }

    @Test
    void pureCursedEnergyAttackAlsoMakesArsenalNonPhysical() {
        // A pure CE blast isn't "reinforcement", but it still means a physical-only
        // block won't cover everything the opponent can throw.
        BattleCombatant opp = AIFixtures.sorcerer("opp",
            AIFixtures.meleeAttack("punch", 20, 10),
            AIFixtures.pureCeAttack("blast", 30, 10));

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertFalse(intel.physicalOnly);
        assertTrue(intel.hasCursedEnergy);
    }

    @Test
    void guardBreakAndIntangibleFlagsComeFromAuthoredAttacks() {
        BattleCombatant opp = AIFixtures.sorcerer("opp",
            AIFixtures.guardBreakAttack("gb", 20, 10),
            AIFixtures.intangibleAttack("int", 20, 10));

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertTrue(intel.hasGuardBreak);
        assertTrue(intel.hasIntangible);
    }

    @Test
    void countsAvailableDefenseOptionsIndependentOfOpponentPlan() {
        Move meleeDodge = AIFixtures.dodge("mDodge", "MELEE");
        Move rangedDodge = AIFixtures.dodge("rDodge", "RANGED");
        Move bothDodge = AIFixtures.dodge("bDodge", "BOTH");
        Move blk = AIFixtures.block("blk", List.of("PHYSICAL"));
        Move parry = AIFixtures.parry("parry");
        BattleCombatant opp = AIFixtures.sorcerer(
            "opp", meleeDodge, rangedDodge, bothDodge, blk, parry);

        OpponentIntel beforePlan = OpponentIntel.forOpponent(opp);
        AIFixtures.commitTimeline(opp, 60, meleeDodge, rangedDodge, bothDodge, blk);
        OpponentIntel afterPlan = OpponentIntel.forOpponent(opp);

        assertEquals(2, beforePlan.availableMeleeDodge, "MELEE + BOTH");
        assertEquals(2, beforePlan.availableRangedDodge, "RANGED + BOTH");
        assertEquals(1, beforePlan.availableBlock);
        assertEquals(1, beforePlan.availableParry);
        assertEquals(beforePlan.availableMeleeDodge, afterPlan.availableMeleeDodge);
        assertEquals(beforePlan.availableRangedDodge, afterPlan.availableRangedDodge);
        assertEquals(beforePlan.availableBlock, afterPlan.availableBlock);
        assertEquals(beforePlan.availableParry, afterPlan.availableParry);
    }

    @Test
    void filtersUnavailableMovesFromAttackAndDefenseIntel() {
        Move affordableAttack = AIFixtures.meleeAttack("punch", 20, 10);
        Move tooMuchAp = AIFixtures.meleeAttack("tooMuchAp", 200, 61);
        Move tooMuchCe = ceAttack("tooMuchCe", 20, 10, 1);
        Move tooMuchApDodge = new Move.Builder("tooMuchApDodge")
            .name("tooMuchApDodge").category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.DEFENSIVE))
            .defenseType(com.jjktbf.model.move.DefenseType.DODGE)
            .dodgeScope("MELEE").dodgeChance(50).apCost(61).unleashPoint(1).build();
        BattleCombatant opp = AIFixtures.sorcerer(
            "opp", affordableAttack, tooMuchAp, tooMuchCe, tooMuchApDodge);
        opp.drainCe(opp.getCurrentCe());

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertEquals(List.of(affordableAttack), intel.attacks);
        assertEquals(0, intel.availableMeleeDodge);
    }

    @Test
    void anticipatesEfficientAttackUsesUpToThreeTimes() {
        Move efficient = AIFixtures.meleeAttack("efficient", 20, 10);
        Move inefficient = AIFixtures.meleeAttack("inefficient", 100, 60);
        BattleCombatant opp = AIFixtures.sorcerer("opp", efficient, inefficient);

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertEquals(List.of(1, 11, 21), intel.anticipatedAttackFireTicks);
    }

    @Test
    void anticipatedUsesRespectMoveCap() {
        Move capped = new Move.Builder("capped")
            .name("capped").category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE))
            .hitComponents(List.of(new HitComponent(
                20, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .apCost(10).unleashPoint(1).moveCap(2).build();
        BattleCombatant opp = AIFixtures.sorcerer("opp", capped);

        OpponentIntel intel = OpponentIntel.forOpponent(opp);

        assertEquals(List.of(1, 11), intel.anticipatedAttackFireTicks);
    }

    @Test
    void emptyIntelForNullOrCharacterlessOpponent() {
        assertEquals(0, OpponentIntel.forOpponent(null).attacks.size());
        assertEquals(0, OpponentIntel.forOpponent(null).availableBlock);
        assertFalse(OpponentIntel.forOpponent(null).physicalOnly);
    }

    private static Move ceAttack(String id, int basePower, int apCost, int ceCost) {
        return new Move.Builder(id)
            .name(id).category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE))
            .hitComponents(List.of(new HitComponent(
                basePower, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .baseCeCost(ceCost).hasCeCost(true)
            .minCeCost(ceCost).maxCeCost(ceCost)
            .apCost(apCost).unleashPoint(1).build();
    }
}
