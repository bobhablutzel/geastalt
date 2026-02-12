-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

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
