-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contact_contracts table linking contacts to their contracts (MySQL equivalent of PostgreSQL migration 014)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_014;

DELIMITER //
CREATE PROCEDURE run_migration_014()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_contracts (
        id              BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id      BIGINT NOT NULL,
        contract_id     VARCHAR(36) NOT NULL,
        effective_date  TIMESTAMP NOT NULL,
        expiration_date TIMESTAMP NOT NULL,
        created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        CONSTRAINT fk_contact_contracts_contact FOREIGN KEY (contact_id) REFERENCES contacts(id),
        CONSTRAINT fk_contact_contracts_contract FOREIGN KEY (contract_id) REFERENCES contracts(id)
    ) COMMENT = 'Links contacts to their contracts with effective date ranges';

    -- Create indexes for faster lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_contracts' AND index_name = 'idx_contact_contracts_contact_id') THEN
        CREATE INDEX idx_contact_contracts_contact_id ON contact_contracts(contact_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_contracts' AND index_name = 'idx_contact_contracts_contract_id') THEN
        CREATE INDEX idx_contact_contracts_contract_id ON contact_contracts(contract_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_014();
DROP PROCEDURE IF EXISTS run_migration_014;
