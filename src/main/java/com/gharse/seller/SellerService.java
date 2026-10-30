package com.gharse.seller;

import com.gharse.auth.AuthService;
import com.gharse.auth.dto.AuthResponse;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.common.exception.UnauthorizedException;
import com.gharse.review.ReviewRepository;
import com.gharse.seller.dto.SellerProfileRequest;
import com.gharse.seller.dto.SellerProfileResponse;
import com.gharse.user.User;
import com.gharse.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SellerService {

    private final SellerProfileRepository sellers;
    private final UserRepository users;
    private final AuthService auth;
    private final ReviewRepository reviews;

    public SellerService(SellerProfileRepository sellers, UserRepository users,
                         AuthService auth, ReviewRepository reviews) {
        this.sellers = sellers;
        this.users = users;
        this.auth = auth;
        this.reviews = reviews;
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

    @Transactional(readOnly = true)
    public SellerProfileResponse publicProfile(Long sellerProfileId) {
        SellerProfile profile = sellers.findById(sellerProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found."));

        List<Object[]> rows = reviews.aggregateForSeller(sellerProfileId);
        Object[] row = rows.isEmpty() ? new Object[] { null, 0L } : rows.get(0);

        Double avg = (Double) row[0];
        long count = ((Number) row[1]).longValue();
        BigDecimal rounded = avg == null
                ? null
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);

        return SellerProfileResponse.of(profile, rounded, count);
    }
}