-- Widen id_type column from VARCHAR(20) to VARCHAR(50) to support free-form alternate ID types (MySQL equivalent of PostgreSQL migration 016)
-- In MySQL, MODIFY COLUMN is naturally idempotent (re-running produces the same result)

DROP PROCEDURE IF EXISTS run_migration_016;

DELIMITER //
CREATE PROCEDURE run_migration_016()
BEGIN
    ALTER TABLE contact_alternate_ids MODIFY COLUMN id_type VARCHAR(50) NOT NULL;
END //
DELIMITER ;

CALL run_migration_016();
DROP PROCEDURE IF EXISTS run_migration_016;
