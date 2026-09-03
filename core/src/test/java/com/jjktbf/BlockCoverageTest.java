package com.jjktbf;

import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCoverageTest {

    @Test
    void rangeAndEveryElementMustBeCovered() {
        Move rangedFireBlock = block(
            "RANGED_FIRE", Set.of(MoveTag.RANGED), Set.of(MoveTag.FIRE), 100, List.of());
        Move allRangeFireBlock = block(
            "ALL_RANGE_FIRE", Set.of(MoveTag.MELEE, MoveTag.RANGED),
            Set.of(MoveTag.FIRE), 100, List.of());

        Move incoming = attack(
            "FIRE_REINFORCEMENT",
            MoveCategory.PHYSICAL_CURSED_ENERGY,
            MoveTag.RANGED,
            MoveTag.FIRE);
        assertTrue(rangedFireBlock.blocksAttack(incoming));
        assertFalse(rangedFireBlock.blocksAttack(attack(
            "MELEE_FIRE_REINFORCEMENT",
            MoveCategory.PHYSICAL_CURSED_ENERGY,
            MoveTag.MELEE,
            MoveTag.FIRE)));
        assertFalse(rangedFireBlock.blocksAttack(attack(
            "RANGED_ICE_REINFORCEMENT",
            MoveCategory.PHYSICAL_CURSED_ENERGY,
            MoveTag.RANGED,
            MoveTag.ICE)));
        assertTrue(allRangeFireBlock.blocksAttack(attack(
            "MELEE_FIRE_REINFORCEMENT",
            MoveCategory.PHYSICAL_CURSED_ENERGY,
            MoveTag.MELEE,
            MoveTag.FIRE)));
        assertFalse(allRangeFireBlock.blocksAttack(attack(
            "MULTI_ELEMENT_REINFORCEMENT",
            MoveCategory.PHYSICAL_CURSED_ENERGY,
            MoveTag.MELEE,
            MoveTag.FIRE,
            MoveTag.ICE)));

        Move fireParry = new Move.Builder("FIRE_PARRY")
            .name("Fire Parry")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.PARRY)
            .blockRanges(Set.of(MoveTag.RANGED))
            .blockElementalTags(Set.of(MoveTag.FIRE))
            .apCost(5)
            .unleashPoint(1)
            .build();
        assertTrue(fireParry.blocksAttack(incoming));
        assertFalse(fireParry.blocksAttack(attack(
            "RANGED_ICE_REINFORCEMENT",
            MoveCategory.PHYSICAL_CURSED_ENERGY,
            MoveTag.RANGED,
            MoveTag.ICE)));
    }

    @Test
    void fireConditionHalvesAnOtherwiseFullBlock() {
        MoveEffectData halfAgainstFire = blockMultiplier(0.5, MoveTag.FIRE);
        Move conditionalBlock = block(
            "CONDITIONAL_BLOCK", Set.of(), Set.of(), 100, List.of(halfAgainstFire));

        List<CombatEvent> fireEvents = resolve(
            conditionalBlock, attack("FIRE_ATTACK", MoveCategory.PHYSICAL, MoveTag.FIRE));
        List<CombatEvent> iceEvents = resolve(
            conditionalBlock, attack("ICE_ATTACK", MoveCategory.PHYSICAL, MoveTag.ICE));

        assertTrue(hasEvent(fireEvents, CombatEvent.Type.MOVE_BLOCK_REDUCED));
        assertFalse(hasEvent(fireEvents, CombatEvent.Type.MOVE_BLOCKED));
        assertTrue(hasEvent(iceEvents, CombatEvent.Type.MOVE_BLOCKED));
    }

    @Test
    void cursedEnergyConditionCanDoubleBlockEffectiveness() {
        MoveEffectData doubleAgainstCe = blockMultiplier(2.0, MoveTag.CURSED_ENERGY);
        Move conditionalBlock = block(
            "CE_CONDITIONAL_BLOCK", Set.of(), Set.of(), 50, List.of(doubleAgainstCe));

        List<CombatEvent> ceEvents = resolve(
            conditionalBlock, attack("CE_ATTACK", MoveCategory.CURSED_ENERGY));
        List<CombatEvent> physicalEvents = resolve(
            conditionalBlock, attack("PHYSICAL_ATTACK", MoveCategory.PHYSICAL));

        assertTrue(hasEvent(ceEvents, CombatEvent.Type.MOVE_BLOCKED));
        assertTrue(hasEvent(physicalEvents, CombatEvent.Type.MOVE_BLOCK_REDUCED));
    }

    private static MoveEffectData blockMultiplier(double multiplier, MoveTag incomingTag) {
        MoveEffectData effect =
            AbilityEffectType.BLOCK_EFFECTIVENESS_MULTIPLY.createDefaultMoveEffect();
        effect.trigger = MoveEffectTrigger.BLOCK_CALCULATION.name();
        effect.doubleValue = multiplier;
        AbilityConditionData condition = AbilityConditionType.INCOMING_HIT_HAS_TAG.createDefault();
        condition.moveTag = incomingTag.name();
        effect.condition = condition;
        return effect;
    }

    private static Move block(
        String id,
        Set<MoveTag> ranges,
        Set<MoveTag> elements,
        int reduction,
        List<MoveEffectData> effects
    ) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.BLOCK)
            .blockStyle(BlockStyle.PERCENTAGE)
            .blockRanges(ranges)
            .blockElementalTags(elements)
            .blockDamageReduction(reduction)
            .blockDuration(5)
            .apCost(5)
            .unleashPoint(1)
            .effects(effects)
            .build();
    }

    private static Move attack(String id, MoveCategory category, MoveTag... modifiers) {
        EnumSet<MoveTag> tags = EnumSet.copyOf(category.getTags());
        tags.addAll(List.of(modifiers));
        Move.Builder builder = new Move.Builder(id)
            .name(id)
            .category(category)
            .hitComponents(List.of(new HitComponent(100, tags, 0, false, true)))
            .neverMiss(true)
            .apCost(2)
            .unleashPoint(2);
        if (tags.contains(MoveTag.INNATE_TECHNIQUE)) {
            builder.requiredTechniqueId("TEST_TECHNIQUE")
                .prerequisites(java.util.Map.of("cursedTechniqueMastery", 0));
        }
        if (tags.contains(MoveTag.NON_INNATE_TECHNIQUE)) {
            java.util.Map<String, Integer> prerequisites =
                tags.contains(MoveTag.INNATE_TECHNIQUE)
                    ? java.util.Map.of("cursedTechniqueMastery", 0, "jujutsuSkill", 0)
                    : java.util.Map.of("jujutsuSkill", 0);
            builder.prerequisites(prerequisites);
        }
        return builder.build();
    }

    private static List<CombatEvent> resolve(Move block, Move attack) {
        CharacterStats attackerStats = new CharacterStats.Builder()
            .vitality(300).speed(80).build();
        CharacterStats defenderStats = new CharacterStats.Builder()
            .vitality(300).speed(120).build();
        Character attackerCharacter = new SorcererCharacter(
            "ATTACKER", "Attacker", attackerStats, null, List.of(attack));
        Character defenderCharacter = new SorcererCharacter(
            "DEFENDER", "Defender", defenderStats, null, List.of(block));
        BattleCombatant attacker = new BattleCombatant(attackerCharacter);
        BattleCombatant defender = new BattleCombatant(defenderCharacter);

        Timeline attackTimeline = new Timeline(10);
        attackTimeline.placeAt(attack, 1, 0).setTarget(defender.getInstanceId());
        Timeline blockTimeline = new Timeline(10);
        blockTimeline.placeAt(block, 1, 0);
        attacker.setTimeline(attackTimeline);
        defender.setTimeline(blockTimeline);

        BattleState state = new BattleState(attacker, defender);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return new CombatResolver(new FixedRandom()).resolveRound(state);
    }

    private static boolean hasEvent(List<CombatEvent> events, CombatEvent.Type type) {
        return events.stream().anyMatch(event -> event.getType() == type);
    }

    private static final class FixedRandom extends Random {
        @Override public double nextDouble() { return 0.0; }
        @Override public boolean nextBoolean() { return true; }
    }
}
