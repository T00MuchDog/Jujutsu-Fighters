package com.jjktbf.multiplayer.protocol;

import com.jjktbf.model.move.Targeting;
import com.jjktbf.model.move.DefenseTargeting;
import com.jjktbf.model.move.AttackLaunchMode;

import java.util.List;

/** Canonical move fields needed to display and construct plan intent. */
public record MoveState(
    String moveId,
    String name,
    String description,
    String category,
    List<String> tags,
    PlanBoard board,
    int basePower,
    List<HitComponentState> hitComponents,
    double baseAccuracy,
    boolean neverMiss,
    int apCost,
    int unleashPoint,
    boolean hasCeCost,
    int baseCeCost,
    int effectiveCeCost,
    int minCeCost,
    int maxCeCost,
    int moveCap,
    boolean available,
    String restrictionReason,
    String summonCharacterId,
    List<String> summonedCharacterIds,
    String aoeType,
    int aoeTargetCount,
    String commandMode,
    String requiredTechniqueId,
    String defenseTargeting,
    int defenseTargetCount,
    @com.fasterxml.jackson.annotation.JsonAlias("pairTargeting")
    String targeting,
    String attackLaunchMode,
    String attackLaunchMoveId,
    List<BoundedResourceTransactionState> boundedResourceTransactions,
    ReinforcementMoveState reinforcement
) {
    public MoveState {
        tags = tags == null ? List.of() : List.copyOf(tags);
        hitComponents = hitComponents == null ? List.of() : List.copyOf(hitComponents);
        summonedCharacterIds = summonedCharacterIds == null
            ? List.of() : List.copyOf(summonedCharacterIds);
        defenseTargeting = DefenseTargeting.fromName(defenseTargeting).name();
        defenseTargetCount = defenseTargetCount < 2 ? 2 : defenseTargetCount;
        targeting = Targeting.fromName(targeting).name();
        AttackLaunchMode launchMode = AttackLaunchMode.fromName(attackLaunchMode);
        attackLaunchMode = launchMode == null ? null : launchMode.name();
        boundedResourceTransactions = boundedResourceTransactions == null
            ? List.of() : List.copyOf(boundedResourceTransactions);
    }

    /** Source-compatible constructor for protocol-v19 callers. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason,
        String summonCharacterId,
        List<String> summonedCharacterIds,
        String aoeType,
        int aoeTargetCount,
        String commandMode,
        String requiredTechniqueId,
        String defenseTargeting,
        int defenseTargetCount,
        String targeting,
        String attackLaunchMode,
        String attackLaunchMoveId
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            summonCharacterId, summonedCharacterIds, aoeType, aoeTargetCount, commandMode,
            requiredTechniqueId, defenseTargeting, defenseTargetCount, targeting,
            attackLaunchMode, attackLaunchMoveId, List.of(), null);
    }

    /** Source-compatible constructor for callers predating hybrid launch metadata. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason,
        String summonCharacterId,
        List<String> summonedCharacterIds,
        String aoeType,
        int aoeTargetCount,
        String commandMode,
        String requiredTechniqueId,
        String defenseTargeting,
        int defenseTargetCount,
        String targeting
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            summonCharacterId, summonedCharacterIds, aoeType, aoeTargetCount, commandMode,
            requiredTechniqueId, defenseTargeting, defenseTargetCount, targeting,
            null, null, List.of(), null);
    }

    /** Source-compatible constructor for callers predating pair targeting. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason,
        String summonCharacterId,
        List<String> summonedCharacterIds,
        String aoeType,
        int aoeTargetCount,
        String commandMode,
        String requiredTechniqueId
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            summonCharacterId, summonedCharacterIds, aoeType, aoeTargetCount, commandMode,
            requiredTechniqueId, "SELF", 2, "DEFAULT", null, null, List.of(), null);
    }

    /** Source-compatible constructor for protocol-v12 callers. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason,
        String summonCharacterId,
        List<String> summonedCharacterIds,
        String aoeType,
        int aoeTargetCount,
        String commandMode
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            summonCharacterId, summonedCharacterIds, aoeType, aoeTargetCount, commandMode,
            null, "SELF", 2, "DEFAULT", null, null, List.of(), null);
    }

    /** Source-compatible constructor for early protocol-v12 callers. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason,
        String summonCharacterId,
        List<String> summonedCharacterIds,
        String aoeType,
        int aoeTargetCount
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            summonCharacterId, summonedCharacterIds, aoeType, aoeTargetCount, null, null,
            "SELF", 2, "DEFAULT", null, null, List.of(), null);
    }

    /** Source-compatible constructor for protocol-v11 callers with summon metadata. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason,
        String summonCharacterId,
        List<String> summonedCharacterIds
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            summonCharacterId, summonedCharacterIds, null, 0, null, null,
            "SELF", 2, "DEFAULT", null, null, List.of(), null);
    }

    /** Source-compatible constructor for protocol-v9 callers. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        int moveCap,
        boolean available,
        String restrictionReason
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, moveCap, available, restrictionReason,
            null, List.of(), null, 0, null, null, "SELF", 2, "DEFAULT", null,
            null, List.of(), null);
    }

    /** Source-compatible constructor for protocol-v7 callers with hit components. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        List<HitComponentState> hitComponents,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        boolean available,
        String restrictionReason
    ) {
        this(moveId, name, description, category, tags, board, basePower, hitComponents,
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, 0, available, restrictionReason,
            null, List.of(), null, 0, null, null, "SELF", 2, "DEFAULT", null,
            null, List.of(), null);
    }

    /** Source-compatible constructor for protocol-v6 callers. */
    public MoveState(
        String moveId,
        String name,
        String description,
        String category,
        List<String> tags,
        PlanBoard board,
        int basePower,
        double baseAccuracy,
        boolean neverMiss,
        int apCost,
        int unleashPoint,
        boolean hasCeCost,
        int baseCeCost,
        int effectiveCeCost,
        int minCeCost,
        int maxCeCost,
        boolean available,
        String restrictionReason
    ) {
        this(moveId, name, description, category, tags, board, basePower, List.of(),
            baseAccuracy, neverMiss, apCost, unleashPoint, hasCeCost, baseCeCost,
            effectiveCeCost, minCeCost, maxCeCost, 0, available, restrictionReason,
            null, List.of(), null, 0, null, null, "SELF", 2, "DEFAULT");
    }
}
