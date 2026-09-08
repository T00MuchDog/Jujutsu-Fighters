package com.jjktbf.model.character;

/**
 * Target of an automatically applied ability/move effect.
 *
 * <p>Resolution (see {@code AbilityActivationEngine.targets}) depends on context:
 * <ul>
 *   <li>{@link #SELF} — the effect's owner (the move's caster / the ability's bearer).</li>
 *   <li>{@link #ENEMY} — the move's selected targets (move context) or every active
 *       enemy of the owner (ability context).</li>
 *   <li>{@link #ALLY} — the defensive move's conferred beneficiaries (move context)
 *       or every active ally of the owner (ability context). For a defensive move
 *       this is exactly the allies its active-defense window is granted to.</li>
 *   <li>{@link #BOTH} — the owner plus its enemies.</li>
 *   <li>{@link #SELF_AND_ALLY} — the owner plus its allies.</li>
 *   <li>{@link #PAIR_FIRST}, {@link #PAIR_SECOND}, {@link #PAIR_BOTH} — ordered
 *       endpoints selected by a pair-targeted move.</li>
 * </ul>
 */
public enum AbilityEffectTarget {
    SELF,
    ENEMY,
    /** Only the current event's enemy counterpart, not the whole opposing team. */
    CURRENT_ENEMY,
    ALLY,
    BOTH,
    SELF_AND_ALLY,
    PAIR_FIRST,
    PAIR_SECOND,
    PAIR_BOTH
}
