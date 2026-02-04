-- Create member_pending_actions table for tracking pending actions per member

CREATE TABLE IF NOT EXISTS member_pending_actions (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    action_type VARCHAR(50) NOT NULL,  -- GENERATE_EXTERNAL_IDENTIFIERS, VALIDATE_ADDRESS
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_member_pending_actions_member_type UNIQUE (member_id, action_type)
);

-- Create index for efficient lookups by member
CREATE INDEX IF NOT EXISTS idx_member_pending_actions_member ON member_pending_actions(member_id);

-- Create index for finding all members with a specific pending action
CREATE INDEX IF NOT EXISTS idx_member_pending_actions_type ON member_pending_actions(action_type);

-- Add comments
COMMENT ON TABLE member_pending_actions IS 'Pending actions for members, supporting zero to many actions per member';
COMMENT ON COLUMN member_pending_actions.action_type IS 'Type of pending action: GENERATE_EXTERNAL_IDENTIFIERS, VALIDATE_ADDRESS';
