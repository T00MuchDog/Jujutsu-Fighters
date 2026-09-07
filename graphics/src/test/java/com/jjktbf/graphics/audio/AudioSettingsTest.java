package com.jjktbf.graphics.audio;

import com.badlogic.gdx.Preferences;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioSettingsTest {
    @Test
    void normalizesVolumesAndMixesMasterWithTheSelectedChannel() {
        AudioSettings settings = new AudioSettings(
            0.5f, 0.8f, 2f, -1f, false, BattleMusicSelection.RANDOM);

        assertEquals(0.4f, settings.effectiveVolume(AudioChannel.MUSIC, 1f), 0.0001f);
        assertEquals(0.5f, settings.effectiveVolume(AudioChannel.UI_SFX, 1f), 0.0001f);
        assertEquals(0f, settings.effectiveVolume(AudioChannel.BATTLE_SFX, 1f), 0.0001f);
    }

    @Test
    void muteAndInvalidCueGainProduceSilence() {
        AudioSettings muted = AudioSettings.defaults().withMuted(true);

        assertEquals(0f, muted.effectiveVolume(AudioChannel.MUSIC, 1f));
        assertEquals(0f, AudioSettings.defaults().effectiveVolume(
            AudioChannel.UI_SFX, Float.NaN));
    }

    @Test
    void firstInstallUsesSixtyPercentMusicWithoutChangingExistingProfiles() {
        assertEquals(0.6f, GameAudio.loadSettings(preferences(Map.of()), true).musicVolume());
        assertEquals(1f, GameAudio.loadSettings(preferences(Map.of()), false).musicVolume());
    }

    @Test
    void savedMusicVolumeOverridesTheFirstInstallDefault() {
        AudioSettings settings = GameAudio.loadSettings(
            preferences(Map.of("musicVolume", 0.35f)), true);

        assertEquals(0.35f, settings.musicVolume());
    }

    @Test
    void battleMusicDefaultsToRandomAndLoadsKnownChoices() {
        assertEquals(BattleMusicSelection.RANDOM,
            GameAudio.loadSettings(preferences(Map.of()), false).battleMusic());
        assertEquals(BattleMusicSelection.SPECIALZ, GameAudio.loadSettings(
            preferences(Map.of("battleMusic", "SPECIALZ")), false).battleMusic());
        assertEquals(BattleMusicSelection.RANDOM, GameAudio.loadSettings(
            preferences(Map.of("battleMusic", "unknown")), false).battleMusic());
    }

    private static Preferences preferences(Map<String, Object> values) {
        return (Preferences) Proxy.newProxyInstance(
            Preferences.class.getClassLoader(),
            new Class<?>[] {Preferences.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getFloat" -> {
                    Object value = values.get(arguments[0]);
                    yield value instanceof Number number ? number.floatValue() : arguments[1];
                }
                case "getBoolean" -> {
                    Object value = values.get(arguments[0]);
                    yield value instanceof Boolean bool ? bool : arguments[1];
                }
                case "getString" -> {
                    Object value = values.get(arguments[0]);
                    yield value instanceof String string ? string : arguments[1];
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
