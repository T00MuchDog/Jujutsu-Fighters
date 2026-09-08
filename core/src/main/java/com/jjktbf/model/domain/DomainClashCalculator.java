package com.jjktbf.model.domain;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.combat.BattleCombatant;

/**
 * Narrow rule owner for Domain clash math.
 *
 * <p>Each clashing Domain holds a clash score built from three parts:</p>
 * <ul>
 *   <li>the Domain's authored base clash value,</li>
 *   <li>the owner's Domain refinement — one part Jujutsu Skill, one part
 *       Cursed Technique Mastery — which carries three times the weight of</li>
 *   <li>the owner's live cursed-energy pressure: the current CE pool capped
 *       by cursed-energy output.</li>
 * </ul>
 * <p>During a clash, the lower-scoring Domain loses integrity equal to the
 * difference between the two scores. Anti-Domains do not clash and instead
 * lose integrity equal to the hostile Domain's full score.</p>
 */
public final class DomainClashCalculator {

    /** Stats are read on a 0–100 scale; refinement is weighted 3:1 over CE. */
    private static final double STAT_SCALE = 100.0;

    /** Keep clash pressure proportional to combat stats without erasing barriers in a few ticks. */
    private static final double CLASH_SCORE_DIVISOR = 4.0;

    private DomainClashCalculator() { }

    /**
     * Domain refinement of a combatant: one part Jujutsu Skill and one part
     * Cursed Technique Mastery, read from effective (status-modified) stats.
     */
    public static double refinement(BattleCombatant owner) {
        CharacterStats stats = owner.getEffectiveStats();
        return (stats.getJujutsuSkill() + stats.getCursedTechniqueMastery()) / 2.0;
    }

    /**
     * Live cursed-energy pressure of a combatant on a 0–100 scale: the current
     * CE pool capped by cursed-energy output, as a fraction of the maximum pool.
     */
    public static double cursedEnergyPressure(BattleCombatant owner) {
        int maxCe = Math.max(1, owner.getMaxCursedEnergy());
        int effective = Math.min(owner.getCurrentCe(),
            owner.getEffectiveStats().getCursedEnergyOutput());
        return 100.0 * Math.min(1.0, (double) effective / maxCe);
    }

    /**
     * Effective clash score of one established Domain. The base value is
     * scaled by refinement at three times the weight of CE pressure, then
     * divided by four to keep per-tick barrier pressure gradual.
     */
    public static double clashScore(DomainDefinition definition, BattleCombatant owner) {
        double base = Math.max(1.0, definition.clashValue());
        return scale(base, refinement(owner), cursedEnergyPressure(owner))
            / CLASH_SCORE_DIVISOR;
    }

    /**
     * Runtime barrier integrity. Jujutsu Skill carries three times the weight
     * of cursed-energy output. Barrier scaling is not divided like clash score.
     */
    public static int barrierIntegrity(DomainDefinition definition, BattleCombatant owner) {
        CharacterStats stats = owner.getEffectiveStats();
        double scaled = scale(Math.max(0.0, definition.internalBarrierIntegrity()),
            stats.getJujutsuSkill(), stats.getCursedEnergyOutput());
        return (int) Math.min(Integer.MAX_VALUE, Math.round(scaled));
    }

    private static double scale(double base, double primary, double secondary) {
        return base * (1.0 + (3.0 * primary + secondary) / STAT_SCALE);
    }
}
