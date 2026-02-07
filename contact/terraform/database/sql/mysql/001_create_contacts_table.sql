-- Create contacts table (MySQL equivalent of PostgreSQL migration 001)
-- This script is idempotent and can be run multiple times safely

DROP PROCEDURE IF EXISTS run_migration_001;

DELIMITER //
CREATE PROCEDURE run_migration_001()
BEGIN
    CREATE TABLE IF NOT EXISTS contacts (
        id         BIGINT AUTO_INCREMENT PRIMARY KEY,
        email      VARCHAR(255),
        first_name VARCHAR(255),
        last_name  VARCHAR(255),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) COMMENT = 'Contact information table';

    -- Create index on email for faster lookups
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_email') THEN
        CREATE INDEX idx_contacts_email ON contacts(email);
    END IF;
END //
DELIMITER ;

CALL run_migration_001();
DROP PROCEDURE IF EXISTS run_migration_001;
