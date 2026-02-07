-- Create contact_emails table (MySQL equivalent of PostgreSQL migration 004)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_004;

DELIMITER //
CREATE PROCEDURE run_migration_004()
BEGIN
    CREATE TABLE IF NOT EXISTS contact_emails (
        id         BIGINT AUTO_INCREMENT PRIMARY KEY,
        contact_id BIGINT NOT NULL,
        email      VARCHAR(255) NOT NULL,
        email_type VARCHAR(50) NOT NULL,
        CONSTRAINT uk_contact_email_type UNIQUE (contact_id, email_type),
        CONSTRAINT fk_contact_emails_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
    ) COMMENT = 'Stores contact email addresses with type (HOME, BUSINESS, MAILING)';

    -- Create index for faster lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contact_emails' AND index_name = 'idx_contact_emails_contact_id') THEN
        CREATE INDEX idx_contact_emails_contact_id ON contact_emails(contact_id);
    END IF;
END //
DELIMITER ;

CALL run_migration_004();
DROP PROCEDURE IF EXISTS run_migration_004;
