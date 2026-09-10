package com.jjktbf.model.character.coded;

import com.jjktbf.model.combat.CombatEvent;

import java.util.ArrayList;
import java.util.List;

/** Hit-local changes supplied by compiled abilities after accuracy succeeds. */
public record CodedHitModifiers(
    boolean bypassBlock,
    boolean bypassConventionalDefenses,
    double defenseMultiplier,
    int effectiveDefenseCap,
    boolean negateHit,
    int recoilDamage,
    List<CombatEvent> events
) {

    private static final CodedHitModifiers NONE = new CodedHitModifiers(
        false, false, 1.0, Integer.MAX_VALUE, false, 0, List.of());

    /** Source-compatible constructor for coded effects that only alter block/defense. */
    public CodedHitModifiers(
        boolean bypassBlock,
        double defenseMultiplier,
        List<CombatEvent> events
    ) {
        this(bypassBlock, false, defenseMultiplier, Integer.MAX_VALUE, false, 0, events);
    }

    public CodedHitModifiers(
        boolean bypassBlock,
        double defenseMultiplier,
        int effectiveDefenseCap,
        List<CombatEvent> events
    ) {
        this(bypassBlock, false, defenseMultiplier, effectiveDefenseCap, false, 0, events);
    }

    /** Source-compatible constructor for coded effects without a Defense cap. */
    public CodedHitModifiers(
        boolean bypassBlock,
        boolean bypassConventionalDefenses,
        double defenseMultiplier,
        boolean negateHit,
        int recoilDamage,
        List<CombatEvent> events
    ) {
        this(bypassBlock, bypassConventionalDefenses, defenseMultiplier,
            Integer.MAX_VALUE, negateHit, recoilDamage, events);
    }

    public CodedHitModifiers {
        if (!Double.isFinite(defenseMultiplier) || defenseMultiplier <= 0) {
            throw new IllegalArgumentException("Defense multiplier must be positive and finite");
        }
        if (effectiveDefenseCap <= 0) {
            throw new IllegalArgumentException("Effective Defense cap must be positive");
        }
        if (recoilDamage < 0) {
            throw new IllegalArgumentException("Recoil damage must be non-negative");
        }
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static CodedHitModifiers none() {
        return NONE;
    }

    public CodedHitModifiers combine(CodedHitModifiers other) {
        if (other == null || other == NONE) return this;
        if (this == NONE) return other;
        List<CombatEvent> combinedEvents = new ArrayList<>(events);
        combinedEvents.addAll(other.events);
        return new CodedHitModifiers(
            bypassBlock || other.bypassBlock,
            bypassConventionalDefenses || other.bypassConventionalDefenses,
            Math.min(defenseMultiplier, other.defenseMultiplier),
            Math.min(effectiveDefenseCap, other.effectiveDefenseCap),
            negateHit || other.negateHit,
            Math.addExact(recoilDamage, other.recoilDamage),
            combinedEvents
        );
    }
}
