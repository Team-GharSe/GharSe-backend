ALTER TABLE payment
    ADD COLUMN seller_profile_id BIGINT NOT NULL,
    ADD COLUMN fulfilment_mode_selected ENUM('SELF_DELIVERY','PICKUP','POSTAL') NOT NULL,
    ADD COLUMN delivery_address_snapshot VARCHAR(255) NULL,
    MODIFY COLUMN razorpay_order_id VARCHAR(64) NULL,
    ADD CONSTRAINT uq_payment_razorpay_order UNIQUE (razorpay_order_id),
    ADD CONSTRAINT fk_payment_seller FOREIGN KEY (seller_profile_id) REFERENCES seller_profile(seller_profile_id);

CREATE TABLE payment_item (
                              payment_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              payment_id BIGINT NOT NULL,
                              product_id BIGINT NOT NULL,
                              product_name_snapshot VARCHAR(150) NOT NULL,
                              price_snapshot DECIMAL(10,2) NOT NULL,
                              quantity INT NOT NULL,
                              UNIQUE (payment_id, product_id),
                              FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
                              FOREIGN KEY (product_id) REFERENCES product(product_id)
);