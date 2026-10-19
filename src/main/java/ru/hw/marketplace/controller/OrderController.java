package ru.hw.marketplace.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.hw.marketplace.generated.api.OrdersApi;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.service.OrderService;

@RestController
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class OrderController implements OrdersApi {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    @Override public ResponseEntity<OrderResponse> createOrder(OrderCreate r) { ContractChecks.items(r.getItems()); return ResponseEntity.status(201).body(orders.create(r)); }
    @Override public ResponseEntity<OrderResponse> getOrder(UUID id) { return ResponseEntity.ok(orders.get(id)); }
    @Override public ResponseEntity<OrderResponse> updateOrder(UUID id, OrderUpdate r) { ContractChecks.items(r.getItems()); return ResponseEntity.ok(orders.update(id, r)); }
    @Override public ResponseEntity<OrderResponse> cancelOrder(UUID id) { return ResponseEntity.ok(orders.cancel(id)); }
    @Override @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> changeOrderStatus(UUID id, OrderStatusChange r) { return ResponseEntity.ok(orders.changeStatus(id, r.getStatus())); }
}
