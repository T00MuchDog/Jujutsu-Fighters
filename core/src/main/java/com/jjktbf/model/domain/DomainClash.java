package com.jjktbf.model.domain;

/** Stable snapshot of one pair of opposing, overlapping Domain instances. */
public record DomainClash(String firstInstanceId, String secondInstanceId) {
    public DomainClash {
        if (firstInstanceId == null || secondInstanceId == null
            || firstInstanceId.equals(secondInstanceId)) {
            throw new IllegalArgumentException("A clash needs two distinct Domain instances");
        }
    }
}
