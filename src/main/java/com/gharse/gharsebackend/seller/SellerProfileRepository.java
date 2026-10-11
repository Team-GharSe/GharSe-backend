package com.gharse.gharsebackend.seller;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    boolean existsByUserId(Long userId);
}
