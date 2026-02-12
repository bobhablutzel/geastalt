-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create contracts table for contract definitions (MySQL equivalent of PostgreSQL migration 013)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_013;

DELIMITER //
CREATE PROCEDURE run_migration_013()
BEGIN
    CREATE TABLE IF NOT EXISTS contracts (
        id             VARCHAR(36) PRIMARY KEY,
        contract_name  VARCHAR(255) NOT NULL,
        company_id     VARCHAR(36) NOT NULL,
        company_name   VARCHAR(255) NOT NULL
    ) COMMENT = 'Contract definitions with company information';

    -- Create index for company lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contracts' AND index_name = 'idx_contracts_company_id') THEN
        CREATE INDEX idx_contracts_company_id ON contracts(company_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_013();
DROP PROCEDURE IF EXISTS run_migration_013;
