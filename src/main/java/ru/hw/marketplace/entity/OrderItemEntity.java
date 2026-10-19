package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {
    @Id public UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") public OrderEntity order;
    @Column(nullable = false) public UUID productId;
    @Column(nullable = false) public int quantity;
    @Column(nullable = false, precision = 15, scale = 2) public BigDecimal priceAtOrder;
}
