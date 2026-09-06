package com.jjktbf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.technique.InnateTechniqueData;
import com.jjktbf.model.technique.SkillTreeNodeData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural consistency of the bundled Domain catalog: every authored
 * definition validates and every technique coupling resolves. Deliberately
 * free of value assertions — editors may retune any of these numbers.
 */
class DomainCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    @Test
    void bundledDomainsValidateAndResolveTheirTechniqueCouplings() throws IOException {
        List<DomainData> domains = read("domains", new TypeReference<>() { });
        List<InnateTechniqueData> techniques = read("techniques", new TypeReference<>() { });
        List<MoveData> moves = read("moves", new TypeReference<>() { });
        Set<String> techniqueNames = new HashSet<>();
        for (InnateTechniqueData technique : techniques) {
            techniqueNames.add(technique.name == null ? "" : technique.name.trim().toLowerCase());
        }

        Set<String> domainIds = new HashSet<>();
        for (DomainData domain : domains) {
            domain.validate();
            assertTrue(domainIds.add(domain.id), "duplicate domain id " + domain.id);
            if (!domain.antiDomain && domain.requiredTechniqueName != null
                && !domain.requiredTechniqueName.isBlank()) {
                assertTrue(techniqueNames.contains(
                        domain.requiredTechniqueName.trim().toLowerCase()),
                    domain.id + " requires unknown technique " + domain.requiredTechniqueName);
                // A coupled Domain is reachable through a DOMAIN node or, when
                // the technique opens it with a move of its own, through that
                // move's node — the two representations never coexist.
                boolean hasDomainNode = techniques.stream().anyMatch(technique ->
                    domain.requiredTechniqueName.equalsIgnoreCase(technique.name)
                        && technique.skillTree != null
                        && technique.skillTree.stream().anyMatch(node ->
                            SkillTreeNodeData.DOMAIN.equalsIgnoreCase(node.contentType)
                                && domain.id.equals(node.contentId)));
                boolean hasOpeningMoveNode = techniques.stream().anyMatch(technique ->
                    domain.requiredTechniqueName.equalsIgnoreCase(technique.name)
                        && technique.skillTree != null
                        && technique.skillTree.stream().anyMatch(node ->
                            SkillTreeNodeData.MOVE.equalsIgnoreCase(node.contentType)
                                && opensDomain(moves, node.contentId, domain.id)));
                assertTrue(hasDomainNode || hasOpeningMoveNode,
                    domain.id + " is unreachable on " + domain.requiredTechniqueName
                        + " (no DOMAIN node and no opening-move node)");
                assertFalse(hasDomainNode && hasOpeningMoveNode,
                    domain.id + " is represented twice on "
                        + domain.requiredTechniqueName
                        + " (both a DOMAIN node and an opening-move node)");
            }
        }

        for (InnateTechniqueData technique : techniques) {
            for (SkillTreeNodeData node : technique.skillTree == null
                    ? List.<SkillTreeNodeData>of() : technique.skillTree) {
                if (SkillTreeNodeData.DOMAIN.equalsIgnoreCase(node.contentType)) {
                    assertTrue(domainIds.contains(node.contentId),
                        technique.name + " references unknown domain " + node.contentId);
                }
            }
        }
    }

    /** True when the move establishes the given Domain through its effect rows. */
    private static boolean opensDomain(List<MoveData> moves, String moveId, String domainId) {
        for (MoveData move : moves) {
            if (move == null || !moveId.equals(move.id) || move.effects == null) continue;
            for (MoveEffectData effect : move.effects) {
                if (effect != null
                        && AbilityEffectType.ESTABLISH_DOMAIN.name()
                            .equalsIgnoreCase(effect.type)
                        && domainId.equals(effect.domainId == null ? null : effect.domainId.trim())) {
                    return true;
                }
            }
        }
        return false;
    }
}
