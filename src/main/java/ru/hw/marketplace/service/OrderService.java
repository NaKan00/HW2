package ru.hw.marketplace.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hw.marketplace.entity.*;
import ru.hw.marketplace.repository.*;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.security.CurrentUser;
import ru.hw.marketplace.exception.BusinessException;

@Service
public class OrderService {
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PromoRepository promos;
    private final UserOperationRepository operations;
    private final PromoService promoService;
    private final long rateMinutes;
    public OrderService(UserRepository users, ProductRepository products, OrderRepository orders, PromoRepository promos,
                        UserOperationRepository operations, PromoService promoService,
                        @Value("${app.order-rate-limit-minutes}") long rateMinutes) {
        if (rateMinutes < 0) throw new IllegalArgumentException("Order rate interval must not be negative");
        this.users = users; this.products = products; this.orders = orders; this.promos = promos;
        this.operations = operations; this.promoService = promoService; this.rateMinutes = rateMinutes;
    }
    @Transactional public OrderResponse create(OrderCreate request) {
        UUID owner = CurrentUser.get().id(); lockUser(owner);
        checkRate(owner, "CREATE_ORDER");
        if (orders.existsByUserIdAndStatusIn(owner, List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING)))
            throw new BusinessException(ErrorCode.ORDER_HAS_ACTIVE, "User already has an active order");
        Map<UUID, ProductEntity> locked = lockProducts(request.getItems().stream().map(OrderItemRequest::getProductId).toList());
        validateProducts(request.getItems(), locked);
        OrderEntity order = new OrderEntity(); order.userId = owner; order.status = OrderStatus.CREATED;
        replaceItems(order, request.getItems(), locked);
        order.discountAmount = BigDecimal.ZERO.setScale(2);
        if (request.getPromoCode() != null) {
            PromoEntity p = promos.lockByCode(request.getPromoCode()).orElseThrow(() -> new BusinessException(ErrorCode.PROMO_CODE_INVALID, "Promo code not found"));
            promoService.validate(p, false);
            order.discountAmount = promoService.discount(p, order.subtotal);
            p.currentUses++; order.promoCodeId = p.id;
        }
        order.totalAmount = order.subtotal.subtract(order.discountAmount);
        record(owner, "CREATE_ORDER");
        return ApiMapper.order(orders.saveAndFlush(order));
    }
    @Transactional(readOnly = true) public OrderResponse get(UUID id) {
        OrderEntity order = orders.findById(id).orElseThrow(() -> missing()); own(order.userId);
        return ApiMapper.order(order);
    }
    @Transactional public OrderResponse update(UUID id, OrderUpdate request) {
        OrderEntity order = lockOrder(id);
        if (order.status != OrderStatus.CREATED) throw transition();
        checkRate(order.userId, "UPDATE_ORDER");
        Set<UUID> ids = new HashSet<>(request.getItems().stream().map(OrderItemRequest::getProductId).toList());
        order.items.forEach(i -> ids.add(i.productId));
        Map<UUID, ProductEntity> locked = lockProducts(ids);
        restore(order, locked);
        validateProducts(request.getItems(), locked);
        // Delete orphaned rows before inserting replacements with the same (order_id, product_id).
        order.items.clear(); orders.flush();
        replaceItems(order, request.getItems(), locked);
        order.discountAmount = BigDecimal.ZERO.setScale(2);
        if (order.promoCodeId != null) {
            PromoEntity p = promos.lockById(order.promoCodeId).orElseThrow(() -> new BusinessException(ErrorCode.PROMO_CODE_INVALID, "Promo code not found"));
            promoService.validate(p, true);
            if (order.subtotal.compareTo(p.minOrderAmount) < 0) {
                promoService.release(p); order.promoCodeId = null;
            } else order.discountAmount = promoService.discount(p, order.subtotal);
        }
        order.totalAmount = order.subtotal.subtract(order.discountAmount);
        record(order.userId, "UPDATE_ORDER");
        order.updateTimestamp();
        orders.flush(); return ApiMapper.order(order);
    }
    @Transactional public OrderResponse cancel(UUID id) {
        OrderEntity order = lockOrder(id);
        if (order.status != OrderStatus.CREATED && order.status != OrderStatus.PAYMENT_PENDING) throw transition();
        Map<UUID, ProductEntity> locked = lockProducts(order.items.stream().map(i -> i.productId).toList());
        restore(order, locked);
        if (order.promoCodeId != null) promoService.release(promos.lockById(order.promoCodeId).orElseThrow());
        order.status = OrderStatus.CANCELED;
        orders.flush(); return ApiMapper.order(order);
    }
    @Transactional public OrderResponse changeStatus(UUID id, OrderStatus target) {
        OrderEntity order = lockOrder(id);
        OrderStatus next = switch (order.status) {
            case CREATED -> OrderStatus.PAYMENT_PENDING;
            case PAYMENT_PENDING -> OrderStatus.PAID;
            case PAID -> OrderStatus.SHIPPED;
            case SHIPPED -> OrderStatus.COMPLETED;
            default -> null;
        };
        if (next == null || next != target) throw transition();
        order.status = target; orders.flush(); return ApiMapper.order(order);
    }
    private void lockUser(UUID id) {
        users.lockById(id).orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "Unknown user"));
    }
    private OrderEntity lockOrder(UUID id) {
        UUID owner = orders.findOwner(id).orElseThrow(() -> missing()); own(owner);
        lockUser(owner);
        return orders.lockById(id).orElseThrow(() -> missing());
    }
    private void own(UUID owner) {
        CurrentUser user = CurrentUser.get();
        if (!user.admin() && !owner.equals(user.id()))
            throw new BusinessException(ErrorCode.ORDER_OWNERSHIP_VIOLATION, "Order belongs to another user");
    }
    private Map<UUID, ProductEntity> lockProducts(Collection<UUID> ids) {
        Map<UUID, ProductEntity> result = new LinkedHashMap<>();
        // A single lock order is shared by create, update and cancel, preventing cross-product deadlocks.
        ids.stream().distinct().sorted().forEach(id -> result.put(id, products.lockById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found", Map.of("product_id", id)))));
        return result;
    }
    private void validateProducts(List<OrderItemRequest> items, Map<UUID, ProductEntity> locked) {
        for (var item : items) {
            if (locked.get(item.getProductId()).status != ProductStatus.ACTIVE)
                throw new BusinessException(ErrorCode.PRODUCT_INACTIVE, "Product is not active", Map.of("product_id", item.getProductId()));
        }
        List<Map<String, Object>> insufficient = new ArrayList<>();
        for (var item : items) {
            var p = locked.get(item.getProductId());
            if (p.stock < item.getQuantity()) insufficient.add(Map.of("product_id", p.id, "requested_quantity", item.getQuantity(), "available_stock", p.stock));
        }
        if (!insufficient.isEmpty()) throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock", Map.of("products", insufficient));
    }
    private void replaceItems(OrderEntity order, List<OrderItemRequest> items, Map<UUID, ProductEntity> locked) {
        order.subtotal = BigDecimal.ZERO.setScale(2);
        for (var request : items) {
            ProductEntity p = locked.get(request.getProductId()); p.stock -= request.getQuantity();
            OrderItemEntity item = new OrderItemEntity(); item.order = order; item.productId = p.id; item.quantity = request.getQuantity(); item.priceAtOrder = p.price;
            order.items.add(item); order.subtotal = order.subtotal.add(item.priceAtOrder.multiply(BigDecimal.valueOf(item.quantity)));
        }
    }
    private void restore(OrderEntity order, Map<UUID, ProductEntity> locked) {
        order.items.forEach(i -> locked.get(i.productId).stock = Math.addExact(locked.get(i.productId).stock, i.quantity));
    }
    private void checkRate(UUID user, String type) {
        operations.findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(user, type).ifPresent(op -> {
            if (op.createdAt.plusMinutes(rateMinutes).isAfter(OffsetDateTime.now()))
                throw new BusinessException(ErrorCode.ORDER_LIMIT_EXCEEDED, "Order operation rate limit exceeded", Map.of("retry_at", op.createdAt.plusMinutes(rateMinutes).toString()));
        });
    }
    private void record(UUID user, String type) {
        UserOperationEntity op = new UserOperationEntity(); op.userId = user; op.operationType = type; operations.save(op);
    }
    private BusinessException missing() { return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found"); }
    private BusinessException transition() { return new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Invalid order state transition"); }
}
