package com.jjktbf.model.move;

import com.jjktbf.model.character.BattleStatKey;
import com.jjktbf.model.character.StatKey;

import java.util.Set;

/** Temporary effects that can be applied by moves or abilities. */
public enum StatusEffectType {

    // Base character stats
    VITALITY_INCREASE("Increase Vitality", StatKey.VITALITY, 1),
    VITALITY_DECREASE("Decrease Vitality", StatKey.VITALITY, -1),
    STRENGTH_INCREASE("Increase Strength", StatKey.STRENGTH, 1),
    STRENGTH_DECREASE("Decrease Strength", StatKey.STRENGTH, -1),
    DURABILITY_INCREASE("Increase Durability", StatKey.DURABILITY, 1),
    DURABILITY_DECREASE("Decrease Durability", StatKey.DURABILITY, -1),
    SPEED_INCREASE("Increase Speed", StatKey.SPEED, 1),
    SPEED_DECREASE("Decrease Speed", StatKey.SPEED, -1),
    COMBAT_ABILITY_INCREASE("Increase Combat Ability", StatKey.COMBAT_ABILITY, 1),
    COMBAT_ABILITY_DECREASE("Decrease Combat Ability", StatKey.COMBAT_ABILITY, -1),
    CURSED_ENERGY_RESERVES_INCREASE(
        "Increase Cursed Energy Reserves", StatKey.CURSED_ENERGY_RESERVES, 1),
    CURSED_ENERGY_RESERVES_DECREASE(
        "Decrease Cursed Energy Reserves", StatKey.CURSED_ENERGY_RESERVES, -1),
    CURSED_ENERGY_EFFICIENCY_INCREASE(
        "Increase Cursed Energy Efficiency", StatKey.CURSED_ENERGY_EFFICIENCY, 1),
    CURSED_ENERGY_EFFICIENCY_DECREASE(
        "Decrease Cursed Energy Efficiency", StatKey.CURSED_ENERGY_EFFICIENCY, -1),
    CURSED_ENERGY_OUTPUT_INCREASE(
        "Increase Cursed Energy Output", StatKey.CURSED_ENERGY_OUTPUT, 1),
    CURSED_ENERGY_OUTPUT_DECREASE(
        "Decrease Cursed Energy Output", StatKey.CURSED_ENERGY_OUTPUT, -1),
    JUJUTSU_SKILL_INCREASE("Increase Jujutsu Skill", StatKey.JUJUTSU_SKILL, 1),
    JUJUTSU_SKILL_DECREASE("Decrease Jujutsu Skill", StatKey.JUJUTSU_SKILL, -1),
    CURSED_TECHNIQUE_MASTERY_INCREASE(
        "Increase Cursed Technique Mastery", StatKey.CURSED_TECHNIQUE_MASTERY, 1),
    CURSED_TECHNIQUE_MASTERY_DECREASE(
        "Decrease Cursed Technique Mastery", StatKey.CURSED_TECHNIQUE_MASTERY, -1),

    // Derived combat stats that remain meaningful after a fight has started
    MAX_HP_INCREASE("Increase Max HP", BattleStatKey.MAX_HP, 1),
    MAX_HP_DECREASE("Decrease Max HP", BattleStatKey.MAX_HP, -1),
    MAX_CURSED_ENERGY_INCREASE("Increase Max Cursed Energy", BattleStatKey.MAX_CE, 1),
    MAX_CURSED_ENERGY_DECREASE("Decrease Max Cursed Energy", BattleStatKey.MAX_CE, -1),
    MAX_AP_INCREASE("Increase Max AP", BattleStatKey.MAX_AP, 1),
    MAX_AP_DECREASE("Decrease Max AP", BattleStatKey.MAX_AP, -1),
    ACCURACY_INCREASE("Increase Accuracy", BattleStatKey.ACCURACY, 1),
    ACCURACY_DECREASE("Decrease Accuracy", BattleStatKey.ACCURACY, -1),
    EVASION_INCREASE("Increase Evasion", BattleStatKey.EVASION, 1),
    EVASION_DECREASE("Decrease Evasion", BattleStatKey.EVASION, -1),
    POWER_INCREASE("Increase Power", BattleStatKey.POWER, 1),
    POWER_DECREASE("Decrease Power", BattleStatKey.POWER, -1),
    DEFENSE_INCREASE("Increase Defense", BattleStatKey.DEFENSE, 1),
    DEFENSE_DECREASE("Decrease Defense", BattleStatKey.DEFENSE, -1),

    /**
     * A tick-only control status. While active, it stuns the affected combatant's
     * currently active action segments without changing any stat.
     */
    STAGGER("Stagger", 0),

    /** Prevents actions while active and ends on damage, natural recovery, or round expiry. */
    SLEEP("Sleep", 0, 0.05),

    /** Halves Speed and rolls Strength-based escape plus a separate action stun each tick. */
    RESTRAINED("Restrained", StatKey.SPEED, 0.5),

    /** Reduces Speed by 20% and enables elemental reactions from electric and ice hits. */
    WET("Wet", StatKey.SPEED, 0.8),

    /** Halves Defense and prevents non-fire actions, with a 10% breakout roll each tick. */
    FROZEN("Frozen", BattleStatKey.DEFENSE, 0.5, 0.10),

    /** Halves outgoing melee damage and deals 0.03% max-HP damage each active tick. */
    BURNED("Burned", 0),

    /** Adds two AP ticks to both the cost and firing point of every planned move. */
    FATIGUED("Fatigued", 0),

    /**
     * Covers the holder's ears in cursed energy. Incoming Cursed Speech commands
     * automatically fail against the holder, and the status may carry a CE upkeep
     * drained each resolution tick while it remains active.
     */
    CURSED_SPEECH_WARD("Cursed Speech Ward", 0),

    /** Damages the holder whenever they voluntarily pay cursed energy for a move. */
    CURSED_ENERGY_PARASITE("Cursed Energy Parasite", 0),

    /** Multiplies every base stat by 0.8 and deals max-HP damage each active tick. */
    POISON("Poison", 0.8);

    private final String displayName;
    private final StatKey baseStat;
    private final BattleStatKey battleStat;
    private final int direction;
    private final double defaultPerTickRemovalChance;
    private final Double statMultiplier;

    StatusEffectType(String displayName, StatKey baseStat, int direction) {
        this(displayName, baseStat, null, direction, 0.0, null);
    }

    StatusEffectType(String displayName, BattleStatKey battleStat, int direction) {
        this(displayName, null, battleStat, direction, 0.0, null);
    }

    StatusEffectType(String displayName, int direction) {
        this(displayName, null, null, direction, 0.0, null);
    }

    StatusEffectType(String displayName, int direction, double defaultPerTickRemovalChance) {
        this(displayName, null, null, direction, defaultPerTickRemovalChance, null);
    }

    StatusEffectType(String displayName, double statMultiplier) {
        this(displayName, null, null, 0, 0.0, statMultiplier);
    }

    StatusEffectType(String displayName, StatKey baseStat, double statMultiplier) {
        this(displayName, baseStat, null, 0, 0.0, statMultiplier);
    }

    StatusEffectType(
        String displayName,
        BattleStatKey battleStat,
        double statMultiplier,
        double defaultPerTickRemovalChance
    ) {
        this(displayName, null, battleStat, 0,
            defaultPerTickRemovalChance, statMultiplier);
    }

    StatusEffectType(
        String displayName,
        StatKey baseStat,
        BattleStatKey battleStat,
        int direction,
        double defaultPerTickRemovalChance,
        Double statMultiplier
    ) {
        this.displayName = displayName;
        this.baseStat = baseStat;
        this.battleStat = battleStat;
        this.direction = direction;
        this.defaultPerTickRemovalChance = defaultPerTickRemovalChance;
        this.statMultiplier = statMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public StatKey baseStat() {
        return baseStat;
    }

    public BattleStatKey battleStat() {
        return battleStat;
    }

    /** True when this status modifies a base or derived combat stat. */
    public boolean isStatModifier() {
        return statMultiplier == null && (baseStat != null || battleStat != null);
    }

    /** True when the status multiplies a base or derived battle stat. */
    public boolean isStatMultiplier() {
        return statMultiplier != null
            && (baseStat != null || battleStat != null || affectsAllBaseStats());
    }

    /** True when one multiplier applies to every base character stat. */
    public boolean affectsAllBaseStats() {
        return statMultiplier != null && baseStat == null && battleStat == null;
    }

    public double statMultiplier() {
        return statMultiplier == null ? 1.0 : statMultiplier;
    }

    /** Whether this status uses the descriptor's magnitude field. */
    public boolean usesMagnitude() {
        return isStatModifier();
    }

    /** Whether this status must be configured exclusively in AP ticks. */
    public boolean requiresTickDuration() {
        return this == STAGGER;
    }

    /** Whether this status rejects AP-tick durations. */
    public boolean requiresRoundDuration() {
        return this == POISON || this == SLEEP;
    }

    /** Default chance for a live status to remove itself at each resolution tick. */
    public double defaultPerTickRemovalChance() {
        return defaultPerTickRemovalChance;
    }

    /** Whether authored effects may override this status's per-tick removal chance. */
    public boolean usesConfigurablePerTickRemovalChance() {
        return this != FROZEN;
    }

    public double signedMagnitude(double magnitude) {
        return direction * magnitude;
    }

    /** Whether applying this status again replaces its existing instance. */
    public boolean refreshesOnReapply() {
        return this == RESTRAINED || this == WET || this == FROZEN
            || this == BURNED || this == FATIGUED || this == POISON
            || this == CURSED_SPEECH_WARD || this == CURSED_ENERGY_PARASITE;
    }

    /**
     * True when this status represents physical, anatomy-dependent injury —
     * damage to the body's structure rather than the mind, cursed-energy flow,
     * or an external restraint. A body that reshapes itself (e.g. Maintaining
     * the Soul restoring the damage it took) does not keep these once the
     * associated damage is restored. Mental effects, CE disruption, control
     * statuses, soul effects, and plain stat debuffs are excluded.
     */
    public boolean isBodilyInjury() {
        return this == BURNED || this == FROZEN || this == POISON;
    }

    /** Resolve current names plus stat-based equivalents from pre-rework catalogs. */
    public static StatusEffectType fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Status effect type is required");
        }
        return switch (name.trim().toUpperCase()) {
            case "FOCUS" -> ACCURACY_INCREASE;
            case "CE_OUTPUT_UP" -> CURSED_ENERGY_OUTPUT_INCREASE;
            case "SPEED_UP" -> SPEED_INCREASE;
            case "POWER_UP" -> POWER_INCREASE;
            case "DEFENSE_UP" -> DEFENSE_INCREASE;
            case "BIND" -> EVASION_DECREASE;
            case "CURSED_SEAL" -> CURSED_ENERGY_EFFICIENCY_DECREASE;
            case "AP_DRAIN" -> MAX_AP_DECREASE;
            default -> valueOf(name.trim().toUpperCase());
        };
    }

    /** Resolve the direction encoded by old signed amounts into an explicit type. */
    public static StatusEffectType fromName(String name, double storedMagnitude) {
        StatusEffectType type = fromName(name);
        return type.isStatModifier() && storedMagnitude < 0 ? type.opposite() : type;
    }

    public StatusEffectType opposite() {
        if (!isStatModifier()) {
            throw new IllegalStateException(name() + " has no opposite status");
        }
        String suffix = name().endsWith("_INCREASE") ? "_INCREASE" : "_DECREASE";
        String oppositeSuffix = "_INCREASE".equals(suffix) ? "_DECREASE" : "_INCREASE";
        return valueOf(name().substring(0, name().length() - suffix.length()) + oppositeSuffix);
    }

    /** Statuses selected by a persisted reference; old signed aliases can mean either direction. */
    public static Set<StatusEffectType> referencedTypes(String name) {
        try {
            StatusEffectType type = fromName(name);
            if (isSignedLegacyName(name)) return Set.of(type, type.opposite());
            return Set.of(type);
        } catch (IllegalArgumentException ignored) {
            return Set.of();
        }
    }

    public static String referenceDisplayName(String name) {
        Set<StatusEffectType> referenced = referencedTypes(name);
        if (referenced.isEmpty()) {
            return "Missing status: " + (name == null || name.isBlank() ? "(blank)" : name);
        }
        if (referenced.size() > 1) return "Legacy " + name + " (either direction)";
        return referenced.iterator().next().displayName();
    }

    private static boolean isSignedLegacyName(String name) {
        if (name == null) return false;
        return switch (name.trim().toUpperCase()) {
            case "FOCUS", "CE_OUTPUT_UP", "SPEED_UP", "POWER_UP", "DEFENSE_UP",
                 "BIND", "CURSED_SEAL", "AP_DRAIN" -> true;
            default -> false;
        };
    }

    /** Convert old fractional Power/Defense/Accuracy amounts into flat stat points. */
    public static double normalizeStoredMagnitude(String name, double magnitude) {
        if (name == null) return magnitude;
        try {
            if (!fromName(name).usesMagnitude()) return 0.0;
        } catch (IllegalArgumentException ignored) {
            // Preserve the existing tolerant behavior for unknown persisted names.
        }
        double normalized = switch (name.trim().toUpperCase()) {
            case "FOCUS", "POWER_UP", "DEFENSE_UP" -> magnitude * 100.0;
            default -> magnitude;
        };
        return Math.abs(normalized);
    }
}
