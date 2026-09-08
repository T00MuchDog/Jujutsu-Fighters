package com.jjktbf.graphics.audio;

import com.badlogic.gdx.audio.Music;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameAudioTest {
    private static final String DOMAIN_ONE = "domain-1";
    private static final String DOMAIN_TWO = "domain-2";

    @Test
    void eventMusicCrossFadesWithBackgroundUntilItsOwnerStopsIt() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);

        assertTrue(background.playing);
        assertTrue(event.playing);
        assertTrue(event.looping);
        assertEquals(1f, background.volume);
        assertEquals(0f, event.volume);

        audio.update(0.5f);
        assertEquals(0.5f, background.volume);
        assertEquals(0.5f, event.volume);

        audio.update(0.5f);
        assertEquals(0f, background.volume);
        assertEquals(1f, event.volume);

        audio.stopEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.25f);
        assertEquals(0.25f, background.volume);
        assertEquals(0.75f, event.volume);

        audio.update(0.75f);
        assertEquals(1f, background.volume);
        assertFalse(event.playing);
        assertEquals(1, event.stopCalls);
        audio.dispose();
    }

    @Test
    void missingEventMusicDoesNotAttenuateBackgroundMusic() {
        FakeMusic background = new FakeMusic();
        GameAudio audio = new GameAudio(
            AudioSettings.defaults(), Map.of(MusicTrack.BATTLE_AIZO, background));

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(1f);

        assertEquals(1f, background.volume);
        assertEquals(0, background.stopCalls);
        assertTrue(background.playing);
        audio.dispose();
    }

    @Test
    void collapseDuringFadeInReversesAndReopenContinuesTheSameTrack() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.4f);
        audio.stopEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.2f);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.3f);

        assertEquals(0.5f, background.volume);
        assertEquals(0.5f, event.volume);
        assertEquals(1, event.playCalls);
        assertEquals(0, event.stopCalls);
        audio.dispose();
    }

    @Test
    void sharedTrackFadesOutOnlyAfterEveryDomainInstanceCloses() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_TWO);
        audio.update(1f);
        audio.stopEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(1f);

        assertEquals(0f, background.volume);
        assertEquals(1f, event.volume);
        assertEquals(0, event.stopCalls);

        audio.stopEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_TWO);
        audio.update(1f);
        assertEquals(1f, background.volume);
        assertEquals(1, event.stopCalls);
        audio.dispose();
    }

    @Test
    void unrelatedCollapseCannotStopTheActiveEventTrack() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(1f);
        audio.stopEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, "other-domain");
        audio.update(1f);

        assertEquals(0f, background.volume);
        assertEquals(1f, event.volume);
        assertTrue(event.playing);
        audio.dispose();
    }

    @Test
    void screenMusicTransitionCancelsEventMusicAndRestoresFullGain() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.5f);
        audio.playMusic(MusicTrack.BATTLE_AIZO);

        assertEquals(1f, background.volume);
        assertEquals(1, background.playCalls);
        assertFalse(event.playing);
        audio.dispose();
    }

    @Test
    void pauseFreezesTheCrossFadeAndResumeContinuesBothStreams() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.4f);
        audio.pause();
        audio.update(0.5f);

        assertEquals(0.6f, background.volume, 0.0001f);
        assertEquals(0.4f, event.volume, 0.0001f);
        assertFalse(background.playing);
        assertFalse(event.playing);

        audio.resume();
        audio.update(0.2f);
        assertEquals(0.4f, background.volume, 0.0001f);
        assertEquals(0.6f, event.volume, 0.0001f);
        assertTrue(background.playing);
        assertTrue(event.playing);
        audio.dispose();
    }

    @Test
    void mixerChangesApplyToBothSidesOfAPartialCrossFade() {
        FakeMusic background = new FakeMusic();
        FakeMusic event = new FakeMusic();
        GameAudio audio = audio(background, event);

        audio.playMusic(MusicTrack.BATTLE_AIZO);
        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(0.25f);
        audio.previewSettings(new AudioSettings(
            0.5f, 0.4f, 1f, 1f, false, BattleMusicSelection.RANDOM));

        assertEquals(0.15f, background.volume, 0.0001f);
        assertEquals(0.05f, event.volume, 0.0001f);
        audio.dispose();
    }

    @Test
    void eventMusicCanFadeWithoutABackgroundTrack() {
        FakeMusic event = new FakeMusic();
        GameAudio audio = new GameAudio(AudioSettings.defaults(), Map.of(
            MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, event));

        audio.playEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(1f);
        assertEquals(1f, event.volume);

        audio.stopEventMusic(MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, DOMAIN_ONE);
        audio.update(1f);
        assertFalse(event.playing);
        audio.dispose();
    }

    private static GameAudio audio(FakeMusic background, FakeMusic event) {
        return new GameAudio(AudioSettings.defaults(), Map.of(
            MusicTrack.BATTLE_AIZO, background,
            MusicTrack.SELF_EMBODIMENT_OF_PERFECTION, event
        ));
    }

    private static final class FakeMusic implements Music {
        private boolean playing;
        private boolean looping;
        private float volume;
        private float position;
        private int playCalls;
        private int stopCalls;
        private OnCompletionListener listener;

        @Override
        public void play() {
            playing = true;
            playCalls++;
        }

        @Override
        public void pause() {
            playing = false;
        }

        @Override
        public void stop() {
            playing = false;
            stopCalls++;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void setLooping(boolean looping) {
            this.looping = looping;
        }

        @Override
        public boolean isLooping() {
            return looping;
        }

        @Override
        public void setVolume(float volume) {
            this.volume = volume;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public void setPan(float pan, float volume) {
            this.volume = volume;
        }

        @Override
        public void setPosition(float position) {
            this.position = position;
        }

        @Override
        public float getPosition() {
            return position;
        }

        @Override
        public void dispose() {
            playing = false;
        }

        @Override
        public void setOnCompletionListener(OnCompletionListener listener) {
            this.listener = listener;
        }
    }
}
