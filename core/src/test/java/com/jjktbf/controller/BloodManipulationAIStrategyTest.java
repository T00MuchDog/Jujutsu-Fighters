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
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural tests for the code-composed Blood Manipulation planner. */
class BloodManipulationAIStrategyTest {

    private final BloodManipulationAIStrategy strategy = new BloodManipulationAIStrategy();

    @Test
    void equallyStrongFreePressureBeatsSpendingTheRemainingCe() {
        Move free = physicalAttack("free", 50, 10, List.of());
        Move costly = new Move.Builder("costly").name("costly").category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK)).basePower(50)
            .apCost(10).unleashPoint(1).hasCeCost(true)
            .baseCeCost(50).minCeCost(50).maxCeCost(50).build();
        BattleCombatant ai = c(free, costly);
        ai.drainCe(ai.getCurrentCe() - 50);
        for (int seed = 0; seed < 20; seed++) {
            BattlePlan plan = strategy.selectPlan(ai, durableOpponent(), new SeededRandomSource(seed));
            assertEquals(0, plan.selectedUses(costly));
            assertTrue(plan.selectedUses(free) > 2, "keep pressing beyond the old two-attack cap");
        }
    }

    @Test
    void worthwhileSurgeIsUsedProactivelyAndFollowedByAttacks() {
        Move boost = utility("surge", 1, List.of(coreBoost("boost", MoveEffectTrigger.ON_FIRE)));
        Move attack = physicalAttack("attack", 40, 12, List.of());
        BattleCombatant ai = c(boost, attack);
        BattlePlan plan = strategy.selectPlan(ai, durableOpponent(), new SeededRandomSource(1));
        ActionSegment surge = segmentFor(plan, "surge");
        assertNotNull(surge, "an efficient buff need not wait for either fighter to be dying");
        assertTrue(plan.selectedUses(attack) >= 2);
        assertTrue(plan.allSegments().stream().filter(s -> s.getMove() == attack)
            .allMatch(s -> s.getFireTick() > surge.getFireTick()));
    }

    @Test
    void onFireConversionCompletesBeforeAConsumerStarts() {
        MoveEffectData transaction = transaction("conversion", MoveEffectTrigger.ON_FIRE,
            "BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        Move converter = new Move.Builder("delayed").name("delayed").category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY)).apCost(12).unleashPoint(10)
            .effects(List.of(transaction)).build();
        Move shot = compressionAttack("shot", 150, 10, 0);
        BattleCombatant ai = c(converter, shot);
        BattlePlan plan = strategy.selectPlan(ai, durableOpponent(), new SeededRandomSource(1));
        assertTrue(segmentFor(plan, "shot").getStartTick() > segmentFor(plan, "delayed").getFireTick());
    }

    @Test
    void slowDefenseIsNotBoughtForAnUncatchableOpening() {
        Move enemyAttack = new Move.Builder("fast").name("fast").category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK)).basePower(20)
            .apCost(10).unleashPoint(1).moveCap(1).build();
        BattleCombatant enemy = AIFixtures.sorcerer("faster", true, AIFixtures.strongStats(), null, enemyAttack);
        BattleCombatant ai = c(physicalAttack("poke", 15, 10, List.of()),
            AIFixtures.block("block", List.of("PHYSICAL")));
        BattlePlan plan = strategy.selectPlan(ai, enemy, new SeededRandomSource(1));
        assertEquals(0, plan.allSegments().stream().filter(s -> s.getMove().isActiveDefense()).count());
    }

    @Test
    void offenseUsesTheApMajorityAndAtMostOneDefenseAcrossTwentySeeds() {
        Move poke = physicalAttack("poke", 18, 8, List.of());
        Move compressionAttack = compressionAttack("compression", 90, 18, 0);
        Move defense = AIFixtures.block("block", List.of("PHYSICAL"), 1, 80, 8);
        BattleCombatant ai = c(poke, compressionAttack, defense);
        BattleCombatant opponent = opponent(physicalAttack("enemy-punch", 25, 12, List.of()));

        for (long seed = 1; seed <= 20; seed++) {
            BattlePlan plan = strategy.selectPlan(ai, opponent, new SeededRandomSource(seed));
            int attackAp = plan.allSegments().stream()
                .filter(segment -> segment.getMove().hasTag("ATTACK"))
                .mapToInt(ActionSegment::getApCost).sum();
            int defenseAp = plan.allSegments().stream()
                .filter(segment -> segment.getMove().isActiveDefense())
                .mapToInt(ActionSegment::getApCost).sum();
            long defenses = plan.allSegments().stream()
                .filter(segment -> segment.getMove().isActiveDefense()).count();

            assertTrue(attackAp > plan.totalApUsed() / 2,
                "seed " + seed + " should spend an AP majority on offense");
            assertTrue(defenses <= 1,
                "seed " + seed + " should keep at most one defensive layer");
            assertTrue(attackAp > defenseAp,
                "seed " + seed + " should spend more AP attacking than defending");
        }
    }

    @Test
    void loadedNonlethalCompressionIsSpentRatherThanHoarded() {
        Move poke = physicalAttack("poke", 8, 8, List.of());
        Move compressionAttack = compressionAttack("loaded-shot", 85, 18, 0);
        BattleCombatant ai = c(poke, compressionAttack);
        ai.transactBoundedResources("BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        BattleCombatant opponent = durableOpponent(physicalAttack("enemy-punch", 20, 12, List.of()));

        BattlePlan plan = strategy.selectPlan(ai, opponent, new SeededRandomSource(17L));

        assertTrue(plan.allSegments().stream()
                .anyMatch(segment -> segment.getMove().getId().equals("loaded-shot")),
            "a loaded, nonlethal compression shot should be used this round");
        assertTrue(plan.allSegments().stream()
                .anyMatch(segment -> spends(segment.getMove(), "COMPRESSION")),
            "the selected plan must actually spend compression");
    }

    @Test
    void conversionMakesACompressionFinisherAvailableInTheSameRound() {
        Move conversion = conversion("convert", "BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        Move poke = physicalAttack("free-poke", 5, 8, List.of());
        // This is deliberately much more efficient than the free poke so that
        // converting is justified by the follow-through, not by setup alone.
        Move finisher = compressionAttack("converted-finisher", 150, 20, 0);
        BattleCombatant ai = c(conversion, poke, finisher);
        BattleCombatant opponent = durableOpponent(physicalAttack("enemy-punch", 20, 12, List.of()));

        BattlePlan plan = strategy.selectPlan(ai, opponent, new SeededRandomSource(3L));

        ActionSegment setup = segmentFor(plan, "convert");
        ActionSegment shot = segmentFor(plan, "converted-finisher");
        assertNotNull(setup, "the purposeful conversion should be committed");
        assertNotNull(shot, "the finisher should be available after conversion");
        assertTrue(setup.getFireTick() < shot.getFireTick(),
            "conversion must resolve chronologically before the finisher");
        assertNull(MoveAvailability.boundedResourcePlanRestrictionReason(
            ai, chronologicalMoves(plan)),
            "conversion followed by its finisher must be resource-valid");
    }

    @Test
    void conversionIsNotCommittedWithoutAUsefulFinisherOrBudgetRoom() {
        Move conversion = conversion("convert", "BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        Move poke = physicalAttack("free-poke", 8, 8, List.of());
        BattleCombatant opponent = durableOpponent(physicalAttack("enemy-punch", 20, 12, List.of()));

        BattlePlan withoutFinisher = strategy.selectPlan(
            c(conversion, poke), opponent, new SeededRandomSource(2L));
        assertFalse(hasMove(withoutFinisher, "convert"),
            "conversion without a compression consumer is useless");

        Move expensiveFinisher = compressionAttack("too-expensive", 150, 20, 0, 100);
        BattleCombatant ceStarved = c(conversion, poke, expensiveFinisher);
        ceStarved.drainCe(ceStarved.getCurrentCe());
        BattlePlan withoutCe = strategy.selectPlan(
            ceStarved, opponent, new SeededRandomSource(2L));
        assertFalse(hasMove(withoutCe, "convert"),
            "conversion must not be committed when its finisher cannot pay CE");

        Move tooWide = compressionAttack("too-wide", 150, 100, 0);
        BattlePlan withoutAp = strategy.selectPlan(
            c(conversion, poke, tooWide), opponent, new SeededRandomSource(2L));
        assertFalse(hasMove(withoutAp, "convert"),
            "conversion must not be committed when its finisher cannot fit AP");
    }

    @Test
    void chronologicalResourceValidationAcceptsScarceMultiRowSetupAndAttackCosts() {
        Move setup = utility("multi-setup", 6, List.of(
            transaction("setup-blood", MoveEffectTrigger.ON_START,
                "BLOOD_SUPPLY", 1, "COMPRESSION", 1),
            transaction("setup-token", MoveEffectTrigger.ON_START,
                "TOKEN", 1, null, 0)));
        Move attack = physicalAttack("multi-attack", 70, 18, List.of(
            transaction("attack-compression", MoveEffectTrigger.ON_START,
                "COMPRESSION", 1, null, 0),
            transaction("attack-blood", MoveEffectTrigger.ON_START,
                "BLOOD_SUPPLY", 1, null, 0)));
        BattleCombatant ai = c(setup, attack);
        ai.defineBoundedResource("TOKEN", "Token", 1, 1);

        ai.transactBoundedResources("BLOOD_SUPPLY", 3, null, 0);
        BattlePlan plan = strategy.selectPlan(ai, durableOpponent(), new SeededRandomSource(3L));
        assertTrue(hasMove(plan, "multi-setup"));
        assertTrue(hasMove(plan, "multi-attack"));
        assertEquals(1, plan.selectedUses(attack));
        assertNull(MoveAvailability.boundedResourcePlanRestrictionReason(ai, chronologicalMoves(plan)));
        assertEquals(2, ai.boundedResourceValue("BLOOD_SUPPLY").orElseThrow());
        assertEquals(0, ai.boundedResourceValue("COMPRESSION").orElseThrow());
        assertEquals(1, ai.boundedResourceValue("TOKEN").orElseThrow());
        assertNotNull(MoveAvailability.boundedResourcePlanRestrictionReason(
            ai, List.of(attack, setup)),
            "the same rows in reverse chronology must not be accepted");
    }

    @Test
    void planningDoesNotMutateOwnRuntimeResources() {
        Move poke = physicalAttack("poke", 12, 8, List.of());
        Move shot = compressionAttack("shot", 70, 18, 0);
        BattleCombatant ai = c(poke, shot);
        ai.transactBoundedResources("BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        int blood = ai.boundedResourceValue("BLOOD_SUPPLY").orElseThrow();
        int compression = ai.boundedResourceValue("COMPRESSION").orElseThrow();
        int ce = ai.getCurrentCe();

        strategy.selectPlan(ai,
            durableOpponent(physicalAttack("enemy-punch", 20, 12, List.of())),
            new SeededRandomSource(9L));

        assertEquals(blood, ai.boundedResourceValue("BLOOD_SUPPLY").orElseThrow());
        assertEquals(compression, ai.boundedResourceValue("COMPRESSION").orElseThrow());
        assertEquals(ce, ai.getCurrentCe());
    }

    @Test
    void loadedLethalFinisherFiresFirstWithoutBuffDelay() {
        Move lethal = compressionAttack("lethal", 300, 20, 1);
        Move surge = utility("surge", 8, List.of(coreBoost("surge-boost", MoveEffectTrigger.ON_FIRE)));
        BattleCombatant ai = c(lethal, surge);
        ai.transactBoundedResources("BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        BattleCombatant opponent = opponent();
        opponent.applyDamage(Math.max(0, opponent.getCurrentHp() - 1));

        BattlePlan plan = strategy.selectPlan(ai, opponent, new SeededRandomSource(4L));

        ActionSegment firstAttack = plan.allSegments().stream()
            .filter(segment -> segment.getMove().hasTag("ATTACK"))
            .min(Comparator.comparingInt(ActionSegment::getFireTick))
            .orElseThrow();
        assertEquals("lethal", firstAttack.getMove().getId());
        assertEquals(1, firstAttack.getStartTick(),
            "an affordable lethal shot must not wait for a setup buff");
        assertFalse(hasMove(plan, "surge"), "the buff must not delay a loaded kill");
    }

    @Test
    void activeNeverMissDoesNotBuyARedundantNeverMissAttackOverAnEqualBow() {
        Move bow = physicalAttack("free-bow", 55, 12,
            List.of(), MoveTag.RANGED, MoveTag.BOW);
        Move redundant = physicalAttack("nevermiss-shot", 55, 12,
            List.of(neverMiss("nevermiss-row", MoveEffectTrigger.ON_FIRE)),
            MoveTag.RANGED);
        Move accuracySetup = utility("accuracy-setup", 8,
            List.of(accuracyBoost("accuracy-row", MoveEffectTrigger.ON_FIRE)));
        BattleCombatant ai = c(bow, redundant, accuracySetup);
        ai.addRuntimeAbilityEffect(activeNeverMiss(), 1, BattleState.Phase.PLANNING);

        for (long seed = 1; seed <= 20; seed++) {
            BattlePlan plan = strategy.selectPlan(
                ai, durableOpponent(physicalAttack("enemy-punch", 20, 12, List.of())),
                new SeededRandomSource(seed));
            assertTrue(plan.allSegments().stream()
                    .filter(segment -> segment.getMove().hasTag("ATTACK"))
                    .allMatch(segment -> segment.getMove().getId().equals("free-bow")),
                "seed " + seed + " should use the equally efficient free bow");
            assertFalse(hasMove(plan, "accuracy-setup"),
                "an active Never Miss window makes accuracy setup redundant");
        }
    }

    @Test
    void dispatcherRoutesBloodAndIgnoresOpponentCommittedTimelineUnderTheSameSeed() {
        Move conversion = conversion("convert", "BLOOD_SUPPLY", 1, "COMPRESSION", 1);
        Move poke = physicalAttack("poke", 14, 8, List.of());
        Move finisher = compressionAttack("finisher", 120, 18, 0);

        BattleCombatant aiAtStart = c(conversion, poke, finisher);
        BattleCombatant aiAtEnd = c(conversion, poke, finisher);
        BattleCombatant opponentAtStart = opponent(physicalAttack("enemy-attack", 25, 12, List.of()));
        BattleCombatant opponentAtEnd = opponent(physicalAttack("enemy-attack", 25, 12, List.of()));
        setCommittedPlan(opponentAtStart, opponentAtStart.getCharacter().getKnownMoves().get(0), 1);
        setCommittedPlan(opponentAtEnd, opponentAtEnd.getCharacter().getKnownMoves().get(0), 40);

        ArchetypeAIStrategy dispatcher = new ArchetypeAIStrategy();
        TeamBattlePlan first = dispatcher.selectTeamPlan(
            state(aiAtStart, opponentAtStart), List.of(aiAtStart), new SeededRandomSource(21L));
        TeamBattlePlan second = dispatcher.selectTeamPlan(
            state(aiAtEnd, opponentAtEnd), List.of(aiAtEnd), new SeededRandomSource(21L));
        BattlePlan firstPlan = first.get(aiAtStart.getInstanceId());
        BattlePlan secondPlan = second.get(aiAtEnd.getInstanceId());
        aiAtStart.setPlan(firstPlan);
        aiAtEnd.setPlan(secondPlan);

        assertTrue(firstPlan.allSegments().stream()
                .anyMatch(segment -> segment.getMove().getId().equals("convert")
                    || segment.getMove().getId().equals("finisher")),
            "the dispatcher must route a Blood Manipulation character to its planner");
        assertEquals(signature(firstPlan), signature(secondPlan),
            "hidden opponent timeline commitments must not affect same-seed planning");
        assertNotNull(aiAtStart.getPlan());
        assertNotNull(aiAtEnd.getPlan());
    }

    // --- Hand-authored move and combatant fixtures ---------------------------

    private static BattleCombatant c(Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).speed(100).combatAbility(100).strength(120).durability(100)
            .cursedEnergyReserves(300).cursedEnergyEfficiency(300).cursedEnergyOutput(300)
            .jujutsuSkill(150).cursedTechniqueMastery(150).build();
        SorcererCharacter character = new SorcererCharacter(
            "blood-ai", "Blood AI", stats, "Blood Manipulation", List.of(moves), List.of(),
            Equipment.base(WeaponType.BOW));
        BattleCombatant combatant = new BattleCombatant(character, List.of());
        combatant.defineBoundedResource("BLOOD_SUPPLY", "Blood Supply", 5, 5);
        combatant.defineBoundedResource("COMPRESSION", "Compression", 2, 0);
        return combatant;
    }

    private static BattleCombatant opponent(Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).speed(90).combatAbility(90).strength(90).durability(100)
            .cursedEnergyReserves(200).cursedEnergyEfficiency(200).cursedEnergyOutput(200)
            .jujutsuSkill(100).cursedTechniqueMastery(100).build();
        SorcererCharacter character = new SorcererCharacter(
            "opponent", "Opponent", stats, null, List.of(moves), List.of(),
            Equipment.base(WeaponType.KATANA));
        return new BattleCombatant(character, List.of());
    }

    private static BattleCombatant durableOpponent(Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(300).speed(90).combatAbility(90).strength(90).durability(300)
            .cursedEnergyReserves(200).cursedEnergyEfficiency(200).cursedEnergyOutput(200)
            .jujutsuSkill(100).cursedTechniqueMastery(100).build();
        SorcererCharacter character = new SorcererCharacter(
            "durable-opponent", "Durable Opponent", stats, null, List.of(moves), List.of(),
            Equipment.base(WeaponType.KATANA));
        return new BattleCombatant(character, List.of());
    }

    private static Move physicalAttack(String id, int power, int ap, List<MoveEffectData> effects,
                                       MoveTag... extraTags) {
        Set<MoveTag> tags = new java.util.HashSet<>(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK));
        tags.addAll(Set.of(extraTags));
        return new Move.Builder(id)
            .name(id).category(MoveCategory.PHYSICAL).tags(tags)
            .basePower(power).apCost(ap).unleashPoint(1).effects(effects).build();
    }

    private static Move compressionAttack(String id, int power, int ap, int moveCap) {
        return compressionAttack(id, power, ap, moveCap, 0);
    }

    private static Move compressionAttack(String id, int power, int ap, int moveCap, int ceCost) {
        return new Move.Builder(id)
            .name(id).category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .basePower(power).baseCeCost(ceCost).hasCeCost(ceCost > 0)
            .minCeCost(ceCost).maxCeCost(ceCost)
            .apCost(ap).unleashPoint(1)
            .moveCap(moveCap)
            .effects(List.of(transaction(id + "-compression", MoveEffectTrigger.ON_START,
                "COMPRESSION", 1, null, 0)))
            .build();
    }

    private static Move utility(String id, int ap, List<MoveEffectData> effects) {
        return new Move.Builder(id)
            .name(id).category(MoveCategory.UTILITY).tags(Set.of(MoveTag.UTILITY))
            .apCost(ap).unleashPoint(1).effects(effects).build();
    }

    private static Move conversion(String id, String source, int sourceAmount,
                                   String target, int targetAmount) {
        return utility(id, 6, List.of(transaction(id + "-conversion", MoveEffectTrigger.ON_START,
            source, sourceAmount, target, targetAmount)));
    }

    private static MoveEffectData transaction(String id, MoveEffectTrigger trigger,
                                              String source, int sourceAmount,
                                              String target, int targetAmount) {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = id;
        effect.trigger = trigger.name();
        effect.target = "SELF";
        effect.sourceResourceKey = source;
        effect.sourceResourceAmount = sourceAmount;
        effect.targetResourceKey = target;
        effect.targetResourceAmount = targetAmount;
        return effect;
    }

    private static MoveEffectData coreBoost(String id, MoveEffectTrigger trigger) {
        MoveEffectData effect = AbilityEffectType.TIMED_STAT_MODIFIER.createDefaultMoveEffect();
        effect.effectId = id;
        effect.trigger = trigger.name();
        effect.target = "SELF";
        effect.stat = "strength";
        effect.statType = "CORE";
        effect.statOperation = "CHANGE";
        effect.valueMode = "PERCENT";
        effect.doubleValue = 0.20;
        effect.durationRounds = 1;
        return effect;
    }

    private static MoveEffectData accuracyBoost(String id, MoveEffectTrigger trigger) {
        MoveEffectData effect = coreBoost(id, trigger);
        effect.stat = "accuracy";
        effect.stringValue = "ACCURACY";
        effect.statType = "BATTLE";
        return effect;
    }

    private static MoveEffectData neverMiss(String id, MoveEffectTrigger trigger) {
        MoveEffectData effect = AbilityEffectType.APPLY_NEVER_MISS.createDefaultMoveEffect();
        effect.effectId = id;
        effect.trigger = trigger.name();
        effect.target = "SELF";
        effect.intValue = 5;
        effect.accuracyDuration = "DURATION";
        effect.durationRounds = 1;
        return effect;
    }

    private static AbilityEffectData activeNeverMiss() {
        AbilityEffectData effect = AbilityEffectType.APPLY_NEVER_MISS.createDefault();
        effect.target = "SELF";
        effect.intValue = 5;
        effect.accuracyDuration = "DURATION";
        effect.durationRounds = 1;
        return effect;
    }

    private static ActionSegment segmentFor(BattlePlan plan, String id) {
        return plan.allSegments().stream()
            .filter(segment -> segment.getMove().getId().equals(id)).findFirst().orElse(null);
    }

    private static boolean hasMove(BattlePlan plan, String id) {
        return segmentFor(plan, id) != null;
    }

    private static boolean spends(Move move, String resource) {
        return move.getEffects().stream()
            .filter(effect -> AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.name()
                .equalsIgnoreCase(effect.type))
            .anyMatch(effect -> resource.equalsIgnoreCase(effect.sourceResourceKey)
                && effect.sourceResourceAmount != null && effect.sourceResourceAmount > 0);
    }

    private static List<Move> chronologicalMoves(BattlePlan plan) {
        return plan.allSegments().stream()
            .sorted(Comparator.comparingInt(ActionSegment::getStartTick))
            .map(ActionSegment::getMove).toList();
    }

    private static void setCommittedPlan(BattleCombatant combatant, Move move, int startTick) {
        int grid = Math.max(80, Timeline.gridLengthForStrongestAp(combatant.getMaxApBar()));
        BattlePlan plan = new BattlePlan(combatant.getMaxApBar(), combatant.getCurrentCe(), grid);
        assertNotNull(plan.place(move, startTick, combatant.computeMoveCeCost(move)));
        combatant.setPlan(plan);
        combatant.setTimeline(plan.toLegacyTimeline());
    }

    private static BattleState state(BattleCombatant ai, BattleCombatant opponent) {
        return new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(ai)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(opponent)));
    }

    private static List<String> signature(BattlePlan plan) {
        return plan.allSegments().stream()
            .sorted(Comparator.comparingInt(ActionSegment::getStartTick)
                .thenComparingInt(ActionSegment::getFireTick))
            .map(segment -> segment.getMove().getId() + "@" + segment.getStartTick()
                + ":" + segment.getFireTick() + ":" + segment.getActualCeCost())
            .toList();
    }
}
