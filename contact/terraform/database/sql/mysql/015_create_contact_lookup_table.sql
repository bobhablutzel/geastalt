-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contact_lookup table for partition-aware contact lookups (MySQL equivalent of PostgreSQL migration 015)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_015;

DELIMITER //
CREATE PROCEDURE run_migration_015()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_lookup (
        contact_id       BIGINT PRIMARY KEY,
        partition_number INTEGER NOT NULL,
        created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_contact_lookup_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
    ) COMMENT = 'Partition-aware contact lookup table, referenced by contact_alternate_ids';

    -- Create index for partition-based queries
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_lookup' AND index_name = 'idx_contact_lookup_partition') THEN
        CREATE INDEX idx_contact_lookup_partition ON contact_lookup(partition_number);
    END IF;
END //
DELIMITER ;

CALL run_migration_015();
DROP PROCEDURE IF EXISTS run_migration_015;
