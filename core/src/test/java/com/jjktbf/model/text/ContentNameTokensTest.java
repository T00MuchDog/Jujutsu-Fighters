package com.jjktbf.model.text;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentNameTokensTest {

    private final ContentNameTokens.NameLookup lookup = ContentNameTokens.of(
        Map.of("000004", "Body Blow", "000108", "Convergence"),
        Map.of("000001", "Miracle Reservoir", "000032", "Well's Unknown Abyss"));

    @Test
    void resolvesMoveAndAbilityReferencesToCurrentNames() {
        assertEquals(
            "With Miracle Reservoir active, answer with Body Blow.",
            ContentNameTokens.resolve(
                "With *ability:000001* active, answer with *move:000004*.", lookup));
    }

    @Test
    void typePrefixIsCaseInsensitiveAndReferencesMayCarryPunctuation() {
        assertEquals(
            "Through Well's Unknown Abyss: Convergence!",
            ContentNameTokens.resolve(
                "Through *ABILITY:000032*: *Move:000108*!", lookup));
    }

    @Test
    void unknownReferencesStayVerbatimInsteadOfErasingText() {
        assertEquals(
            "Uses *move:999999* and *ability:999999* verbatim.",
            ContentNameTokens.resolve(
                "Uses *move:999999* and *ability:999999* verbatim.", lookup));
    }

    @Test
    void textWithoutReferencesPassesThroughUnchanged() {
        assertEquals("Plain description.", ContentNameTokens.resolve("Plain description.", lookup));
        assertEquals("", ContentNameTokens.resolve(null, lookup));
        assertEquals("Plain.", ContentNameTokens.resolve("Plain.", null));
    }

    @Test
    void moveAndAbilityNamespacesAreIndependent() {
        // Move 000001 and ability 000001 are different content; the prefix picks the namespace.
        ContentNameTokens.NameLookup disjoint = ContentNameTokens.of(
            id -> "000001".equals(id) ? "Jab" : null,
            id -> "000001".equals(id) ? "Iron Constitution" : null);
        assertEquals(
            "Jab pairs with Iron Constitution.",
            ContentNameTokens.resolve(
                "*move:000001* pairs with *ability:000001*.", disjoint));
    }

    @Test
    void validationReportsUnknownAndMalformedReferences() {
        assertNull(ContentNameTokens.validationError(
            "With *ability:000001* active.", lookup));
        assertEquals(
            "Unknown move reference *move:999999*.",
            ContentNameTokens.validationError("Uses *move:999999* here.", lookup));
        assertEquals(
            "Malformed content reference *move:000004*x*.",
            ContentNameTokens.validationError("Bad *move:000004*x* ref.", lookup));
        assertEquals(
            "Close the content reference that starts at character 5.",
            ContentNameTokens.validationError("Bad *move:000004 here.", lookup));
    }

    @Test
    void moveConversionResolvesDescriptionReferencesOnlyWhenGivenALookup() {
        MoveData data = new MoveData();
        data.id = "000096";
        data.name = "Counter";
        data.tags = List.of("DEFENSIVE");
        data.apCost = 10;
        data.unleashPoint = 2;
        data.description = "Answer with *move:000004* under *ability:000001*.";

        Move resolved = data.toMoveResolved(null, lookup);
        assertEquals("Answer with Body Blow under Miracle Reservoir.",
            resolved.getDescription());

        Move raw = data.toMove();
        assertEquals("Answer with *move:000004* under *ability:000001*.",
            raw.getDescription());
    }

    @Test
    void abilityConversionResolvesBothPlayerFacingTextLayers() {
        AbilityData data = new AbilityData();
        data.id = "000100";
        data.name = "Counter Training";
        data.flavourText = "Study *move:000004* under *ability:000001*.";
        data.mechanicText = "Grant *move:000108*.";

        Ability resolved = new Ability(data, lookup);

        assertEquals("Study Body Blow under Miracle Reservoir.", resolved.getFlavourText());
        assertEquals("Grant Convergence.", resolved.getMechanicText());

        Ability raw = new Ability(data);
        assertEquals("Study *move:000004* under *ability:000001*.", raw.getFlavourText());
        assertEquals("Grant *move:000108*.", raw.getMechanicText());
    }
}
