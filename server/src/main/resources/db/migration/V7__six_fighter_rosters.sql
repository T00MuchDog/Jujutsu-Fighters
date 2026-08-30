-- Six-fighter formats exceed the original two-fighter comma-joined roster size.
ALTER TABLE challenge DROP CONSTRAINT ck_challenge_format;

ALTER TABLE challenge ALTER COLUMN host_character_ids TYPE VARCHAR(512);
ALTER TABLE challenge ALTER COLUMN requested_character_ids TYPE VARCHAR(512);
ALTER TABLE challenge ALTER COLUMN accepted_character_ids TYPE VARCHAR(512);
ALTER TABLE match_participant ALTER COLUMN character_ids TYPE VARCHAR(512);

ALTER TABLE challenge ADD CONSTRAINT ck_challenge_format
    CHECK (format IN ('ONE_V_ONE', 'TWO_V_TWO', 'SIX_V_SIX'));
