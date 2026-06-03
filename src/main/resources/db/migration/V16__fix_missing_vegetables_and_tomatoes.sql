-- Vegetables category and Tomatoes produce were created manually in local DB
-- and never included in a migration. Added here for fresh DB compatibility.

INSERT IGNORE INTO produce_category (id, name, refund_window_days, created_at, updated_at) VALUES
('d7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Vegetables', 4, NOW(), NOW());

INSERT IGNORE INTO produce (id, category_id, name, description, unit, is_active, image_url, created_at, updated_at) VALUES
('66f0324b-7d1a-4e30-a228-cc6e7f2bc91e', 'd7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Tomatoes', 'Fresh vine-ripened tomatoes', 'KG', true, 'https://images.unsplash.com/photo-1546470427-e26264be0b0d?w=400', NOW(), NOW());