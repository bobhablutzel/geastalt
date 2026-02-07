-- Create contact_plans table linking contacts to their plans (MySQL equivalent of PostgreSQL migration 014)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_014;

DELIMITER //
CREATE PROCEDURE run_migration_014()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_plans (
        id              BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id      BIGINT NOT NULL,
        plan_id         BIGINT NOT NULL,
        effective_date  TIMESTAMP NOT NULL,
        expiration_date TIMESTAMP NOT NULL,
        created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        CONSTRAINT fk_contact_plans_contact FOREIGN KEY (contact_id) REFERENCES contacts(id),
        CONSTRAINT fk_contact_plans_plan FOREIGN KEY (plan_id) REFERENCES plans(id)
    ) COMMENT = 'Links contacts to their insurance/benefit plans with effective date ranges';

    -- Create indexes for faster lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_plans' AND index_name = 'idx_contact_plans_contact_id') THEN
        CREATE INDEX idx_contact_plans_contact_id ON contact_plans(contact_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_plans' AND index_name = 'idx_contact_plans_plan_id') THEN
        CREATE INDEX idx_contact_plans_plan_id ON contact_plans(plan_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_014();
DROP PROCEDURE IF EXISTS run_migration_014;
