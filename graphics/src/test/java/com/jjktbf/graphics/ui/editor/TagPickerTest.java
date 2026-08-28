package com.jjktbf.graphics.ui.editor;

import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TagPickerTest {

    @Test
    void aoeSurvivesWithoutAttackAndFriendlyFireRequiresAoe() {
        // AOE is category-agnostic: it stays on a pure utility tag set.
        Set<MoveTag> tags = new LinkedHashSet<>(Set.of(
            MoveTag.UTILITY, MoveTag.MELEE, MoveTag.RANGED,
            MoveTag.AOE, MoveTag.FRIENDLY_FIRE));

        TagPicker.enforceTargetingRules(tags);

        assertEquals(Set.of(MoveTag.UTILITY, MoveTag.AOE, MoveTag.FRIENDLY_FIRE), tags);

        // Friendly Fire still depends on AOE, and hit-only tags are still
        // stripped from the parent move.
        tags = new LinkedHashSet<>(Set.of(
            MoveTag.ATTACK, MoveTag.AOE, MoveTag.FRIENDLY_FIRE,
            MoveTag.MELEE, MoveTag.GUARD_BREAK, MoveTag.INTANGIBLE));
        tags.remove(MoveTag.AOE);
        TagPicker.enforceTargetingRules(tags);

        assertEquals(Set.of(MoveTag.ATTACK), tags);
    }
}
