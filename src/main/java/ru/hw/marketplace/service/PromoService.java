package ru.hw.marketplace.service;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hw.marketplace.entity.PromoEntity;
import ru.hw.marketplace.repository.PromoRepository;
import ru.hw.marketplace.generated.model.*;
import ru.hw.marketplace.exception.BusinessException;

@Service
public class PromoService {
    private final PromoRepository promos;
    public PromoService(PromoRepository promos) { this.promos = promos; }
    @Transactional public PromoResponse create(PromoCreate r) {
        PromoEntity p = new PromoEntity(); p.code = r.getCode(); p.discountType = r.getDiscountType(); p.discountValue = r.getDiscountValue();
        p.minOrderAmount = r.getMinOrderAmount(); p.maxUses = r.getMaxUses(); p.validFrom = r.getValidFrom(); p.validUntil = r.getValidUntil(); p.active = r.getActive();
        return ApiMapper.promo(promos.saveAndFlush(p));
    }
    public void validate(PromoEntity p, boolean alreadyApplied) {
        OffsetDateTime now = OffsetDateTime.now();
        // The order already owns one use when being updated, even when the code has reached its limit.
        int otherUses = p.currentUses - (alreadyApplied ? 1 : 0);
        if (!p.active || otherUses >= p.maxUses || now.isBefore(p.validFrom) || now.isAfter(p.validUntil))
            throw new BusinessException(ErrorCode.PROMO_CODE_INVALID, "Promo code is inactive, expired or exhausted");
    }
    public BigDecimal discount(PromoEntity p, BigDecimal subtotal) {
        if (subtotal.compareTo(p.minOrderAmount) < 0)
            throw new BusinessException(ErrorCode.PROMO_CODE_MIN_AMOUNT, "Order amount is below promo minimum", Map.of("min_order_amount", p.minOrderAmount));
        BigDecimal amount = p.discountType == DiscountType.PERCENTAGE
            ? subtotal.multiply(p.discountValue).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .min(subtotal.multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.DOWN))
            : p.discountValue.min(subtotal);
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
    public void release(PromoEntity p) { p.currentUses = Math.max(0, p.currentUses - 1); }
}
