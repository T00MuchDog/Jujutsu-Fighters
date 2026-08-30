package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.move.Targeting;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.AttackLaunchMode;
import com.jjktbf.model.move.DefenseType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetExchangeEffectTest {

    @Test
    void selfAndEnemyExchangeRedirectsRangedAttackOntoAttackersAlly() {
        BattleCombatant todo = fighter("Todo", 200);
        BattleCombatant attacker = fighter("Attacker", 80);
        BattleCombatant replacement = fighter("Replacement", 60);
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(todo)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(attacker, replacement)));

        plan(todo, exchange(Targeting.SELF_AND_ENEMY, "RANGED"),
            replacement.getInstanceId());
        plan(attacker, attack("Ranged", MoveTag.RANGED), todo.getInstanceId());
        replacement.setTimeline(new BattlePlan(300, 300, 60).toLegacyTimeline());

        int todoHp = todo.getCurrentHp();
        int replacementHp = replacement.getCurrentHp();
        List<CombatEvent> events = resolve(state);

        assertEquals(todoHp, todo.getCurrentHp());
        assertTrue(replacement.getCurrentHp() < replacementHp);
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.TARGETS_EXCHANGED
                && event.getTarget() == todo
                && event.getRelatedTarget() == replacement));
    }

    @Test
    void allyAndEnemyExchangeUsesOrderedMixedPair() {
        BattleCombatant todo = fighter("Todo", 200);
        BattleCombatant ally = fighter("Ally", 100);
        BattleCombatant attacker = fighter("Attacker", 80);
        BattleCombatant replacement = fighter("Replacement", 60);
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(todo, ally)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(attacker, replacement)));

        BattlePlan todoPlan = new BattlePlan(300, 300, 60);
        todoPlan.placeWithTargets(exchange(
            Targeting.ALLY_AND_ENEMY, "MELEE"), 1, 0,
            List.of(ally.getInstanceId(), replacement.getInstanceId()));
        todo.setTimeline(todoPlan.toLegacyTimeline());
        ally.setTimeline(new BattlePlan(300, 300, 60).toLegacyTimeline());
        plan(attacker, attack("Melee", MoveTag.MELEE), ally.getInstanceId());
        replacement.setTimeline(new BattlePlan(300, 300, 60).toLegacyTimeline());

        int allyHp = ally.getCurrentHp();
        int replacementHp = replacement.getCurrentHp();
        resolve(state);

        assertEquals(allyHp, ally.getCurrentHp());
        assertTrue(replacement.getCurrentHp() < replacementHp);
    }

    @Test
    void scopeAndAoeFilteringDoNotConsumeWindow() {
        BattleCombatant owner = fighter("Owner", 100);
        BattleCombatant other = fighter("Other", 100);
        BattleCombatant attacker = fighter("Attacker", 100);
        assertTrue(new BattleState(owner, attacker).targetExchanges().register(
            owner, owner, other, "RANGED", 0, 10, 1));

        var registry = new BattleState(owner, attacker).targetExchanges();
        assertTrue(registry.register(owner, owner, other, "RANGED", 0, 10, 1));
        assertEquals(owner, registry.resolve(attacker, owner, attack("Melee", MoveTag.MELEE)).target());
        assertEquals(other, registry.resolve(attacker, owner, attack("Ranged", MoveTag.RANGED)).target());
    }

    @Test
    void referencedHybridExchangesOnlyItsFinalAttack() {
        BattleCombatant todo = fighter("Todo", 100);
        BattleCombatant replacement = fighter("Replacement", 100);
        BattleCombatant attacker = fighter("Attacker", 100);
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(todo, replacement)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(attacker)));
        assertTrue(state.targetExchanges().register(
            todo, todo, replacement, "RANGED", 0, 20, 2));

        Move launched = attack("Launched", MoveTag.RANGED);
        Move hybrid = new Move.Builder("HYBRID")
            .name("Hybrid").category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.DEFENSIVE, MoveTag.ATTACK, MoveTag.RANGED))
            .defenseType(DefenseType.BLOCK)
            .attackLaunchMode(AttackLaunchMode.ON_FIRE)
            .attackLaunchMoveId(launched.getId())
            .attackLaunchMove(launched)
            .apCost(2).unleashPoint(1)
            .build();
        todo.setTimeline(new BattlePlan(300, 300, 60).toLegacyTimeline());
        replacement.setTimeline(new BattlePlan(300, 300, 60).toLegacyTimeline());
        plan(attacker, hybrid, todo.getInstanceId());

        int todoHp = todo.getCurrentHp();
        int replacementHp = replacement.getCurrentHp();
        resolve(state);

        assertEquals(todoHp, todo.getCurrentHp());
        assertTrue(replacement.getCurrentHp() < replacementHp);
    }

    @Test
    void roundDurationCompletesBeforeTickTailExpires() {
        BattleCombatant owner = fighter("Owner", 100);
        BattleCombatant other = fighter("Other", 100);
        BattleCombatant attacker = fighter("Attacker", 100);
        var registry = new BattleState(owner, attacker).targetExchanges();
        Move ranged = attack("Ranged", MoveTag.RANGED);
        assertTrue(registry.register(owner, owner, other, "RANGED", 1, 2, -1));

        for (int i = 0; i < 20; i++) registry.advanceTick();
        assertEquals(other, registry.resolve(attacker, owner, ranged).target());
        registry.endRound();
        registry.advanceTick();
        assertEquals(other, registry.resolve(attacker, owner, ranged).target());
        registry.advanceTick();
        assertEquals(owner, registry.resolve(attacker, owner, ranged).target());
    }

    private static void plan(BattleCombatant actor, Move move, com.jjktbf.model.combat.CombatantId target) {
        BattlePlan plan = new BattlePlan(300, 300, 60);
        plan.place(move, 1, 0, target);
        actor.setTimeline(plan.toLegacyTimeline());
    }

    private static Move exchange(Targeting targeting, String scope) {
        MoveEffectData effect = AbilityEffectType.EXCHANGE_ATTACK_TARGETS.createDefaultMoveEffect();
        effect.trigger = MoveEffectTrigger.ON_FIRE.name();
        effect.moveTag = scope;
        effect.durationRounds = 0;
        effect.durationTicks = 20;
        effect.uses = 1;
        return new Move.Builder("EXCHANGE_" + targeting)
            .name("Exchange").category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(2).unleashPoint(1)
            .targeting(targeting)
            .effects(List.of(effect))
            .build();
    }

    private static Move attack(String name, MoveTag range) {
        return new Move.Builder(name.toUpperCase())
            .name(name).category(MoveCategory.PHYSICAL).neverMiss(true)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, range))
            .apCost(8).unleashPoint(5)
            .hitComponents(List.of(new HitComponent(
                80, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .build();
    }

    private static BattleCombatant fighter(String name, int speed) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(200).strength(100).durability(80).speed(speed)
            .combatAbility(100).build();
        return new BattleCombatant(new SorcererCharacter(
            name.toLowerCase(), name, stats, null, List.of()), List.of());
    }

    private static List<CombatEvent> resolve(BattleState state) {
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return new CombatResolver(new SeededRandomSource(1L)).resolveRound(state);
    }
}
