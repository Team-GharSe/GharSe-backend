package com.gharse.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import com.gharse.catalog.FulfilmentType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Buyer-facing detail: inactive listings are invisible. */
    Optional<Product> findByIdAndActiveTrue(Long id);

    /** Every active listing, used by search. */
    List<Product> findByActiveTrue();

    /** Seller's active made-to-order listings, for the backlog view. */
    List<Product> findBySellerProfileIdAndActiveTrueAndFulfilmentType(Long sellerProfileId, FulfilmentType fulfilmentType);

    /** Seller ownership check for edit and delete. */
    Optional<Product> findByIdAndSellerProfileId(Long id, Long sellerProfileId);

    /** Seller's own listings, including inactive ones, so they can see what they removed. */
    List<Product> findBySellerProfileIdOrderByCreatedAtDesc(Long sellerProfileId);
}