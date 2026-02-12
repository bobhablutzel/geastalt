-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contact_addresses table (MySQL equivalent of PostgreSQL migration 003)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_003;

DELIMITER //
CREATE PROCEDURE run_migration_003()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_addresses (
        id           BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id   BIGINT NOT NULL,
        address_id   BIGINT NOT NULL,
        address_type VARCHAR(50) NOT NULL,
        CONSTRAINT uk_contact_address_type UNIQUE (contact_id, address_type),
        CONSTRAINT fk_contact_addresses_contact FOREIGN KEY (contact_id) REFERENCES contacts(id),
        CONSTRAINT fk_contact_addresses_address FOREIGN KEY (address_id) REFERENCES addresses(id)
    ) COMMENT = 'Links contacts to their standardized addresses with address type';

    -- Create indexes for faster lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_addresses' AND index_name = 'idx_contact_addresses_contact_id') THEN
        CREATE INDEX idx_contact_addresses_contact_id ON contact_addresses(contact_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_addresses' AND index_name = 'idx_contact_addresses_address_id') THEN
        CREATE INDEX idx_contact_addresses_address_id ON contact_addresses(address_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_003();
DROP PROCEDURE IF EXISTS run_migration_003;
