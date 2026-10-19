package ru.hw.marketplace.exception;

import org.springframework.http.HttpStatus;
import ru.hw.marketplace.generated.model.*;
import java.util.Map;

public final class Errors {
    private Errors() {}
    public static ApiError body(ErrorCode code, String message, Map<String, Object> details) {
        ApiError body = new ApiError(code, message);
        if (!details.isEmpty()) body.setDetails(details);
        return body;
    }
    public static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case PRODUCT_NOT_FOUND, ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PRODUCT_INACTIVE, ORDER_HAS_ACTIVE, INVALID_STATE_TRANSITION, INSUFFICIENT_STOCK -> HttpStatus.CONFLICT;
            case ORDER_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case PROMO_CODE_INVALID, PROMO_CODE_MIN_AMOUNT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case ORDER_OWNERSHIP_VIOLATION, ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case TOKEN_EXPIRED, TOKEN_INVALID, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
