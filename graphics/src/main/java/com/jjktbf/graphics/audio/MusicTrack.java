package com.jjktbf.graphics.audio;

import java.util.concurrent.ThreadLocalRandom;

/** Streamed tracks used as looping screen music or contextual event music. */
public enum MusicTrack {
    MENU("assets/audio/music/menu.ogg", 1f),
    BATTLE_AIZO("assets/audio/music/battle_aizo.ogg", 1f),
    BATTLE_ABODE_OF_BLUE("assets/audio/music/battle_AbodeOfBlue.ogg", 1f),
    BATTLE_SPECIALZ("assets/audio/music/battle_specialz.ogg", 1f),
    BATTLE_KAKAI_KITAN("assets/audio/music/battle_KakaiKitan.ogg", 1f),
    SELF_EMBODIMENT_OF_PERFECTION(
        "assets/audio/music/events/self_embodiment_of_perfection.ogg", 1f);

    /** Tracks eligible to open a battle, each with an equal chance. */
    private static final MusicTrack[] BATTLE_TRACKS = {
        BATTLE_AIZO, BATTLE_ABODE_OF_BLUE, BATTLE_SPECIALZ, BATTLE_KAKAI_KITAN
    };

    private final String assetPath;
    private final float gain;

    MusicTrack(String assetPath, float gain) {
        this.assetPath = assetPath;
        this.gain = gain;
    }

    public String assetPath() {
        return assetPath;
    }

    public float gain() {
        return gain;
    }

    /** Returns one of the battle tracks, each with equal probability. */
    public static MusicTrack randomBattleTrack() {
        return BATTLE_TRACKS[ThreadLocalRandom.current().nextInt(BATTLE_TRACKS.length)];
    }

    /** Returns the battle tracks in fallback preference order. */
    public static MusicTrack[] battleTracks() {
        return BATTLE_TRACKS.clone();
    }
}
