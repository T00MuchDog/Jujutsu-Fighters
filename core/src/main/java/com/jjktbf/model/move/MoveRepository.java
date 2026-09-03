package com.jjktbf.model.move;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jjktbf.model.repo.BaseRepository;

import java.util.List;
import java.io.IOException;

/**
 * Persistent repository for move definitions ({@code data/moves/all_moves.json}).
 *
 * IDs are stable 6-digit authored identifiers. Deleting a move leaves a gap,
 * and new moves are assigned after the highest existing numeric ID.
 *
 * On first run (no file), seeds from the bundled classpath default
 * ({@code data/moves/all_moves.json}).
 *
 * Technique restrictions live on {@link MoveData#requiredTechniqueId}; a
 * dedicated technique registry is future work.
 */
public class MoveRepository extends BaseRepository<MoveData> {

    public MoveRepository(String dataDirectory) {
        super(dataDirectory, "all_moves.json");
    }

    @Override
    public void load() throws IOException {
        super.load();
        boolean migrated = false;
        for (MoveData move : getAll()) {
            if (move != null) {
                migrated |= move.migrateLegacyEffects();
                migrated |= move.migrateLegacyHitTags();
            }
        }
        if (migrated) save();
    }

    @Override protected String idOf(MoveData d)            { return d.id; }
    @Override protected void assignId(MoveData d, String id){ d.id = id; }
    @Override protected String entityName()                 { return "move"; }

    /** Move IDs are referenced throughout authored combat data and the network protocol. */
    @Override protected void resequence() { }

    @Override public String nextId() {
        int next = getAll().stream()
            .map(move -> move.id)
            .filter(id -> id != null && id.matches("\\d{6}"))
            .mapToInt(Integer::parseInt)
            .max()
            .orElse(-1) + 1;
        return formatId(next);
    }

    @Override protected TypeReference<List<MoveData>> typeReference() {
        return new TypeReference<>() {};
    }

    @Override protected String bundledResourcePath() {
        return "data/moves/all_moves.json";
    }
}
