package com.zoick.farmmarket.domain.order;
import com.zoick.farmmarket.domain.audit.AuditService;
import com.zoick.farmmarket.domain.cart.Cart;
import com.zoick.farmmarket.domain.cart.CartItem;
import com.zoick.farmmarket.domain.cart.CartItemRepository;
import com.zoick.farmmarket.domain.cart.CartRepository;
import com.zoick.farmmarket.domain.delivery.DeliverySlot;
import com.zoick.farmmarket.domain.delivery.DeliverySlotRepository;
import com.zoick.farmmarket.domain.delivery.OrderDelivery;
import com.zoick.farmmarket.domain.delivery.OrderDeliveryRepository;
import com.zoick.farmmarket.domain.payment.Payment;
import com.zoick.farmmarket.domain.payment.PaymentRepository;
import com.zoick.farmmarket.domain.produce.HarvestBatchRepository;
import com.zoick.farmmarket.domain.produce.ListingRepository;
import com.zoick.farmmarket.domain.user.User;
import com.zoick.farmmarket.domain.user.UserRepository;
import com.zoick.farmmarket.infrastructure.websocket.InventoryUpdateEvent;
import com.zoick.farmmarket.infrastructure.websocket.InventoryWebSocketService;
import com.zoick.farmmarket.infrastructure.websocket.OrderStatusChangedEvent;
import com.zoick.farmmarket.infrastructure.websocket.OrderStatusWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationRepository reservationRepository;
    private final HarvestBatchRepository harvestBatchRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final DeliverySlotRepository deliverySlotRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryWebSocketService inventoryWebSocketService;
    private final OrderStatusWebSocketService orderStatusWebSocketService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    @Value("${reservation.timeout.minutes:15}")
    private int reservationTimeoutMinutes;

    //checkout
    /**
     * DB operations only inside @Transactional, stripe call happens outside this method in the controller
     * returns saved order-> controller uses it to create paymentIntent
     */
    @Transactional
    public CheckoutInitResult initiateCheckout(UUID userId, CheckoutRequest request){
        Optional<Order> existing= orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if(existing.isPresent()){
            return new CheckoutInitResult(existing.get(), true);
        }
        expireStaleReservations(userId);
        Cart cart= cartRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("No cart found for user"));
        List<CartItem> cartItems= cartItemRepository.findAllByCartIdWithDetails(cart.getId());
        if(cartItems.isEmpty()){
            throw new IllegalStateException("Cart is empty");
        }
        DeliverySlot slot = deliverySlotRepository.findById(request.getDeliverySlotId()).orElseThrow(() -> new IllegalArgumentException(
                "Delivery slot not found"));
        int slotBooked= deliverySlotRepository.incrementBookedCount(slot.getId());
        if(slotBooked == 0){
            throw new IllegalStateException("Delivery slot is fully booked");
        }
        PricingType pricingType= determinePricingType(cartItems);
        BigDecimal totalAmount= computeTotal(cartItems, pricingType);
        User user= userRepository.getReferenceById(userId);
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(totalAmount);
        order.setCurrency("CAD");
        order.setIdempotencyKey(request.getIdempotencyKey());
        order.setPricingType(pricingType);
        order.setRefundStatus(RefundStatus.NONE);
        orderRepository.saveAndFlush(order);

        for(CartItem cartItem : cartItems) {
            try {
                var batch = cartItem.getListing().getBatch();
                BigDecimal quantity = cartItem.getQuantity();
                BigDecimal quantityBefore = batch.getQuantityAvailable();
                int rowsAffected = harvestBatchRepository.deductStock(batch.getId(), quantity);
                if (rowsAffected == 0) {
                    throw new IllegalStateException("Insufficient stock for: " + batch.getProduce().getName());
                }
                //cpmpute post-deduction value explicitly-> cache is stale after atomic Update
                auditService.log("HarvestBatch", batch.getId(), "STOCK_DEDUCTED", userId,
                        Map.of("quantityAvailable", quantityBefore, "quantityDeducted", quantity),
                        Map.of("quantityAvailable", quantityBefore.subtract(quantity)));
                Reservation reservation = new Reservation();
                reservation.setOrder(order);
                reservation.setUser(user);
                reservation.setBatch(batch);
                reservation.setQuantity(quantity);
                reservation.setStatus(ReservationStatus.ACTIVE);
                reservation.setExpiresAt(LocalDateTime.now().plusMinutes(reservationTimeoutMinutes));
                reservationRepository.save(reservation);

                BigDecimal price = pricingType == PricingType.BULK ? cartItem.getListing().getBulkPrice() : cartItem
                        .getListing().getRetailPrice();
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setListing(cartItem.getListing());
                orderItem.setBatch(batch);
                orderItem.setQuantity(quantity);
                orderItem.setPriceAtPurchase(price);
                orderItem.setPricingType(pricingType);
                orderItemRepository.save(orderItem);
            } catch (Exception e) {
                log.error("FAILED on cart item batch {}: {}",
                        cartItem.getListing().getBatch().getId(), e.getMessage(), e);
                throw e;
            }
        }
        OrderDelivery delivery = new OrderDelivery();
        delivery.setOrder(order);
        delivery.setDeliverySlot(slot);
        delivery.setAddressLine1(request.getAddressLine1());
        delivery.setAddressLine2(request.getAddressLine2());
        delivery.setCity(request.getCity());
        delivery.setProvince(request.getProvince());
        delivery.setPostalCode(request.getPostalCode());
        delivery.setCountry(request.getCountry() != null ? request.getCountry() : "CA");
        delivery.setDeliveryNotes(request.getDeliveryNotes());
        orderDeliveryRepository.save(delivery);
        Order managedOrder = orderRepository.findById(order.getId()).orElseThrow(() -> new IllegalStateException(
                "Order not found after creation"));
        managedOrder.setStatus(OrderStatus.RESERVED);
        orderRepository.save(managedOrder);

        auditService.log("Order", managedOrder.getId(), "ORDER_RESERVED", userId,
                Map.of("status", OrderStatus.PENDING.name()),
                Map.of("status", OrderStatus.RESERVED.name()));

        //broadcast inventory update
        for (CartItem cartItem : cartItems) {
            eventPublisher.publishEvent(new InventoryUpdateEvent(cartItem.getListing().getBatch()
                    .getId()));
        }
        return new CheckoutInitResult(managedOrder, false);
    }
    /**
     * Compensating transaction-> called when stripe fails after checkout commits
     * Separate connection
     */
    @Transactional
    public void expireImmediately(UUID orderId){
        reservationRepository.findByOrderId(orderId).ifPresent(reservation -> {
            if(reservation.getStatus() == ReservationStatus.ACTIVE){
                harvestBatchRepository.returnStock(reservation.getBatch().getId(), reservation.getQuantity());
                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);
                auditService.log("HarvestBatch", reservation.getBatch().getId(), "STOCK_RETURNED", null,
                        Map.of("reason", "STRIPE_INIT_FAILED"),
                        Map.of("quantityReturned", reservation.getQuantity()));
                //publish event
                eventPublisher.publishEvent(new InventoryUpdateEvent(reservation.getBatch().getId()));
            }
        });
        orderRepository.findById(orderId).ifPresent(order ->{
            String previousStatus= order.getStatus().name();
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            //publish event
            eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), order.getId(),
                    previousStatus, OrderStatus.PAYMENT_FAILED.name()));
        });
    }
    //order queries
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForUser(UUID userId){
        List<Order> orders= orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if(orders.isEmpty()) return List.of();

        List<UUID> orderIds= orders.stream().map(Order::getId).collect(Collectors.toList());
        //bulk fetch
        Map<UUID, List<OrderItemResponse>> itemsMap= orderItemRepository.findAllByOrderIdsWithDetails(orderIds).stream().collect(Collectors
                .groupingBy(oi -> oi.getOrder().getId(), Collectors.mapping(OrderItemResponse::from,
                        Collectors.toList())));
        Map<UUID, LocalDateTime> expiryMap= reservationRepository.findAllByOrderIdIn(orderIds).stream().collect(Collectors
                .toMap(r -> r.getOrder().getId(), Reservation::getExpiresAt, (a,b) -> a));
        Map<UUID, String> disputeMap= orderDeliveryRepository.findAllByOrderIdIn(orderIds).stream().collect(Collectors
                .toMap(od -> od.getOrder().getId(), od -> od.getDisputeReason() !=null ? od.getDisputeReason() : "",
                        (a, b) -> a));
        return orders.stream().map(order -> OrderResponse.from(order, itemsMap.getOrDefault(order.getId(), List.of()), expiryMap.get(order.getId()),
                disputeMap.get(order.getId()))).collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID userId){
        Order order= orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found: "+ orderId));
        if(!order.getUser().getId().equals(userId)){
            throw new IllegalArgumentException("Order not found: "+ orderId);
        }
        List<OrderItemResponse> items= orderItemRepository.findAllByOrderIdWithDetails(orderId).stream().map(OrderItemResponse::from)
                .collect(Collectors.toList());
        LocalDateTime expiresAt= reservationRepository.findByOrderId(order.getId()).map(Reservation::getExpiresAt)
                .orElse(null);
        String disputeReason= orderDeliveryRepository.findByOrderId(orderId).map(d -> d.getDisputeReason())
                .orElse(null);
        return OrderResponse.from(order, items, expiresAt, disputeReason);
    }
    //cancellation
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId){
        Order order= orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found: "+ orderId));
        if(!order.getUser().getId().equals(userId)){
            throw new IllegalArgumentException("Order not found: "+ orderId);
        }
        if(order.getStatus() != OrderStatus.RESERVED){
            throw new IllegalStateException("Order cannot be cancelled in status: "+ order.getStatus());
        }
        Reservation reservation= reservationRepository.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException(
                "No reservation found for order"));
        harvestBatchRepository.returnStock(reservation.getBatch().getId(), reservation.getQuantity());
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        auditService.log("HarvestBatch", reservation.getBatch().getId(), "STOCK_RETURNED", userId,
                Map.of("reason", "ORDER_CANCELLED_BY_CUSTOMER"),
                Map.of("quantityReturned", reservation.getQuantity()));
        String previousStatus= order.getStatus().name();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason("Cancelled by customer");
        orderRepository.save(order);
        auditService.log("Order", order.getId(), "ORDER_CANCELLED", userId,
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.CANCELLED.name()));
        eventPublisher.publishEvent(new InventoryUpdateEvent(reservation.getBatch().getId()));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(userId, orderId, previousStatus, OrderStatus.CANCELLED.name()));
        User user= userRepository.findById(order.getUser().getId()).orElseThrow(() -> new IllegalStateException(
                "User not found"));
        order.setUser(user);
        List<OrderItemResponse> items= orderItemRepository.findAllByOrderIdWithDetails(orderId).stream().map(OrderItemResponse::from)
                .collect(Collectors.toList());
        return OrderResponse.from(order, items, null, null);
    }
    //Delivery confirmation
    @Transactional
    public void confirmDelivery(UUID orderId, UUID userId){
        Order order= orderRepository.findByIdWithUser(orderId).orElseThrow(() -> new IllegalArgumentException("order not found: "+ orderId));
        if(!order.getUser().getId().equals(userId)){
            throw new IllegalArgumentException("order not found: "+ orderId);
        }
        if(order.getStatus() != OrderStatus.FULFILLED){
            throw new IllegalStateException("Order is not yet fulfilled");
        }
        OrderDelivery delivery= orderDeliveryRepository.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException(
                "No delivery record found for order"));
        if(delivery.getCustomerConfirmedAt() != null){
            throw new IllegalStateException("Delivery already confirmed");
        }
        if(delivery.isAutoConfirmed()){
            throw new IllegalStateException("Order was automatically confirmed");
        }
        String previousStatus= order.getStatus().name();
        delivery.setCustomerConfirmedAt(LocalDateTime.now());
        orderDeliveryRepository.save(delivery);
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        auditService.log("Order", order.getId(), "ORDER_DELIVERED", userId,
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.DELIVERED.name(), "confirmedBy", "CUSTOMER"));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(userId, orderId, previousStatus, OrderStatus.DELIVERED.name()));
    }
    //Admin
    /*
    increment moved to webhook handler at Paid, fulfillorder only transitions state and records fulfilled_at
     */
    @Transactional
    public OrderResponse fulfillOrder(UUID orderId, UUID adminId){
        Order order= orderRepository.findByIdWithUser(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found: "+orderId));
        if(order.getStatus() != OrderStatus.PAID){
            throw new IllegalStateException("Only PAID orders can be fulfilled");
        }
        OrderDelivery delivery = orderDeliveryRepository.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException(
                "No delivery record found"));
        String previousStatus= order.getStatus().name();
        delivery.setFulfilledAt(LocalDateTime.now());
        orderDeliveryRepository.save(delivery);
        order.setStatus(OrderStatus.FULFILLED);
        orderRepository.save(order);
        auditService.log("Order", order.getId(), "ORDER_FULFILLED", adminId,
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.FULFILLED.name()));
        //notify customer
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), order.getId(),
                previousStatus, OrderStatus.FULFILLED.name()));
        List<OrderItemResponse> items= orderItemRepository.findAllByOrderIdWithDetails(orderId).stream().map(OrderItemResponse::from)
                .collect(Collectors.toList());
        return OrderResponse.from(order, items, null, null);
    }
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders(){
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(order -> {
            List<OrderItemResponse> items= orderItemRepository.findAllByOrderIdWithDetails(order.getId()).stream().map(OrderItemResponse::from)
                    .collect(Collectors.toList());
            LocalDateTime expiresAt= reservationRepository.findByOrderId(order.getId()).map(Reservation::getExpiresAt)
                    .orElse(null);
            String disputeReason= orderDeliveryRepository.findByOrderId(order.getId()).map(d -> d.getDisputeReason())
                    .orElse(null);
            return OrderResponse.from(order, items, expiresAt, null);
        }).collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrdersPaged(int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderRepository.findAllPagedOrderByCreatedAtDesc(pageable);
        List<UUID> orderIds = orderPage.getContent().stream().map(Order::getId).collect(Collectors.toList());
        if(orderIds.isEmpty()) return orderPage.map(o -> null);
        Map<UUID, List<OrderItemResponse>> itemsMap = orderItemRepository.findAllByOrderIdsWithDetails(orderIds).stream().collect(Collectors
                        .groupingBy(oi -> oi.getOrder().getId(),
                        Collectors.mapping(OrderItemResponse::from, Collectors.toList())));
        Map<UUID, LocalDateTime> expiryMap = reservationRepository.findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(r -> r.getOrder().getId(), Reservation::getExpiresAt,
                        (a, b) -> a));
        Map<UUID, String> disputeMap = orderDeliveryRepository.findAllByOrderIdIn(orderIds).stream().collect(Collectors
                        .toMap(od -> od.getOrder().getId(), od -> od.getDisputeReason() != null ?
                                        od.getDisputeReason() : "", (a, b) -> a));
        return orderPage.map(order -> OrderResponse.from(order, itemsMap.getOrDefault(order.getId(), List.of()),
                expiryMap.get(order.getId()), disputeMap.get(order.getId())));
    }
    @Transactional(readOnly = true)
    public Optional<String> getClientSecretForOrder(UUID orderId){
        return paymentRepository.findByOrderIdAndStatus(orderId, "PENDING").map(Payment::getClientSecret);
    }
    @Transactional
    public void disputeOrder(UUID orderId, UUID userId, String reason){
        Order order= orderRepository.findByIdWithUser(orderId).orElseThrow(() -> new IllegalArgumentException(
                "Order not found: "+ orderId));
        if(!order.getUser().getId().equals(userId)){
            throw new IllegalArgumentException("Order not found: "+ orderId);
        }
        if(order.getStatus() != OrderStatus.FULFILLED){
            throw new IllegalStateException("Only FULFILLED orders can be disputed");
        }
        OrderDelivery delivery = orderDeliveryRepository.findByOrderId(orderId).orElseThrow(() -> new IllegalArgumentException(
                "No Delivery record found for order"));
        if(delivery.getDisputeReason() != null){
            throw new IllegalStateException("Order already disputed");
        }
        String previousStatus= order.getStatus().name();
        delivery.setDisputeReason(reason);
        orderDeliveryRepository.save(delivery);
        order.setStatus(OrderStatus.DISPUTED);
        orderRepository.save(order);
        auditService.log("Order", order.getId(), "ORDER_DISPUTED", userId,
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.DISPUTED.name(), "reason", reason));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(userId, orderId,
                previousStatus, OrderStatus.DISPUTED.name()));
    }
    @Transactional
    public OrderResponse resolveDispute(UUID orderId, UUID adminId){
        Order order = orderRepository.findByIdWithUser(orderId).orElseThrow(() -> new IllegalArgumentException(
                "Order not found: "+ orderId));
        if(order.getStatus() != OrderStatus.DISPUTED){
            throw new IllegalStateException("Order is not disputed");
        }
        OrderDelivery delivery= orderDeliveryRepository.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException(
                "No Delivery record found for order"));
        String previousStatus= order.getStatus().name();
        delivery.setDisputeResolvedAt(LocalDateTime.now());
        orderDeliveryRepository.save(delivery);
        order.setStatus(OrderStatus.FULFILLED);
        orderRepository.save(order);
        auditService.log("Order", order.getId(), "DISPUTE_RESOLVED", adminId,
                Map.of("status", previousStatus),
                Map.of("status", OrderStatus.FULFILLED.name()));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getUser().getId(), orderId,
                previousStatus, OrderStatus.FULFILLED.name()));
        List<OrderItemResponse> items = orderItemRepository.findAllByOrderIdWithDetails(orderId).stream()
                .map(OrderItemResponse::from).collect(Collectors.toList());
        return OrderResponse.from(order, items, null, null);
    }
    //helpers
    private void expireStaleReservations(UUID userId){
        reservationRepository.findAllByUserIdAndStatusAndExpiresAtBefore(userId, ReservationStatus.ACTIVE, LocalDateTime.now())
                .forEach(r -> {
                    harvestBatchRepository.returnStock(r.getBatch().getId(), r.getQuantity());
                    r.setStatus(ReservationStatus.EXPIRED);
                    reservationRepository.save(r);

                    auditService.log("HarvestBatch", r.getBatch().getId(), "STOCK_RETURNED", userId,
                            Map.of("reason", "LAZY_EXPIRY_ON_CHECKOUT"),
                            Map.of("quantityReturned", r.getQuantity()));

                    auditService.log("Reservation", r.getId(), "RESERVATION_EXPIRED", userId,
                            Map.of("status", "ACTIVE"),
                            Map.of("status", "EXPIRED"));
                });
    }
    private PricingType determinePricingType(List<CartItem> cartItems){
        boolean allBulk= cartItems.stream().allMatch(item -> {
            var listing= item.getListing();
            return item.getQuantity().compareTo(listing.getMinBulkQuantity()) >= 0;
        });
        return allBulk ? PricingType.BULK : PricingType.RETAIL;
    }
    private BigDecimal computeTotal(List<CartItem> cartItems, PricingType pricingType){
        return cartItems.stream().map(item -> {
            BigDecimal price= pricingType == PricingType.BULK ? item.getListing().getBulkPrice() : item.getListing().getRetailPrice();
            return price.multiply(item.getQuantity());
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
