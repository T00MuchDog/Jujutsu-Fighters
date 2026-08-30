-- Ordered per-fighter move sets are match-owned selection data. JSON preserves
-- both roster alignment and move placement order while waiting matches restart.
ALTER TABLE match_participant
    ADD COLUMN move_set_ids VARCHAR(10000) NOT NULL DEFAULT '[]';
