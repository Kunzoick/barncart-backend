-- Extend produce.unit to include BAG for bagged goods (e.g. 50kg rice bags)
-- MariaDB 10.4 does not enforce CHECK constraints — application layer enforces valid values
ALTER TABLE produce
    MODIFY COLUMN unit VARCHAR(10) NOT NULL;

-- Add bag_weight_kg to listing — nullable, only set when produce.unit = BAG
-- For loose produce sold by weight (KG, G, LB), this stays NULL
-- For bagged goods, this records the weight per bag for logistics and display
ALTER TABLE listing
    ADD COLUMN bag_weight_kg DECIMAL(10, 2) NULL;