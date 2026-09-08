package com.jjktbf.graphics.ui;

import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.CodedAbilityRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityStateMeterTest {

    @Test
    void onlyBoundedResourcesReceiveGenericBars() {
        AbilityStateMeter meter = new AbilityStateMeter();

        meter.setStates(CodedAbilityRegistry.stateKeys().stream()
            .map(state -> new CodedAbilityState(state.key(), state.label(), 1, 1, false))
            .toList());
        assertEquals(0, meter.stateCount());

        meter.setStates(List.of(
            new CodedAbilityState("BLOOD_SUPPLY", "Blood Supply", 4, 5, true),
            new CodedAbilityState("COMPRESSION", "Compression", 1, 3, true),
            new CodedAbilityState("STANCE", "Stance", 1, 1, false)));
        assertEquals(2, meter.stateCount());
    }
}
