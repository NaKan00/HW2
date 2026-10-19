#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 <<'SQL'
SELECT id, email, role, created_at, updated_at FROM users ORDER BY created_at;
SELECT id, name, price, stock, category, status, seller_id, created_at, updated_at FROM products ORDER BY created_at;
SELECT * FROM orders ORDER BY created_at;
SELECT * FROM order_items ORDER BY order_id;
SELECT * FROM promo_codes ORDER BY code;
SELECT * FROM user_operations ORDER BY created_at;
SQL
