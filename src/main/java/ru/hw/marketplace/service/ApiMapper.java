package ru.hw.marketplace.service;

import ru.hw.marketplace.entity.*;
import ru.hw.marketplace.generated.model.*;

public final class ApiMapper {
    private ApiMapper() {}
    public static ProductResponse product(ProductEntity p) {
        return new ProductResponse(p.id, p.name, p.price, p.stock, p.category, p.status, p.sellerId, p.createdAt, p.updatedAt).description(p.description);
    }
    public static OrderResponse order(OrderEntity o) {
        var items = o.items.stream().map(i -> new OrderItemResponse(i.id, i.productId, i.quantity, i.priceAtOrder)).toList();
        return new OrderResponse(o.id, o.userId, items, o.status, o.subtotal, o.discountAmount, o.totalAmount, o.createdAt, o.updatedAt).promoCodeId(o.promoCodeId);
    }
    public static PromoResponse promo(PromoEntity p) {
        return new PromoResponse(p.id, p.code, p.discountType, p.discountValue, p.minOrderAmount, p.maxUses, p.validFrom, p.validUntil, p.active, p.currentUses);
    }
    public static UserResponse user(UserEntity u) { return new UserResponse(u.id, u.email, u.role, u.createdAt); }
}
