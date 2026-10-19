CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'SELLER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(4000),
    price NUMERIC(15,2) NOT NULL CHECK (price >= 0.01),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    category VARCHAR(100) NOT NULL CHECK (length(trim(category)) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    seller_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_products_status ON products(status);
CREATE TABLE promo_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9_]{4,20}$'),
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value NUMERIC(15,2) NOT NULL CHECK (discount_value > 0),
    min_order_amount NUMERIC(15,2) NOT NULL CHECK (min_order_amount >= 0),
    max_uses INTEGER NOT NULL CHECK (max_uses > 0),
    current_uses INTEGER NOT NULL DEFAULT 0 CHECK (current_uses >= 0 AND current_uses <= max_uses),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL CHECK (valid_until >= valid_from),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PAYMENT_PENDING', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELED')),
    promo_code_id UUID REFERENCES promo_codes(id),
    subtotal NUMERIC(22,2) NOT NULL CHECK (subtotal >= 0),
    discount_amount NUMERIC(22,2) NOT NULL CHECK (discount_amount >= 0 AND discount_amount <= subtotal),
    total_amount NUMERIC(22,2) NOT NULL CHECK (total_amount = subtotal - discount_amount),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_one_active_order_per_user ON orders(user_id) WHERE status IN ('CREATED', 'PAYMENT_PENDING');
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    price_at_order NUMERIC(15,2) NOT NULL CHECK (price_at_order >= 0.01),
    UNIQUE(order_id, product_id)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE TABLE user_operations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('CREATE_ORDER', 'UPDATE_ORDER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_operations_latest ON user_operations(user_id, operation_type, created_at DESC);
CREATE FUNCTION set_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER users_updated BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER products_updated BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER orders_updated BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER promos_updated BEFORE UPDATE ON promo_codes FOR EACH ROW EXECUTE FUNCTION set_updated_at();
