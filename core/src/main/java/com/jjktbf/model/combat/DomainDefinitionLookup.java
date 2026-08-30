package com.jjktbf.model.combat;

import com.jjktbf.model.domain.DomainDefinition;

import java.util.Optional;

/** Injected lookup for immutable Domain definitions used by battle effects. */
@FunctionalInterface
public interface DomainDefinitionLookup {
    Optional<DomainDefinition> findDomain(String domainId);
}
