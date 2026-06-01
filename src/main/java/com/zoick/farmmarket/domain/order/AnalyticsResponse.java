package com.zoick.farmmarket.domain.order;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsResponse(
        BigDecimal totalRevenue,
        long totalOrders,
        long completedOrders,
        BigDecimal averageOrderValue,
        List<StatusCount> ordersByStatus,
        List<WeeklyRevenue> revenueByWeek,
        List<TopProduct> topProducts
) {
    public record StatusCount(String status, long count) {}
    public record WeeklyRevenue(String week, BigDecimal revenue) {}
    public record TopProduct(String produceName, BigDecimal totalQuantity, BigDecimal totalRevenue) {}
}