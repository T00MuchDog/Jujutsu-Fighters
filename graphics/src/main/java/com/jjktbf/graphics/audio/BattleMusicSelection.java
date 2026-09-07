package com.jjktbf.graphics.audio;

import java.util.Optional;

/** Persisted choice for music played when a battle starts. */
public enum BattleMusicSelection {
    RANDOM("Random", null, true),
    NONE("None", null, false),
    AIZO("Aizo", MusicTrack.BATTLE_AIZO, false),
    ABODE_OF_BLUE("Abode of Blue", MusicTrack.BATTLE_ABODE_OF_BLUE, false),
    SPECIALZ("Specialz", MusicTrack.BATTLE_SPECIALZ, false),
    KAKAI_KITAN("Kakai Kitan", MusicTrack.BATTLE_KAKAI_KITAN, false);

    private final String label;
    private final MusicTrack track;
    private final boolean random;

    BattleMusicSelection(String label, MusicTrack track, boolean random) {
        this.label = label;
        this.track = track;
        this.random = random;
    }

    public Optional<MusicTrack> resolveTrack() {
        return Optional.ofNullable(random ? MusicTrack.randomBattleTrack() : track);
    }

    static BattleMusicSelection fromPreference(String value) {
        if (value == null) return RANDOM;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return RANDOM;
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
