package com.jjktbf.model.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One ordered team of combatants within a {@link BattleState}.
 *
 * <p>Ordering is stable: initial fighters appear first in roster order, then
 * summons in creation order. This stable order is the deterministic fallback
 * for tie-breaking, targeting retargets, and UI page layout (the first/main
 * fighter is always the leftmost page).
 *
 * <p>A team owns its combatants by {@link CombatantId}; defeated and removed
 * combatants remain in the roster (preserving order) but are excluded from the
 * "active"/"living fighter" views.
 */
public final class BattleTeam {

    private final BattleTeamId id;
    private final List<BattleCombatant> combatants = new ArrayList<>();
    private final Map<CombatantId, BattleCombatant> byInstance = new LinkedHashMap<>();
    private final List<BattleCombatant> fighterSlots = new ArrayList<>();
    private boolean fighterSlotsConfigured;
    private int activeFighterLimit;

    record FighterSlotChange(int slot, BattleCombatant outgoing, BattleCombatant incoming) { }

    public BattleTeam(BattleTeamId id) {
        this.id = Objects.requireNonNull(id, "team id");
        if (!BattleTeamId.PLAYER.equals(id) && !BattleTeamId.ENEMY.equals(id)) {
            throw new IllegalArgumentException("Unsupported battle team " + id);
        }
    }

    public BattleTeamId id() {
        return id;
    }

    /** All combatants in stable order, including defeated/removed ones. */
    public List<BattleCombatant> all() {
        return Collections.unmodifiableList(combatants);
    }

    /** Combatants still present in combat (lifecycle ACTIVE or DEFEATED), stable order. */
    public List<BattleCombatant> present() {
        List<BattleCombatant> out = new ArrayList<>();
        for (BattleCombatant c : combatants) {
            if (!c.isRemoved()) out.add(c);
        }
        return out;
    }

    /** Fielded fighters in slot order, followed by active summons in creation order. */
    public List<BattleCombatant> active() {
        List<BattleCombatant> out = new ArrayList<>();
        if (fighterSlotsConfigured) {
            for (BattleCombatant fighter : fighterSlots) {
                if (fighter != null && fighter.isActive()) out.add(fighter);
            }
            for (BattleCombatant combatant : combatants) {
                if (combatant.isSummon() && combatant.isActive()) out.add(combatant);
            }
            return out;
        }
        for (BattleCombatant c : combatants) {
            if (c.isActive()) out.add(c);
        }
        return out;
    }

    /** Living fighters waiting off the field, in roster order. */
    public List<BattleCombatant> reserves() {
        List<BattleCombatant> out = new ArrayList<>();
        for (BattleCombatant combatant : combatants) {
            if (combatant.isFighter() && combatant.isReserve() && !combatant.isDefeated()) {
                out.add(combatant);
            }
        }
        return out;
    }

    public List<BattleCombatant> activeFighters() {
        if (!fighterSlotsConfigured) {
            return combatants.stream()
                .filter(combatant -> combatant.isFighter() && combatant.isActive())
                .toList();
        }
        return fighterSlots.stream()
            .filter(Objects::nonNull)
            .filter(BattleCombatant::isActive)
            .toList();
    }

    public int activeFighterLimit() {
        return fighterSlotsConfigured ? activeFighterLimit : livingFighters().size();
    }

    public int fighterSlotOf(BattleCombatant combatant) {
        if (combatant == null || !fighterSlotsConfigured) return -1;
        for (int slot = 0; slot < fighterSlots.size(); slot++) {
            if (fighterSlots.get(slot) == combatant) return slot;
        }
        return -1;
    }

    public BattleCombatant fighterAt(int slot) {
        if (!fighterSlotsConfigured || slot < 0 || slot >= fighterSlots.size()) return null;
        BattleCombatant fighter = fighterSlots.get(slot);
        return fighter != null && fighter.isActive() ? fighter : null;
    }

    /** Living (HP > 0) fighters, stable order. */
    public List<BattleCombatant> livingFighters() {
        List<BattleCombatant> out = new ArrayList<>();
        for (BattleCombatant c : combatants) {
            if (c.isFighter() && !c.isDefeated() && !c.isRemoved()) out.add(c);
        }
        return out;
    }

    /** True once the team has no living fighters left. */
    public boolean isEliminated() {
        return livingFighters().isEmpty();
    }

    public BattleCombatant get(CombatantId instanceId) {
        return instanceId == null ? null : byInstance.get(instanceId);
    }

    public boolean contains(CombatantId instanceId) {
        return instanceId != null && byInstance.containsKey(instanceId);
    }

    /** True only when this exact combatant object belongs to the team. */
    public boolean contains(BattleCombatant combatant) {
        if (combatant == null) return false;
        BattleCombatant registered = byInstance.get(combatant.getInstanceId());
        return registered == combatant;
    }

    public int size() {
        return combatants.size();
    }

    /** Package-private: combatants are registered by {@link BattleState}. */
    void add(BattleCombatant combatant) {
        Objects.requireNonNull(combatant, "combatant");
        if (combatant.getInstanceId() == null) {
            throw new IllegalStateException(
                "Combatant must have an instance id before joining a team");
        }
        if (!id.equals(combatant.getTeamId())) {
            throw new IllegalStateException(
                "Combatant " + combatant.getInstanceId() + " belongs to "
                    + combatant.getTeamId() + ", not " + id);
        }
        if (combatant.getRole() == null) {
            throw new IllegalStateException("Combatant must have a role before joining a team");
        }
        for (BattleCombatant member : combatants) {
            if (member == combatant) {
                throw new IllegalStateException("Combatant object is already on team " + id);
            }
        }
        if (byInstance.containsKey(combatant.getInstanceId())) {
            throw new IllegalStateException(
                "Duplicate combatant instance id " + combatant.getInstanceId());
        }
        combatants.add(combatant);
        byInstance.put(combatant.getInstanceId(), combatant);
        if (fighterSlotsConfigured && combatant.isFighter()) {
            if (fighterSlots.size() < activeFighterLimit) {
                fighterSlots.add(combatant);
            } else if (combatant.isActive()) {
                combatant.moveToReserve();
            }
        }
    }

    void configureActiveFighters(int limit) {
        long fighterCount = combatants.stream().filter(BattleCombatant::isFighter).count();
        if (limit < 1 || limit > fighterCount) {
            throw new IllegalArgumentException(
                "Active fighter limit must be between 1 and the roster size");
        }
        activeFighterLimit = limit;
        fighterSlotsConfigured = true;
        fighterSlots.clear();
        int deployed = 0;
        for (BattleCombatant combatant : combatants) {
            if (!combatant.isFighter()) continue;
            if (deployed < limit) {
                if (combatant.isReserve()) combatant.deployFromReserve();
                fighterSlots.add(combatant);
                deployed++;
            } else if (combatant.isActive()) {
                combatant.moveToReserve();
            }
        }
    }

    FighterSlotChange switchFighter(BattleCombatant outgoing, BattleCombatant incoming) {
        int slot = fighterSlotOf(outgoing);
        if (slot < 0 || !outgoing.isActive() || !outgoing.isFighter()) {
            throw new IllegalArgumentException("Switch actor must occupy an active fighter slot");
        }
        if (!contains(incoming) || !incoming.isReserve() || !incoming.isFighter()
            || incoming.isDefeated()) {
            throw new IllegalArgumentException("Switch target must be a living reserve fighter");
        }
        outgoing.moveToReserve();
        incoming.deployFromReserve();
        fighterSlots.set(slot, incoming);
        return new FighterSlotChange(slot, outgoing, incoming);
    }

    List<FighterSlotChange> fillVacantFighterSlots() {
        if (!fighterSlotsConfigured || reserves().isEmpty()) return List.of();
        List<FighterSlotChange> changes = new ArrayList<>();
        for (int slot = 0; slot < fighterSlots.size(); slot++) {
            BattleCombatant outgoing = fighterSlots.get(slot);
            if (outgoing != null && outgoing.isActive()) continue;
            List<BattleCombatant> available = reserves();
            if (available.isEmpty()) break;
            BattleCombatant incoming = available.get(0);
            incoming.deployFromReserve();
            fighterSlots.set(slot, incoming);
            changes.add(new FighterSlotChange(slot, outgoing, incoming));
        }
        return List.copyOf(changes);
    }
}
