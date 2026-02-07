-- Create plans table for insurance/benefit plan definitions (MySQL equivalent of PostgreSQL migration 013)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_013;

DELIMITER //
CREATE PROCEDURE run_migration_013()
BEGIN
    CREATE TABLE IF NOT EXISTS plans (
        id           BIGINT AUTO_INCREMENT PRIMARY KEY,
        plan_name    VARCHAR(255) NOT NULL,
        carrier_id   INTEGER NOT NULL,
        carrier_name VARCHAR(255) NOT NULL,
        created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) COMMENT = 'Insurance/benefit plan definitions with carrier information';

    -- Create index for carrier lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'plans' AND index_name = 'idx_plans_carrier_id') THEN
        CREATE INDEX idx_plans_carrier_id ON plans(carrier_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_013();
DROP PROCEDURE IF EXISTS run_migration_013;
