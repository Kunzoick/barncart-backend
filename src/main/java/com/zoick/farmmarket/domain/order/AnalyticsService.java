package com.zoick.farmmarket.domain.order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    private static final List<OrderStatus> REVENUE_STATUSES= Arrays.asList(OrderStatus.PAID, OrderStatus.FULFILLED, OrderStatus.DELIVERED);

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics() {
        //all agg in sql
        BigDecimal totalRevenue= orderRepository.sumRevenue(REVENUE_STATUSES);
        totalRevenue= totalRevenue != null ? totalRevenue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        long totalOrders= orderRepository.count();
        long completeOrders= orderRepository.countByStatusIn(REVENUE_STATUSES);
        BigDecimal avgOrderValue= orderRepository.avgTotalAmount(REVENUE_STATUSES);
        avgOrderValue= avgOrderValue !=null ? avgOrderValue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        //orders by status
        List<AnalyticsResponse.StatusCount> ordersByStatus= orderRepository.countGroupByStatus().stream().map(row ->
                new AnalyticsResponse.StatusCount(((OrderStatus) row[0]).name(), (Long) row[1])).sorted(
                        Comparator.comparingLong(AnalyticsResponse.StatusCount::count).reversed())
                .collect(Collectors.toList());
        //weekly revenue
        List<AnalyticsResponse.WeeklyRevenue> revenueByWeek= orderRepository.findWeeklyRevenue().stream().map(row ->
                new AnalyticsResponse.WeeklyRevenue((String) row[0], new BigDecimal(row[1].toString()).setScale(2,
                        RoundingMode.HALF_UP))).collect(Collectors.toList());
        //top products
        List<AnalyticsResponse.TopProduct> topProducts= orderItemRepository.findTopProducts().stream().limit(8).map(row ->
                new AnalyticsResponse.TopProduct((String) row[0], ((BigDecimal) row[1]).setScale(2,
                        RoundingMode.HALF_UP), ((BigDecimal) row[2]).setScale(2, RoundingMode.HALF_UP)))
                .collect(Collectors.toList());
        return new AnalyticsResponse(totalRevenue, totalOrders, completeOrders, avgOrderValue, ordersByStatus, revenueByWeek,
                topProducts);
    }
}
