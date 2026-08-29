package com.jjktbf.model.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jjktbf.model.repo.BaseRepository;

import java.util.List;
import java.util.Optional;

/** Persistent repository for {@code data/domains/all_domains.json}. */
public final class DomainRepository extends BaseRepository<DomainData> {

    public DomainRepository(String dataDirectory) {
        super(dataDirectory, "all_domains.json");
    }

    @Override protected String idOf(DomainData data) { return data.id; }
    @Override protected void assignId(DomainData data, String id) { data.id = id; }
    @Override protected TypeReference<List<DomainData>> typeReference() {
        return new TypeReference<>() { };
    }
    @Override protected String bundledResourcePath() { return "data/domains/all_domains.json"; }
    @Override protected String entityName() { return "domain"; }

    public Optional<DomainData> findByName(String name) {
        if (name == null) return Optional.empty();
        return getAll().stream().filter(domain -> name.equalsIgnoreCase(domain.name)).findFirst();
    }
}
