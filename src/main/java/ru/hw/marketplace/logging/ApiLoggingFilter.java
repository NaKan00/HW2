package ru.hw.marketplace.logging;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public ApiLoggingFilter(ObjectMapper mapper) { this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        String id = UUID.randomUUID().toString();
        res.setHeader("X-Request-Id", id);
        var cached = new ContentCachingRequestWrapper(req, 16384);
        try { chain.doFilter(cached, res); }
        finally {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("request_id", id); log.put("method", req.getMethod()); log.put("endpoint", req.getRequestURI());
            log.put("status_code", res.getStatus()); log.put("duration_ms", (System.nanoTime() - start) / 1_000_000);
            log.put("user_id", req.getAttribute("user_id")); log.put("timestamp", Instant.now().toString());
            if (Set.of("POST", "PUT", "DELETE").contains(req.getMethod())) {
                if (cached.getContentAsByteArray().length == 0) cached.getInputStream().readNBytes(16384);
                log.put("body", safeBody(cached.getContentAsByteArray()));
            }
            LoggerFactory.getLogger("api.requests").info(mapper.writeValueAsString(log));
        }
    }
    private Object safeBody(byte[] body) {
        if (body.length == 0) return null;
        try { return mask(mapper.readTree(body)); }
        catch (IOException e) { return "[unparseable or truncated body redacted]"; }
    }
    private JsonNode mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>(); object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String key = name.toLowerCase(Locale.ROOT);
                if (key.contains("password") || key.contains("token") || key.contains("secret") || key.contains("authorization")) object.put(name, "***");
                else object.set(name, mask(object.get(name)));
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) array.set(i, mask(array.get(i)));
        }
        return node;
    }
}
