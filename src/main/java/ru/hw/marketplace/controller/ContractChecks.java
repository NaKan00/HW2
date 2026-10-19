package ru.hw.marketplace.controller;

import java.nio.charset.StandardCharsets;
import java.util.*;
import ru.hw.marketplace.exception.BusinessException;
import ru.hw.marketplace.generated.model.*;

final class ContractChecks {
    private ContractChecks() {}
    static void items(List<OrderItemRequest> items) {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == null) invalid("items[" + i + "]", "must not be null");
            if (!seen.add(items.get(i).getProductId())) invalid("items[" + i + "].product_id", "duplicate product_id");
        }
    }
    static void password(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) invalid("password", "must be at most 72 UTF-8 bytes");
    }
    static void invalid(String field, String violation) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed", Map.of("violations", List.of(Map.of("field", field, "violation", violation))));
    }
}
