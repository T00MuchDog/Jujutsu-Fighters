package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.ReinforcementAbility;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleStatMode;
import com.jjktbf.model.combat.CeEfficiencyCalculator;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.ReinforcementDefenseType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementMechanicsTest {

    @Test
    void reinforcedHitIsAnExecutionOnlyView() {
        HitComponent authored = eligiblePhysicalHit(40, 15);

        HitComponent reinforced = authored.reinforced();

        assertEquals(55, reinforced.getBasePower());
        assertTrue(reinforced.hasTag(MoveTag.CURSED_ENERGY));
        assertTrue(reinforced.isActivelyReinforced());
        assertTrue(reinforced.isBlackFlashEligible());
        assertSame(authored, reinforced.getAuthoredComponent());
        assertEquals(40, authored.getBasePower());
        assertFalse(authored.hasTag(MoveTag.CURSED_ENERGY));
        assertFalse(authored.isActivelyReinforced());

        HitComponent ineligible = new HitComponent(
            40, Set.of(MoveTag.PHYSICAL), 0, false, true);
        assertSame(ineligible, ineligible.reinforced());
    }

    @Test
    void moveDataRoundTripPreservesReinforcementAuthoring() {
        MoveData data = new MoveData();
        data.id = "TEST";
        data.name = "Test";
        data.tags = new ArrayList<>(List.of("ATTACK", "PHYSICAL"));
        data.apCost = 5;
        data.unleashPoint = 1;
        data.canBeReinforced = true;
        data.reinforcementBaseCeCost = 20;
        data.reinforcementMinCeCost = 4;
        data.reinforcementMaxCeCost = 60;
        data.reinforcementDefenseType = ReinforcementDefenseType.FLAT_BLOCK.name();
        data.reinforcementDefenseValue = 7;
        MoveData.HitComponentData hit = new MoveData.HitComponentData();
        hit.basePower = 30;
        hit.tags = List.of("PHYSICAL", "MELEE");
        hit.reinforcementEligible = true;
        hit.reinforcementBonusPower = 12;
        data.hitComponents = new ArrayList<>(List.of(hit));

        Move move = data.toMove();
        MoveData restored = MoveData.fromMove(move);

        assertTrue(move.canBeReinforced());
        assertEquals(20, restored.reinforcementBaseCeCost);
        assertEquals(4, restored.reinforcementMinCeCost);
        assertEquals(60, restored.reinforcementMaxCeCost);
        assertEquals(ReinforcementDefenseType.FLAT_BLOCK.name(), restored.reinforcementDefenseType);
        assertEquals(7, restored.reinforcementDefenseValue);
        assertTrue(restored.hitComponents.get(0).reinforcementEligible);
        assertEquals(12, restored.hitComponents.get(0).reinforcementBonusPower);
    }

    @Test
    void reinforcementCostIsIndependentAndRequiresTheCapability() {
        Move move = reinforcementMove();
        BattleCombatant withoutCapability = combatant(move, List.of());
        BattleCombatant withCapability = combatant(move, List.of(reinforcementAbility()));

        assertEquals(20, CeEfficiencyCalculator.computeReinforcementCost(
            move, 80, 80, null, BattleStatMode.STANDARD));
        assertFalse(withoutCapability.canReinforce(move));
        assertEquals(0, withoutCapability.computeReinforcementCeCost(move));
        assertTrue(withCapability.canReinforce(move));
        assertEquals(20, withCapability.computeReinforcementCeCost(move));
    }

    @Test
    void statTotalWaiverUsesTheReinforcementBaseCost() {
        Move move = reinforcementMove();
        AbilityEffectData waiver = AbilityEffectType.CE_COST_WAIVE_BY_STAT_TOTAL.createDefault();
        waiver.intValue = 100;
        AbilityData waiverData = new AbilityData();
        waiverData.id = "WAIVER";
        waiverData.name = "Waiver";
        waiverData.effects = List.of(waiver);
        BattleCombatant combatant = combatant(
            move, List.of(reinforcementAbility(), new Ability(waiverData)));

        assertEquals(20, combatant.computeReinforcementCeCost(move),
            "an intrinsic base cost of zero must not waive a larger reinforcement surcharge");
    }

    @Test
    void planTracksAndRefundsTheCombinedCost() {
        Move move = reinforcementMove();
        BattlePlan plan = new BattlePlan(10, 100, 10);

        ActionSegment segment = plan.placeWithTargets(
            move, 1, 25, List.of(), true, 20);

        assertNotNull(segment);
        assertTrue(segment.isReinforced());
        assertEquals(25, plan.totalCeUsed());
        assertEquals(20, segment.getReinforcementCeCost());
        assertEquals(5, segment.getIntrinsicCeCost());
        assertTrue(segment.effectiveHitComponent(0).isActivelyReinforced());
        assertTrue(plan.remove(segment));
        assertEquals(0, plan.totalCeUsed());
    }

    @Test
    void defensiveReinforcementAppliesOnlyItsAuthoredMode() {
        ActionSegment percentage = reinforcedDefense(
            BlockStyle.PERCENTAGE, 60, 0,
            ReinforcementDefenseType.PERCENTAGE_BLOCK, 50, 0);
        ActionSegment flat = reinforcedDefense(
            BlockStyle.FLAT, 100, 25,
            ReinforcementDefenseType.FLAT_BLOCK, 10, 0);
        ActionSegment parry = reinforcedDefense(
            BlockStyle.PERCENTAGE, 100, 0,
            ReinforcementDefenseType.STAGGER_LENGTH, 4, 3);

        assertEquals(100, percentage.effectiveBlockDamageReduction());
        assertEquals(0, percentage.effectiveBlockFlatReduction());
        assertEquals(35, flat.effectiveBlockFlatReduction());
        assertEquals(100, flat.effectiveBlockDamageReduction());
        assertEquals(7, parry.effectiveParryStaggerTicks());
    }

    private static ActionSegment reinforcedDefense(
        BlockStyle style,
        int percentage,
        int flat,
        ReinforcementDefenseType reinforcementType,
        int reinforcementValue,
        int parryStagger
    ) {
        DefenseType defenseType = parryStagger > 0 ? DefenseType.PARRY : DefenseType.BLOCK;
        Move move = new Move.Builder("DEFENSE-" + reinforcementType)
            .name("Defense")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(defenseType)
            .blockStyle(style)
            .blockDamageReduction(percentage)
            .blockFlatReduction(flat)
            .parryStaggerTicks(parryStagger)
            .canBeReinforced(true)
            .reinforcementCeCosts(10, 1, 20)
            .reinforcementDefense(reinforcementType, reinforcementValue)
            .apCost(5)
            .unleashPoint(1)
            .build();
        return new ActionSegment(move, 1, 10, List.of(), true, 10);
    }

    private static Move reinforcementMove() {
        return new Move.Builder("REINFORCEABLE")
            .name("Reinforceable")
            .category(MoveCategory.PHYSICAL)
            .hitComponents(List.of(eligiblePhysicalHit(30, 10)))
            .canBeReinforced(true)
            .reinforcementCeCosts(20, 4, 60)
            .apCost(5)
            .unleashPoint(1)
            .build();
    }

    private static HitComponent eligiblePhysicalHit(int power, int bonus) {
        return new HitComponent(power, Set.of(MoveTag.PHYSICAL), 0, false, true,
            HitComponent.INHERIT_MOVE_ACCURACY, List.of(), true, bonus);
    }

    private static Ability reinforcementAbility() {
        AbilityData data = new AbilityData();
        data.id = ReinforcementAbility.ID;
        data.name = ReinforcementAbility.NAME;
        return new Ability(data);
    }

    private static BattleCombatant combatant(Move move, List<Ability> abilities) {
        Character character = new SorcererCharacter(
            "TEST", "Test", new CharacterStats.Builder().build(), null,
            List.of(move), abilities);
        return new BattleCombatant(character);
    }
}
