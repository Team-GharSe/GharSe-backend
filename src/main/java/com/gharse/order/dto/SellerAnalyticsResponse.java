package com.gharse.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SellerAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        long orders,
        long unitsSold,
        List<DayPoint> daily) {

    public record DayPoint(LocalDate date, long orders, BigDecimal revenue) {
    }
}