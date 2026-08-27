package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursedSpeechWardTest {

    @Test
    void wardedDefenderAutomaticallyBlocksCommandsThatWouldOtherwiseSucceed() {
        Move command = command("DONT_MOVE", 95, 10);
        BattleCombatant inumaki = cursedSpeechUser(command);
        BattleCombatant target = fighter("TARGET", 80);
        target.addStatusEffect(new StatusEffect(
            StatusEffectType.CURSED_SPEECH_WARD, 0, 5, 0.0));
        BattleState state = new BattleState(inumaki, target);
        place(inumaki, command, List.of(target));

        int hpBefore = inumaki.getCurrentHp();
        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.0))
            .resolveRound(state);

        assertFalse(target.hasEffect(StatusEffectType.STAGGER),
            "the warded defender cannot be staggered by a command");
        assertTrue(events.stream().anyMatch(event ->
                event.getMessage().contains("cursed energy earplugs block")),
            "the block is narrated");
        assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.ABILITY_ACTIVATED
                    && event.getMove() == command
                    && event.getIntValue() == 0),
            "the reported command chance is 0% while warded");
        assertEquals(hpBefore - 10, inumaki.getCurrentHp(),
            "the speaker still pays recoil for speaking");
    }

    @Test
    void coverEarsMoveRaisesAWardForTwentyTicksAndDrainsScaledUpkeep() {
        Move cover = coverEars(1.5);
        BattleCombatant user = fighter("WARD_USER", 80, cover);
        BattleCombatant enemy = fighter("ENEMY", 80);
        BattleState state = new BattleState(user, enemy);
        place(user, cover, List.of());

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.0))
            .resolveRound(state);

        assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.STATUS_APPLIED
                    && event.getTarget() == user
                    && event.getTick() == 3),
            "the ward is applied on the move's third AP tick");
        int drained = events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.CE_DRAINED)
            .filter(event -> event.getSource() == user && event.getTarget() == user)
            .mapToInt(CombatEvent::getIntValue)
            .sum();
        assertEquals(30, drained,
            "1.5 upkeep per tick for 20 active ticks at neutral efficiency is 30 CE");
        assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.STATUS_EXPIRED
                    && event.getTarget() == user
                    && event.getTick() == 22),
            "a ward raised on tick 3 for 20 ticks expires on tick 22");
        assertFalse(user.hasEffect(StatusEffectType.CURSED_SPEECH_WARD));
    }

    @Test
    void upkeepScalesWithCursedEnergyEfficiency() {
        BattleCombatant efficient = fighter("EFFICIENT", 300, coverEars(1.5));
        BattleState efficientState = new BattleState(efficient, fighter("ENEMY", 80));
        place(efficient, coverEars(1.5), List.of());
        efficientState.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> efficientEvents = new CombatResolver(new SequenceRandom(0.0))
            .resolveRound(efficientState);

        // Raw efficiency 300 → scaled 472 → 0.2× upkeep: 1.5 × 0.2 × 20 = 6 CE.
        assertEquals(6, upkeepDrained(efficientEvents, efficient));

        BattleCombatant inefficient = fighter("INEFFICIENT", 10, coverEars(1.5));
        BattleState inefficientState = new BattleState(inefficient, fighter("ENEMY", 80));
        place(inefficient, coverEars(1.5), List.of());
        inefficientState.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> inefficientEvents = new CombatResolver(new SequenceRandom(0.0))
            .resolveRound(inefficientState);

        // Raw efficiency 10 → scaled 10 → 2.0× upkeep: 1.5 × 2.0 × 20 = 60 CE.
        assertEquals(60, upkeepDrained(inefficientEvents, inefficient));
    }

    @Test
    void wardCollapsesWhenItsUpkeepCannotBePaid() {
        Move cover = coverEars(100.0);
        BattleCombatant user = fighter("BROKE_USER", 80, cover);
        BattleCombatant enemy = fighter("ENEMY", 80);
        BattleState state = new BattleState(user, enemy);
        place(user, cover, List.of());

        user.drainCe(user.getCurrentCe() - 1);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.0))
            .resolveRound(state);

        assertFalse(user.hasEffect(StatusEffectType.CURSED_SPEECH_WARD),
            "an unpaid ward collapses instead of lingering for free");
        assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.STATUS_EXPIRED
                    && event.getTarget() == user
                    && event.getMessage().contains("collapses")),
            "the collapse is narrated");
        assertTrue(events.stream().noneMatch(event ->
                event.getType() == CombatEvent.Type.STATUS_EXPIRED
                    && event.getTarget() == user
                    && event.getMessage().isBlank()),
            "a collapsed ward does not also emit an ordinary expiry");
    }

    @Test
    void wardApplicationCarriesItsUpkeepThroughStatusTickdown() {
        StatusEffect ward = new StatusEffect(
            StatusEffectType.CURSED_SPEECH_WARD, 0, 4, 0.0, 0.0, 1.5);

        StatusEffect afterOneTick = ward.withDuration(0, 3);

        assertEquals(1.5, afterOneTick.getCeUpkeepPerTick());
        assertEquals(3, afterOneTick.getDurationTicks());
    }

    @Test
    void fractionalUpkeepCarriesItsRemainderAcrossTicks() {
        BattleCombatant combatant = fighter("ACCUMULATOR", 80);

        assertEquals(1, combatant.accrueStatusCeUpkeep(1.5), "1.5 → pays 1, owes 0.5");
        assertEquals(2, combatant.accrueStatusCeUpkeep(1.5), "3.0 total → pays 2, owes 0");
        assertEquals(1, combatant.accrueStatusCeUpkeep(1.5), "4.5 total → pays 1, owes 0.5");
        assertEquals(0, combatant.accrueStatusCeUpkeep(0.4), "0.9 total → pays nothing yet");
        assertEquals(0.9, combatant.getStatusCeUpkeepDebt(), 1.0e-9);
    }

    private static int upkeepDrained(List<CombatEvent> events, BattleCombatant payer) {
        return events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.CE_DRAINED)
            .filter(event -> event.getSource() == payer && event.getTarget() == payer)
            .mapToInt(CombatEvent::getIntValue)
            .sum();
    }

    /** The canonical Cover Ears shape: self ward on the third of six AP ticks. */
    private static Move coverEars(double upkeepPerTick) {
        MoveEffectData ward = AbilityEffectType.APPLY_STATUS.createDefaultMoveEffect();
        ward.effectId = "effect-000000";
        ward.trigger = MoveEffectTrigger.ON_FIRE.name();
        ward.target = AbilityEffectTarget.SELF.name();
        ward.stringValue = StatusEffectType.CURSED_SPEECH_WARD.name();
        ward.durationRounds = 0;
        ward.durationTicks = 20;
        ward.magnitude = 0.0;
        ward.ceUpkeepPerTick = upkeepPerTick;
        return new Move.Builder("COVER_EARS")
            .name("Cover Ears")
            .category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.CURSED_ENERGY, MoveTag.UTILITY))
            .basePower(0)
            .apCost(6)
            .unleashPoint(3)
            .effects(List.of(ward))
            .build();
    }

    private static Move command(String mode, int chance, int recoil) {
        StatusEffect command = StatusEffect.coded(
            CursedSpeechAbility.KEY,
            CursedSpeechAbility.COMMAND,
            mode,
            null,
            Map.of(
                CursedSpeechAbility.BASE_CHANCE_PERCENT, chance,
                CursedSpeechAbility.BASE_RECOIL, recoil),
            null);
        return new Move.Builder(mode)
            .name(mode)
            .category(MoveCategory.INNATE_TECHNIQUE)
            .tags(Set.of(MoveTag.INNATE_TECHNIQUE, MoveTag.CURSED_ENERGY, MoveTag.ATTACK))
            .basePower(0)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .aoeTargetCount(1)
            .requiredTechniqueId("Cursed Speech")
            .prerequisites(Map.of("cursedTechniqueMastery", 0))
            .onHitEffects(List.of(command))
            .build();
    }

    private static void place(BattleCombatant actor, Move move, List<BattleCombatant> targets) {
        Timeline timeline = new Timeline(60);
        assertNotNull(timeline.placeAt(move, 1, 0));
        actor.setTimeline(timeline);
        actor.getTimeline().getSegments().get(0)
            .setTargets(targets.stream().map(BattleCombatant::getInstanceId).toList());
    }

    private static BattleCombatant cursedSpeechUser(Move move) {
        Character character = new SorcererCharacter(
            "INUMAKI", "INUMAKI", stats(80), "Cursed Speech", List.of(move));
        return new BattleCombatant(character);
    }

    private static BattleCombatant fighter(String id, int cursedEnergyEfficiency, Move... moves) {
        Character character = new SorcererCharacter(
            id, id, stats(cursedEnergyEfficiency), null, List.of(moves));
        return new BattleCombatant(character);
    }

    private static CharacterStats stats(int cursedEnergyEfficiency) {
        return new CharacterStats.Builder()
            .vitality(80).speed(80).combatAbility(80)
            .cursedEnergyReserves(80).cursedEnergyEfficiency(cursedEnergyEfficiency)
            .cursedEnergyOutput(80)
            .jujutsuSkill(80).cursedTechniqueMastery(120)
            .build();
    }

    private static final class SequenceRandom implements RandomSource {
        private final ArrayDeque<Double> values = new ArrayDeque<>();
        private final double fallback;

        private SequenceRandom(double... values) {
            for (double value : values) this.values.add(value);
            this.fallback = 0.0;
        }

        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() {
            return values.isEmpty() ? fallback : values.removeFirst();
        }
        @Override public boolean nextBoolean() { return false; }
    }
}
