INSERT INTO users (
    id,
    email,
    password_hash,
    first_name,
    last_name,
    phone,
    role,
    is_active
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@farmmarket.com',
    '$2a$10$r.4D3zNAEAxLWNgtTUbGUu3hJqfQrqaxvyYF.1jMiqO0ZD1y6Lls2',
    'Farm',
    'Admin',
    NULL,
    'ADMIN',
    TRUE
);