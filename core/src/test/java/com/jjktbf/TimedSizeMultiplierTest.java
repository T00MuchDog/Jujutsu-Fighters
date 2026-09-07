package com.jjktbf;

import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimedSizeMultiplierTest {

    @Test
    void moveSizeMultiplierEmitsApplicationAndExpiryEvents() {
        MoveEffectData size = AbilityEffectType.TIMED_SIZE_MULTIPLIER.createDefaultMoveEffect();
        size.effectId = "effect-000000";
        size.doubleValue = 0.38;
        size.target = AbilityEffectTarget.SELF.name();
        size.durationRounds = 0;
        size.durationTicks = 2;
        size.trigger = MoveEffectTrigger.ON_FIRE.name();
        size.condition = AbilityConditionData.always();
        AbilityEffectType.TIMED_SIZE_MULTIPLIER.prepare(size);

        Move miniature = new Move.Builder("MINIATURE")
            .name("Miniature")
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(2)
            .unleashPoint(1)
            .effects(List.of(size))
            .build();
        BattleCombatant fighter = combatant("FIGHTER", List.of(miniature));
        BattleCombatant enemy = combatant("ENEMY", List.of());
        BattleState state = new BattleState(fighter, enemy);
        Timeline timeline = new Timeline(6);
        timeline.placeAt(miniature, 1, 0);
        fighter.setTimeline(timeline);
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> scaleEvents = new CombatResolver(new SeededRandomSource(1L))
            .resolveRound(state).stream()
            .filter(event -> event.getType() == CombatEvent.Type.SIZE_MULTIPLIER_CHANGED)
            .toList();

        assertEquals(List.of(38, 100), scaleEvents.stream()
            .map(CombatEvent::getIntValue).toList());
        assertEquals(List.of(1, 2), scaleEvents.stream().map(CombatEvent::getTick).toList());
        assertEquals(miniature, scaleEvents.get(0).getMove());
        assertNull(scaleEvents.get(1).getMove());
        assertEquals(1.0, fighter.getSizeMultiplier(), 0.000001);
    }

    @Test
    void sizeMultiplierMustBePositive() {
        MoveEffectData size = AbilityEffectType.TIMED_SIZE_MULTIPLIER.createDefaultMoveEffect();
        assertNull(AbilityEffectType.TIMED_SIZE_MULTIPLIER.validationError(size));

        size.doubleValue = 0.0;

        assertEquals("Size multiplier must be greater than 0.",
            AbilityEffectType.TIMED_SIZE_MULTIPLIER.validationError(size));
    }

    private static BattleCombatant combatant(String id, List<Move> moves) {
        return new BattleCombatant(new SorcererCharacter(
            id, id, new CharacterStats.Builder().build(), null, moves, List.of()));
    }
}
