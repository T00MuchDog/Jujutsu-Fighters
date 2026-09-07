package com.jjktbf.graphics.animation;

import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleChoreographyTest {
    @TempDir
    Path root;

    @Test
    void interpolatesAllPoseComponentsWithConfiguredEasing() throws IOException {
        Path file = root.resolve("choreography.json");
        Files.writeString(file, """
            {
              "schemaVersion": 1,
              "profiles": {
                "motion": {
                  "durationSeconds": 2,
                  "impactSeconds": 1,
                  "source": [
                    {"at": 0, "x": 0, "y": 0, "scaleX": 1, "scaleY": 1,
                     "rotation": 0, "alpha": 1, "red": 1, "green": 1, "blue": 1},
                    {"at": 1, "x": 2, "y": 4, "scaleX": 3, "scaleY": 5,
                     "rotation": 90, "alpha": 0.5, "red": 0.2, "green": 0.4, "blue": 0.6,
                     "easing": "linear"}
                  ],
                  "target": [
                    {"at": 0, "x": 0},
                    {"at": 1, "x": 8, "easing": "smooth"}
                  ]
                },
                "stepMotion": {
                  "source": [
                    {"at": 0, "x": 3},
                    {"at": 1, "x": 9, "easing": "step"}
                  ]
                }
              }
            }
            """);

        BattleChoreography choreography = new BattleChoreography(new FileHandle(file.toFile()));
        BattleChoreography.Profile motion = choreography.profiles().get(0);
        BattleChoreography.Pose linear = motion.source().sample(0.5f);

        assertEquals(1f, linear.x(), 0.0001f);
        assertEquals(2f, linear.y(), 0.0001f);
        assertEquals(2f, linear.scaleX(), 0.0001f);
        assertEquals(3f, linear.scaleY(), 0.0001f);
        assertEquals(45f, linear.rotation(), 0.0001f);
        assertEquals(0.75f, linear.alpha(), 0.0001f);
        assertEquals(0.6f, linear.red(), 0.0001f);
        assertEquals(0.7f, linear.green(), 0.0001f);
        assertEquals(0.8f, linear.blue(), 0.0001f);

        // The easing belongs to the arriving keyframe. Smooth at 25% is 0.15625.
        assertEquals(1.25f, motion.target().sample(0.25f).x(), 0.0001f);
        assertEquals(0f, motion.source().sample(-1f).x(), 0.0001f,
            "the first keyframe is returned before the track starts");

        BattleChoreography.Track step = choreography.profiles().get(1).source();
        assertEquals(3f, step.sample(0.5f).x(), 0.0001f);
        assertEquals(9f, step.sample(1f).x(), 0.0001f);
        assertEquals(9f, step.sample(2f).x(), 0.0001f,
            "the final keyframe is held after the track ends");
    }

    @Test
    void rejectsInvalidDurationAndKeyframeMetadata() throws IOException {
        Path duration = root.resolve("duration.json");
        Files.writeString(duration, """
            {"schemaVersion":1,"profiles":{"bad":
              {"durationSeconds":0.2,"impactSeconds":0.3}}}
            """);
        assertThrows(IllegalArgumentException.class,
            () -> new BattleChoreography(new FileHandle(duration.toFile())));

        Path keyframes = root.resolve("keyframes.json");
        Files.writeString(keyframes, """
            {"schemaVersion":1,"profiles":{"bad":
              {"source":[{"at":0.5},{"at":0.5}]}}}
            """);
        assertThrows(IllegalArgumentException.class,
            () -> new BattleChoreography(new FileHandle(keyframes.toFile())));

        Path layer = root.resolve("layer.json");
        Files.writeString(layer, """
            {"schemaVersion":1,"profiles":{"bad":
              {"layers":[{"effect":"x","placement":"screen","plane":"front",
                "startSeconds":-0.1}]}}}
            """);
        assertThrows(IllegalArgumentException.class,
            () -> new BattleChoreography(new FileHandle(layer.toFile())));
    }

    @Test
    void acceptsSourceToTargetLayerPlacements() throws IOException {
        Path file = root.resolve("path-layers.json");
        Files.writeString(file, """
            {"schemaVersion":1,"profiles":{"path":{"layers":[
              {"effect":"ray","placement":"beam"},
              {"effect":"orb","placement":"projectile"}]}}}
            """);
        BattleChoreography choreography = new BattleChoreography(new FileHandle(file.toFile()));
        assertEquals("beam", choreography.profiles().get(0).layers().get(0).placement());
        assertEquals("projectile", choreography.profiles().get(0).layers().get(1).placement());
    }
}
