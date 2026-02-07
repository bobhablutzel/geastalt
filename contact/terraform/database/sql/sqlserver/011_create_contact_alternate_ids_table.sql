-- SQL Server equivalent of PostgreSQL 011_create_contact_alternate_ids_table.sql
-- Create contact_alternate_ids table for multiple alternate ID types per contact
-- Migrates existing external_id data from contacts table

-- Create the new table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contact_alternate_ids' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.contact_alternate_ids (
        id           BIGINT IDENTITY(1,1) PRIMARY KEY,
        contact_id   BIGINT NOT NULL REFERENCES dbo.contacts(id),
        id_type      VARCHAR(20) NOT NULL,  -- NEW_NATIONS, OLD_NATIONS, PAN_HASH, CONTACT_TUPLE
        alternate_id VARCHAR(255) NOT NULL,
        created_at   DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT uk_contact_alternate_ids_contact_type UNIQUE (contact_id, id_type),
        CONSTRAINT uk_contact_alternate_ids_type_alternate UNIQUE (id_type, alternate_id)
    );
END;

-- Create indexes for efficient lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_alternate_ids_contact' AND object_id = OBJECT_ID('dbo.contact_alternate_ids'))
    CREATE INDEX idx_contact_alternate_ids_contact ON dbo.contact_alternate_ids(contact_id);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_contact_alternate_ids_lookup' AND object_id = OBJECT_ID('dbo.contact_alternate_ids'))
    CREATE INDEX idx_contact_alternate_ids_lookup ON dbo.contact_alternate_ids(id_type, alternate_id);

-- Migrate existing external_id data from contacts table as NEW_NATIONS type
-- Uses WHERE NOT EXISTS as the SQL Server equivalent of ON CONFLICT DO NOTHING
INSERT INTO dbo.contact_alternate_ids (contact_id, id_type, alternate_id, created_at)
SELECT id, 'NEW_NATIONS', external_id, GETDATE()
FROM dbo.contacts
WHERE external_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM dbo.contact_alternate_ids cai
      WHERE cai.contact_id = dbo.contacts.id
        AND cai.id_type = 'NEW_NATIONS'
  );
