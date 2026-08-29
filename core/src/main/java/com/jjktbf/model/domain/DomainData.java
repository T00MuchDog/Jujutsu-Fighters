package com.jjktbf.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.move.StatusEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Editable definition for a Domain or an anti-Domain field. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainData {

    public String id;
    public String name;
    public String description;

    /** Required for ordinary Domains; null only for anti-Domain definitions. */
    public String requiredTechniqueName;
    public boolean antiDomain;

    public String topology = DomainTopology.CLOSED.name();
    public String capturePolicy = DomainCapturePolicy.ALL_ACTIVE.name();
    public String recognitionPolicy = DomainRecognitionPolicy.ALL_MEMBERS.name();
    public String entrantPolicy = DomainEntrantPolicy.FOLLOW_SUMMONER.name();
    public String protectionPolicy = DomainProtectionPolicy.OWNER.name();

    public Integer durationRounds = 1;
    public Integer durationTicks = 0;
    /** Technique-specific burnout applied when an ordinary Domain collapses. */
    public Integer burnoutRounds = 1;
    public Integer burnoutTicks = 0;
    public Double ceUpkeepPerTick = 0.0;
    public Integer internalBarrierIntegrity = 100;
    public Integer externalBarrierIntegrity = 100;
    public Integer clashPressurePerTick = 0;
    public Integer externalPressurePerTick = 0;

    public String counterType = DomainCounterType.NONE.name();
    public Integer counterPotency = 0;
    public Integer counterUses = -1;
    public Boolean counterBreakOnOwnerMove = false;

    /** Value prerequisites mirrored by the Domain's technique-tree node. */
    public Map<String, Integer> prerequisites;

    public List<AbilityEffectData> sureHitEffects = new ArrayList<>();
    public List<AbilityEffectData> fieldEffects = new ArrayList<>();
    public List<AbilityEffectData> casterEffects = new ArrayList<>();
    public List<AbilityEffectData> barrierEffects = new ArrayList<>();
    public List<AbilityEffectData> procedureEffects = new ArrayList<>();

    public DomainData copy() {
        DomainData copy = new DomainData();
        copy.id = id;
        copy.name = name;
        copy.description = description;
        copy.requiredTechniqueName = requiredTechniqueName;
        copy.antiDomain = antiDomain;
        copy.topology = topology;
        copy.capturePolicy = capturePolicy;
        copy.recognitionPolicy = recognitionPolicy;
        copy.entrantPolicy = entrantPolicy;
        copy.protectionPolicy = protectionPolicy;
        copy.durationRounds = durationRounds;
        copy.durationTicks = durationTicks;
        copy.burnoutRounds = burnoutRounds;
        copy.burnoutTicks = burnoutTicks;
        copy.ceUpkeepPerTick = ceUpkeepPerTick;
        copy.internalBarrierIntegrity = internalBarrierIntegrity;
        copy.externalBarrierIntegrity = externalBarrierIntegrity;
        copy.clashPressurePerTick = clashPressurePerTick;
        copy.externalPressurePerTick = externalPressurePerTick;
        copy.counterType = counterType;
        copy.counterPotency = counterPotency;
        copy.counterUses = counterUses;
        copy.counterBreakOnOwnerMove = counterBreakOnOwnerMove;
        copy.prerequisites = prerequisites == null ? null : new LinkedHashMap<>(prerequisites);
        copy.sureHitEffects = copyEffects(sureHitEffects);
        copy.fieldEffects = copyEffects(fieldEffects);
        copy.casterEffects = copyEffects(casterEffects);
        copy.barrierEffects = copyEffects(barrierEffects);
        copy.procedureEffects = copyEffects(procedureEffects);
        return copy;
    }

    public DomainDefinition toDomain() {
        validate();
        return new DomainDefinition(this);
    }

    public void validate() {
        requireText(id, "Domain ID");
        requireText(name, "Domain name");
        if (!antiDomain) requireText(requiredTechniqueName, "Required technique");
        parse(DomainTopology.class, topology, "topology");
        parse(DomainCapturePolicy.class, capturePolicy, "capture policy");
        parse(DomainRecognitionPolicy.class, recognitionPolicy, "recognition policy");
        parse(DomainEntrantPolicy.class, entrantPolicy, "entrant policy");
        parse(DomainProtectionPolicy.class, protectionPolicy, "protection policy");
        DomainCounterType counter = parse(DomainCounterType.class, counterType, "counter type");
        if (antiDomain && counter == DomainCounterType.NONE) {
            throw new IllegalArgumentException("An anti-Domain needs a counter type");
        }
        if (!antiDomain && counter != DomainCounterType.NONE) {
            throw new IllegalArgumentException("Only anti-Domains may define a counter type");
        }
        int rounds = durationRounds == null ? 0 : durationRounds;
        int ticks = durationTicks == null ? 0 : durationTicks;
        StatusEffect.validateDuration(rounds, ticks);
        int collapseBurnoutRounds = burnoutRounds == null ? 0 : burnoutRounds;
        int collapseBurnoutTicks = burnoutTicks == null ? 0 : burnoutTicks;
        if (collapseBurnoutRounds != 0 || collapseBurnoutTicks != 0) {
            StatusEffect.validateDuration(collapseBurnoutRounds, collapseBurnoutTicks);
        }
        if (antiDomain && (collapseBurnoutRounds != 0 || collapseBurnoutTicks != 0)) {
            throw new IllegalArgumentException("Anti-Domains cannot apply technique burnout");
        }
        if (ceUpkeepPerTick == null || !Double.isFinite(ceUpkeepPerTick)
            || ceUpkeepPerTick < 0.0) {
            throw new IllegalArgumentException("Domain CE upkeep must be zero or greater");
        }
        requireNonNegative(internalBarrierIntegrity, "Internal barrier integrity");
        requireNonNegative(externalBarrierIntegrity, "External barrier integrity");
        requireNonNegative(clashPressurePerTick, "Clash pressure");
        requireNonNegative(externalPressurePerTick, "External pressure");
        if (counterPotency == null || counterPotency < 0) {
            throw new IllegalArgumentException("Counter potency must be zero or greater");
        }
        if (counterUses == null || counterUses < -1 || counterUses == 0) {
            throw new IllegalArgumentException("Counter uses must be -1 or greater than zero");
        }
        if (prerequisites != null) {
            for (Map.Entry<String, Integer> entry : prerequisites.entrySet()) {
                StatKey.fromString(entry.getKey());
                if (entry.getValue() == null || entry.getValue() < 0) {
                    throw new IllegalArgumentException("Domain prerequisites cannot be negative");
                }
            }
        }
        validateEffects(sureHitEffects, "sure-hit");
        validateEffects(fieldEffects, "field");
        validateEffects(casterEffects, "caster");
        validateEffects(barrierEffects, "barrier");
        validateEffects(procedureEffects, "procedure");
    }

    private static void validateEffects(List<AbilityEffectData> effects, String channel) {
        if (effects == null) return;
        for (int index = 0; index < effects.size(); index++) {
            AbilityEffectData effect = effects.get(index);
            if (effect == null) throw new IllegalArgumentException(
                "Null " + channel + " effect at row " + (index + 1));
            AbilityEffectType type = AbilityEffectType.fromName(effect.type);
            if (type.isMoveOnly()) {
                throw new IllegalArgumentException(
                    "Move-only effect " + type.displayName() + " cannot be used in a Domain");
            }
            type.prepare(effect);
            String error = type.validationError(effect);
            if (error != null) throw new IllegalArgumentException(
                "Invalid " + channel + " effect row " + (index + 1) + ": " + error);
            parse(DomainTrigger.class, effect.domainTrigger, "Domain trigger");
            parse(DomainAudience.class, effect.domainAudience, "Domain audience");
            parse(DomainDeliveryClass.class, effect.domainDeliveryClass, "Domain delivery class");
            if (effect.domainIntervalTicks == null || effect.domainIntervalTicks < 1) {
                throw new IllegalArgumentException("Domain effect interval must be at least one tick");
            }
            if (Boolean.TRUE.equals(effect.domainActivationChanceEnabled)
                && (effect.domainActivationChance == null
                    || effect.domainActivationChance < 0.0
                    || effect.domainActivationChance > 1.0)) {
                throw new IllegalArgumentException("Domain effect chance must be between 0 and 1");
            }
            if (effect.domainCondition != null) {
                String conditionError = AbilityConditionType.validationError(effect.domainCondition);
                if (conditionError != null) {
                    throw new IllegalArgumentException(
                        "Invalid " + channel + " condition at row " + (index + 1)
                            + ": " + conditionError);
                }
            }
        }
    }

    private static List<AbilityEffectData> copyEffects(List<AbilityEffectData> source) {
        if (source == null) return new ArrayList<>();
        return source.stream().map(effect -> effect == null ? null : effect.copy())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value == null || value < 0) throw new IllegalArgumentException(field + " must be zero or greater");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " is invalid", exception);
        }
    }
}
