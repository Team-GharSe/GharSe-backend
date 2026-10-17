package com.gharse.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Buyer-facing detail: inactive listings are invisible. */
    Optional<Product> findByIdAndActiveTrue(Long id);

    /** Seller ownership check for edit and delete. */
    Optional<Product> findByIdAndSellerProfileId(Long id, Long sellerProfileId);

    /** Seller's own listings, including inactive ones, so they can see what they removed. */
    List<Product> findBySellerProfileIdOrderByCreatedAtDesc(Long sellerProfileId);
}