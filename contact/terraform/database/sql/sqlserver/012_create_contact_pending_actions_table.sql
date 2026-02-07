-- SQL Server equivalent of PostgreSQL 012_create_contact_pending_actions_table.sql
-- Create contact_pending_actions table for tracking pending actions per contact

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_pending_actions' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_pending_actions (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id  BIGINT NOT NULL REFERENCES dbo.contacts(id),
        action_type VARCHAR(50) NOT NULL,  -- GENERATE_EXTERNAL_IDENTIFIERS, VALIDATE_ADDRESS
        created_at  DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT uk_contact_pending_actions_contact_type UNIQUE (contact_id, action_type)
    );
END;

-- Create index for efficient lookups by contact
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_pending_actions_contact' AND object_id = OBJECT_ID('dbo.contact_pending_actions'))
    CREATE INDEX idx_contact_pending_actions_contact ON dbo.contact_pending_actions(contact_id);

-- Create index for finding all contacts with a specific pending action
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_pending_actions_type' AND object_id = OBJECT_ID('dbo.contact_pending_actions'))
    CREATE INDEX idx_contact_pending_actions_type ON dbo.contact_pending_actions(action_type);
