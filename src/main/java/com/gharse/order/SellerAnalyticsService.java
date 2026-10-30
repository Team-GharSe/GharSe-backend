package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.exception.BadRequestException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.order.dto.SellerAnalyticsResponse;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SellerAnalyticsService {

    /** Longest allowed range, so one request can't scan the whole history. */
    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final OrderItemRepository items;
    private final SellerProfileRepository sellers;

    public SellerAnalyticsService(OrderItemRepository items, SellerProfileRepository sellers) {
        this.items = items;
        this.sellers = sellers;
    }

    /**
     * Seller analytics for an inclusive date range. Defaults to the last 30 days.
     * Counts every order that exists (there is no cancellation in Phase 1).
     */
    @Transactional(readOnly = true)
    public SellerAnalyticsResponse analytics(AuthenticatedUser user, LocalDate from, LocalDate to) {
        SellerProfile seller = sellers.findByUserId(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Create a seller profile before viewing analytics."));

        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1) : from;

        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must be on or before 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("The date range can be at most " + MAX_RANGE_DAYS + " days.");
        }

        // Half-open range: [start 00:00, day after end 00:00), so the end date is included.
        LocalDateTime fromDt = start.atStartOfDay();
        LocalDateTime toDt = end.plusDays(1).atStartOfDay();

        Object[] summary = items.analyticsSummary(seller.getId(), fromDt, toDt).get(0);
        BigDecimal revenue = toMoney(summary[0]);
        long unitsSold = ((Number) summary[1]).longValue();
        long orders = ((Number) summary[2]).longValue();

        List<SellerAnalyticsResponse.DayPoint> daily = items
                .analyticsDaily(seller.getId(), fromDt, toDt)
                .stream()
                .map(row -> new SellerAnalyticsResponse.DayPoint(
                        // The DB date may arrive as java.sql.Date or LocalDate; both print as yyyy-MM-dd.
                        LocalDate.parse(String.valueOf(row[0])),
                        ((Number) row[1]).longValue(),
                        toMoney(row[2])))
                .toList();

        return new SellerAnalyticsResponse(start, end, revenue, orders, unitsSold, daily);
    }

    private static BigDecimal toMoney(Object value) {
        if (value instanceof BigDecimal b) {
            return b.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}