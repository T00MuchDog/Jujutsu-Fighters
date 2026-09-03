package com.jjktbf.model.domain;

/**
 * Stable snapshot of one pair of opposing, overlapping Domain instances.
 * When progress is nonzero, {@code leaderInstanceId} names the stronger side
 * and {@code takeoverProgress} is the fraction of integrity lost by the
 * weaker Domain (0 to 1). The accessor name is retained for wire stability.
 */
public record DomainClash(
    String firstInstanceId,
    String secondInstanceId,
    String leaderInstanceId,
    double takeoverProgress
) {
    public DomainClash {
        if (firstInstanceId == null || secondInstanceId == null
            || firstInstanceId.equals(secondInstanceId)) {
            throw new IllegalArgumentException("A clash needs two distinct Domain instances");
        }
        if (Double.isNaN(takeoverProgress)) takeoverProgress = 0.0;
        takeoverProgress = Math.max(0.0, Math.min(1.0, takeoverProgress));
    }

    public DomainClash(String firstInstanceId, String secondInstanceId) {
        this(firstInstanceId, secondInstanceId, null, 0.0);
    }
}
