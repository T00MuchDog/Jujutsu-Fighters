package com.jjktbf.model.combat;

/**
 * Lifecycle state of a combatant instance within a battle.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — on the field, eligible to act, plan, and be targeted.</li>
 *   <li>{@link #RESERVE} — a living roster fighter waiting off the field.</li>
 *   <li>{@link #DEFEATED} — HP reached 0 by normal damage. A defeated fighter
 *       still counts toward team-defeat reconciliation; a defeated summon is
 *       scheduled for removal.</li>
 *   <li>{@link #REMOVED} — no longer present in combat (a dismissed/destroyed
 *       summon). Removed combatants are skipped by all future resolution and
 *       targeting passes.</li>
 * </ul>
 */
public enum CombatantLifecycle {
    ACTIVE,
    RESERVE,
    DEFEATED,
    REMOVED
}
