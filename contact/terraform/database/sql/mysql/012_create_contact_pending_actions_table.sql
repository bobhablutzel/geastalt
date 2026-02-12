-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contact_pending_actions table for tracking pending actions per contact (MySQL equivalent of PostgreSQL migration 012)

DROP PROCEDURE IF EXISTS run_migration_012;

DELIMITER //
CREATE PROCEDURE run_migration_012()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_pending_actions (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id BIGINT NOT NULL,
        action_type VARCHAR(50) NOT NULL,  -- GENERATE_EXTERNAL_IDENTIFIERS, VALIDATE_ADDRESS
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_contact_pending_actions_contact_type UNIQUE (contact_id, action_type),
        CONSTRAINT fk_contact_pending_actions_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
    ) COMMENT = 'Pending actions for contacts, supporting zero to many actions per contact';

    -- Create index for efficient lookups by contact
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_pending_actions' AND index_name = 'idx_contact_pending_actions_contact') THEN
        CREATE INDEX idx_contact_pending_actions_contact ON contact_pending_actions(contact_id);
    END IF;

    -- Create index for finding all contacts with a specific pending action
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_pending_actions' AND index_name = 'idx_contact_pending_actions_type') THEN
        CREATE INDEX idx_contact_pending_actions_type ON contact_pending_actions(action_type);
    END IF;
END //
DELIMITER ;

CALL run_migration_012();
DROP PROCEDURE IF EXISTS run_migration_012;
