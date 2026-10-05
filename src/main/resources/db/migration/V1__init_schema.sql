-- Users table
CREATE TABLE users (
                       user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                       name VARCHAR(100) NOT NULL,
                       phone VARCHAR(15) NOT NULL UNIQUE,
                       email VARCHAR(150) UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       address_text VARCHAR(255),
                       role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Categories
CREATE TABLE category (
                          category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          name VARCHAR(80) NOT NULL UNIQUE
);

-- Seller profiles
CREATE TABLE seller_profile (
                                seller_profile_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                user_id BIGINT NOT NULL UNIQUE,
                                business_name VARCHAR(150) NOT NULL,
                                latitude DECIMAL(9,6) NOT NULL,
                                longitude DECIMAL(9,6) NOT NULL,
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Products
CREATE TABLE product (
                         product_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         seller_profile_id BIGINT NOT NULL,
                         category_id BIGINT NOT NULL,
                         name VARCHAR(150) NOT NULL,
                         description TEXT,
                         price DECIMAL(10,2) NOT NULL,
                         fulfilment_type ENUM('READY_STOCK','MADE_TO_ORDER') NOT NULL,
                         inventory_qty INT,
                         version INT NOT NULL DEFAULT 0,
                         production_time_minutes_per_unit INT,
                         supports_self_delivery BOOLEAN DEFAULT FALSE,
                         supports_pickup BOOLEAN DEFAULT FALSE,
                         supports_postal BOOLEAN DEFAULT FALSE,
                         delivery_radius_km DECIMAL(5,2),
                         is_verified BOOLEAN DEFAULT FALSE,
                         average_rating DECIMAL(3,2) DEFAULT 0,
                         review_count INT DEFAULT 0,
                         is_active BOOLEAN DEFAULT TRUE,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         FOREIGN KEY (seller_profile_id) REFERENCES seller_profile(seller_profile_id),
                         FOREIGN KEY (category_id) REFERENCES category(category_id)
);

-- Product images
CREATE TABLE product_image (
                               product_image_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                               product_id BIGINT NOT NULL,
                               image_url VARCHAR(500) NOT NULL,
                               display_order INT NOT NULL,
                               FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- Verification requests
CREATE TABLE verification_request (
                                      verification_request_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      product_id BIGINT NOT NULL,
                                      proof_document_url VARCHAR(500) NOT NULL,
                                      status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
                                      submitted_at DATETIME NOT NULL,
                                      reviewed_by BIGINT,
                                      reviewed_at DATETIME,
                                      rejection_reason VARCHAR(255),
                                      FOREIGN KEY (product_id) REFERENCES product(product_id),
                                      FOREIGN KEY (reviewed_by) REFERENCES users(user_id)
);

-- Cart
CREATE TABLE cart (
                      cart_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      user_id BIGINT NOT NULL UNIQUE,
                      seller_profile_id BIGINT,
                      FOREIGN KEY (user_id) REFERENCES users(user_id),
                      FOREIGN KEY (seller_profile_id) REFERENCES seller_profile(seller_profile_id)
);

-- Cart items
CREATE TABLE cart_item (
                           cart_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                           cart_id BIGINT NOT NULL,
                           product_id BIGINT NOT NULL,
                           quantity INT NOT NULL,
                           FOREIGN KEY (cart_id) REFERENCES cart(cart_id),
                           FOREIGN KEY (product_id) REFERENCES product(product_id),
                           UNIQUE (cart_id, product_id)
);

-- Payments
CREATE TABLE payment (
                         payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         buyer_id BIGINT NOT NULL,
                         razorpay_order_id VARCHAR(64) NOT NULL,
                         amount_total DECIMAL(10,2) NOT NULL,
                         status ENUM('PENDING','SUCCESS','FAILED','REFUNDED') NOT NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         confirmed_at DATETIME,
                         FOREIGN KEY (buyer_id) REFERENCES users(user_id)
);

-- Orders
CREATE TABLE orders (
                        order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        payment_id BIGINT NOT NULL UNIQUE,
                        buyer_id BIGINT NOT NULL,
                        seller_profile_id BIGINT NOT NULL,
                        status ENUM('CONFIRMED','IN_PRODUCTION','READY','OUT_FOR_DELIVERY','DELIVERED') NOT NULL,
                        fulfilment_mode_selected ENUM('SELF_DELIVERY','PICKUP','POSTAL') NOT NULL,
                        delivery_address_snapshot VARCHAR(255),
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
                        FOREIGN KEY (buyer_id) REFERENCES users(user_id),
                        FOREIGN KEY (seller_profile_id) REFERENCES seller_profile(seller_profile_id)
);

-- Order items
CREATE TABLE order_item (
                            order_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            order_id BIGINT NOT NULL,
                            product_id BIGINT NOT NULL,
                            product_name_snapshot VARCHAR(150) NOT NULL,
                            price_snapshot DECIMAL(10,2) NOT NULL,
                            quantity INT NOT NULL,
                            FOREIGN KEY (order_id) REFERENCES orders(order_id),
                            FOREIGN KEY (product_id) REFERENCES product(product_id)
);

-- Reviews
CREATE TABLE review (
                        review_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        order_item_id BIGINT NOT NULL UNIQUE,
                        rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                        comment TEXT,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (order_item_id) REFERENCES order_item(order_item_id)
);

-- Notifications
CREATE TABLE notification (
                              notification_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              user_id BIGINT NOT NULL,
                              channel ENUM('EMAIL','SMS') NOT NULL,
                              type VARCHAR(50) NOT NULL,
                              content TEXT NOT NULL,
                              status ENUM('SENT','FAILED') NOT NULL,
                              sent_at DATETIME NOT NULL,
                              FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Support queries (FR28)
CREATE TABLE support_query (
                               support_query_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                               user_id BIGINT NOT NULL,
                               subject VARCHAR(255) NOT NULL,
                               message TEXT NOT NULL,
                               status ENUM('OPEN','IN_PROGRESS','RESOLVED') DEFAULT 'OPEN',
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               resolved_at DATETIME,
                               FOREIGN KEY (user_id) REFERENCES users(user_id)
);