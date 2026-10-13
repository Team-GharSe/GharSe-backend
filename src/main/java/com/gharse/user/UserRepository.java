package com.gharse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<User> findByEmailOrPhone(String email, String phone);
}
