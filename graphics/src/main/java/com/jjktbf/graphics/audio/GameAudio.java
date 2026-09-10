package com.jjktbf.graphics.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.jjktbf.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Application-lifetime owner of all native audio resources.
 *
 * Missing catalog assets are intentionally ignored, which lets audio wiring be
 * committed before the final files arrive. Present assets are loaded once and
 * disposed with the game.
 */
public final class GameAudio implements Disposable {
    private static final int MUSIC_HEADER_BYTES = 96;
    private static final float EVENT_MUSIC_FADE_SECONDS = 1f;
    private static final String PREFERENCES_NAME = "jjktbf-audio";
    private static final String MASTER_VOLUME_KEY = "masterVolume";
    private static final String MUSIC_VOLUME_KEY = "musicVolume";
    private static final String UI_VOLUME_KEY = "uiSfxVolume";
    private static final String BATTLE_VOLUME_KEY = "battleSfxVolume";
    private static final String MUTED_KEY = "muted";
    private static final String BATTLE_MUSIC_KEY = "battleMusic";
    private static final float FIRST_INSTALL_MUSIC_VOLUME = 0.6f;

    private final Map<MusicTrack, Music> music = new EnumMap<>(MusicTrack.class);
    private final Map<SoundCue, Sound> sounds = new EnumMap<>(SoundCue.class);
    private final Preferences preferences;

    private AudioSettings settings;
    private MusicTrack requestedTrack;
    private Music backgroundMusic;
    private MusicTrack eventTrack;
    private Music eventMusic;
    private final Set<String> eventMusicOwners = new HashSet<>();
    private float eventMusicMix;
    private float eventMusicTarget;
    private boolean paused;
    private boolean disposed;

    public GameAudio() {
        preferences = openPreferences();
        settings = loadSettings(preferences, AppPaths.isFirstInstallProfile());
        loadAssets();
    }

    GameAudio(AudioSettings settings, Map<MusicTrack, Music> loadedMusic) {
        this.preferences = null;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.music.putAll(Objects.requireNonNull(loadedMusic, "loadedMusic"));
    }

    /** Switches looped music without restarting an already active track. */
    public void playMusic(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (disposed) return;

        cancelEventMusic();
        playBackgroundMusic(track);
    }

    private void playBackgroundMusic(MusicTrack track) {
        if (track == requestedTrack && backgroundMusic == music.get(track)) {
            updateMusicVolumes();
            if (!paused && backgroundMusic != null && !backgroundMusic.isPlaying()) {
                backgroundMusic.play();
            }
            return;
        }

        stopBackgroundMusic();
        requestedTrack = track;
        backgroundMusic = music.get(track);
        if (backgroundMusic == null) return;

        backgroundMusic.setLooping(true);
        backgroundMusic.setOnCompletionListener(null);
        updateMusicVolumes();
        if (!paused) backgroundMusic.play();
    }

    /**
     * Cross-fades from screen music to a looping event track. Owners let
     * overlapping instances share one track without the first collapse
     * stopping music still owned by another instance.
     */
    public void playEventMusic(MusicTrack track, String ownerId) {
        Objects.requireNonNull(track, "track");
        if (disposed) return;

        Music nextMusic = music.get(track);
        if (nextMusic == null) return;
        if (track == eventTrack && eventMusic == nextMusic) {
            eventMusicOwners.add(ownerId);
            eventMusicTarget = 1f;
            updateMusicVolumes();
            if (!paused && !eventMusic.isPlaying()) eventMusic.play();
            return;
        }

        cancelEventMusic();
        eventTrack = track;
        eventMusic = nextMusic;
        eventMusicOwners.add(ownerId);
        eventMusicMix = 0f;
        eventMusicTarget = 1f;
        eventMusic.setLooping(true);
        eventMusic.setOnCompletionListener(null);
        updateMusicVolumes();
        if (!paused) eventMusic.play();
    }

    /** Fades an event track out once its last owning event instance ends. */
    public void stopEventMusic(MusicTrack track, String ownerId) {
        Objects.requireNonNull(track, "track");
        if (disposed || track != eventTrack || !eventMusicOwners.remove(ownerId)) return;
        if (eventMusicOwners.isEmpty()) eventMusicTarget = 0f;
    }

    /** Advances event/background cross-fades in real time. */
    public void update(float deltaSeconds) {
        if (disposed || paused || eventMusic == null || !Float.isFinite(deltaSeconds)) return;
        float step = Math.max(0f, deltaSeconds) / EVENT_MUSIC_FADE_SECONDS;
        if (eventMusicMix < eventMusicTarget) {
            eventMusicMix = Math.min(eventMusicTarget, eventMusicMix + step);
        } else if (eventMusicMix > eventMusicTarget) {
            eventMusicMix = Math.max(eventMusicTarget, eventMusicMix - step);
        }
        updateMusicVolumes();
        if (eventMusicTarget == 0f && eventMusicMix == 0f) cancelEventMusic();
    }

    public void stopMusic() {
        cancelEventMusic();
        stopBackgroundMusic();
        requestedTrack = null;
    }

    /** Starts the configured battle music, or stops music when the choice is None. */
    public void playBattleMusic() {
        MusicTrack requested = settings.battleMusic().resolveTrack().orElse(null);
        if (requested == null) {
            stopMusic();
            return;
        }
        playMusic(playableBattleTrack(requested, music.keySet()));
    }

    /** Re-selects battle background music without interrupting active event music. */
    public void refreshBattleMusic() {
        if (disposed) return;
        MusicTrack requested = settings.battleMusic().resolveTrack().orElse(null);
        if (requested == null) {
            stopBackgroundMusic();
            requestedTrack = null;
            updateMusicVolumes();
            return;
        }
        playBackgroundMusic(playableBattleTrack(requested, music.keySet()));
    }

    /**
     * Returns the requested track when its asset loaded, otherwise the first
     * loaded battle track, so one missing or invalid battle asset cannot
     * silence the battles that select it.
     */
    static MusicTrack playableBattleTrack(MusicTrack requested, Set<MusicTrack> loadedTracks) {
        if (loadedTracks.contains(requested)) return requested;
        for (MusicTrack candidate : MusicTrack.battleTracks()) {
            if (loadedTracks.contains(candidate)) return candidate;
        }
        return requested;
    }

    /** Plays a short cue once and returns its LibGDX sound ID, or {@code -1} if unavailable. */
    public long play(SoundCue cue) {
        Objects.requireNonNull(cue, "cue");
        if (disposed || paused) return -1L;

        Sound sound = sounds.get(cue);
        float volume = settings.effectiveVolume(cue.channel(), cue.gain());
        if (sound == null || volume <= 0f) return -1L;
        try {
            return sound.play(volume);
        } catch (RuntimeException failure) {
            reportFailure("Could not play " + cue.assetPath(), failure);
            return -1L;
        }
    }

    public AudioSettings settings() {
        return settings;
    }

    /** Applies and persists all mixer settings as one atomic in-memory update. */
    public void applySettings(AudioSettings settings) {
        if (disposed) return;
        updateSettings(settings);
        saveSettings();
    }

    /** Applies mixer settings immediately without writing preferences yet. */
    public void previewSettings(AudioSettings settings) {
        updateSettings(settings);
    }

    /** Persists the current mixer settings, typically after a slider interaction ends. */
    public void persistSettings() {
        if (!disposed) saveSettings();
    }

    private void updateSettings(AudioSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (disposed) return;

        boolean newlyMuted = settings.muted() && !this.settings.muted();
        this.settings = settings;
        if (newlyMuted) stopAllSounds();
        updateMusicVolumes();
    }

    public void setMasterVolume(float volume) {
        applySettings(settings.withMasterVolume(volume));
    }

    public void setChannelVolume(AudioChannel channel, float volume) {
        applySettings(settings.withChannelVolume(channel, volume));
    }

    public void setMuted(boolean muted) {
        applySettings(settings.withMuted(muted));
    }

    public boolean isLoaded(MusicTrack track) {
        return music.containsKey(Objects.requireNonNull(track, "track"));
    }

    public boolean isLoaded(SoundCue cue) {
        return sounds.containsKey(Objects.requireNonNull(cue, "cue"));
    }

    public MusicTrack requestedTrack() {
        return requestedTrack;
    }

    public void pause() {
        if (disposed || paused) return;
        paused = true;
        if (backgroundMusic != null && backgroundMusic.isPlaying()) backgroundMusic.pause();
        if (eventMusic != null && eventMusic.isPlaying()) eventMusic.pause();
        stopAllSounds();
    }

    public void resume() {
        if (disposed || !paused) return;
        paused = false;
        updateMusicVolumes();
        if (backgroundMusic != null && !backgroundMusic.isPlaying()) backgroundMusic.play();
        if (eventMusic != null && !eventMusic.isPlaying()) eventMusic.play();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        stopMusic();
        stopAllSounds();
        for (Sound sound : sounds.values()) sound.dispose();
        for (Music track : music.values()) track.dispose();
        sounds.clear();
        music.clear();
    }

    private void loadAssets() {
        for (MusicTrack track : MusicTrack.values()) {
            FileHandle file = Gdx.files.internal(track.assetPath());
            if (!file.exists()) continue;
            try {
                validateMusicAsset(track, file);
                music.put(track, Gdx.audio.newMusic(file));
            } catch (RuntimeException failure) {
                reportFailure("Could not load " + track.assetPath(), failure);
            }
        }

        for (SoundCue cue : SoundCue.values()) {
            FileHandle file = Gdx.files.internal(cue.assetPath());
            if (!file.exists()) continue;
            try {
                sounds.put(cue, Gdx.audio.newSound(file));
            } catch (RuntimeException failure) {
                reportFailure("Could not load " + cue.assetPath(), failure);
            }
        }
    }

    private static void validateMusicAsset(MusicTrack track, FileHandle file) {
        if (!"ogg".equalsIgnoreCase(file.extension())) return;

        byte[] header;
        try (InputStream input = file.read()) {
            header = input.readNBytes(MUSIC_HEADER_BYTES);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not inspect " + track.assetPath(), failure);
        }
        if (!isOggVorbisHeader(header)) {
            throw new IllegalArgumentException(
                track.assetPath() + " has an .ogg extension but is not Ogg Vorbis. "
                    + "Renaming a WebM/Opus file does not convert it."
            );
        }
    }

    static boolean isOggVorbisHeader(byte[] header) {
        if (header == null || header.length < 35
            || header[0] != 'O' || header[1] != 'g'
            || header[2] != 'g' || header[3] != 'S'
            || header[4] != 0 || (header[5] & 0x02) == 0) {
            return false;
        }

        int pageSegments = header[26] & 0xff;
        int packetOffset = 27 + pageSegments;
        if (pageSegments == 0 || header.length < packetOffset + 7
            || (header[27] & 0xff) < 7 || header[packetOffset] != 0x01) {
            return false;
        }

        byte[] vorbis = {'v', 'o', 'r', 'b', 'i', 's'};
        for (int i = 0; i < vorbis.length; i++) {
            if (header[packetOffset + 1 + i] != vorbis[i]) return false;
        }
        return true;
    }

    private void updateMusicVolumes() {
        if (backgroundMusic != null && requestedTrack != null) {
            backgroundMusic.setVolume(settings.effectiveVolume(
                AudioChannel.MUSIC, requestedTrack.gain()) * (1f - eventMusicMix));
        }
        if (eventMusic != null && eventTrack != null) {
            eventMusic.setVolume(settings.effectiveVolume(
                AudioChannel.MUSIC, eventTrack.gain()) * eventMusicMix);
        }
    }

    private void cancelEventMusic() {
        Music playing = eventMusic;
        eventMusic = null;
        eventTrack = null;
        eventMusicOwners.clear();
        eventMusicMix = 0f;
        eventMusicTarget = 0f;
        if (playing != null) {
            playing.setOnCompletionListener(null);
            playing.stop();
        }
        updateMusicVolumes();
    }

    private void stopBackgroundMusic() {
        Music playing = backgroundMusic;
        backgroundMusic = null;
        if (playing == null) return;
        playing.setOnCompletionListener(null);
        playing.stop();
    }

    private void stopAllSounds() {
        for (Sound sound : sounds.values()) sound.stop();
    }

    private static Preferences openPreferences() {
        if (Gdx.app == null) return null;
        try {
            return Gdx.app.getPreferences(PREFERENCES_NAME);
        } catch (RuntimeException failure) {
            reportFailure("Could not open audio preferences", failure);
            return null;
        }
    }

    static AudioSettings loadSettings(Preferences preferences, boolean firstInstallProfile) {
        AudioSettings defaults = AudioSettings.defaults();
        if (firstInstallProfile) {
            defaults = defaults.withChannelVolume(AudioChannel.MUSIC, FIRST_INSTALL_MUSIC_VOLUME);
        }
        if (preferences == null) return defaults;
        try {
            return new AudioSettings(
                preferences.getFloat(MASTER_VOLUME_KEY, defaults.masterVolume()),
                preferences.getFloat(MUSIC_VOLUME_KEY, defaults.musicVolume()),
                preferences.getFloat(UI_VOLUME_KEY, defaults.uiSfxVolume()),
                preferences.getFloat(BATTLE_VOLUME_KEY, defaults.battleSfxVolume()),
                preferences.getBoolean(MUTED_KEY, defaults.muted()),
                BattleMusicSelection.fromPreference(preferences.getString(
                    BATTLE_MUSIC_KEY, defaults.battleMusic().name()))
            );
        } catch (RuntimeException failure) {
            reportFailure("Could not read audio preferences", failure);
            return defaults;
        }
    }

    private void saveSettings() {
        if (preferences == null) return;
        try {
            preferences.putFloat(MASTER_VOLUME_KEY, settings.masterVolume());
            preferences.putFloat(MUSIC_VOLUME_KEY, settings.musicVolume());
            preferences.putFloat(UI_VOLUME_KEY, settings.uiSfxVolume());
            preferences.putFloat(BATTLE_VOLUME_KEY, settings.battleSfxVolume());
            preferences.putBoolean(MUTED_KEY, settings.muted());
            preferences.putString(BATTLE_MUSIC_KEY, settings.battleMusic().name());
            preferences.flush();
        } catch (RuntimeException failure) {
            reportFailure("Could not save audio preferences", failure);
        }
    }

    private static void reportFailure(String message, RuntimeException failure) {
        if (Gdx.app != null) {
            Gdx.app.error("GameAudio", message, failure);
        } else {
            System.err.println("GameAudio: " + message);
        }
        AppPaths.logException(failure);
    }
}
