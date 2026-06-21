INSERT INTO product(id, name, description, category, active, created_at, updated_at) VALUES
('10000000-0000-0000-0000-000000000001', 'Premium Cotton T-Shirt', 'Heavyweight organic cotton T-shirt.', 'Clothing', true, now(), now()),
('10000000-0000-0000-0000-000000000002', 'Noise Cancelling Headphones', 'Wireless over-ear headphones.', 'Electronics', true, now(), now());

INSERT INTO sku(id, product_id, sku_code, attributes, price_minor, currency, active, created_at, updated_at) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'TSHIRT-BLK-M', '{"color":"black","size":"M"}', 7999, 'PLN', true, now(), now()),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'TSHIRT-BLK-L', '{"color":"black","size":"L"}', 7999, 'PLN', true, now(), now()),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'TSHIRT-WHT-M', '{"color":"white","size":"M"}', 7999, 'PLN', true, now(), now()),
('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'HEADPHONE-BLK', '{"color":"black"}', 89999, 'PLN', true, now(), now());
