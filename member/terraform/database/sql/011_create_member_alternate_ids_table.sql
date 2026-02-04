-- Create member_alternate_ids table for multiple alternate ID types per member
-- Migrates existing external_id data from members table

-- Create the new table
CREATE TABLE IF NOT EXISTS member_alternate_ids (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    id_type VARCHAR(20) NOT NULL,  -- NEW_NATIONS, OLD_NATIONS, PAN_HASH, MEMBER_TUPLE
    alternate_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_member_alternate_ids_member_type UNIQUE (member_id, id_type),
    CONSTRAINT uk_member_alternate_ids_type_alternate UNIQUE (id_type, alternate_id)
);

-- Create indexes for efficient lookups
CREATE INDEX IF NOT EXISTS idx_member_alternate_ids_member ON member_alternate_ids(member_id);
CREATE INDEX IF NOT EXISTS idx_member_alternate_ids_lookup ON member_alternate_ids(id_type, alternate_id);

-- Add comments
COMMENT ON TABLE member_alternate_ids IS 'Alternate identifiers for members, supporting multiple ID types per member';
COMMENT ON COLUMN member_alternate_ids.id_type IS 'Type of alternate ID: NEW_NATIONS, OLD_NATIONS, PAN_HASH, MEMBER_TUPLE';
COMMENT ON COLUMN member_alternate_ids.alternate_id IS 'The alternate identifier value';

-- Migrate existing external_id data from members table as NEW_NATIONS type
INSERT INTO member_alternate_ids (member_id, id_type, alternate_id, created_at)
SELECT id, 'NEW_NATIONS', external_id, NOW()
FROM members
WHERE external_id IS NOT NULL
ON CONFLICT (member_id, id_type) DO NOTHING;
