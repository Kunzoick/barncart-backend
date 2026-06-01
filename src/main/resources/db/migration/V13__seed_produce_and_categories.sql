-- New categories
INSERT INTO produce_category (id, name, refund_window_days, created_at, updated_at) VALUES
('a1b2c3d4-0001-0001-0001-000000000001', 'Fruits',     3, NOW(), NOW()),
('a1b2c3d4-0002-0002-0002-000000000002', 'Herbs',      2, NOW(), NOW()),
('a1b2c3d4-0003-0003-0003-000000000003', 'Root Vegetables', 5, NOW(), NOW()),
('a1b2c3d4-0004-0004-0004-000000000004', 'Leafy Greens', 2, NOW(), NOW());

-- Vegetables (existing category)
INSERT INTO produce (id, category_id, name, description, unit, is_active, image_url, created_at, updated_at) VALUES
('b1000001-0000-0000-0000-000000000001', 'd7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Broccoli',     'Fresh farm-grown broccoli, rich in vitamins',         'KG',    true, 'https://images.unsplash.com/photo-1459411621453-7b03977f4bfc?w=400', NOW(), NOW()),
('b1000001-0000-0000-0000-000000000002', 'd7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Cucumber',     'Crisp and refreshing field cucumbers',                'PIECE', true, 'https://images.unsplash.com/photo-1449300079323-02e209d9d3a6?w=400', NOW(), NOW()),
('b1000001-0000-0000-0000-000000000003', 'd7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Zucchini',     'Tender green zucchini, great for grilling',           'KG',    true, 'https://images.unsplash.com/photo-1563565375-f3fdfdbefa83?w=400', NOW(), NOW()),
('b1000001-0000-0000-0000-000000000004', 'd7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Bell Pepper',  'Colourful sweet bell peppers, red and yellow mix',   'KG',    true, 'https://images.unsplash.com/photo-1563565375-f3fdfdbefa83?w=400', NOW(), NOW()),
('b1000001-0000-0000-0000-000000000005', 'd7875f3d-ea57-4ca5-9ccc-79a153bfb690', 'Corn',         'Sweet farm corn, picked fresh daily',                 'PIECE', true, 'https://images.unsplash.com/photo-1601593346740-925612772716?w=400', NOW(), NOW());

-- Fruits
INSERT INTO produce (id, category_id, name, description, unit, is_active, image_url, created_at, updated_at) VALUES
('b2000002-0000-0000-0000-000000000001', 'a1b2c3d4-0001-0001-0001-000000000001', 'Strawberries', 'Sweet Ontario strawberries, hand-picked',             'KG',    true, 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=400', NOW(), NOW()),
('b2000002-0000-0000-0000-000000000002', 'a1b2c3d4-0001-0001-0001-000000000001', 'Blueberries',  'Plump wild blueberries from local farms',             'KG',    true, 'https://images.unsplash.com/photo-1498557850523-fd3d118b962e?w=400', NOW(), NOW()),
('b2000002-0000-0000-0000-000000000003', 'a1b2c3d4-0001-0001-0001-000000000001', 'Apples',       'Crisp Honeycrisp apples, freshly harvested',          'KG',    true, 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=400', NOW(), NOW()),
('b2000002-0000-0000-0000-000000000004', 'a1b2c3d4-0001-0001-0001-000000000001', 'Peaches',      'Juicy Ontario peaches, tree-ripened',                 'KG',    true, 'https://images.unsplash.com/photo-1595743825637-cdafc8ad4173?w=400', NOW(), NOW()),
('b2000002-0000-0000-0000-000000000005', 'a1b2c3d4-0001-0001-0001-000000000001', 'Watermelon',   'Sweet seedless watermelon, locally grown',            'PIECE', true, 'https://images.unsplash.com/photo-1568909344668-6f14a07b56a0?w=400', NOW(), NOW());

-- Herbs
INSERT INTO produce (id, category_id, name, description, unit, is_active, image_url, created_at, updated_at) VALUES
('b3000003-0000-0000-0000-000000000001', 'a1b2c3d4-0002-0002-0002-000000000002', 'Basil',        'Fresh sweet basil, aromatic and flavourful',          'BAG',   true, 'https://images.unsplash.com/photo-1618375569909-3c8616cf7733?w=400', NOW(), NOW()),
('b3000003-0000-0000-0000-000000000002', 'a1b2c3d4-0002-0002-0002-000000000002', 'Mint',         'Fresh spearmint, great for drinks and cooking',       'BAG',   true, 'https://images.unsplash.com/photo-1628556270448-4d4e4148e1b1?w=400', NOW(), NOW()),
('b3000003-0000-0000-0000-000000000003', 'a1b2c3d4-0002-0002-0002-000000000002', 'Rosemary',     'Woody fresh rosemary sprigs, farm grown',             'BAG',   true, 'https://images.unsplash.com/photo-1515586838455-8b9b6f91bf8b?w=400', NOW(), NOW());

-- Root Vegetables
INSERT INTO produce (id, category_id, name, description, unit, is_active, image_url, created_at, updated_at) VALUES
('b4000004-0000-0000-0000-000000000001', 'a1b2c3d4-0003-0003-0003-000000000003', 'Carrots',      'Sweet organic carrots, freshly pulled',               'KG',    true, 'https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?w=400', NOW(), NOW()),
('b4000004-0000-0000-0000-000000000002', 'a1b2c3d4-0003-0003-0003-000000000003', 'Potatoes',     'Yukon Gold potatoes, great for roasting',             'KG',    true, 'https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=400', NOW(), NOW()),
('b4000004-0000-0000-0000-000000000003', 'a1b2c3d4-0003-0003-0003-000000000003', 'Garlic',       'Fresh whole garlic bulbs, strong and aromatic',       'PIECE', true, 'https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=400', NOW(), NOW()),
('b4000004-0000-0000-0000-000000000004', 'a1b2c3d4-0003-0003-0003-000000000003', 'Onions',       'Yellow cooking onions, firm and fresh',               'KG',    true, 'https://images.unsplash.com/photo-1508747703725-719777637510?w=400', NOW(), NOW());

-- Leafy Greens
INSERT INTO produce (id, category_id, name, description, unit, is_active, image_url, created_at, updated_at) VALUES
('b5000005-0000-0000-0000-000000000001', 'a1b2c3d4-0004-0004-0004-000000000004', 'Spinach',      'Baby spinach leaves, tender and fresh',               'BAG',   true, 'https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=400', NOW(), NOW()),
('b5000005-0000-0000-0000-000000000002', 'a1b2c3d4-0004-0004-0004-000000000004', 'Kale',         'Curly kale, packed with nutrients',                   'BAG',   true, 'https://images.unsplash.com/photo-1524179091875-bf99a9a6af57?w=400', NOW(), NOW()),
('b5000005-0000-0000-0000-000000000003', 'a1b2c3d4-0004-0004-0004-000000000004', 'Lettuce',      'Crisp romaine lettuce, perfect for salads',           'PIECE', true, 'https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1?w=400', NOW(), NOW()),
('b5000005-0000-0000-0000-000000000004', 'a1b2c3d4-0004-0004-0004-000000000004', 'Swiss Chard',  'Rainbow Swiss chard, colourful and nutritious',       'BAG',   true, 'https://images.unsplash.com/photo-1518779578993-ec3579fee39f?w=400', NOW(), NOW());