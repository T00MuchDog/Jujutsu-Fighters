package com.jjktbf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.technique.InnateTechniqueData;
import com.jjktbf.model.technique.SkillTreeNodeData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                assertTrue(techniques.stream().anyMatch(technique ->
                        domain.requiredTechniqueName.equalsIgnoreCase(technique.name)
                        && technique.skillTree != null
                        && technique.skillTree.stream().anyMatch(node ->
                            SkillTreeNodeData.DOMAIN.equalsIgnoreCase(node.contentType)
                            && domain.id.equals(node.contentId))),
                    domain.id + " has no DOMAIN node on " + domain.requiredTechniqueName);
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
}
