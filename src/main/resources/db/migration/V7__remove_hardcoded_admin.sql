-- Remove hardcoded admin user seeded in V3
-- Admin is now created at startup via AdminSeeder using environment variables
-- Must delete refresh tokens first due to FK constraint on refresh_token.user_id
DELETE FROM refresh_token WHERE user_id = '00000000-0000-0000-0000-000000000001';
DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000001';