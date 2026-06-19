{\rtf1\ansi\ansicpg1252\cocoartf2870
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\margl1440\margr1440\vieww34000\viewh21460\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\fs24 \cf0 -- ============================================================================\
-- SCRIPT DE CREACI\'d3N DE BASE DE DATOS: StockMaster REST\
-- EST\'c1NDAR: PostgreSQL 15+ / Convenci\'f3n de nomenclatura corporativa (snake_case)\
-- ============================================================================\
\
-- 1. LIMPIEZA DE TABLAS (Para permitir ejecuciones repetidas sin errores)\
DROP TABLE IF EXISTS purchase_order_details CASCADE;\
DROP TABLE IF EXISTS purchase_orders CASCADE;\
DROP TABLE IF EXISTS products CASCADE;\
DROP TABLE IF EXISTS categories CASCADE;\
DROP TABLE IF EXISTS suppliers CASCADE;\
DROP TABLE IF EXISTS user_roles CASCADE;\
DROP TABLE IF EXISTS roles CASCADE;\
DROP TABLE IF EXISTS users CASCADE;\
\
-- ============================================================================\
-- M\'d3DULO DE SEGURIDAD Y ACCESOS\
-- ============================================================================\
\
CREATE TABLE roles (\
    id BIGSERIAL,\
    name VARCHAR(20) NOT NULL,\
    CONSTRAINT pk_roles PRIMARY KEY (id),\
    CONSTRAINT uq_role_name UNIQUE (name)\
);\
\
CREATE TABLE users (\
    id BIGSERIAL,\
    username VARCHAR(50) NOT NULL,\
    password VARCHAR(255) NOT NULL, -- Almacena hash BCrypt generado por Spring Security\
    email VARCHAR(100) NOT NULL,\
    active BOOLEAN DEFAULT TRUE NOT NULL,\
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\
    CONSTRAINT pk_users PRIMARY KEY (id),\
    CONSTRAINT uq_user_username UNIQUE (username),\
    CONSTRAINT uq_user_email UNIQUE (email)\
);\
\
-- Tabla intermedia con Llave Primaria Compuesta y Borrado en Cascada\
CREATE TABLE user_roles (\
    user_id BIGINT NOT NULL,\
    role_id BIGINT NOT NULL,\
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),\
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,\
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE\
);\
\
-- ============================================================================\
-- M\'d3DULO DE CAT\'c1LOGOS COMERCIALES\
-- ============================================================================\
\
CREATE TABLE suppliers (\
    id BIGSERIAL,\
    rfc VARCHAR(13) NOT NULL, -- Est\'e1ndar oficial SAT (M\'e9xico)\
    company_name VARCHAR(150) NOT NULL,\
    contact_name VARCHAR(100),\
    phone VARCHAR(15),\
    email VARCHAR(100),\
    CONSTRAINT pk_suppliers PRIMARY KEY (id),\
    CONSTRAINT uq_supplier_rfc UNIQUE (rfc)\
);\
\
CREATE TABLE categories (\
    id BIGSERIAL,\
    name VARCHAR(50) NOT NULL,\
    description VARCHAR(255),\
    CONSTRAINT pk_categories PRIMARY KEY (id),\
    CONSTRAINT uq_category_name UNIQUE (name)\
);\
\
-- Tabla Maestra de Inventario con Restricci\'f3n de Borrado Prohibitivo (RESTRICT)\
CREATE TABLE products (\
    id BIGSERIAL,\
    sku VARCHAR(50) NOT NULL,\
    name VARCHAR(100) NOT NULL,\
    description TEXT,\
    price NUMERIC(12, 2) NOT NULL, -- Tipo de dato exacto para finanzas/precios\
    current_stock INT DEFAULT 0 NOT NULL,\
    minimum_stock INT DEFAULT 5 NOT NULL,\
    status VARCHAR(20) NOT NULL, -- Valores controlados: 'AVAILABLE', 'LOW_STOCK', 'OUT_OF_STOCK'\
    category_id BIGINT NOT NULL,\
    supplier_id BIGINT NOT NULL,\
    CONSTRAINT pk_products PRIMARY KEY (id),\
    CONSTRAINT uq_product_sku UNIQUE (sku),\
    CONSTRAINT chk_product_price CHECK (price >= 0),\
    CONSTRAINT chk_product_stock CHECK (current_stock >= 0),\
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,\
    CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT\
);\
\
-- ============================================================================\
-- M\'d3DULO DE COMPRAS (\'d3RDENES)\
-- ============================================================================\
\
CREATE TABLE purchase_orders (\
    id BIGSERIAL,\
    order_number VARCHAR(50) NOT NULL, -- Nomenclatura interna, ej: OC-2026-0001\
    status VARCHAR(20) NOT NULL, -- Valores: 'PENDING', 'APPROVED', 'RECEIVED', 'CANCELLED'\
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\
    updated_at TIMESTAMP,\
    supplier_id BIGINT NOT NULL,\
    created_by BIGINT NOT NULL,\
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),\
    CONSTRAINT uq_order_number UNIQUE (order_number),\
    CONSTRAINT fk_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT,\
    CONSTRAINT fk_orders_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT\
);\
\
-- Detalle de la Orden (Muchos a Muchos entre \'d3rdenes y Productos)\
CREATE TABLE purchase_order_details (\
    id BIGSERIAL,\
    purchase_order_id BIGINT NOT NULL,\
    product_id BIGINT NOT NULL,\
    quantity INT NOT NULL,\
    unit_price NUMERIC(12, 2) NOT NULL, -- Resguarda el precio hist\'f3rico del momento de compra\
    CONSTRAINT pk_purchase_order_details PRIMARY KEY (id),\
    CONSTRAINT chk_detail_quantity CHECK (quantity > 0),\
    CONSTRAINT chk_detail_price CHECK (unit_price >= 0),\
    CONSTRAINT fk_details_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,\
    CONSTRAINT fk_details_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT\
);\
\
-- ============================================================================\
-- INSERCI\'d3N DE DATOS SEMILLA (Para pruebas de desarrollo)\
-- ============================================================================\
\
INSERT INTO roles (name) VALUES ('ROLE_ADMIN'), ('ROLE_WAREHOUSEMAN');\
\
-- Contrase\'f1a semilla de ejemplo (En producci\'f3n ser\'e1 un hash BCrypt real)\
INSERT INTO users (username, password, email, active) \
VALUES ('admin_user', '$2a$10$NxW.eD89.n9M...ejemploHashBCrypt...', 'admin@stockmaster.com', true);\
\
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1);\
}