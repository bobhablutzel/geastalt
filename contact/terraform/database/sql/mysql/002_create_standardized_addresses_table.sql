-- Copyright (c) 2026 Bob Hablutzel. All rights reserved.
--
-- Licensed under a dual-license model: freely available for non-commercial use;
-- commercial use requires a separate license. See LICENSE file for details.
-- Contact license@geastalt.com for commercial licensing.

-- Create addresses table (international address model, MySQL)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_002;

DELIMITER //
CREATE PROCEDURE run_migration_002()
BEGIN
    CREATE TABLE IF NOT EXISTS addresses (
        id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
        locality             VARCHAR(255),
        administrative_area  VARCHAR(100),
        postal_code          VARCHAR(20),
        country_code         VARCHAR(2),
        sub_locality         VARCHAR(255),
        sorting_code         VARCHAR(20),
        validated            BOOLEAN NOT NULL DEFAULT false,
        -- Generated columns for index (avoids key length issues)
        locality_key              VARCHAR(150) GENERATED ALWAYS AS (LEFT(COALESCE(locality, ''), 150)) STORED,
        administrative_area_key   VARCHAR(100) GENERATED ALWAYS AS (COALESCE(administrative_area, '')) STORED,
        postal_code_key           VARCHAR(20)  GENERATED ALWAYS AS (COALESCE(postal_code, '')) STORED,
        country_code_key          VARCHAR(2)   GENERATED ALWAYS AS (COALESCE(country_code, '')) STORED
    ) COMMENT = 'International postal addresses';

    -- Create index for deduplication lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'addresses' AND index_name = 'idx_addresses_lookup') THEN
        CREATE INDEX idx_addresses_lookup
        ON addresses(
            locality_key,
            administrative_area_key,
            postal_code_key,
            country_code_key
        );
    END IF;

    -- Create address_lines table
    CREATE TABLE IF NOT EXISTS address_lines (
        id            BIGINT AUTO_INCREMENT PRIMARY KEY,
        address_id    BIGINT NOT NULL,
        line_order    INT NOT NULL,
        line_value    VARCHAR(255) NOT NULL,
        CONSTRAINT uk_address_line_order UNIQUE (address_id, line_order),
        CONSTRAINT fk_address_lines_address FOREIGN KEY (address_id) REFERENCES addresses(id) ON DELETE CASCADE
    ) COMMENT = 'Address line components (street, secondary, etc.)';

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'address_lines' AND index_name = 'idx_address_lines_address_id') THEN
        CREATE INDEX idx_address_lines_address_id ON address_lines(address_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_002();
DROP PROCEDURE IF EXISTS run_migration_002;
