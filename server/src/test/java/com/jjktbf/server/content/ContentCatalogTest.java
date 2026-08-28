package com.jjktbf.server.content;

import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterType;
import com.jjktbf.model.combat.AbilityActivationEngine;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.move.AttackLaunchMode;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.MoveType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentCatalogTest {
    @Test
    void loadsCanonicalClasspathDefinitions() {
        ContentCatalog catalog = ContentCatalog.load();

        assertFalse(catalog.findCharacter("missing").isPresent());
        assertThrows(UnsupportedOperationException.class,
            () -> catalog.characterSummaries().clear());
    }

    @Test
    void loadsAuthoredOnDefenceHybridWithResolvedLaunchMove() {
        MoveData hybrid = new MoveData();
        hybrid.id = "000100";
        hybrid.name = "Authored Riposte";
        hybrid.tags = List.of(
            MoveTag.DEFENSIVE.name(), MoveTag.ATTACK.name(), MoveTag.PHYSICAL.name());
        hybrid.defenseType = DefenseType.BLOCK.name();
        hybrid.blockDuration = 10;
        hybrid.apCost = 2;
        hybrid.unleashPoint = 1;
        hybrid.attackLaunchMode = AttackLaunchMode.ON_DEFENCE.name();
        hybrid.attackLaunchMoveId = "000101";

        MoveData launch = new MoveData();
        launch.id = "000101";
        launch.name = "Authored Counter";
        launch.tags = List.of(MoveTag.ATTACK.name(), MoveTag.PHYSICAL.name());
        launch.apCost = 2;
        launch.unleashPoint = 1;
        MoveData.HitComponentData hit = new MoveData.HitComponentData();
        hit.basePower = 25;
        hit.tags = List.of(MoveTag.PHYSICAL.name());
        launch.hitComponents = List.of(hit);

        CharacterData character = new CharacterData();
        character.id = "000200";
        character.name = "Catalog Fighter";
        character.moveIds = List.of(hybrid.id);

        ContentCatalog catalog = ContentCatalog.build(
            List.of(hybrid, launch), List.of(character), List.of(), List.of(), List.of());

        Move loaded = catalog.findCharacter(character.id).orElseThrow().getKnownMoves().get(0);
        assertEquals(AttackLaunchMode.ON_DEFENCE, loaded.getAttackLaunchMode());
        assertEquals(launch.id, loaded.getAttackLaunchMoveId());
        assertNotNull(loaded.getAttackLaunchMove());
        assertEquals(launch.id, loaded.getAttackLaunchMove().getId());
    }

    @Test
    void loadsPandaAndHiddenGorillaCoreComposition() {
        ContentCatalog catalog = ContentCatalog.load();
        var panda = catalog.findCharacter("000004").orElseThrow();
        var gorilla = catalog.findCharacter("000021").orElseThrow();

        assertEquals(CharacterType.CURSED_CORPSE, panda.getType());
        assertEquals(CharacterType.CURSED_CORPSE, gorilla.getType());
        assertTrue(catalog.findSelectableCharacter("000004").isPresent());
        assertFalse(catalog.findSelectableCharacter("000021").isPresent());
        assertNull(panda.getInnateTechniqueName());
        assertTrue(new BattleCombatant(panda).isPoisonImmune());
        assertTrue(new BattleCombatant(gorilla).isPoisonImmune());
        assertTrue(panda.getLearnedMoves().stream()
            .filter(move -> !"000085".equals(move.getId()))
            .allMatch(move -> move.getMoveType() == MoveType.SORCERER));
        assertEquals(MoveType.SHIKIGAMI, panda.getLearnedMoves().stream()
            .filter(move -> "000085".equals(move.getId()))
            .findFirst().orElseThrow().getMoveType());
        assertEquals(MoveType.SHIKIGAMI, gorilla.getKnownMoves().stream()
            .filter(move -> "000087".equals(move.getId()))
            .findFirst().orElseThrow().getMoveType());
        assertTrue(gorilla.getAbilities().stream()
            .anyMatch(ability -> "000038".equals(ability.getId())));
    }

    @Test
    void pandaAutomaticallyUsesOnlyASurvivingCore() {
        ContentCatalog catalog = ContentCatalog.load();
        BattleCombatant panda = new BattleCombatant(
            catalog.findCharacter("000004").orElseThrow());
        BattleCombatant enemy = new BattleCombatant(
            catalog.findCharacter("000000").orElseThrow());
        BattleState state = new BattleState(panda, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(
            new SeededRandomSource(1L), catalog::findCharacter);

        panda.receiveDamage(panda.getCurrentHp());
        var gorillaEvents = engine.process(state, AbilityTrigger.amount(
            AbilityTrigger.Type.DAMAGE, enemy, panda, 1, 1));

        assertEquals("000021", panda.getCharacter().getId());
        assertEquals(panda.getMaxHp(), panda.getCurrentHp());
        assertTrue(gorillaEvents.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.CHARACTER_TRANSFORMED));

        panda.receiveDamage(panda.getCurrentHp());
        var failedReturn = engine.process(state, AbilityTrigger.amount(
            AbilityTrigger.Type.DAMAGE, enemy, panda, 1, 2));

        assertEquals("000021", panda.getCharacter().getId());
        assertEquals(0, panda.getCurrentHp());
        assertTrue(failedReturn.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.EFFECT_FAILED));
        assertTrue(failedReturn.stream().noneMatch(event ->
            event.getType() == CombatEvent.Type.CHARACTER_REVERTED));
    }

    @Test
    void myBestFriendBuffsOnlyPairedTodoAndYujiBearers() {
        ContentCatalog catalog = ContentCatalog.load();
        BattleCombatant todo = new BattleCombatant(
            catalog.findCharacter("000006").orElseThrow());
        BattleCombatant yuji = new BattleCombatant(
            catalog.findCharacter("000010").orElseThrow());
        BattleCombatant enemy = new BattleCombatant(
            catalog.findCharacter("000000").orElseThrow());
        BattleState paired = new BattleState(
            BattleState.teamOfFighters(com.jjktbf.model.combat.BattleTeamId.PLAYER,
                List.of(todo, yuji)),
            BattleState.teamOfFighters(com.jjktbf.model.combat.BattleTeamId.ENEMY,
                List.of(enemy)));
        int todoBase = todo.getEffectiveStats().getCombatAbility();
        int yujiBase = yuji.getEffectiveStats().getCombatAbility();

        new CombatResolver(new SeededRandomSource(1L)).processRoundStart(paired);

        assertTrue(todo.getEffectiveStats().getCombatAbility() > todoBase);
        assertTrue(yuji.getEffectiveStats().getCombatAbility() > yujiBase);

        BattleCombatant soloTodo = new BattleCombatant(
            catalog.findCharacter("000006").orElseThrow());
        BattleState solo = new BattleState(soloTodo, new BattleCombatant(
            catalog.findCharacter("000000").orElseThrow()));
        int soloBase = soloTodo.getEffectiveStats().getCombatAbility();
        new CombatResolver(new SeededRandomSource(1L)).processRoundStart(solo);
        assertEquals(soloBase, soloTodo.getEffectiveStats().getCombatAbility());
    }
}
