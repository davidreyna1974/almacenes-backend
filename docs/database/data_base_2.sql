{\rtf1\ansi\ansicpg1252\cocoartf2870
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\margl1440\margr1440\vieww11520\viewh8400\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\fs24 \cf0 \
DROP TABLE IF EXISTS purchase_order_details CASCADE;\
DROP TABLE IF EXISTS purchase_orders CASCADE;\
DROP TABLE IF EXISTS products CASCADE;\
DROP TABLE IF EXISTS categories CASCADE;\
DROP TABLE IF EXISTS suppliers CASCADE;\
DROP TABLE IF EXISTS user_roles CASCADE;\
DROP TABLE IF EXISTS roles CASCADE;\
DROP TABLE IF EXISTS users CASCADE;\
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
    password VARCHAR(255) NOT NULL, \
    email VARCHAR(100) NOT NULL,\
    active BOOLEAN DEFAULT TRUE NOT NULL,\
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\
    CONSTRAINT pk_users PRIMARY KEY (id),\
    CONSTRAINT uq_user_username UNIQUE (username),\
    CONSTRAINT uq_user_email UNIQUE (email)\
);\
\
CREATE TABLE user_roles (\
    user_id BIGINT NOT NULL,\
    role_id BIGINT NOT NULL,\
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),\
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,\
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE\
);\
\
CREATE TABLE suppliers (\
    id BIGSERIAL,\
    rfc VARCHAR(13) NOT NULL, \
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
CREATE TABLE products (\
    id BIGSERIAL,\
    sku VARCHAR(50) NOT NULL,\
    name VARCHAR(100) NOT NULL,\
    description TEXT,\
    price NUMERIC(12, 2) NOT NULL, \
}