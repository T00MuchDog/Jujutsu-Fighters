package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectParameter;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.progression.TechniqueMasteryProgressionData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CeDrainOverTimeTest {

    @Test
    void instantDrainKeepsItsExistingImmediateBehavior() {
        AbilityEffectData drain = AbilityEffectType.DRAIN_CE.createDefault();
        drain.target = AbilityEffectTarget.ENEMY.name();
        drain.intValue = 7;
        assertNull(drain.durationTicks);

        BattleCombatant owner = combatant("OWNER", 80, List.of(activationAbility(drain)));
        BattleCombatant target = combatant("TARGET", 80, List.of());
        BattleState state = new BattleState(owner, target);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        int before = target.getCurrentCe();

        List<CombatEvent> events = resolve(new CombatResolver(new Random(0)), state);

        assertEquals(before - 7, target.getCurrentCe());
        assertFalse(AbilityEffectType.DRAIN_CE.uses(AbilityEffectParameter.DURATION, drain));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.CE_DRAINED
                && event.getSource() == owner && event.getTarget() == target
                && event.getIntValue() == 7));
    }

    @Test
    void overTimeDrainAppliesOncePerResolutionTickForItsDuration() {
        AbilityEffectData drain = overTimeDrain(5, 2);

        BattleCombatant owner = combatant("OWNER", 80, List.of(activationAbility(drain)));
        BattleCombatant target = combatant("TARGET", 80, List.of());
        BattleState state = new BattleState(owner, target);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        int before = target.getCurrentCe();

        List<CombatEvent> events = resolve(new CombatResolver(new Random(0)), state);

        assertEquals(before - 10, target.getCurrentCe());
        assertEquals(2, events.stream().filter(event ->
            event.getType() == CombatEvent.Type.CE_DRAINED
                && event.getSource() == owner && event.getTarget() == target
                && event.getIntValue() == 5).count());
    }

    @Test
    void overTimeDrainCanResolveItsAmountFromSourceCeEfficiency() {
        AbilityEffectData drain = overTimeDrain(1, 1);
        drain.ceEfficiencyProgression = Map.of(
            TechniqueMasteryProgressions.INT_VALUE, formula("max(1, cee / 10)"));
        assertNull(AbilityEffectType.DRAIN_CE.validationError(drain));

        BattleCombatant owner = combatant("OWNER", 100, List.of(activationAbility(drain)));
        BattleCombatant target = combatant("TARGET", 80, List.of());
        BattleState state = new BattleState(owner, target);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        int before = target.getCurrentCe();

        List<CombatEvent> events = resolve(new CombatResolver(new Random(0)), state);

        assertEquals(before - 10, target.getCurrentCe());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.CE_DRAINED
                && event.getSource() == owner && event.getTarget() == target
                && event.getIntValue() == 10));
    }

    @Test
    void moveAppliedDrainStartsOnTheFollowingTickAndGetsItsFullDuration() {
        MoveEffectData drain = AbilityEffectType.DRAIN_CE.createDefaultMoveEffect();
        drain.trigger = MoveEffectTrigger.ON_FIRE.name();
        drain.target = AbilityEffectTarget.ENEMY.name();
        drain.ceDrainMode = AbilityEffectType.CeDrainMode.OVER_TIME.name();
        drain.intValue = 5;
        drain.durationRounds = 0;
        drain.durationTicks = 1;
        Move move = utilityMove("MOVE_DRAIN", 1, drain);

        BattleCombatant owner = combatant("OWNER", 80, List.of(move), List.of());
        BattleCombatant target = combatant("TARGET", 80, List.of());
        BattleState state = new BattleState(owner, target);
        Timeline timeline = new Timeline(10);
        timeline.placeAt(move, 1, 0);
        owner.setTimeline(timeline);
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = resolve(new CombatResolver(new Random(0)), state);

        List<CombatEvent> drains = events.stream().filter(event ->
            event.getType() == CombatEvent.Type.CE_DRAINED
                && event.getSource() == owner && event.getTarget() == target
                && event.getIntValue() == 5).toList();
        assertEquals(1, drains.size());
        assertEquals(2, drains.get(0).getTick());
    }

    @Test
    void overTimeDrainWithRoundDurationExpiresAtRoundEnd() {
        AbilityEffectData drain = overTimeDrain(5, 0);
        drain.durationRounds = 1;
        Move move = utilityMove("ROUND_DURATION", 2);

        BattleCombatant owner = combatant(
            "OWNER", 80, List.of(move), List.of(activationAbility(drain)));
        BattleCombatant target = combatant("TARGET", 80, List.of());
        BattleState state = new BattleState(owner, target);
        Timeline timeline = new Timeline(10);
        timeline.placeAt(move, 1, 0);
        owner.setTimeline(timeline);
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = resolve(new CombatResolver(new Random(0)), state);

        assertEquals(2, events.stream().filter(event ->
            event.getType() == CombatEvent.Type.CE_DRAINED
                && event.getSource() == owner && event.getTarget() == target
                && event.getIntValue() == 5).count());
        new CombatResolver(new Random(0)).processRoundEnd(state);
        assertTrue(target.getOverTimeCeDrains().isEmpty());
    }

    private static AbilityEffectData overTimeDrain(int amount, int ticks) {
        AbilityEffectData drain = AbilityEffectType.DRAIN_CE.createDefault();
        drain.target = AbilityEffectTarget.ENEMY.name();
        drain.ceDrainMode = AbilityEffectType.CeDrainMode.OVER_TIME.name();
        drain.intValue = amount;
        drain.durationRounds = 0;
        drain.durationTicks = ticks;
        return drain;
    }

    private static Ability activationAbility(AbilityEffectData effect) {
        AbilityConditionData condition = AbilityConditionType.PHASE_REACHED.createDefault();
        condition.phase = BattleState.Phase.RESOLUTION.name();

        AbilityData data = new AbilityData();
        data.id = "DRAIN";
        data.name = "Drain";
        data.category = "ACTIVE";
        data.sourceType = "CHARACTER";
        data.activationCondition = condition;
        data.effects = List.of(effect);
        return new Ability(data);
    }

    private static BattleCombatant combatant(
        String id,
        int ceEfficiency,
        List<Ability> abilities
    ) {
        return combatant(id, ceEfficiency, List.of(), abilities);
    }

    private static BattleCombatant combatant(
        String id,
        int ceEfficiency,
        List<Move> moves,
        List<Ability> abilities
    ) {
        return new BattleCombatant(new SorcererCharacter(
            id, id,
            new CharacterStats.Builder().cursedEnergyEfficiency(ceEfficiency).build(),
            null, moves, abilities));
    }

    private static Move utilityMove(String id, int apCost, MoveEffectData... effects) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(apCost)
            .unleashPoint(1)
            .effects(List.of(effects))
            .build();
    }

    private static List<CombatEvent> resolve(CombatResolver resolver, BattleState state) {
        List<CombatEvent> events = new ArrayList<>(resolver.beginResolution(state));
        while (resolver.hasMoreTicks()) events.addAll(resolver.resolveTick(state));
        return events;
    }

    private static TechniqueMasteryProgressionData formula(String expression) {
        TechniqueMasteryProgressionData progression = new TechniqueMasteryProgressionData();
        progression.mode = TechniqueMasteryProgressionData.FORMULA;
        progression.formula = expression;
        return progression;
    }
}
