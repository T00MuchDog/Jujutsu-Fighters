package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.DamageCalculator;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccuracyPriorityTest {

    @Test
    void neverMissWinsEqualNeverHitTierAndHigherNeverHitWins() {
        Move tierOne = attack("TIER_ONE", 1);

        assertTrue(resolve(
            combatant("ATTACKER", tierOne),
            combatant("EQUAL", null, priorityAbility(AbilityEffectType.NEVER_HIT, 1)),
            tierOne).isHit(), "Never Miss wins an equal-tier contest");

        assertTrue(resolve(
            combatant("ATTACKER", tierOne),
            combatant("HIGHER", null, priorityAbility(AbilityEffectType.NEVER_HIT, 2)),
            tierOne).isMiss(), "Never Hit must be higher to stop Never Miss");
    }

    @Test
    void domainStyleTierFourOverridesTierThreeNeverHit() {
        Move tierFour = attack("SURE_HIT", 4);
        BattleCombatant defender = combatant(
            "INFINITY", null, priorityAbility(AbilityEffectType.NEVER_HIT, 3));

        assertTrue(resolve(combatant("ATTACKER", tierFour), defender, tierFour).isHit());
    }

    @Test
    void passiveNeverHitStopsUnrankedAndLowerTierAttacks() {
        Ability infinity = priorityAbility(AbilityEffectType.NEVER_HIT, 3);
        BattleCombatant defender = combatant("INFINITY", null, infinity);

        Move unranked = attack("UNRANKED", 0);
        assertTrue(resolve(combatant("A", unranked), defender, unranked).isMiss());

        Move tierTwo = attack("TIER_TWO", 2);
        assertTrue(resolve(combatant("B", tierTwo), defender, tierTwo).isMiss());
    }

    @Test
    void neverHitStillContestsAComponentThatSkipsOrdinaryAccuracy() {
        HitComponent unavoidable = new HitComponent(
            10, Set.of(MoveTag.PHYSICAL), 0, false, false, 0.01, List.of());
        Move move = new Move.Builder("UNAVOIDABLE")
            .name("Unavoidable")
            .category(MoveCategory.PHYSICAL)
            .hitComponents(List.of(unavoidable))
            .apCost(1)
            .unleashPoint(1)
            .build();
        BattleCombatant defender = combatant(
            "DEFENDER", null, priorityAbility(AbilityEffectType.NEVER_HIT, 1));

        assertTrue(DamageCalculator.resolve(
            combatant("ATTACKER", move), defender, move,
            1, new FixedRandom(0.0), 1).isMiss());
    }

    @Test
    void tierOneNeverMissIgnoresNormalAndEqualTierDodges() {
        Move tierOne = attack("CURSED_SPEECH_STYLE", 1);

        BattleCombatant normalDodge = combatantWithDodge(dodge("NORMAL_DODGE", 0));
        assertTrue(resolve(combatant("A", tierOne), normalDodge, tierOne).isHit());

        BattleCombatant equalDodge = combatantWithDodge(dodge("EQUAL_DODGE", 1));
        assertTrue(resolve(combatant("B", tierOne), equalDodge, tierOne).isHit());

        BattleCombatant higherDodge = combatantWithDodge(dodge("HIGHER_DODGE", 2));
        assertTrue(resolve(combatant("C", tierOne), higherDodge, tierOne).isDodged());
    }

    @Test
    void unrankedMovesKeepOrdinaryDodgeAndAccuracyBehavior() {
        Move unranked = attack("UNRANKED", 0);
        assertTrue(resolve(
            combatant("A", unranked),
            combatantWithDodge(dodge("NORMAL_DODGE", 0)),
            unranked).isDodged());

        BattleCombatant plainDefender = combatant("PLAIN", null);
        assertTrue(resolve(combatant("B", unranked), plainDefender, unranked).isMiss(),
            "without priorities, the ordinary accuracy roll remains authoritative");
    }

    @Test
    void multiplePriorityEffectsUseTheHighestTierInsteadOfAdding() {
        Move tierFour = attack("TIER_FOUR", 4);
        Ability twoTierTwos = priorityAbility(
            AbilityEffectType.NEVER_HIT, 2, 2);

        assertTrue(resolve(
            combatant("ATTACKER", tierFour),
            combatant("DEFENDER", null, twoTierTwos),
            tierFour).isHit(), "two tier-2 effects must not combine into tier 4");
    }

    @Test
    void passiveNeverMissContributesToTheAttackPriority() {
        Move unranked = attack("ABILITY_SURE_HIT", 0);
        BattleCombatant attacker = combatant(
            "ATTACKER", unranked, priorityAbility(AbilityEffectType.NEVER_MISS, 4));
        BattleCombatant defender = combatant(
            "DEFENDER", null, priorityAbility(AbilityEffectType.NEVER_HIT, 3));

        assertTrue(resolve(attacker, defender, unranked).isHit());
    }

    @Test
    void legacyNeverMissBooleanLoadsAndMigratesAsTierOne() {
        Move legacyMove = new Move.Builder("LEGACY")
            .name("Legacy")
            .category(MoveCategory.PHYSICAL)
            .basePower(10)
            .baseAccuracy(0.01)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .build();
        assertTrue(legacyMove.isNeverMiss());
        assertEquals(0, legacyMove.getNeverMissTier(),
            "the compatibility boolean is not an authored priority tier");

        MoveData legacyData = new MoveData();
        legacyData.tags = new ArrayList<>(List.of("ATTACK", "PHYSICAL"));
        legacyData.neverMiss = true;
        legacyData.effects = new ArrayList<>();

        assertTrue(legacyData.migrateLegacyNeverMissTier());
        assertFalse(legacyData.neverMiss);
        assertEquals(1, legacyData.getNeverMissTier());
        assertEquals(1, legacyData.effects.stream()
            .filter(effect -> AbilityEffectType.NEVER_MISS.name().equals(effect.type))
            .count());
    }

    @Test
    void priorityTierValidationUsesThePotencyScale() {
        AbilityEffectData invalid = AbilityEffectType.NEVER_MISS.createDefault();
        invalid.intValue = 6;
        assertEquals("Accuracy priority tier must be between 1 and 5.",
            AbilityEffectType.NEVER_MISS.validationError(invalid));
    }

    @Test
    void appliedNeverMissTierCanLastForOneAttackOrADuration() {
        Move unranked = attack("UNRANKED", 0);
        BattleCombatant defender = combatant("DEFENDER", null);

        BattleCombatant oneAttack = combatant("ONE_ATTACK", unranked);
        oneAttack.addRuntimeAbilityEffect(appliedTier(
            AbilityEffectType.APPLY_NEVER_MISS, 3,
            AbilityEffectType.AccuracyDuration.NEXT_ATTACK));
        assertTrue(resolve(oneAttack, defender, unranked).isHit());
        assertTrue(resolve(oneAttack, defender, unranked).isMiss(),
            "the next-attack tier must be consumed by one attack");

        BattleCombatant duration = combatant("DURATION", unranked);
        duration.addRuntimeAbilityEffect(appliedTier(
            AbilityEffectType.APPLY_NEVER_MISS, 3,
            AbilityEffectType.AccuracyDuration.DURATION));
        assertTrue(resolve(duration, defender, unranked).isHit());
        assertTrue(resolve(duration, defender, unranked).isHit(),
            "a duration tier remains active until its timer expires");
    }

    @Test
    void tierZeroAppliedNeverMissCanScopeNormalAccuracyWithoutDefeatingDodges() {
        Move bow = scopedAttack("BOW", MoveTag.BOW);
        Move melee = scopedAttack("MELEE", MoveTag.MELEE);
        BattleCombatant attacker = combatant("ATTACKER", null);
        AbilityEffectData guidance = appliedTier(
            AbilityEffectType.APPLY_NEVER_MISS, 0,
            AbilityEffectType.AccuracyDuration.DURATION);
        guidance.moveTag = MoveTag.BOW.name();
        guidance.durationRounds = 2;
        attacker.addRuntimeAbilityEffect(guidance);

        assertTrue(resolve(attacker, combatant("PLAIN", null), bow).isHit(),
            "matching attacks skip their normal accuracy roll");
        assertTrue(resolve(attacker, combatant("PLAIN_2", null), melee).isMiss(),
            "non-matching attacks keep their normal accuracy roll");
        assertTrue(resolve(attacker, combatantWithDodge(dodge("DODGE", 0)), bow).isDodged(),
            "tier zero must not gain priority over an ordinary dodge");

        attacker.tickRuntimeAbilityEffects(1);
        assertTrue(resolve(attacker, combatant("ROUND_2", null), bow).isHit());
        attacker.tickRuntimeAbilityEffects(2);
        assertTrue(resolve(attacker, combatant("EXPIRED", null), bow).isMiss());
    }

    @Test
    void appliedNeverHitTierIsConsumedByTheNextIncomingAttack() {
        Move tierTwo = attack("TIER_TWO", 2);
        BattleCombatant attacker = combatant("ATTACKER", tierTwo);
        BattleCombatant defender = combatant("DEFENDER", null);
        defender.addRuntimeAbilityEffect(appliedTier(
            AbilityEffectType.APPLY_NEVER_HIT, 3,
            AbilityEffectType.AccuracyDuration.NEXT_ATTACK));

        assertTrue(resolve(attacker, defender, tierTwo).isMiss());
        assertTrue(resolve(attacker, defender, tierTwo).isHit());
    }

    @Test
    void migratedMultiUseGuaranteePreservesEveryUse() {
        Move unranked = attack("MIGRATED_USES", 0);
        BattleCombatant attacker = combatant("ATTACKER", unranked);
        BattleCombatant defender = combatant("DEFENDER", null);
        AbilityEffectData legacy = new AbilityEffectData();
        legacy.type = "GUARANTEE_NEXT_HIT";
        legacy.uses = 2;
        legacy.durationRounds = -1;
        assertTrue(legacy.migrateLegacyType());
        attacker.addRuntimeAbilityEffect(legacy);

        assertTrue(resolve(attacker, defender, unranked).isHit());
        assertTrue(resolve(attacker, defender, unranked).isHit());
        assertTrue(resolve(attacker, defender, unranked).isMiss());
    }

    private static DamageCalculator.DamageResult resolve(
        BattleCombatant attacker,
        BattleCombatant defender,
        Move move
    ) {
        return DamageCalculator.resolve(attacker, defender, move, 1, new FixedRandom(0.99), 1);
    }

    private static Move attack(String id, int tier) {
        List<MoveEffectData> effects = tier == 0
            ? List.of() : List.of(priorityMoveEffect(AbilityEffectType.NEVER_MISS, tier));
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .basePower(10)
            .baseAccuracy(0.01)
            .apCost(1)
            .unleashPoint(1)
            .effects(effects)
            .build();
    }

    private static Move dodge(String id, int tier) {
        List<MoveEffectData> effects = tier == 0
            ? List.of() : List.of(priorityMoveEffect(AbilityEffectType.NEVER_HIT, tier));
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.DODGE)
            .dodgeChance(100)
            .apCost(10)
            .unleashPoint(1)
            .effects(effects)
            .build();
    }

    private static Move scopedAttack(String id, MoveTag scope) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, scope))
            .basePower(10)
            .baseAccuracy(0.01)
            .apCost(1)
            .unleashPoint(1)
            .build();
    }

    private static MoveEffectData priorityMoveEffect(AbilityEffectType type, int tier) {
        MoveEffectData effect = type.createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.intValue = tier;
        effect.trigger = MoveEffectTrigger.ACCURACY_CHECK.name();
        effect.condition = com.jjktbf.model.character.AbilityConditionData.always();
        return effect;
    }

    private static Ability priorityAbility(AbilityEffectType type, int... tiers) {
        AbilityData data = new AbilityData();
        data.id = type.name() + "_ABILITY";
        data.name = type.displayName();
        data.category = "PASSIVE";
        data.sourceType = "CHARACTER";
        data.effects = new ArrayList<>();
        for (int index = 0; index < tiers.length; index++) {
            AbilityEffectData effect = type.createDefault();
            effect.effectId = String.format("effect-%06d", index);
            effect.intValue = tiers[index];
            data.effects.add(effect);
        }
        return new Ability(data);
    }

    private static AbilityEffectData appliedTier(
        AbilityEffectType type,
        int tier,
        AbilityEffectType.AccuracyDuration duration
    ) {
        AbilityEffectData effect = type.createDefault();
        effect.intValue = tier;
        effect.accuracyDuration = duration.name();
        if (duration == AbilityEffectType.AccuracyDuration.DURATION) {
            effect.durationRounds = 1;
            effect.durationTicks = 0;
        }
        return effect;
    }

    private static BattleCombatant combatant(String id, Move move, Ability... abilities) {
        Character character = new SorcererCharacter(
            id,
            id,
            new CharacterStats.Builder().build(),
            null,
            move == null ? List.of() : List.of(move),
            List.of(abilities));
        return new BattleCombatant(character);
    }

    private static BattleCombatant combatantWithDodge(Move dodge) {
        BattleCombatant defender = combatant("DODGER", dodge);
        Timeline timeline = new Timeline(30);
        timeline.placeAt(dodge, 1, 0);
        defender.setTimeline(timeline);
        return defender;
    }

    private static final class FixedRandom extends Random {
        private final double value;

        private FixedRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }
}
