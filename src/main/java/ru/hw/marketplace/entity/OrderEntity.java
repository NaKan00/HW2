package ru.hw.marketplace.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import ru.hw.marketplace.generated.model.*;

@Entity
@Table(name = "orders")
public class OrderEntity extends BaseEntity {
    @Column(nullable = false) public UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public OrderStatus status;
    public UUID promoCodeId;
    @Column(nullable = false, precision = 22, scale = 2) public BigDecimal subtotal;
    @Column(nullable = false, precision = 22, scale = 2) public BigDecimal discountAmount;
    @Column(nullable = false, precision = 22, scale = 2) public BigDecimal totalAmount;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC") public List<OrderItemEntity> items = new ArrayList<>();
}
