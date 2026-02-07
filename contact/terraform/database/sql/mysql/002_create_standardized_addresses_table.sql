-- Create standardized_addresses table (MySQL equivalent of PostgreSQL migration 002)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_002;

DELIMITER //
CREATE PROCEDURE run_migration_002()
BEGIN
    CREATE TABLE IF NOT EXISTS standardized_addresses (
        id                BIGINT AUTO_INCREMENT PRIMARY KEY,
        street_address    VARCHAR(255),
        secondary_address VARCHAR(255),
        city              VARCHAR(255),
        state             VARCHAR(50),
        zip_code          VARCHAR(10),
        zip_plus4         VARCHAR(10),
        -- Generated columns for the unique index (avoids key length issues with COALESCE on large VARCHARs)
        street_address_key    VARCHAR(150) GENERATED ALWAYS AS (LEFT(COALESCE(street_address, ''), 150)) STORED,
        secondary_address_key VARCHAR(150) GENERATED ALWAYS AS (LEFT(COALESCE(secondary_address, ''), 150)) STORED,
        city_key              VARCHAR(100) GENERATED ALWAYS AS (LEFT(COALESCE(city, ''), 100)) STORED,
        state_key             VARCHAR(50)  GENERATED ALWAYS AS (COALESCE(state, '')) STORED,
        zip_code_key          VARCHAR(10)  GENERATED ALWAYS AS (COALESCE(zip_code, '')) STORED,
        zip_plus4_key         VARCHAR(10)  GENERATED ALWAYS AS (COALESCE(zip_plus4, '')) STORED
    ) COMMENT = 'USPS standardized addresses cache';

    -- Create unique index on generated columns to prevent duplicates
    -- Total key length: (150+150+100+50+10+10) * 4 = 1880 bytes, within the 3072 byte limit
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'standardized_addresses' AND index_name = 'idx_standardized_addresses_unique') THEN
        CREATE UNIQUE INDEX idx_standardized_addresses_unique
        ON standardized_addresses(
            street_address_key,
            secondary_address_key,
            city_key,
            state_key,
            zip_code_key,
            zip_plus4_key
        );
    END IF;
END //
DELIMITER ;

CALL run_migration_002();
DROP PROCEDURE IF EXISTS run_migration_002;
