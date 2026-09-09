package com.jjktbf.graphics.ui.battle;

import com.jjktbf.model.move.AoeType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveDetailViewTest {

    @Test
    void horizontalDetailRegionsDoNotOverlap() {
        MoveDetailView view = new MoveDetailView();
        view.setBounds(33f, 33f, 2494f, 129.56f);

        MoveDetailView.LayoutSnapshot layout = view.layoutSnapshot();

        assertEquals(33f, layout.bounds().x, 0.001f);
        assertTrue(layout.title().x + layout.title().width < layout.values().x);
        assertTrue(layout.values().x + layout.values().width < layout.description().x);
        assertEquals(layout.title().y, layout.values().y, 0.001f);
        assertEquals(layout.values().y, layout.description().y, 0.001f);
        assertEquals(layout.bounds().x + layout.bounds().width - 16f,
            layout.description().x + layout.description().width, 0.001f);
    }

    @Test
    void statCountsUseLayoutsThatFillOneOrTwoRows() {
        assertEquals(new MoveDetailView.ValueLayout(1, 0, 1.35f),
            MoveDetailView.valueLayout(1));
        assertEquals(new MoveDetailView.ValueLayout(2, 0, 1.25f),
            MoveDetailView.valueLayout(2));
        assertEquals(new MoveDetailView.ValueLayout(3, 0, 1.15f),
            MoveDetailView.valueLayout(3));
        assertEquals(new MoveDetailView.ValueLayout(2, 2, 1.10f),
            MoveDetailView.valueLayout(4));
        assertEquals(new MoveDetailView.ValueLayout(3, 2, 1.05f),
            MoveDetailView.valueLayout(5));
        assertEquals(new MoveDetailView.ValueLayout(3, 3, 1f),
            MoveDetailView.valueLayout(6));
    }

    @Test
    void attackValuesExposeCombatStatsWithoutRepeatingCardCostsOrTiming() {
        MoveData data = baseMove("ATTACK_DETAIL");
        data.tags = List.of("ATTACK", "PHYSICAL", "AOE");
        data.potency = 3;
        data.baseAccuracy = 0.86;
        data.aoeType = AoeType.MULTIPLE.name();
        data.aoeTargetCount = 3;
        data.hitComponents = List.of(hit(18, 0.86), hit(18, 0.86), hit(24, 0.86));

        List<MoveDetailView.MoveValue> values = MoveDetailView.moveValues(data.toMove(), false);

        assertEquals(List.of(
            new MoveDetailView.MoveValue("POWER", "60"),
            new MoveDetailView.MoveValue("ACCURACY", "86%"),
            new MoveDetailView.MoveValue("HITS", "3"),
            new MoveDetailView.MoveValue("POTENCY", "3"),
            new MoveDetailView.MoveValue("TARGETS", "3")), values);
        assertFalse(values.stream().anyMatch(value ->
            value.label().equals("AP") || value.label().equals("CE") || value.label().equals("FIRE")));
    }

    @Test
    void defensiveValuesUseTheMoveSpecificBlockAndDodgeFields() {
        MoveData block = baseMove("BLOCK_DETAIL");
        block.tags = List.of("DEFENSIVE", "PHYSICAL");
        block.defenseType = "BLOCK";
        block.blockStyle = "FLAT";
        block.blockFlatReduction = 30;
        block.blockDuration = 8;
        block.defenseUses = 2;

        MoveData dodge = baseMove("DODGE_DETAIL");
        dodge.tags = List.of("DEFENSIVE", "PHYSICAL");
        dodge.defenseType = "DODGE";
        dodge.dodgeChance = 90;
        dodge.dodgeScope = "MELEE";
        dodge.blockDuration = 6;

        assertEquals(List.of(
            new MoveDetailView.MoveValue("BLOCK", "30"),
            new MoveDetailView.MoveValue("WINDOW", "8 AP"),
            new MoveDetailView.MoveValue("USES", "2")),
            MoveDetailView.moveValues(block.toMove(), false));
        assertEquals(List.of(
            new MoveDetailView.MoveValue("DODGE", "90%"),
            new MoveDetailView.MoveValue("SCOPE", "MELEE"),
            new MoveDetailView.MoveValue("WINDOW", "6 AP")),
            MoveDetailView.moveValues(dodge.toMove(), false));
    }

    private static MoveData baseMove(String id) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.description = "A test-owned move for detail layout mechanics.";
        data.tags = List.of("ATTACK", "PHYSICAL");
        data.apCost = 10;
        data.unleashPoint = 4;
        return data;
    }

    private static MoveData.HitComponentData hit(int power, double accuracy) {
        MoveData.HitComponentData hit = new MoveData.HitComponentData();
        hit.basePower = power;
        hit.baseAccuracy = accuracy;
        hit.tags = List.of("PHYSICAL");
        return hit;
    }
}
