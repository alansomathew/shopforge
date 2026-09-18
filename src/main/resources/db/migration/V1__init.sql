-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Create PostgreSQL Custom ENUM Types
CREATE TYPE user_status AS ENUM ('ACTIVE', 'BANNED', 'DELETED');
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'SELLER', 'ADMIN', 'SUPPORT');
CREATE TYPE product_status AS ENUM ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED');
CREATE TYPE order_status AS ENUM ('PENDING', 'PAYMENT_RECEIVED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURN_APPROVED', 'RETURN_REJECTED', 'REFUND_INITIATED', 'REFUNDED');
CREATE TYPE payment_status AS ENUM ('INITIATED', 'SUCCESS', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED');
CREATE TYPE coupon_type AS ENUM ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING', 'BUY_X_GET_Y');
CREATE TYPE review_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED');
CREATE TYPE inv_txn_type AS ENUM ('PURCHASE', 'SALE', 'RETURN', 'ADJUSTMENT', 'RESERVATION', 'RELEASE');

-- 2. Create Trigger Functions

-- Trigger function to automatically set updated_at column to current time
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger function to automatically recalculate and update product average rating & review count
CREATE OR REPLACE FUNCTION recalculate_product_rating()
RETURNS TRIGGER AS $$
DECLARE
    p_id UUID;
    v_avg_rating NUMERIC(3,2);
    v_review_count INT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        p_id := OLD.product_id;
    ELSE
        p_id := NEW.product_id;
    END IF;

    SELECT COALESCE(AVG(rating), 0), COUNT(*)
    INTO v_avg_rating, v_review_count
    FROM reviews
    WHERE product_id = p_id AND status = 'APPROVED';

    UPDATE products
    SET avg_rating = v_avg_rating,
        review_count = v_review_count
    WHERE id = p_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger function to update search_vector for full-text search
CREATE OR REPLACE FUNCTION update_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.name, '') || ' ' ||
        coalesce(NEW.short_description, '') || ' ' ||
        coalesce(NEW.description, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger function to update inventory quantity_on_hand and quantity_reserved on transaction entries
CREATE OR REPLACE FUNCTION apply_inventory_delta()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.type = 'RESERVATION' THEN
        UPDATE inventory
        SET quantity_reserved = quantity_reserved + NEW.quantity_delta,
            updated_at = now()
        WHERE variant_id = NEW.variant_id;
    ELSIF NEW.type = 'RELEASE' THEN
        UPDATE inventory
        SET quantity_reserved = quantity_reserved + NEW.quantity_delta,
            updated_at = now()
        WHERE variant_id = NEW.variant_id;
    ELSE
        UPDATE inventory
        SET quantity_on_hand = quantity_on_hand + NEW.quantity_delta,
            updated_at = now()
        WHERE variant_id = NEW.variant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger function to automatically generate order numbers: ORD-YYYYMMDD-{random4}
CREATE OR REPLACE FUNCTION generate_order_number()
RETURNS TRIGGER AS $$
DECLARE
    date_str VARCHAR(8);
    rand_str VARCHAR(4);
    generated_number VARCHAR(30);
    exists_flag BOOLEAN;
BEGIN
    date_str := to_char(CURRENT_DATE, 'YYYYMMDD');
    LOOP
        rand_str := lpad(floor(random() * 10000)::text, 4, '0');
        generated_number := 'ORD-' || date_str || '-' || rand_str;
        SELECT EXISTS(SELECT 1 FROM orders WHERE order_number = generated_number) INTO exists_flag;
        EXIT WHEN NOT exists_flag;
    END LOOP;
    
    NEW.order_number := generated_number;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- 3. Create Tables

-- public.users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    phone VARCHAR(20),
    phone_verified BOOLEAN NOT NULL DEFAULT false,
    password_hash VARCHAR(255),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    avatar_url TEXT,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts SMALLINT NOT NULL DEFAULT 0,
    lockout_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT users_phone_unique UNIQUE (phone)
);
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));
CREATE INDEX users_status_idx ON users (status) WHERE deleted_at IS NULL;

-- public.user_roles
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role user_role NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role)
);

-- public.oauth_providers
CREATE TABLE oauth_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT oauth_provider_user_unique UNIQUE (provider, provider_user_id)
);

-- public.refresh_tokens
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    device_info VARCHAR(500),
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_token_hash_idx ON refresh_tokens (token_hash);

-- public.categories
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description TEXT,
    image_url TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    meta_title VARCHAR(255),
    meta_description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX categories_parent_id_idx ON categories (parent_id);

-- public.brands
CREATE TABLE brands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    logo_url TEXT,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.products
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    brand_id UUID REFERENCES brands(id) ON DELETE SET NULL,
    name VARCHAR(500) NOT NULL,
    slug VARCHAR(550) NOT NULL UNIQUE,
    description TEXT,
    short_description VARCHAR(500),
    status product_status NOT NULL DEFAULT 'DRAFT',
    is_featured BOOLEAN NOT NULL DEFAULT false,
    has_variants BOOLEAN NOT NULL DEFAULT false,
    base_price NUMERIC(12,2) NOT NULL,
    sale_price NUMERIC(12,2),
    tax_class VARCHAR(50),
    weight_grams INT,
    meta_title VARCHAR(255),
    meta_description TEXT,
    search_vector TSVECTOR,
    view_count BIGINT NOT NULL DEFAULT 0,
    avg_rating NUMERIC(3,2) DEFAULT NULL,
    review_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX products_category_status_idx ON products (category_id, status) WHERE deleted_at IS NULL;
CREATE INDEX products_seller_id_idx ON products (seller_id) WHERE deleted_at IS NULL;
CREATE INDEX products_avg_rating_review_count_idx ON products (avg_rating DESC, review_count DESC);
CREATE INDEX products_search_vector_idx ON products USING GIN (search_vector);

-- public.product_variants
CREATE TABLE product_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(300),
    attributes JSONB NOT NULL DEFAULT '{}',
    price NUMERIC(12,2) NOT NULL,
    sale_price NUMERIC(12,2),
    cost_price NUMERIC(12,2),
    barcode VARCHAR(100),
    weight_grams INT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX product_variants_attributes_idx ON product_variants USING GIN (attributes);

-- public.product_images
CREATE TABLE product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES product_variants(id) ON DELETE SET NULL,
    url TEXT NOT NULL,
    alt_text VARCHAR(255),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX product_images_primary_idx ON product_images (product_id) WHERE is_primary = true;

-- public.inventory
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE UNIQUE,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    quantity_available INT GENERATED ALWAYS AS (quantity_on_hand - quantity_reserved) STORED,
    reorder_threshold INT NOT NULL DEFAULT 10,
    reorder_quantity INT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT inventory_stock_check CHECK (quantity_on_hand >= 0 AND quantity_reserved >= 0)
);

-- public.inventory_transactions
CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    type inv_txn_type NOT NULL,
    quantity_delta INT NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    note TEXT,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX inventory_transactions_variant_idx ON inventory_transactions (variant_id);
CREATE INDEX inventory_transactions_created_at_brin_idx ON inventory_transactions USING BRIN (created_at);

-- public.carts
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(255),
    coupon_id UUID, -- added FK later
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.cart_items
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cart_items_cart_variant_unique UNIQUE (cart_id, variant_id),
    CONSTRAINT cart_items_quantity_check CHECK (quantity > 0)
);

-- public.addresses
CREATE TABLE addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(100),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    phone VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(300) NOT NULL,
    address_line2 VARCHAR(300),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'IN',
    postal_code VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.orders
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(30) UNIQUE, -- Auto-generated via trigger
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    guest_email VARCHAR(255),
    status order_status NOT NULL DEFAULT 'PENDING',
    shipping_address_id UUID REFERENCES addresses(id) ON DELETE SET NULL,
    shipping_address_snapshot JSONB NOT NULL,
    shipping_method VARCHAR(100),
    shipping_cost NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    subtotal NUMERIC(12,2) NOT NULL,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(12,2) NOT NULL,
    coupon_id UUID, -- added FK later
    coupon_code VARCHAR(50),
    notes TEXT,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX orders_user_id_idx ON orders (user_id);
CREATE INDEX orders_status_idx ON orders (status);
CREATE INDEX orders_created_at_desc_idx ON orders (created_at DESC);

-- public.order_items
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    product_snapshot JSONB NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    total_price NUMERIC(12,2) NOT NULL,
    tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    seller_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    fulfillment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT order_items_quantity_check CHECK (quantity > 0)
);
CREATE INDEX order_items_order_id_idx ON order_items (order_id);

-- public.order_status_history
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status order_status,
    to_status order_status NOT NULL,
    changed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.shipments
CREATE TABLE shipments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    tracking_number VARCHAR(200),
    carrier VARCHAR(100),
    carrier_url TEXT,
    shipped_at TIMESTAMPTZ,
    expected_delivery DATE,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.payments
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT UNIQUE,
    gateway VARCHAR(50) NOT NULL,
    gateway_order_id VARCHAR(255),
    gateway_payment_id VARCHAR(255),
    gateway_signature VARCHAR(500),
    method VARCHAR(50),
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status payment_status NOT NULL DEFAULT 'INITIATED',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.refunds
CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    amount NUMERIC(12,2) NOT NULL,
    reason TEXT,
    gateway_refund_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- public.reviews
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_item_id UUID REFERENCES order_items(id) ON DELETE SET NULL,
    rating SMALLINT NOT NULL,
    title VARCHAR(200),
    body TEXT NOT NULL,
    status review_status NOT NULL DEFAULT 'PENDING',
    is_verified_purchase BOOLEAN NOT NULL DEFAULT false,
    helpful_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reviews_product_user_unique UNIQUE (product_id, user_id),
    CONSTRAINT reviews_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT reviews_body_check CHECK (length(body) >= 20)
);
CREATE INDEX reviews_product_status_idx ON reviews (product_id, status);
CREATE INDEX reviews_rating_idx ON reviews (rating);

-- public.coupons
CREATE TABLE coupons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    type coupon_type NOT NULL,
    value NUMERIC(12,2) NOT NULL,
    min_order_value NUMERIC(12,2),
    max_discount_amount NUMERIC(12,2),
    usage_limit INT,
    usage_count INT NOT NULL DEFAULT 0,
    per_user_limit INT,
    applicable_to VARCHAR(50) NOT NULL,
    applicable_ids UUID[],
    starts_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX coupons_code_active_idx ON coupons (code, is_active);
CREATE INDEX coupons_applicable_ids_idx ON coupons USING GIN (applicable_ids);

-- Add dependencies for coupons table
ALTER TABLE carts ADD CONSTRAINT fk_carts_coupons FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL;
ALTER TABLE orders ADD CONSTRAINT fk_orders_coupons FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL;

-- public.notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(100) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body TEXT,
    data JSONB NOT NULL DEFAULT '{}',
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_unread_idx ON notifications (user_id, read_at) WHERE read_at IS NULL;

-- audit.audit_logs
CREATE SCHEMA IF NOT EXISTS audit;
CREATE TABLE audit.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name VARCHAR(100) NOT NULL,
    record_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    old_values JSONB,
    new_values JSONB,
    changed_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_table_record_idx ON audit.audit_logs (table_name, record_id);


-- 4. Apply Triggers & Functions to Tables

-- Set updated_at trigger for tables supporting it
CREATE TRIGGER set_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_categories_updated_at BEFORE UPDATE ON categories FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_products_updated_at BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_product_variants_updated_at BEFORE UPDATE ON product_variants FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_inventory_updated_at BEFORE UPDATE ON inventory FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_carts_updated_at BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_cart_items_updated_at BEFORE UPDATE ON cart_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_payments_updated_at BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_refunds_updated_at BEFORE UPDATE ON refunds FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER set_reviews_updated_at BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Full-text search trigger on products
CREATE TRIGGER update_products_search_vector BEFORE INSERT OR UPDATE ON products FOR EACH ROW EXECUTE FUNCTION update_search_vector();

-- Recalculate average rating trigger on reviews
CREATE TRIGGER update_products_rating_on_review AFTER INSERT OR UPDATE OR DELETE ON reviews FOR EACH ROW EXECUTE FUNCTION recalculate_product_rating();

-- Apply inventory transaction delta trigger
CREATE TRIGGER update_inventory_on_transaction AFTER INSERT ON inventory_transactions FOR EACH ROW EXECUTE FUNCTION apply_inventory_delta();

-- Auto-generate order number BEFORE INSERT on orders
CREATE TRIGGER generate_order_number_trigger BEFORE INSERT ON orders FOR EACH ROW EXECUTE FUNCTION generate_order_number();
