package ru.hw.marketplace.exception;

import java.util.Map;
import ru.hw.marketplace.generated.model.ErrorCode;

public class BusinessException extends RuntimeException {
    public final ErrorCode code;
    public final Map<String, Object> details;
    public BusinessException(ErrorCode code, String message) { this(code, message, Map.of()); }
    public BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        super(message); this.code = code; this.details = details;
    }
}
