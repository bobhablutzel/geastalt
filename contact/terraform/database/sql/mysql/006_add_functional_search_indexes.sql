-- Create functional indexes for case-insensitive name search (MySQL equivalent of PostgreSQL migration 006)
-- MySQL functional indexes (8.0.13+) replace PostgreSQL's LOWER() with varchar_pattern_ops

DROP PROCEDURE IF EXISTS run_migration_006;

DELIMITER //
CREATE PROCEDURE run_migration_006()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_last_name_lower') THEN
        CREATE INDEX idx_contacts_last_name_lower ON contacts ((LOWER(last_name)));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_first_name_lower') THEN
        CREATE INDEX idx_contacts_first_name_lower ON contacts ((LOWER(first_name)));
    END IF;
END //
DELIMITER ;

CALL run_migration_006();
DROP PROCEDURE IF EXISTS run_migration_006;
