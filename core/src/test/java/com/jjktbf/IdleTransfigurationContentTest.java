package com.jjktbf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.technique.InnateTechniqueData;
import com.jjktbf.model.technique.SkillTreeNodeData;
import com.jjktbf.model.technique.TechniqueSkillTree;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural consistency of the bundled Idle Transfiguration content: every
 * referenced id resolves, the tree syncs without dropping nodes, the Domain
 * validates, and the authored moves use percentage modifiers rather than flat
 * stat additions. Free of value assertions beyond structure — editors may
 * retune any of these numbers.
 */
class IdleTransfigurationContentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TECHNIQUE = "Idle Transfiguration";
    private static final List<String> MOVE_IDS = List.of(
        "000140", "000141", "000142", "000143", "000144",
        "000145", "000146", "000147", "000148", "000149", "000150");

    private record Catalogs(
        List<Map<String, Object>> characters,
        List<AbilityData> abilities,
        List<MoveData> moves,
        List<DomainData> domains,
        List<InnateTechniqueData> techniques
    ) { }

    private static <T> List<T> read(String folder, TypeReference<List<T>> type)
        throws IOException {
        Path path = List.of(
                Path.of("data", folder, "all_" + folder + ".json"),
                Path.of("..", "data", folder, "all_" + folder + ".json"))
            .stream()
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IOException("Could not locate canonical " + folder));
        return MAPPER.readValue(path.toFile(), type);
    }

    private static Catalogs catalogs() throws IOException {
        return new Catalogs(
            read("characters", new TypeReference<>() { }),
            read("abilities", new TypeReference<>() { }),
            read("moves", new TypeReference<>() { }),
            read("domains", new TypeReference<>() { }),
            read("techniques", new TypeReference<>() { }));
    }

    @Test
    void everyMahitoReferenceResolves() throws IOException {
        Catalogs catalogs = catalogs();
        Set<String> moveIds = catalogs.moves().stream()
            .map(move -> move.id).collect(Collectors.toSet());
        Set<String> abilityIds = catalogs.abilities().stream()
            .map(ability -> ability.id).collect(Collectors.toSet());
        Set<String> domainIds = catalogs.domains().stream()
            .map(domain -> domain.id).collect(Collectors.toSet());

        Map<String, Object> mahito = catalogs.characters().stream()
            .filter(character -> "000021".equals(character.get("id")))
            .findFirst().orElseThrow();
        assertEquals("CURSED_SPIRIT", mahito.get("type"));
        assertEquals(TECHNIQUE, mahito.get("innateTechniqueName"));

        @SuppressWarnings("unchecked")
        List<String> knownMoves = (List<String>) mahito.get("moveIds");
        for (String moveId : knownMoves) {
            assertTrue(moveIds.contains(moveId), "unknown move " + moveId);
        }
        @SuppressWarnings("unchecked")
        List<String> ownedAbilities = (List<String>) mahito.get("abilityIds");
        for (String abilityId : ownedAbilities) {
            assertTrue(abilityIds.contains(abilityId), "unknown ability " + abilityId);
        }
        assertTrue(ownedAbilities.contains("000012"),
            "Mahito carries the soul-awareness ability through his technique");
        assertTrue(ownedAbilities.contains("000049"),
            "Mahito carries cursed spirit physiology");

        Map<String, Object> summon = catalogs.characters().stream()
            .filter(character -> "000022".equals(character.get("id")))
            .findFirst().orElseThrow();
        assertEquals("SHIKIGAMI", summon.get("type"));
        assertEquals(Boolean.FALSE, summon.get("directlySelectable"));
    }

    @Test
    void everyTechniqueMoveIsValidAndTagged() throws IOException {
        Catalogs catalogs = catalogs();
        Map<String, MoveData> byId = catalogs.moves().stream()
            .collect(Collectors.toMap(move -> move.id, Function.identity()));
        for (String moveId : MOVE_IDS) {
            MoveData move = byId.get(moveId);
            assertNotNull(move, "missing technique move " + moveId);
            assertEquals(TECHNIQUE, move.requiredTechniqueId);
            assertTrue(move.tags.contains("INNATE_TECHNIQUE"),
                moveId + " must be an innate-technique move");
        }
    }

    @Test
    void temporaryModifiersUsePercentagesNotFlatStats() throws IOException {
        Catalogs catalogs = catalogs();
        Map<String, MoveData> byId = catalogs.moves().stream()
            .collect(Collectors.toMap(move -> move.id, Function.identity()));
        for (String moveId : MOVE_IDS) {
            for (AbilityEffectData effect : byId.get(moveId).effects) {
                if (AbilityEffectType.TIMED_STAT_MODIFIER.name()
                        .equalsIgnoreCase(effect.type)) {
                    assertEquals("PERCENT", effect.valueMode,
                        moveId + " must scale percentages, not add flat stats");
                }
                assertFalse(
                    AbilityEffectType.STAT_ADD.name().equalsIgnoreCase(effect.type),
                    moveId + " must not use permanent flat stat additions");
            }
        }
        // Hooved Legs and Miniature Form are pure percentage transformations.
        assertTrue(byId.get("000142").effects.stream().anyMatch(effect ->
            AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)
                && "speed".equals(effect.stat)));
        assertTrue(byId.get("000144").effects.stream().anyMatch(effect ->
            AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)
                && "EVASION".equals(effect.stringValue)));
        assertTrue(byId.get("000144").effects.stream().anyMatch(effect ->
            AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)
                && "DAMAGE_TAKEN".equals(effect.stringValue)));
    }

    @Test
    void resourceMovesDeclareGuaranteedStockTransactions() throws IOException {
        Catalogs catalogs = catalogs();
        Map<String, MoveData> byId = catalogs.moves().stream()
            .collect(Collectors.toMap(move -> move.id, Function.identity()));
        for (String moveId : List.of("000140", "000141")) {
            assertTrue(byId.get(moveId).effects.stream().anyMatch(effect ->
                    AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.name()
                        .equalsIgnoreCase(effect.type)
                        && "TRANSFIGURED_HUMANS".equals(effect.sourceResourceKey)
                        && "ON_START".equalsIgnoreCase(effect.trigger)),
                moveId + " must consume its stock when the move starts");
        }
        AbilityData stockpile = catalogs.abilities().stream()
            .filter(ability -> "000063".equals(ability.id))
            .findFirst().orElseThrow();
        assertTrue(stockpile.effects.stream().anyMatch(effect ->
            AbilityEffectType.DEFINE_BOUNDED_RESOURCE.name().equalsIgnoreCase(effect.type)
                && "TRANSFIGURED_HUMANS".equals(effect.resourceKey)));
    }

    @Test
    void theIdleTransfigurationTouchIsSoulDamageAndTheDomainIsEFFECTDelivered()
        throws IOException {
        Catalogs catalogs = catalogs();
        Map<String, MoveData> byId = catalogs.moves().stream()
            .collect(Collectors.toMap(move -> move.id, Function.identity()));
        assertTrue(Boolean.TRUE.equals(byId.get("000143").hitComponents.get(0).soulDamage),
            "the touch must strike the soul");
        assertTrue(byId.get("000143").effects.stream().anyMatch(effect ->
            AbilityEffectType.CODED_MOVE_ACTION.name().equalsIgnoreCase(effect.type)));

        MoveData domainMove = byId.get("000150");
        assertTrue(domainMove.effects.stream().anyMatch(effect ->
            AbilityEffectType.ESTABLISH_DOMAIN.name().equalsIgnoreCase(effect.type)
                && "000001".equals(effect.domainId)));

        DomainData domain = catalogs.domains().stream()
            .filter(entry -> "000001".equals(entry.id))
            .findFirst().orElseThrow();
        domain.validate();
        assertEquals(TECHNIQUE, domain.requiredTechniqueName);
        assertFalse(domain.antiDomain);
        assertEquals("CLOSED", domain.topology);
        assertEquals("EVERYONE", domain.capturePolicy);
        assertEquals("OWNER", domain.protectionPolicy);
        assertTrue(domain.clashValue <= 100,
            "Simple Domain's potency 100 must be able to contest the sure-hit");
        for (AbilityEffectData row : domain.sureHitEffects) {
            assertEquals("EFFECT", row.domainDeliveryClass);
            assertTrue(Boolean.TRUE.equals(row.soulDamage));
            assertFalse("ON_ESTABLISH".equalsIgnoreCase(row.domainTrigger),
                "the sure-hit must never fire merely on establishment");
        }
        assertTrue(domain.sureHitEffects.stream().anyMatch(row ->
            "ON_MEMBER_ENTER".equalsIgnoreCase(row.domainTrigger)
                && "ENTERING_MEMBER".equalsIgnoreCase(row.domainAudience)));
        assertTrue(domain.sureHitEffects.stream().anyMatch(row ->
            "EACH_TICK".equalsIgnoreCase(row.domainTrigger)
                && "ENEMY_MEMBERS".equalsIgnoreCase(row.domainAudience)
                && row.domainIntervalTicks == 1));
    }

    @Test
    void soulDamageSurvivesTheMoveRoundTrip() throws IOException {
        Catalogs catalogs = catalogs();
        MoveData touch = catalogs.moves().stream()
            .filter(move -> "000143".equals(move.id))
            .findFirst().orElseThrow();
        assertTrue(touch.hitComponents.get(0).soulDamage);
        MoveData roundTrip = MoveData.fromMove(touch.toMove());
        assertTrue(roundTrip.hitComponents.get(0).soulDamage,
            "DTO -> Move -> DTO must preserve the soul-damage flag");
    }

    @Test
    void theTechniqueTreeResolvesAndSynchronizesWithoutDroppingNodes()
        throws IOException {
        Catalogs catalogs = catalogs();
        InnateTechniqueData technique = catalogs.techniques().stream()
            .filter(entry -> TECHNIQUE.equalsIgnoreCase(entry.name))
            .findFirst().orElseThrow();

        Set<String> moveIds = catalogs.moves().stream()
            .map(move -> move.id).collect(Collectors.toSet());
        Set<String> abilityIds = catalogs.abilities().stream()
            .map(ability -> ability.id).collect(Collectors.toSet());
        Set<String> domainIds = catalogs.domains().stream()
            .map(domain -> domain.id).collect(Collectors.toSet());

        Set<String> nodeIds = new HashSet<>();
        for (SkillTreeNodeData node : technique.skillTree) {
            assertTrue(nodeIds.add(node.id), "duplicate node " + node.id);
            switch (node.contentType.toUpperCase()) {
                case SkillTreeNodeData.MOVE ->
                    assertTrue(moveIds.contains(node.contentId), node.id);
                case SkillTreeNodeData.ABILITY ->
                    assertTrue(abilityIds.contains(node.contentId), node.id);
                case SkillTreeNodeData.DOMAIN ->
                    assertTrue(domainIds.contains(node.contentId), node.id);
                default -> throw new AssertionError(
                    "unknown content type " + node.contentType);
            }
        }
        assertTrue(technique.skillTree.stream().noneMatch(node ->
                SkillTreeNodeData.DOMAIN.equalsIgnoreCase(node.contentType)),
            "the opening move alone represents the Domain");
        assertTrue(technique.skillTree.stream().anyMatch(node ->
                SkillTreeNodeData.MOVE.equalsIgnoreCase(node.contentType)
                    && "000150".equals(node.contentId)),
            "the Domain-opening move must be reachable through the tree");

        // The sync pass must consider every authored node legitimate: nothing
        // dangles, so nothing is pruned and nothing is auto-added.
        int before = technique.skillTree.size();
        TechniqueSkillTree.synchronize(
            technique, catalogs.moves(), catalogs.abilities(), catalogs.domains());
        assertEquals(before, technique.skillTree.size(),
            "sync must not change a fully consistent tree");
    }

    @Test
    void theCodedAbilitiesValidateAgainstTheRegistry() throws IOException {
        Catalogs catalogs = catalogs();
        for (String abilityId : List.of("000060", "000061", "000062")) {
            AbilityData ability = catalogs.abilities().stream()
                .filter(entry -> abilityId.equals(entry.id))
                .findFirst().orElseThrow();
            assertEquals(TECHNIQUE, ability.sourceValue);
            for (AbilityEffectData effect : ability.effects) {
                assertTrue(AbilityEffectType.CODED.name()
                    .equalsIgnoreCase(effect.type));
                String error = AbilityEffectType.CODED.validationError(effect);
                assertTrue(error == null, abilityId + ": " + error);
            }
        }
    }
}
