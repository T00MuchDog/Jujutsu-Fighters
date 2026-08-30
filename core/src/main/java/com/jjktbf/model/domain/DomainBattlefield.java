package com.jjktbf.model.domain;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Authoritative battle-wide owner of active Domains, counters, and clashes. */
public final class DomainBattlefield {

    public record DeclarationResult(boolean accepted, String error) {
        static DeclarationResult success() { return new DeclarationResult(true, null); }
        static DeclarationResult rejection(String error) {
            return new DeclarationResult(false, error);
        }
    }

    private record PendingDeclaration(
        DomainDefinition definition,
        CombatantId ownerId,
        Set<CombatantId> selectedTargetIds,
        int round,
        int tick
    ) { }

    private record ClashKey(String first, String second) {
        static ClashKey of(String left, String right) {
            return left.compareTo(right) <= 0
                ? new ClashKey(left, right) : new ClashKey(right, left);
        }
    }

    private final List<DomainInstance> active = new ArrayList<>();
    private final List<PendingDeclaration> pending = new ArrayList<>();
    private final LinkedHashSet<ClashKey> activeClashes = new LinkedHashSet<>();
    private long instanceSequence;

    public List<DomainInstance> activeDomains() { return List.copyOf(active); }

    public List<DomainClash> clashes() {
        return activeClashes.stream()
            .map(key -> new DomainClash(key.first(), key.second()))
            .toList();
    }

    public DomainInstance find(String instanceId) {
        if (instanceId == null) return null;
        return active.stream().filter(domain -> instanceId.equals(domain.instanceId()))
            .findFirst().orElse(null);
    }

    public boolean hasPendingDeclarations() { return !pending.isEmpty(); }

    public int remainingTimelineTicks() {
        return active.stream()
            .filter(domain -> domain.remainingRounds() == 0)
            .mapToInt(DomainInstance::remainingTicks)
            .max().orElse(0);
    }

    public DeclarationResult queueDeclaration(
        BattleState state,
        DomainDefinition definition,
        BattleCombatant owner,
        Collection<BattleCombatant> selectedTargets,
        int tick
    ) {
        if (state == null || definition == null || owner == null
            || owner.getInstanceId() == null || !owner.isActive()
            || state.teamOf(owner) == null) {
            return DeclarationResult.rejection("A living battle participant is required.");
        }
        if (!definition.antiDomain()) {
            if (!owner.getCharacter().canEstablishDomain(definition.id())) {
                return DeclarationResult.rejection("This Domain is not unlocked.");
            }
            if (!owner.getCharacter().canUseTechnique(definition.requiredTechniqueName())) {
                return DeclarationResult.rejection("The required technique is unavailable.");
            }
            if (owner.isTechniqueLocked(definition.requiredTechniqueName())) {
                return DeclarationResult.rejection("The required technique is temporarily locked.");
            }
        }
        boolean duplicate = pending.stream().anyMatch(candidate ->
            candidate.ownerId().equals(owner.getInstanceId())
                && candidate.definition().antiDomain() == definition.antiDomain());
        if (duplicate) {
            return DeclarationResult.rejection(
                "Only one Domain of this kind may be declared by a combatant on one tick.");
        }
        LinkedHashSet<CombatantId> selected = new LinkedHashSet<>();
        if (selectedTargets != null) {
            selectedTargets.stream().filter(Objects::nonNull)
                .filter(target -> target.getInstanceId() != null)
                .map(BattleCombatant::getInstanceId).forEach(selected::add);
        }
        pending.add(new PendingDeclaration(
            definition, owner.getInstanceId(), Set.copyOf(selected),
            state.getRoundNumber(), tick));
        return DeclarationResult.success();
    }

    /** Establish every declaration from this tick before any immediate program runs. */
    public List<CombatEvent> resolveDeclarations(
        BattleState state,
        DomainEffectExecutor executor,
        int tick
    ) {
        if (pending.isEmpty()) return List.of();
        List<CombatEvent> events = new ArrayList<>();
        List<PendingDeclaration> declarations = List.copyOf(pending);
        pending.clear();
        List<DomainInstance> established = new ArrayList<>();

        for (PendingDeclaration declaration : declarations) {
            BattleCombatant owner = state.combatant(declaration.ownerId());
            if (owner == null || !owner.isActive()) continue;
            List<DomainInstance> replaced = active.stream()
                .filter(domain -> domain.ownerId().equals(declaration.ownerId()))
                .filter(domain -> domain.definition().antiDomain()
                    == declaration.definition().antiDomain())
                .toList();
            for (DomainInstance previous : replaced) {
                collapse(previous, DomainCollapseReason.REPLACED, state, executor, tick, events);
            }

            DomainInstance instance = new DomainInstance(
                "domain-" + (++instanceSequence), declaration.definition(),
                declaration.ownerId(), declaration.selectedTargetIds(),
                declaration.round(), declaration.tick());
            captureInitialMembers(instance, state);
            active.add(instance);
            established.add(instance);
            events.add(domainEvent(instance.definition().antiDomain()
                    ? CombatEvent.Type.DOMAIN_COUNTER_ESTABLISHED
                    : CombatEvent.Type.DOMAIN_ESTABLISHED,
                instance, owner, tick,
                owner.getCharacter().getName() + " establishes "
                    + instance.definition().name() + "!").build());
        }

        refreshClashes(state, tick, events);
        for (DomainInstance instance : established) {
            if (!active.contains(instance)) continue;
            runPrograms(instance, DomainTrigger.ON_ESTABLISH, null, state, executor, tick, events);
            for (CombatantId memberId : List.copyOf(instance.memberIds())) {
                BattleCombatant entrant = state.combatant(memberId);
                if (entrant != null) {
                    runPrograms(instance, DomainTrigger.ON_MEMBER_ENTER, entrant,
                        state, executor, tick, events);
                }
            }
        }
        refreshClashes(state, tick, events);
        return events;
    }

    /** Charge upkeep, resolve continuous pressure/programs, and expire tick tails. */
    public List<CombatEvent> processTick(
        BattleState state,
        DomainEffectExecutor executor,
        int tick
    ) {
        if (active.isEmpty()) return List.of();
        List<CombatEvent> events = new ArrayList<>();
        reconcileOwners(state, executor, tick, events);

        for (DomainInstance instance : List.copyOf(active)) {
            BattleCombatant owner = state.combatant(instance.ownerId());
            if (owner == null || !owner.isActive()) continue;
            int due = instance.addUpkeepDebt();
            if (due <= 0) continue;
            int drained = owner.drainCe(due);
            if (drained > 0) {
                events.add(CombatEvent.of(CombatEvent.Type.CE_DRAINED)
                    .source(owner).target(owner).intValue(drained).tick(tick)
                    .domainInstanceId(instance.instanceId())
                    .domainId(instance.definition().id())
                    .domainName(instance.definition().name()).build());
            }
            if (drained < due) {
                collapse(instance, DomainCollapseReason.UPKEEP_FAILED,
                    state, executor, tick, events);
            }
        }

        applyClashPressure(state, executor, tick, events);
        refreshClashes(state, tick, events);
        for (DomainInstance instance : List.copyOf(active)) {
            instance.beginActiveTick();
            runPrograms(instance, DomainTrigger.EACH_TICK, null,
                state, executor, tick, events);
        }
        for (DomainInstance instance : List.copyOf(active)) {
            if (instance.advanceTickDuration()) {
                collapse(instance, DomainCollapseReason.DURATION_EXPIRED,
                    state, executor, tick, events);
            }
        }
        reconcileOwners(state, executor, tick, events);
        refreshClashes(state, tick, events);
        return events;
    }

    public List<CombatEvent> processRoundStart(
        BattleState state,
        DomainEffectExecutor executor
    ) {
        List<CombatEvent> events = new ArrayList<>();
        reconcileOwners(state, executor, 0, events);
        for (DomainInstance instance : List.copyOf(active)) {
            runPrograms(instance, DomainTrigger.ON_ROUND_START, null,
                state, executor, 0, events);
        }
        return events;
    }

    public List<CombatEvent> processRoundEnd(
        BattleState state,
        DomainEffectExecutor executor
    ) {
        List<CombatEvent> events = new ArrayList<>();
        for (DomainInstance instance : List.copyOf(active)) {
            runPrograms(instance, DomainTrigger.ON_ROUND_END, null,
                state, executor, 0, events);
        }
        for (DomainInstance instance : List.copyOf(active)) {
            if (instance.advanceRoundDuration()) {
                collapse(instance, DomainCollapseReason.DURATION_EXPIRED,
                    state, executor, 0, events);
            }
        }
        reconcileOwners(state, executor, 0, events);
        refreshClashes(state, 0, events);
        return events;
    }

    public List<CombatEvent> onCombatantEntered(
        BattleState state,
        BattleCombatant entrant,
        DomainEffectExecutor executor,
        int tick
    ) {
        if (entrant == null || entrant.getInstanceId() == null) return List.of();
        List<CombatEvent> events = new ArrayList<>();
        for (DomainInstance instance : List.copyOf(active)) {
            boolean joins = switch (instance.definition().entrantPolicy()) {
                case SNAPSHOT -> false;
                case ALL_NEW_COMBATANTS -> true;
                case FOLLOW_SUMMONER -> entrant.getSummonerId() != null
                    && instance.contains(entrant.getSummonerId());
            };
            if (!joins) continue;
            instance.addMember(entrant.getInstanceId());
            applyProtection(instance, entrant, state);
            runPrograms(instance, DomainTrigger.ON_MEMBER_ENTER, entrant,
                state, executor, tick, events);
        }
        refreshClashes(state, tick, events);
        return events;
    }

    public List<CombatEvent> onOwnerMove(
        BattleState state,
        BattleCombatant owner,
        DomainEffectExecutor executor,
        int tick
    ) {
        if (owner == null || owner.getInstanceId() == null) return List.of();
        List<CombatEvent> events = new ArrayList<>();
        for (DomainInstance instance : List.copyOf(active)) {
            if (instance.definition().antiDomain()
                && instance.ownerId().equals(owner.getInstanceId())
                && instance.definition().counterBreakOnOwnerMove()) {
                collapse(instance, DomainCollapseReason.OWNER_ACTED,
                    state, executor, tick, events);
            }
        }
        refreshClashes(state, tick, events);
        return events;
    }

    public List<CombatEvent> reconcileOwners(
        BattleState state,
        DomainEffectExecutor executor,
        int tick
    ) {
        List<CombatEvent> events = new ArrayList<>();
        reconcileOwners(state, executor, tick, events);
        refreshClashes(state, tick, events);
        return events;
    }

    public List<CombatEvent> collapseAll(
        BattleState state,
        DomainCollapseReason reason,
        DomainEffectExecutor executor,
        int tick
    ) {
        List<CombatEvent> events = new ArrayList<>();
        pending.clear();
        for (DomainInstance instance : List.copyOf(active)) {
            collapse(instance, reason, state, executor, tick, events);
        }
        refreshClashes(state, tick, events);
        return events;
    }

    private void reconcileOwners(
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        for (DomainInstance instance : List.copyOf(active)) {
            BattleCombatant owner = state.combatant(instance.ownerId());
            if (owner == null || owner.isRemoved()) {
                collapse(instance, DomainCollapseReason.OWNER_REMOVED,
                    state, executor, tick, events);
            } else if (owner.isDefeated() || !owner.isActive()) {
                collapse(instance, DomainCollapseReason.OWNER_DEFEATED,
                    state, executor, tick, events);
            }
        }
    }

    private void captureInitialMembers(DomainInstance instance, BattleState state) {
        BattleCombatant owner = state.combatant(instance.ownerId());
        List<BattleCombatant> captured = switch (instance.definition().capturePolicy()) {
            case NONE -> List.of();
            case ALL_ACTIVE -> state.activeCombatants();
            case ALL_ENEMIES -> owner == null ? List.of() : state.activeEnemiesOf(owner);
            case SELECTED_TARGETS -> instance.selectedTargetIds().stream()
                .map(state::combatant).filter(Objects::nonNull).filter(BattleCombatant::isActive)
                .toList();
            case OWNER_AND_SELECTED -> {
                List<BattleCombatant> members = new ArrayList<>();
                if (owner != null) members.add(owner);
                instance.selectedTargetIds().stream().map(state::combatant)
                    .filter(Objects::nonNull).filter(BattleCombatant::isActive)
                    .filter(member -> !members.contains(member)).forEach(members::add);
                yield members;
            }
        };
        captured.forEach(member -> instance.addMember(member.getInstanceId()));
        for (BattleCombatant member : captured) applyProtection(instance, member, state);
    }

    private static void applyProtection(
        DomainInstance instance,
        BattleCombatant candidate,
        BattleState state
    ) {
        BattleCombatant owner = state.combatant(instance.ownerId());
        if (owner == null || candidate == null) return;
        boolean protectedMember = switch (instance.definition().protectionPolicy()) {
            case NONE -> false;
            case OWNER -> candidate == owner;
            case OWNER_AND_ALLIES -> candidate == owner || candidate.isAlliedWith(owner);
            case OWNER_AND_SELECTED -> candidate == owner
                || instance.selectedTargetIds().contains(candidate.getInstanceId());
        };
        if (protectedMember) instance.protect(candidate.getInstanceId());
    }

    private void runPrograms(
        DomainInstance instance,
        DomainTrigger trigger,
        BattleCombatant entrant,
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        if (executor == null || !active.contains(instance)) return;
        runRows(instance, instance.definition().sureHitEffects(), true,
            trigger, entrant, state, executor, tick, events);
        runRows(instance, instance.definition().fieldEffects(), false,
            trigger, entrant, state, executor, tick, events);
        runRows(instance, instance.definition().casterEffects(), false,
            trigger, entrant, state, executor, tick, events);
        runRows(instance, instance.definition().barrierEffects(), false,
            trigger, entrant, state, executor, tick, events);
        runRows(instance, instance.definition().procedureEffects(), false,
            trigger, entrant, state, executor, tick, events);
    }

    private void runRows(
        DomainInstance instance,
        List<AbilityEffectData> rows,
        boolean sureHit,
        DomainTrigger trigger,
        BattleCombatant entrant,
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        for (AbilityEffectData row : rows) {
            if (!active.contains(instance) || row == null
                || !trigger.name().equalsIgnoreCase(row.domainTrigger)) continue;
            int interval = row.domainIntervalTicks == null ? 1 : row.domainIntervalTicks;
            if (trigger == DomainTrigger.EACH_TICK
                && (instance.elapsedTicks() - 1) % Math.max(1, interval) != 0) continue;
            DomainAudience audience;
            try { audience = DomainAudience.valueOf(row.domainAudience); }
            catch (RuntimeException exception) { continue; }
            if (audience == DomainAudience.BARRIER) {
                applyBarrierRow(instance, row, state, tick, events);
                continue;
            }
            for (BattleCombatant target : audience(
                instance, audience, entrant, state)) {
                if (!active.contains(instance) || target == null || !target.isActive()) continue;
                if (sureHit && !admitSureHit(instance, row, target, state, executor, tick, events)) {
                    continue;
                }
                AbilityEffectData effect = row.copy();
                effect.target = target == state.combatant(instance.ownerId())
                    ? AbilityEffectTarget.SELF.name() : AbilityEffectTarget.ENEMY.name();
                events.addAll(executor.execute(
                    state, state.combatant(instance.ownerId()), target, effect, tick,
                    instance.sourceLease()));
                if (sureHit) {
                    events.add(domainEvent(CombatEvent.Type.DOMAIN_SURE_HIT_APPLIED,
                        instance, state.combatant(instance.ownerId()), tick,
                        instance.definition().name() + " admits its sure-hit against "
                            + target.getCharacter().getName() + ".")
                        .withTarget(target));
                }
            }
        }
    }

    private boolean admitSureHit(
        DomainInstance source,
        AbilityEffectData row,
        BattleCombatant target,
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        if (source.protects(target.getInstanceId())) return false;
        if (isClashing(source.instanceId())) {
            events.add(negatedEvent(source, target, tick,
                "The Domain clash suppresses the sure-hit."));
            return false;
        }
        DomainDeliveryClass delivery;
        try { delivery = DomainDeliveryClass.valueOf(row.domainDeliveryClass); }
        catch (RuntimeException exception) { delivery = DomainDeliveryClass.EFFECT; }
        for (DomainInstance counter : List.copyOf(active)) {
            if (!counter.definition().antiDomain() || !counter.protects(target.getInstanceId())) {
                continue;
            }
            BattleCombatant counterOwner = state.combatant(counter.ownerId());
            if (counterOwner == null || !target.isAlliedWith(counterOwner)) continue;
            if (counter.definition().counterPotency() < source.definition().clashPressurePerTick()) {
                continue;
            }
            boolean blocks = switch (counter.definition().counterType()) {
                case NONE -> false;
                case SURE_HIT_NULLIFICATION -> true;
                case SURE_HIT_INTERCEPTION -> delivery == DomainDeliveryClass.ATTACK;
                case TECHNIQUE_CONTACT_NULLIFICATION -> delivery != DomainDeliveryClass.RULE;
            };
            if (!blocks) continue;
            events.add(negatedEvent(source, target, tick,
                counter.definition().name() + " negates the sure-hit."));
            if (counter.consumeCounterUse()) {
                collapse(counter, DomainCollapseReason.COUNTER_EXHAUSTED,
                    state, executor, tick, events);
            }
            return false;
        }
        return true;
    }

    private List<BattleCombatant> audience(
        DomainInstance instance,
        DomainAudience audience,
        BattleCombatant entrant,
        BattleState state
    ) {
        BattleCombatant owner = state.combatant(instance.ownerId());
        if (owner == null) return List.of();
        List<BattleCombatant> candidates = switch (audience) {
            case OWNER -> List.of(owner);
            case ENTERING_MEMBER -> entrant == null ? List.of() : List.of(entrant);
            case ENEMY_MEMBERS -> members(instance, state).stream()
                .filter(member -> !member.isAlliedWith(owner)).toList();
            case ALLY_MEMBERS -> members(instance, state).stream()
                .filter(member -> member.isAlliedWith(owner)).toList();
            case ALL_MEMBERS -> members(instance, state);
            case UNPROTECTED_MEMBERS -> members(instance, state).stream()
                .filter(member -> !instance.protects(member.getInstanceId())).toList();
            case EXTERIOR_ENEMIES -> state.activeEnemiesOf(owner).stream()
                .filter(enemy -> !instance.contains(enemy.getInstanceId())).toList();
            case BARRIER -> List.of();
        };
        return candidates.stream().filter(BattleCombatant::isActive)
            .filter(candidate -> audience == DomainAudience.OWNER
                || recognized(instance, candidate, owner))
            .distinct().toList();
    }

    private static List<BattleCombatant> members(
        DomainInstance instance,
        BattleState state
    ) {
        return instance.memberIds().stream().map(state::combatant)
            .filter(Objects::nonNull).filter(BattleCombatant::isActive).toList();
    }

    private static boolean recognized(
        DomainInstance instance,
        BattleCombatant candidate,
        BattleCombatant owner
    ) {
        return switch (instance.definition().recognitionPolicy()) {
            case ALL_MEMBERS -> true;
            case CURSED_ENERGY_USERS -> candidate.getMaxCursedEnergy() > 0;
            case ENEMIES_ONLY -> !candidate.isAlliedWith(owner);
        };
    }

    private void applyBarrierRow(
        DomainInstance source,
        AbilityEffectData row,
        BattleState state,
        int tick,
        List<CombatEvent> events
    ) {
        AbilityEffectType type;
        try { type = AbilityEffectType.fromName(row.type); }
        catch (RuntimeException exception) { return; }
        if (type == AbilityEffectType.HEAL_HP) {
            int amount = effectAmount(row, source.definition().internalBarrierIntegrity());
            int healed = source.healInternalBarrier(amount) + source.healExternalBarrier(amount);
            if (healed > 0) {
                events.add(domainEvent(CombatEvent.Type.DOMAIN_BARRIER_DAMAGED,
                    source, state.combatant(source.ownerId()), tick,
                    source.definition().name() + " repairs its barrier.").build());
            }
            return;
        }
        if (type != AbilityEffectType.DEAL_DIRECT_DAMAGE
            && type != AbilityEffectType.INSTANT_KILL) return;
        for (DomainInstance target : List.copyOf(active)) {
            if (target == source || target.definition().antiDomain()
                || !hostile(source, target, state) || !overlaps(source, target)) continue;
            int maximum = target.definition().internalBarrierIntegrity();
            int amount = type == AbilityEffectType.INSTANT_KILL
                ? maximum : effectAmount(row, maximum);
            boolean exterior = source.definition().topology() == DomainTopology.OPEN
                && target.definition().topology() == DomainTopology.CLOSED;
            int damage = exterior ? target.damageExternalBarrier(amount)
                : target.damageInternalBarrier(amount);
            if (damage > 0) events.add(barrierEvent(source, target, damage, tick));
        }
    }

    private void applyClashPressure(
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        if (activeClashes.isEmpty()) return;
        for (ClashKey clash : List.copyOf(activeClashes)) {
            DomainInstance first = find(clash.first());
            DomainInstance second = find(clash.second());
            if (first == null || second == null) continue;
            int firstDamage = first.damageInternalBarrier(
                second.definition().clashPressurePerTick());
            int secondDamage = second.damageInternalBarrier(
                first.definition().clashPressurePerTick());
            if (firstDamage > 0) events.add(barrierEvent(second, first, firstDamage, tick));
            if (secondDamage > 0) events.add(barrierEvent(first, second, secondDamage, tick));

            if (first.definition().topology() == DomainTopology.OPEN
                && second.definition().topology() == DomainTopology.CLOSED) {
                int damage = second.damageExternalBarrier(
                    first.definition().externalPressurePerTick());
                if (damage > 0) events.add(barrierEvent(first, second, damage, tick));
            }
            if (second.definition().topology() == DomainTopology.OPEN
                && first.definition().topology() == DomainTopology.CLOSED) {
                int damage = first.damageExternalBarrier(
                    second.definition().externalPressurePerTick());
                if (damage > 0) events.add(barrierEvent(second, first, damage, tick));
            }
        }
        for (DomainInstance instance : List.copyOf(active)) {
            DomainCollapseReason reason = instance.internalBarrierIntegrity() == 0
                ? DomainCollapseReason.INTERNAL_BARRIER_BROKEN
                : instance.definition().topology() == DomainTopology.CLOSED
                    && instance.externalBarrierIntegrity() == 0
                        ? DomainCollapseReason.EXTERNAL_BARRIER_BROKEN : null;
            if (reason != null) collapse(instance, reason, state, executor, tick, events);
        }
    }

    private void refreshClashes(BattleState state, int tick, List<CombatEvent> events) {
        LinkedHashSet<ClashKey> next = new LinkedHashSet<>();
        for (int firstIndex = 0; firstIndex < active.size(); firstIndex++) {
            DomainInstance first = active.get(firstIndex);
            if (first.definition().antiDomain()) continue;
            for (int secondIndex = firstIndex + 1; secondIndex < active.size(); secondIndex++) {
                DomainInstance second = active.get(secondIndex);
                if (second.definition().antiDomain() || !hostile(first, second, state)
                    || !overlaps(first, second)) continue;
                next.add(ClashKey.of(first.instanceId(), second.instanceId()));
            }
        }
        for (ClashKey started : next) {
            if (activeClashes.contains(started)) continue;
            DomainInstance first = find(started.first());
            DomainInstance second = find(started.second());
            events.add(CombatEvent.of(CombatEvent.Type.DOMAIN_CLASH_STARTED)
                .source(first == null ? null : state.combatant(first.ownerId()))
                .domainInstanceId(started.first())
                .relatedDomainInstanceId(started.second()).tick(tick)
                .message("Opposing Domains enter a clash.").build());
        }
        for (ClashKey ended : activeClashes) {
            if (next.contains(ended)) continue;
            events.add(CombatEvent.of(CombatEvent.Type.DOMAIN_CLASH_ENDED)
                .domainInstanceId(ended.first())
                .relatedDomainInstanceId(ended.second()).tick(tick)
                .message("The Domain clash ends.").build());
        }
        activeClashes.clear();
        activeClashes.addAll(next);
    }

    private static boolean hostile(
        DomainInstance first,
        DomainInstance second,
        BattleState state
    ) {
        BattleCombatant firstOwner = state.combatant(first.ownerId());
        BattleCombatant secondOwner = state.combatant(second.ownerId());
        return firstOwner != null && secondOwner != null
            && !firstOwner.isAlliedWith(secondOwner);
    }

    private static boolean overlaps(DomainInstance first, DomainInstance second) {
        for (CombatantId member : first.memberIds()) {
            if (second.memberIds().contains(member)) return true;
        }
        return false;
    }

    private boolean isClashing(String instanceId) {
        return activeClashes.stream().anyMatch(clash ->
            clash.first().equals(instanceId) || clash.second().equals(instanceId));
    }

    private void collapse(
        DomainInstance instance,
        DomainCollapseReason reason,
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        if (!active.remove(instance)) return;
        BattleCombatant owner = state.combatant(instance.ownerId());
        if (reason != DomainCollapseReason.BATTLE_ENDED && executor != null) {
            runCollapsePrograms(instance, state, executor, tick, events);
        }
        for (BattleCombatant combatant : state.allCombatants()) {
            int previousMaxHp = combatant.getMaxHp();
            int previousMaxCe = combatant.getMaxCursedEnergy();
            combatant.removeEffectsByLease(instance.sourceLease());
            appendMaximumEvents(owner, combatant, previousMaxHp, previousMaxCe, tick, events);
        }
        if (owner != null && !instance.definition().antiDomain()
            && reason != DomainCollapseReason.BATTLE_ENDED
            && (instance.definition().burnoutRounds() != 0
                || instance.definition().burnoutTicks() != 0)) {
            AbilityEffectData burnout = AbilityEffectType.TEMP_LOCK_TECHNIQUE.createDefault();
            burnout.stringValue = instance.definition().requiredTechniqueName();
            burnout.durationRounds = instance.definition().burnoutRounds();
            burnout.durationTicks = instance.definition().burnoutTicks();
            owner.addRuntimeAbilityEffect(
                burnout, state.getRoundNumber(), state.getCurrentPhase(),
                "DOMAIN_BURNOUT:" + burnout.stringValue.toLowerCase(), owner, tick);
        }
        events.add(CombatEvent.of(CombatEvent.Type.DOMAIN_COLLAPSED)
            .source(owner).tick(tick)
            .domainInstanceId(instance.instanceId())
            .domainId(instance.definition().id())
            .domainName(instance.definition().name())
            .domainCollapseReason(reason.name())
            .message(instance.definition().name() + " collapses ("
                + reason.name().toLowerCase().replace('_', ' ') + ").").build());
    }

    private void runCollapsePrograms(
        DomainInstance instance,
        BattleState state,
        DomainEffectExecutor executor,
        int tick,
        List<CombatEvent> events
    ) {
        List<List<AbilityEffectData>> channels = List.of(
            instance.definition().sureHitEffects(), instance.definition().fieldEffects(),
            instance.definition().casterEffects(), instance.definition().barrierEffects(),
            instance.definition().procedureEffects());
        for (List<AbilityEffectData> channel : channels) {
            for (AbilityEffectData row : channel) {
                if (row == null || !DomainTrigger.ON_COLLAPSE.name()
                    .equalsIgnoreCase(row.domainTrigger)) continue;
                DomainAudience audience;
                try { audience = DomainAudience.valueOf(row.domainAudience); }
                catch (RuntimeException exception) { continue; }
                if (audience == DomainAudience.BARRIER) continue;
                for (BattleCombatant target : audience(instance, audience, null, state)) {
                    AbilityEffectData effect = row.copy();
                    effect.target = target == state.combatant(instance.ownerId())
                        ? AbilityEffectTarget.SELF.name() : AbilityEffectTarget.ENEMY.name();
                    events.addAll(executor.execute(
                        state, state.combatant(instance.ownerId()), target, effect, tick, null));
                }
            }
        }
    }

    private static CombatEvent barrierEvent(
        DomainInstance source,
        DomainInstance target,
        int damage,
        int tick
    ) {
        return CombatEvent.of(CombatEvent.Type.DOMAIN_BARRIER_DAMAGED)
            .intValue(damage).tick(tick)
            .domainInstanceId(target.instanceId())
            .relatedDomainInstanceId(source.instanceId())
            .domainId(target.definition().id())
            .domainName(target.definition().name())
            .message(target.definition().name() + " loses " + damage + " barrier integrity.")
            .build();
    }

    private static CombatEvent negatedEvent(
        DomainInstance source,
        BattleCombatant target,
        int tick,
        String message
    ) {
        return domainEvent(CombatEvent.Type.DOMAIN_SURE_HIT_NEGATED,
            source, null, tick, message).withTarget(target);
    }

    private static EventBuilderResult domainEvent(
        CombatEvent.Type type,
        DomainInstance instance,
        BattleCombatant owner,
        int tick,
        String message
    ) {
        return new EventBuilderResult(CombatEvent.of(type)
            .source(owner).tick(tick)
            .domainInstanceId(instance.instanceId())
            .domainId(instance.definition().id())
            .domainName(instance.definition().name())
            .message(message));
    }

    /** Small adapter that keeps target assignment available without exposing event builders. */
    private record EventBuilderResult(CombatEvent.Builder builder) {
        CombatEvent withTarget(BattleCombatant target) { return builder.target(target).build(); }
        CombatEvent build() { return builder.build(); }
    }

    private static int effectAmount(AbilityEffectData effect, int maximum) {
        return AbilityEffectType.valueMode(effect) == AbilityEffectType.ValueMode.FLAT
            ? Math.max(0, effect.intValue == null ? 0 : effect.intValue)
            : Math.max(0, (int) Math.round(maximum
                * (effect.doubleValue == null ? 0.0 : effect.doubleValue)));
    }

    private static void appendMaximumEvents(
        BattleCombatant source,
        BattleCombatant target,
        int previousMaxHp,
        int previousMaxCe,
        int tick,
        List<CombatEvent> events
    ) {
        if (target.getMaxHp() != previousMaxHp) {
            events.add(CombatEvent.of(CombatEvent.Type.MAX_HP_CHANGED)
                .source(source).target(target).intValue(target.getMaxHp()).tick(tick).build());
        }
        if (target.getMaxCursedEnergy() != previousMaxCe) {
            events.add(CombatEvent.of(CombatEvent.Type.MAX_CE_CHANGED)
                .source(source).target(target).intValue(target.getMaxCursedEnergy()).tick(tick).build());
        }
    }
}
