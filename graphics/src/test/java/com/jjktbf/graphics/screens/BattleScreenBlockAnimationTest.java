package com.jjktbf.graphics.screens;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.multiplayer.protocol.BattleEventState;
import com.jjktbf.multiplayer.protocol.BattleEventType;
import com.jjktbf.multiplayer.protocol.PlayerSide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleScreenBlockAnimationTest {

    @Test
    void localReducedBlockPairsAcrossInterleavedStatusAndDamageIgnored() {
        BattleCombatant attacker = fighter("Attacker");
        BattleCombatant defender = fighter("Defender");
        Move attack = move("attack");
        CombatEvent block = local(
            CombatEvent.Type.MOVE_BLOCK_REDUCED, attacker, defender, attack, 0, 8, "guard");
        CombatEvent status = local(
            CombatEvent.Type.STATUS_APPLIED, attacker, defender, attack, 0, 8, null);
        CombatEvent damageIgnored = local(
            CombatEvent.Type.DAMAGE_IGNORED, attacker, defender, attack, 0, 8, "guard");

        assertTrue(BattleScreen.hasFollowingBlockDamage(
            List.of(block, status, damageIgnored), block));
    }

    @Test
    void localReducedBlockIgnoresMismatchedComponentTargetMoveTickAndDefense() {
        BattleCombatant attacker = fighter("Attacker");
        BattleCombatant defender = fighter("Defender");
        BattleCombatant otherDefender = fighter("Other Defender");
        Move attack = move("attack");
        Move otherAttack = move("other-attack");
        CombatEvent block = local(
            CombatEvent.Type.MOVE_BLOCK_REDUCED, attacker, defender, attack, 0, 8, "guard");

        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, local(CombatEvent.Type.DAMAGE_DEALT, attacker, defender,
                attack, 1, 8, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, local(CombatEvent.Type.DAMAGE_DEALT, attacker, otherDefender,
                attack, 0, 8, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, local(CombatEvent.Type.DAMAGE_DEALT, attacker, defender,
                otherAttack, 0, 8, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, local(CombatEvent.Type.DAMAGE_DEALT, attacker, defender,
                attack, 0, 9, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, local(CombatEvent.Type.DAMAGE_DEALT, attacker, defender,
                attack, 0, 8, "other-guard")), block));
    }

    @Test
    void onlineReducedBlockPairsAcrossStatusAndDistinguishesDuplicateInstances() {
        BattleEventState block = online(
            "block", BattleEventType.MOVE_BLOCK_REDUCED, 3, 8,
            "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "guard");
        BattleEventState status = online(
            "status", BattleEventType.STATUS_APPLIED, 3, 8,
            "attacker", "defender", "attacker-1", "defender-1", "attack", 0, null);
        BattleEventState duplicateTargetDamage = online(
            "wrong-target-instance", BattleEventType.DAMAGE_DEALT, 3, 8,
            "attacker", "defender", "attacker-1", "defender-2", "attack", 0, "guard");
        BattleEventState damageIgnored = online(
            "damage-ignored", BattleEventType.DAMAGE_IGNORED, 3, 8,
            "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "guard");

        assertFalse(BattleScreen.hasFollowingBlockDamage(
            List.of(block, duplicateTargetDamage), block));
        assertTrue(BattleScreen.hasFollowingBlockDamage(
            List.of(block, status, duplicateTargetDamage, damageIgnored), block));

        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, online("component", BattleEventType.DAMAGE_DEALT, 3, 8,
                "attacker", "defender", "attacker-1", "defender-1", "attack", 1, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, online("move", BattleEventType.DAMAGE_DEALT, 3, 8,
                "attacker", "defender", "attacker-1", "defender-1", "other-attack", 0, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, online("tick", BattleEventType.DAMAGE_DEALT, 3, 9,
                "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, online("round", BattleEventType.DAMAGE_DEALT, 4, 8,
                "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "guard")), block));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(
            block, online("defense", BattleEventType.DAMAGE_DEALT, 3, 8,
                "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "other-guard")), block));
    }

    @Test
    void fullyBlockedAndZeroPowerOrUnidentifiedReducedBlocksDoNotDefer() {
        BattleCombatant attacker = fighter("Attacker");
        BattleCombatant defender = fighter("Defender");
        Move attack = move("attack");
        CombatEvent fullBlock = local(
            CombatEvent.Type.MOVE_BLOCKED, attacker, defender, attack, 0, 8, "guard");
        CombatEvent damage = local(
            CombatEvent.Type.DAMAGE_DEALT, attacker, defender, attack, 0, 8, "guard");
        CombatEvent zeroPowerReducedBlock = local(
            CombatEvent.Type.MOVE_BLOCK_REDUCED, attacker, defender, attack,
            0, 0, 8, "guard");
        CombatEvent unidentifiedReducedBlock = local(
            CombatEvent.Type.MOVE_BLOCK_REDUCED, attacker, defender, attack, 0, 8, null);

        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(fullBlock, damage), fullBlock));
        assertFalse(BattleScreen.hasFollowingBlockDamage(
            List.of(zeroPowerReducedBlock), zeroPowerReducedBlock));
        assertFalse(BattleScreen.hasFollowingBlockDamage(
            List.of(unidentifiedReducedBlock, damage), unidentifiedReducedBlock));
    }

    @Test
    void reducedBlockMarkersNeverPairWithDamageBeforeTheMarker() {
        BattleCombatant attacker = fighter("Attacker");
        BattleCombatant defender = fighter("Defender");
        Move attack = move("attack");
        CombatEvent localBlock = local(
            CombatEvent.Type.MOVE_BLOCK_REDUCED, attacker, defender, attack, 0, 8, "guard");
        CombatEvent localDamage = local(
            CombatEvent.Type.DAMAGE_DEALT, attacker, defender, attack, 0, 8, "guard");
        BattleEventState onlineBlock = online(
            "online-block", BattleEventType.MOVE_BLOCK_REDUCED, 3, 8,
            "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "guard");
        BattleEventState onlineDamage = online(
            "online-damage", BattleEventType.DAMAGE_DEALT, 3, 8,
            "attacker", "defender", "attacker-1", "defender-1", "attack", 0, "guard");

        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(localDamage, localBlock), localBlock));
        assertFalse(BattleScreen.hasFollowingBlockDamage(List.of(onlineDamage, onlineBlock), onlineBlock));
    }

    private static CombatEvent local(
        CombatEvent.Type type,
        BattleCombatant source,
        BattleCombatant target,
        Move move,
        Integer componentIndex,
        int tick,
        String defenseMoveId
    ) {
        return local(type, source, target, move, componentIndex,
            type == CombatEvent.Type.DAMAGE_IGNORED ? 0 : 1, tick, defenseMoveId);
    }

    private static CombatEvent local(
        CombatEvent.Type type,
        BattleCombatant source,
        BattleCombatant target,
        Move move,
        Integer componentIndex,
        int value,
        int tick,
        String defenseMoveId
    ) {
        return CombatEvent.of(type)
            .source(source)
            .target(target)
            .move(move)
            .componentIndex(componentIndex)
            .intValue(value)
            .tick(tick)
            .defenseMoveId(defenseMoveId)
            .build();
    }

    private static BattleEventState online(
        String eventId,
        BattleEventType type,
        int roundNumber,
        int tick,
        String sourceCharacterId,
        String targetCharacterId,
        String sourceInstanceId,
        String targetInstanceId,
        String moveId,
        Integer componentIndex,
        String defenseMoveId
    ) {
        return new BattleEventState(
            eventId, type, roundNumber, tick,
            PlayerSide.PLAYER_ONE, sourceCharacterId, "Attacker",
            PlayerSide.PLAYER_TWO, targetCharacterId, "Defender",
            moveId, moveId, componentIndex,
            type == BattleEventType.DAMAGE_IGNORED ? 0 : 1,
            null, null,
            sourceInstanceId, targetInstanceId,
            null, null, null, null,
            null, null, null, null, null,
            null, defenseMoveId, null);
    }

    private static Move move(String id) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.tags = List.of("PHYSICAL", "ATTACK");
        data.apCost = 10;
        data.unleashPoint = 1;
        return data.toMove();
    }

    private static BattleCombatant fighter(String name) {
        SorcererCharacter character = new SorcererCharacter(
            name.toLowerCase(), name, new CharacterStats.Builder().build(),
            null, List.of(), List.of(), Equipment.NONE);
        return new BattleCombatant(character, List.of());
    }
}
