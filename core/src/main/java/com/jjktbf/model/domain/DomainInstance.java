package com.jjktbf.model.domain;

import com.jjktbf.model.combat.CombatantId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Mutable battle-scoped state for one established Domain definition. */
public final class DomainInstance {

    private final String instanceId;
    private final DomainDefinition definition;
    private final CombatantId ownerId;
    private final Set<CombatantId> selectedTargetIds;
    private final LinkedHashSet<CombatantId> memberIds = new LinkedHashSet<>();
    private final LinkedHashSet<CombatantId> protectedIds = new LinkedHashSet<>();
    private final int establishedRound;
    private final int establishedTick;
    private int remainingRounds;
    private int remainingTicks;
    private final int maximumInternalBarrierIntegrity;
    private int internalBarrierIntegrity;
    private int remainingCounterUses;
    private int elapsedTicks;
    private double upkeepDebt;

    DomainInstance(
        String instanceId,
        DomainDefinition definition,
        CombatantId ownerId,
        Set<CombatantId> selectedTargetIds,
        int establishedRound,
        int establishedTick,
        int maximumInternalBarrierIntegrity
    ) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.ownerId = ownerId;
        this.selectedTargetIds = Collections.unmodifiableSet(
            new LinkedHashSet<>(selectedTargetIds));
        this.establishedRound = establishedRound;
        this.establishedTick = establishedTick;
        this.remainingRounds = definition.durationRounds();
        this.remainingTicks = definition.durationTicks();
        this.maximumInternalBarrierIntegrity = Math.max(0, maximumInternalBarrierIntegrity);
        this.internalBarrierIntegrity = this.maximumInternalBarrierIntegrity;
        this.remainingCounterUses = definition.counterUses();
    }

    public String instanceId() { return instanceId; }
    public DomainDefinition definition() { return definition; }
    public CombatantId ownerId() { return ownerId; }
    public Set<CombatantId> selectedTargetIds() { return selectedTargetIds; }
    public Set<CombatantId> memberIds() { return Collections.unmodifiableSet(memberIds); }
    public Set<CombatantId> protectedIds() { return Collections.unmodifiableSet(protectedIds); }
    public int establishedRound() { return establishedRound; }
    public int establishedTick() { return establishedTick; }
    public int remainingRounds() { return remainingRounds; }
    public int remainingTicks() { return remainingTicks; }
    public int maximumInternalBarrierIntegrity() { return maximumInternalBarrierIntegrity; }
    public int internalBarrierIntegrity() { return internalBarrierIntegrity; }
    public int remainingCounterUses() { return remainingCounterUses; }
    public int elapsedTicks() { return elapsedTicks; }
    public String sourceLease() { return "DOMAIN:" + instanceId; }

    public boolean contains(CombatantId combatantId) {
        return combatantId != null && memberIds.contains(combatantId);
    }

    public boolean protects(CombatantId combatantId) {
        return combatantId != null && protectedIds.contains(combatantId);
    }

    void addMember(CombatantId combatantId) {
        if (combatantId != null) memberIds.add(combatantId);
    }

    void protect(CombatantId combatantId) {
        if (combatantId != null) protectedIds.add(combatantId);
    }

    int addUpkeepDebt() {
        upkeepDebt += definition.ceUpkeepPerTick();
        int due = (int) Math.floor(upkeepDebt + 1.0e-9);
        upkeepDebt -= due;
        return due;
    }

    int damageInternalBarrier(int amount) {
        int previous = internalBarrierIntegrity;
        internalBarrierIntegrity = Math.max(0, internalBarrierIntegrity - Math.max(0, amount));
        return previous - internalBarrierIntegrity;
    }

    int healInternalBarrier(int amount) {
        int previous = internalBarrierIntegrity;
        long healed = (long) internalBarrierIntegrity + Math.max(0, amount);
        internalBarrierIntegrity = (int) Math.min(maximumInternalBarrierIntegrity, healed);
        return internalBarrierIntegrity - previous;
    }

    boolean consumeCounterUse() {
        if (remainingCounterUses == -1) return false;
        if (remainingCounterUses > 0) remainingCounterUses--;
        return remainingCounterUses == 0;
    }

    boolean advanceTickDuration() {
        if (remainingRounds != 0 || remainingTicks <= 0) return false;
        remainingTicks--;
        return remainingTicks == 0;
    }

    void beginActiveTick() { elapsedTicks++; }

    boolean advanceRoundDuration() {
        if (remainingRounds <= 0) return false;
        remainingRounds--;
        return remainingRounds == 0 && remainingTicks == 0;
    }
}
