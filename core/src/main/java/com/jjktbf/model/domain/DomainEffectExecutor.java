package com.jjktbf.model.domain;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;

import java.util.List;

/** Executes one admitted Domain row through the shared effect primitive runtime. */
@FunctionalInterface
public interface DomainEffectExecutor {
    List<CombatEvent> execute(
        BattleState state,
        BattleCombatant owner,
        BattleCombatant target,
        AbilityEffectData effect,
        int tick,
        String sourceLease
    );
}
