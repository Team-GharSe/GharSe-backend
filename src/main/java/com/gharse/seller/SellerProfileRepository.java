package com.gharse.seller;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    boolean existsByUserId(Long userId);

    Optional<SellerProfile> findByUserId(Long userId);
}
