-- Create contact_alternate_ids table for multiple alternate ID types per contact (MySQL equivalent of PostgreSQL migration 011)
-- Migrates existing external_id data from contacts table

DROP PROCEDURE IF EXISTS run_migration_011;

DELIMITER //
CREATE PROCEDURE run_migration_011()
BEGIN
    -- Create the new table
    CREATE TABLE IF NOT EXISTS contact_alternate_ids (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id BIGINT NOT NULL,
        id_type VARCHAR(20) NOT NULL,  -- NEW_NATIONS, OLD_NATIONS, PAN_HASH, CONTACT_TUPLE
        alternate_id VARCHAR(255) NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_contact_alternate_ids_contact_type UNIQUE (contact_id, id_type),
        CONSTRAINT uk_contact_alternate_ids_type_alternate UNIQUE (id_type, alternate_id),
        CONSTRAINT fk_contact_alternate_ids_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
    ) COMMENT = 'Alternate identifiers for contacts, supporting multiple ID types per contact';

    -- Create indexes for efficient lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_alternate_ids' AND index_name = 'idx_contact_alternate_ids_contact') THEN
        CREATE INDEX idx_contact_alternate_ids_contact ON contact_alternate_ids(contact_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_alternate_ids' AND index_name = 'idx_contact_alternate_ids_lookup') THEN
        CREATE INDEX idx_contact_alternate_ids_lookup ON contact_alternate_ids(id_type, alternate_id);
    END IF;

    -- Migrate existing external_id data from contacts table as NEW_NATIONS type
    -- INSERT IGNORE is naturally idempotent (skips rows that violate unique constraints)
    INSERT IGNORE INTO contact_alternate_ids (contact_id, id_type, alternate_id, created_at)
    SELECT id, 'NEW_NATIONS', external_id, NOW()
    FROM contacts
    WHERE external_id IS NOT NULL;
END //
DELIMITER ;

CALL run_migration_011();
DROP PROCEDURE IF EXISTS run_migration_011;
