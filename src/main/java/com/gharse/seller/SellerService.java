package com.gharse.seller;

import com.gharse.auth.AuthService;
import com.gharse.auth.dto.AuthResponse;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.common.exception.UnauthorizedException;
import com.gharse.seller.dto.SellerProfileRequest;
import com.gharse.seller.dto.SellerProfileResponse;
import com.gharse.user.User;
import com.gharse.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

    private final SellerProfileRepository sellers;
    private final UserRepository users;
    private final AuthService auth;

    public SellerService(SellerProfileRepository sellers, UserRepository users, AuthService auth) {
        this.sellers = sellers;
        this.users = users;
        this.auth = auth;
    }

    /** Makes the current user a seller and returns a new token that carries the SELLER role. */
    @Transactional
    public AuthResponse becomeSeller(Long userId, SellerProfileRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please log in again."));
        if (sellers.existsByUserId(userId)) {
            throw new ConflictException("You already have a seller profile.");
        }

        SellerProfile profile = new SellerProfile();
        profile.setUserId(userId);
        profile.setBusinessName(req.businessName().trim());
        profile.setLatitude(req.latitude());
        profile.setLongitude(req.longitude());
        sellers.save(profile);

        return auth.issueFor(user);
    }

    public SellerProfileResponse myProfile(Long userId) {
        SellerProfile profile = sellers.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("You don't have a seller profile yet."));
        return SellerProfileResponse.from(profile);
    }

    public SellerProfileResponse publicProfile(Long sellerProfileId) {
        SellerProfile profile = sellers.findById(sellerProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found."));
        return SellerProfileResponse.from(profile);
    }
}
