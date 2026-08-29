package com.jjktbf.model.domain;

import com.jjktbf.model.character.AbilityEffectData;

import java.util.List;
import java.util.Map;

/** Immutable battle-facing Domain definition. */
public final class DomainDefinition {

    private final DomainData data;

    DomainDefinition(DomainData source) {
        data = source.copy();
    }

    public String id() { return data.id; }
    public String name() { return data.name; }
    public String description() { return data.description; }
    public String requiredTechniqueName() { return data.requiredTechniqueName; }
    public boolean antiDomain() { return data.antiDomain; }
    public DomainTopology topology() { return enumValue(DomainTopology.class, data.topology); }
    public DomainCapturePolicy capturePolicy() {
        return enumValue(DomainCapturePolicy.class, data.capturePolicy);
    }
    public DomainRecognitionPolicy recognitionPolicy() {
        return enumValue(DomainRecognitionPolicy.class, data.recognitionPolicy);
    }
    public DomainEntrantPolicy entrantPolicy() {
        return enumValue(DomainEntrantPolicy.class, data.entrantPolicy);
    }
    public DomainProtectionPolicy protectionPolicy() {
        return enumValue(DomainProtectionPolicy.class, data.protectionPolicy);
    }
    public int durationRounds() { return integer(data.durationRounds); }
    public int durationTicks() { return integer(data.durationTicks); }
    public int burnoutRounds() { return integer(data.burnoutRounds); }
    public int burnoutTicks() { return integer(data.burnoutTicks); }
    public double ceUpkeepPerTick() { return decimal(data.ceUpkeepPerTick); }
    public int internalBarrierIntegrity() { return integer(data.internalBarrierIntegrity); }
    public int externalBarrierIntegrity() { return integer(data.externalBarrierIntegrity); }
    public int clashPressurePerTick() { return integer(data.clashPressurePerTick); }
    public int externalPressurePerTick() { return integer(data.externalPressurePerTick); }
    public DomainCounterType counterType() {
        return enumValue(DomainCounterType.class, data.counterType);
    }
    public int counterPotency() { return integer(data.counterPotency); }
    public int counterUses() { return data.counterUses == null ? -1 : data.counterUses; }
    public boolean counterBreakOnOwnerMove() { return Boolean.TRUE.equals(data.counterBreakOnOwnerMove); }
    public Map<String, Integer> prerequisites() {
        return data.prerequisites == null ? Map.of() : Map.copyOf(data.prerequisites);
    }
    public List<AbilityEffectData> sureHitEffects() { return immutableEffects(data.sureHitEffects); }
    public List<AbilityEffectData> fieldEffects() { return immutableEffects(data.fieldEffects); }
    public List<AbilityEffectData> casterEffects() { return immutableEffects(data.casterEffects); }
    public List<AbilityEffectData> barrierEffects() { return immutableEffects(data.barrierEffects); }
    public List<AbilityEffectData> procedureEffects() { return immutableEffects(data.procedureEffects); }

    private static List<AbilityEffectData> immutableEffects(List<AbilityEffectData> effects) {
        return effects == null ? List.of()
            : effects.stream().map(AbilityEffectData::copy).toList();
    }

    private static int integer(Integer value) { return value == null ? 0 : value; }
    private static double decimal(Double value) { return value == null ? 0.0 : value; }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
