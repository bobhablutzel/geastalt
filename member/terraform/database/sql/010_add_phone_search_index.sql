-- Migration 010: Add index for phone number searches
-- This enables efficient lookups of members by phone number

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_member_phones_phone_number
ON member_phones (phone_number);
