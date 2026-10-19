package ru.hw.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.*;
import ru.hw.marketplace.logging.ApiLoggingFilter;
import static org.assertj.core.api.Assertions.*;

class ApiLoggingFilterTest {
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger("api.requests");
    @BeforeEach void attach() { events.start(); logger.addAppender(events); }
    @AfterEach void detach() { logger.detachAppender(events); events.stop(); }
    private String logged() { return events.list.getLast().getFormattedMessage(); }
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void jsonLogsContainRequiredFieldsAndRecursivelyMaskSecrets() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("{\"email\":\"test@example.com\",\"password\":\"never-print-me\",\"nested\":{\"refresh_token\":\"private-token\"}}".getBytes(StandardCharsets.UTF_8));
        request.setAttribute("user_id", "user-123");
        var response = new MockHttpServletResponse();
        new ApiLoggingFilter(mapper).doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes(); ((jakarta.servlet.http.HttpServletResponse)res).setStatus(201);
        });
        assertThat(logged()).doesNotContain("never-print-me", "private-token");
        var event = mapper.readTree(logged());
        assertThat(event.path("body").path("password").asText()).isEqualTo("***");
        assertThat(event.path("body").path("nested").path("refresh_token").asText()).isEqualTo("***");
        assertThat(event.path("status_code").asInt()).isEqualTo(201);
        assertThat(event.path("user_id").asText()).isEqualTo("user-123");
        for (String field : new String[]{"request_id", "method", "endpoint", "status_code", "duration_ms", "user_id", "timestamp"}) assertThat(event.has(field)).isTrue();
        assertThat(UUID.fromString(response.getHeader("X-Request-Id")).toString()).isEqualTo(event.path("request_id").asText());
    }
    @Test void malformedBodyIsNeverLoggedVerbatimEvenIfSecurityDoesNotReadIt() throws Exception {
        var request = new MockHttpServletRequest("PUT", "/products"); request.setContent("password=do-not-print".getBytes(StandardCharsets.UTF_8));
        new ApiLoggingFilter(mapper).doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertThat(logged()).doesNotContain("do-not-print").contains("redacted");
    }
}
