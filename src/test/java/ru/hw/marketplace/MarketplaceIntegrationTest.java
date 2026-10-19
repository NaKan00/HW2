package ru.hw.marketplace;

import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import ru.hw.marketplace.entity.UserEntity;
import ru.hw.marketplace.repository.UserRepository;
import ru.hw.marketplace.generated.model.Role;
import ru.hw.marketplace.security.JwtService;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"app.jwt.secret=" + MarketplaceIntegrationTest.SECRET,
    "app.order-rate-limit-minutes=1", "app.bootstrap-admin.email=", "app.bootstrap-admin.password="})
@AutoConfigureMockMvc
@Testcontainers
class MarketplaceIntegrationTest {
    static final String SECRET = "integration-test-signing-secret-at-least-32-bytes-long";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    String seller, buyer, admin;

    @BeforeEach void clean() throws Exception {
        db.execute("TRUNCATE user_operations, order_items, orders, products, promo_codes, users CASCADE");
        seller = account("SELLER"); buyer = account("USER");
        UserEntity u = new UserEntity(); u.email = "admin@example.com"; u.role = Role.ADMIN; u.passwordHash = passwords.encode("Admin123!");
        admin = jwt.tokens(users.saveAndFlush(u)).getAccessToken();
    }
    @Test void productCrudExactFilterPaginationAndSoftDelete() throws Exception {
        var p = product("ACTIVE", 10, "12.50", "Books"); String id = p.path("id").asText();
        assertThat(p.path("seller_id").asText()).isNotBlank();
        assertThat(p.path("created_at").asText()).isNotBlank();
        call("GET", "/products/" + id, buyer, null, 200);
        product("ACTIVE", 5, "10.00", "Book");
        var page = call("GET", "/products?category=Books&status=ACTIVE&size=1&page=0", buyer, null, 200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(1); assertThat(page.path("items").size()).isEqualTo(1);
        assertThat(call("GET", "/products?category=books", buyer, null, 200).path("items").size()).isZero();
        assertThat(call("GET", "/products?size=1&page=1", buyer, null, 200).path("items").size()).isEqualTo(1);
        call("PUT", "/products/" + id, seller, productBody("INACTIVE", 7, "14.00", "Books"), 200);
        call("DELETE", "/products/" + id, seller, null, 204);
        assertThat(call("GET", "/products/" + id, buyer, null, 200).path("status").asText()).isEqualTo("ARCHIVED");
        assertThat(db.queryForObject("SELECT count(*) FROM products WHERE id = ?::uuid AND status = 'ARCHIVED'", Integer.class, id)).isEqualTo(1);
    }
    @Test void generatedProductAndQueryValidation() throws Exception {
        for (var change : List.of(Map.of("name", ""), Map.of("name", " "), Map.of("name", "x".repeat(256)),
                Map.of("description", "x".repeat(4001)), Map.of("price", new BigDecimal("0.001")),
                Map.of("price", new BigDecimal("1.234")), Map.of("stock", -1), Map.of("category", ""), Map.of("category", "x".repeat(101)))) {
            var body = productBody("ACTIVE", 10, "1.00", "Books"); body.putAll(change);
            error("POST", "/products", seller, body, 400, "VALIDATION_ERROR");
        }
        var fractionalStock = productBody("ACTIVE", 10, "1.00", "Books"); fractionalStock.put("stock", new BigDecimal("1.5"));
        error("POST", "/products", seller, fractionalStock, 400, "VALIDATION_ERROR");
        var stringPrice = productBody("ACTIVE", 10, "1.00", "Books"); stringPrice.put("price", "1.00");
        error("POST", "/products", seller, stringPrice, 400, "VALIDATION_ERROR");
        error("POST", "/products", seller, Map.of(), 400, "VALIDATION_ERROR");
        error("GET", "/products?page=-1", buyer, null, 400, "VALIDATION_ERROR");
        error("GET", "/products?size=0", buyer, null, 400, "VALIDATION_ERROR");
        error("GET", "/products?status=BAD", buyer, null, 400, "VALIDATION_ERROR");
        error("GET", "/products/not-a-uuid", buyer, null, 400, "VALIDATION_ERROR");
        assertThat(db.queryForObject("SELECT count(*) FROM products", Integer.class)).isZero();
    }
    @Test void generatedNestedOrderValidationRunsBeforeBusinessLogic() throws Exception {
        String id = product("ACTIVE", 100, "10", "Books").path("id").asText();
        for (int quantity : List.of(0, 1000)) error("POST", "/orders", buyer, order(id, quantity), 400, "VALIDATION_ERROR");
        error("POST", "/orders", buyer, Map.of("items", List.of()), 400, "VALIDATION_ERROR");
        error("POST", "/orders", buyer, Map.of("items", Collections.nCopies(51, Map.of("product_id", id, "quantity", 1))), 400, "VALIDATION_ERROR");
        error("POST", "/orders", buyer, Map.of("items", Arrays.asList((Object) null)), 400, "VALIDATION_ERROR");
        error("POST", "/orders", buyer, Map.of("items", List.of(Map.of("quantity", 1))), 400, "VALIDATION_ERROR");
        var duplicate = order(id, 1); duplicate.put("items", Collections.nCopies(2, Map.of("product_id", id, "quantity", 1)));
        error("POST", "/orders", buyer, duplicate, 400, "VALIDATION_ERROR");
        var badPromo = order(id, 1); badPromo.put("promo_code", "bad");
        error("POST", "/orders", buyer, badPromo, 400, "VALIDATION_ERROR");
        assertThat(stock(id)).isEqualTo(100); assertThat(count("user_operations")).isZero();
    }
    @Test void authenticationJwtRefreshAndErrorContract() throws Exception {
        error("GET", "/products", null, null, 401, "TOKEN_INVALID");
        error("GET", "/products", "broken", null, 401, "TOKEN_INVALID");
        error("POST", "/auth/refresh", null, Map.of("refresh_token", "broken"), 401, "REFRESH_TOKEN_INVALID");
        error("POST", "/auth/refresh", null, Map.of("refresh_token", buyer), 401, "REFRESH_TOKEN_INVALID");
        error("POST", "/auth/register", null, Map.of("email", "a@b.com", "password", "Password123!", "role", "ADMIN"), 400, "VALIDATION_ERROR");
        error("POST", "/auth/login", null, Map.of("email", "unknown@example.com", "password", "nope"), 401, "TOKEN_INVALID");
        String email = "refresh@example.com";
        call("POST", "/auth/register", null, Map.of("email", email, "password", "Password123!"), 201);
        var tokens = call("POST", "/auth/login", null, Map.of("email", email, "password", "Password123!"), 200);
        call("POST", "/auth/refresh", null, Map.of("refresh_token", tokens.path("refresh_token").asText()), 200);
        error("GET", "/products", tokens.path("refresh_token").asText(), null, 401, "TOKEN_INVALID");
        assertThat(db.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email)).startsWith("$2");
        String expired = token("access", Instant.now().minusSeconds(30));
        error("GET", "/products", expired, null, 401, "TOKEN_EXPIRED");
        error("POST", "/auth/refresh", null, Map.of("refresh_token", token("refresh", Instant.now().minusSeconds(30))), 401, "REFRESH_TOKEN_INVALID");
    }
    @Test void roleMatrixAndProductOwnership() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        String otherSeller = account("SELLER");
        error("PUT", "/products/" + id, otherSeller, productBody("ACTIVE", 10, "10", "Books"), 403, "ACCESS_DENIED");
        error("DELETE", "/products/" + id, otherSeller, null, 403, "ACCESS_DENIED");
        for (String method : List.of("POST", "PUT", "DELETE")) {
            String path = method.equals("POST") ? "/products" : "/products/" + id;
            error(method, path, buyer, method.equals("DELETE") ? null : productBody("ACTIVE", 10, "10", "Books"), 403, "ACCESS_DENIED");
        }
        error("POST", "/promo-codes", buyer, promoBody("TEST10", "PERCENTAGE", "10", "0", 2), 403, "ACCESS_DENIED");
        error("POST", "/orders", seller, order(id, 1), 403, "ACCESS_DENIED");
        String oid = call("POST", "/orders", buyer, order(id, 1), 201).path("id").asText();
        error("GET", "/orders/" + oid, seller, null, 403, "ACCESS_DENIED");
        error("PUT", "/orders/" + oid, seller, order(id, 1), 403, "ACCESS_DENIED");
        error("POST", "/orders/" + oid + "/cancel", seller, null, 403, "ACCESS_DENIED");
        error("POST", "/orders/" + oid + "/status", buyer, Map.of("status", "PAID"), 403, "ACCESS_DENIED");
        call("PUT", "/products/" + id, admin, productBody("ACTIVE", 9, "11", "Books"), 200);
        call("GET", "/orders/" + oid, admin, null, 200);
        call("PUT", "/orders/" + oid, admin, order(id, 2), 200);
        call("POST", "/orders/" + oid + "/cancel", admin, null, 200);
        call("DELETE", "/products/" + id, admin, null, 204);
    }
    @Test void reservationSnapshotsAndTotals() throws Exception {
        String id = product("ACTIVE", 10, "12.50", "Books").path("id").asText();
        var o = call("POST", "/orders", buyer, order(id, 3), 201);
        assertThat(stock(id)).isEqualTo(7); assertMoney(o, "subtotal", "37.50"); assertMoney(o, "total_amount", "37.50");
        call("PUT", "/products/" + id, seller, productBody("ACTIVE", 7, "99.00", "Books"), 200);
        var saved = call("GET", "/orders/" + o.path("id").asText(), buyer, null, 200);
        assertThat(saved.path("items").get(0).path("price_at_order").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(count("user_operations")).isEqualTo(1);
    }
    @Test void insufficientStockListsAllProductsWithoutWrites() throws Exception {
        String a = product("ACTIVE", 1, "10", "Books").path("id").asText();
        String b = product("ACTIVE", 0, "10", "Books").path("id").asText();
        var failure = error("POST", "/orders", buyer, Map.of("items", List.of(Map.of("product_id", a, "quantity", 2), Map.of("product_id", b, "quantity", 3))), 409, "INSUFFICIENT_STOCK");
        assertThat(failure.path("details").path("products").size()).isEqualTo(2);
        assertThat(stock(a)).isEqualTo(1); assertThat(stock(b)).isZero(); assertThat(count("orders")).isZero();
    }
    @Test void promoFailureRollsBackReservationOrderAndOperation() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        var body = order(id, 3); body.put("promo_code", "NONE");
        error("POST", "/orders", buyer, body, 422, "PROMO_CODE_INVALID");
        assertThat(stock(id)).isEqualTo(10); assertThat(count("orders")).isZero(); assertThat(count("order_items")).isZero(); assertThat(count("user_operations")).isZero();
    }
    @Test void missingAndInactiveProducts() throws Exception {
        error("GET", "/products/" + UUID.randomUUID(), buyer, null, 404, "PRODUCT_NOT_FOUND");
        error("POST", "/orders", buyer, order(UUID.randomUUID().toString(), 1), 404, "PRODUCT_NOT_FOUND");
        for (String status : List.of("INACTIVE", "ARCHIVED")) {
            String id = product(status, 10, "10", "Books").path("id").asText();
            error("POST", "/orders", buyer, order(id, 1), 409, "PRODUCT_INACTIVE");
        }
    }
    @Test void createRateLimitPrecedesActiveOrderAndSurvivesCancellation() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        String oid = call("POST", "/orders", buyer, order(id, 1), 201).path("id").asText();
        error("POST", "/orders", buyer, order(id, 1), 429, "ORDER_LIMIT_EXCEEDED");
        ageOperations(); error("POST", "/orders", buyer, order(id, 1), 409, "ORDER_HAS_ACTIVE");
        call("POST", "/orders/" + oid + "/cancel", buyer, null, 200);
        String second = call("POST", "/orders", buyer, order(id, 1), 201).path("id").asText();
        call("POST", "/orders/" + second + "/cancel", buyer, null, 200);
        error("POST", "/orders", buyer, order(id, 1), 429, "ORDER_LIMIT_EXCEEDED");
    }
    @Test void orderUpdateReplacesSnapshotAndAppliesIndependentRateLimit() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        String oid = call("POST", "/orders", buyer, order(id, 2), 201).path("id").asText();
        call("PUT", "/products/" + id, seller, productBody("ACTIVE", 8, "15", "Books"), 200);
        var updated = call("PUT", "/orders/" + oid, buyer, order(id, 3), 200);
        assertThat(stock(id)).isEqualTo(7); assertMoney(updated, "total_amount", "45.00");
        assertThat(updated.path("items").get(0).path("price_at_order").decimalValue()).isEqualByComparingTo("15");
        error("PUT", "/orders/" + oid, buyer, order(id, 1), 429, "ORDER_LIMIT_EXCEEDED");
        assertThat(count("order_items")).isEqualTo(1); assertThat(count("user_operations")).isEqualTo(2);
    }
    @Test void failedUpdateRestoresOriginalReservationAndItems() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        String oid = call("POST", "/orders", buyer, order(id, 2), 201).path("id").asText();
        error("PUT", "/orders/" + oid, buyer, order(id, 11), 409, "INSUFFICIENT_STOCK");
        assertThat(stock(id)).isEqualTo(8);
        assertThat(call("GET", "/orders/" + oid, buyer, null, 200).path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(count("user_operations")).isEqualTo(1);
    }
    @Test void percentageCapPromoUsageAndRemovalWhenUpdateFallsBelowMinimum() throws Exception {
        String id = product("ACTIVE", 20, "10", "Books").path("id").asText();
        call("POST", "/promo-codes", seller, promoBody("CAP90", "PERCENTAGE", "90", "50", 1), 201);
        var body = order(id, 10); body.put("promo_code", "CAP90");
        var o = call("POST", "/orders", buyer, body, 201); String oid = o.path("id").asText();
        assertMoney(o, "discount_amount", "70.00"); assertMoney(o, "total_amount", "30.00");
        // The order's own last available use must still be valid on update.
        var kept = call("PUT", "/orders/" + oid, buyer, order(id, 8), 200); assertMoney(kept, "discount_amount", "56.00");
        ageOperations();
        var removed = call("PUT", "/orders/" + oid, buyer, order(id, 2), 200);
        assertMoney(removed, "discount_amount", "0"); assertMoney(removed, "total_amount", "20");
        assertThat(removed.path("promo_code_id").isNull()).isTrue();
        assertThat(db.queryForObject("SELECT current_uses FROM promo_codes WHERE code='CAP90'", Integer.class)).isZero();
        call("POST", "/orders/" + oid + "/cancel", buyer, null, 200); assertThat(stock(id)).isEqualTo(20);
    }
    @Test void fixedDiscountCannotExceedSubtotalAndCancelReleasesUseExactlyOnce() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        call("POST", "/promo-codes", seller, promoBody("FIX100", "FIXED_AMOUNT", "100", "0", 1), 201);
        var body = order(id, 2); body.put("promo_code", "FIX100");
        var o = call("POST", "/orders", buyer, body, 201); assertMoney(o, "total_amount", "0");
        String oid = o.path("id").asText();
        call("POST", "/orders/" + oid + "/status", admin, Map.of("status", "PAYMENT_PENDING"), 200);
        call("POST", "/orders/" + oid + "/cancel", buyer, null, 200);
        error("POST", "/orders/" + oid + "/cancel", buyer, null, 409, "INVALID_STATE_TRANSITION");
        assertThat(stock(id)).isEqualTo(10); assertThat(db.queryForObject("SELECT current_uses FROM promo_codes", Integer.class)).isZero();
    }
    @Test void promoMinimumExpiryActivityAndCapacityFailures() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        call("POST", "/promo-codes", seller, promoBody("MIN50", "PERCENTAGE", "10", "50", 1), 201);
        var body = order(id, 1); body.put("promo_code", "MIN50");
        error("POST", "/orders", buyer, body, 422, "PROMO_CODE_MIN_AMOUNT");
        db.update("UPDATE promo_codes SET active=false"); error("POST", "/orders", buyer, body, 422, "PROMO_CODE_INVALID");
        db.update("UPDATE promo_codes SET active=true, valid_from=now()-interval '3 days', valid_until=now()-interval '1 day'");
        error("POST", "/orders", buyer, body, 422, "PROMO_CODE_INVALID");
        db.update("UPDATE promo_codes SET valid_until=now()+interval '1 day', current_uses=max_uses");
        error("POST", "/orders", buyer, body, 422, "PROMO_CODE_INVALID");
        assertThat(stock(id)).isEqualTo(10); assertThat(count("orders")).isZero();
    }
    @Test void invalidPromoOnUpdateRollsBackFlushedItemsAndStocks() throws Exception {
        String id = product("ACTIVE", 20, "10", "Books").path("id").asText();
        call("POST", "/promo-codes", seller, promoBody("EXPIRE", "PERCENTAGE", "10", "0", 1), 201);
        var body = order(id, 2); body.put("promo_code", "EXPIRE");
        String oid = call("POST", "/orders", buyer, body, 201).path("id").asText();
        db.update("UPDATE promo_codes SET active=false");
        error("PUT", "/orders/" + oid, buyer, order(id, 3), 422, "PROMO_CODE_INVALID");
        assertThat(stock(id)).isEqualTo(18);
        assertThat(call("GET", "/orders/" + oid, buyer, null, 200).path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT current_uses FROM promo_codes", Integer.class)).isEqualTo(1);
    }
    @Test void lifecycleAllowsOnlyForwardStepsAndCancellationFromEarlyStates() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        String oid = call("POST", "/orders", buyer, order(id, 2), 201).path("id").asText();
        error("POST", "/orders/" + oid + "/status", admin, Map.of("status", "PAID"), 409, "INVALID_STATE_TRANSITION");
        call("POST", "/orders/" + oid + "/status", admin, Map.of("status", "PAYMENT_PENDING"), 200);
        error("PUT", "/orders/" + oid, buyer, order(id, 1), 409, "INVALID_STATE_TRANSITION");
        for (String status : List.of("PAID", "SHIPPED", "COMPLETED")) call("POST", "/orders/" + oid + "/status", admin, Map.of("status", status), 200);
        error("POST", "/orders/" + oid + "/cancel", buyer, null, 409, "INVALID_STATE_TRANSITION");
        error("POST", "/orders/" + oid + "/status", admin, Map.of("status", "CREATED"), 409, "INVALID_STATE_TRANSITION");
        assertThat(stock(id)).isEqualTo(8);
    }
    @Test void orderOwnershipAndMissingOrder() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        String oid = call("POST", "/orders", buyer, order(id, 1), 201).path("id").asText(); String other = account("USER");
        error("GET", "/orders/" + oid, other, null, 403, "ORDER_OWNERSHIP_VIOLATION");
        error("PUT", "/orders/" + oid, other, order(id, 2), 403, "ORDER_OWNERSHIP_VIOLATION");
        error("POST", "/orders/" + oid + "/cancel", other, null, 403, "ORDER_OWNERSHIP_VIOLATION");
        error("GET", "/orders/" + UUID.randomUUID(), buyer, null, 404, "ORDER_NOT_FOUND");
    }
    @Test void concurrentBuyersCannotOversell() throws Exception {
        String id = product("ACTIVE", 1, "10", "Books").path("id").asText();
        String other = account("USER"); CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> tasks = new ArrayList<>();
            for (String user : List.of(buyer, other)) tasks.add(executor.submit(() -> {
                gate.await(); return raw("POST", "/orders", user, order(id, 1)).getResponse().getStatus();
            }));
            gate.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(stock(id)).isZero(); assertThat(count("orders")).isEqualTo(1);
    }
    @Test void concurrentRequestsForSameUserRespectRateLimit() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText(); CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 2; i++) tasks.add(executor.submit(() -> { gate.await(); return raw("POST", "/orders", buyer, order(id, 1)).getResponse().getStatus(); }));
            gate.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 429);
        }
        assertThat(count("orders")).isEqualTo(1); assertThat(stock(id)).isEqualTo(9);
    }
    @Test void concurrentPromoUsersCannotExceedMaxUses() throws Exception {
        String id = product("ACTIVE", 10, "10", "Books").path("id").asText();
        call("POST", "/promo-codes", seller, promoBody("LASTUSE", "PERCENTAGE", "10", "0", 1), 201);
        String other = account("USER"); CountDownLatch gate = new CountDownLatch(1);
        var body = order(id, 1); body.put("promo_code", "LASTUSE");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> tasks = new ArrayList<>();
            for (String user : List.of(buyer, other)) tasks.add(executor.submit(() -> { gate.await(); return raw("POST", "/orders", user, body).getResponse().getStatus(); }));
            gate.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 422);
        }
        assertThat(db.queryForObject("SELECT current_uses FROM promo_codes", Integer.class)).isEqualTo(1);
        assertThat(stock(id)).isEqualTo(9); assertThat(count("orders")).isEqualTo(1); assertThat(count("user_operations")).isEqualTo(1);
    }
    @Test void percentageDiscountNeverExceedsSeventyPercentEvenForOneCent() throws Exception {
        String id = product("ACTIVE", 10, "0.01", "Books").path("id").asText();
        call("POST", "/promo-codes", seller, promoBody("SMALL90", "PERCENTAGE", "90", "0", 1), 201);
        var body = order(id, 1); body.put("promo_code", "SMALL90");
        var result = call("POST", "/orders", buyer, body, 201);
        assertMoney(result, "discount_amount", "0"); assertMoney(result, "total_amount", "0.01");
    }
    private String account(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        call("POST", "/auth/register", null, Map.of("email", email, "password", "Password123!", "role", role), 201);
        return call("POST", "/auth/login", null, Map.of("email", email, "password", "Password123!"), 200).path("access_token").asText();
    }
    private Map<String,Object> productBody(String status, int stock, String price, String category) {
        return new HashMap<>(Map.of("name", "Test product", "description", "Description", "price", new BigDecimal(price), "stock", stock, "category", category, "status", status));
    }
    private JsonNode product(String status, int stock, String price, String category) throws Exception {
        return call("POST", "/products", seller, productBody(status, stock, price, category), 201);
    }
    private Map<String,Object> order(String product, int qty) {
        return new HashMap<>(Map.of("items", List.of(Map.of("product_id", product, "quantity", qty))));
    }
    private Map<String,Object> promoBody(String code, String type, String value, String minimum, int uses) {
        return new HashMap<>(Map.of("code", code, "discount_type", type, "discount_value", new BigDecimal(value), "min_order_amount", new BigDecimal(minimum), "max_uses", uses,
            "valid_from", OffsetDateTime.now().minusDays(1).toString(), "valid_until", OffsetDateTime.now().plusDays(1).toString(), "active", true));
    }
    private MvcResult raw(String method, String path, String token, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType("application/json").content(mapper.writeValueAsBytes(body));
        return mvc.perform(request).andReturn();
    }
    private JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var result = raw(method, path, token, body); var response = result.getResponse();
        assertThat(response.getStatus()).as("%s %s: %s", method, path, response.getContentAsString()).isEqualTo(expected);
        assertThatCode(() -> UUID.fromString(response.getHeader("X-Request-Id"))).doesNotThrowAnyException();
        return response.getContentAsString().isEmpty() ? mapper.nullNode() : mapper.readTree(response.getContentAsString());
    }
    private JsonNode error(String method, String path, String token, Object body, int status, String code) throws Exception {
        var result = call(method, path, token, body, status);
        assertThat(result.path("error_code").asText()).isEqualTo(code); assertThat(result.path("message").asText()).isNotBlank();
        List<String> fields = new ArrayList<>(); result.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).contains("error_code", "message").allMatch(Set.of("error_code", "message", "details")::contains);
        return result;
    }
    private int stock(String id) { return db.queryForObject("SELECT stock FROM products WHERE id=?::uuid", Integer.class, id); }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void ageOperations() { db.update("UPDATE user_operations SET created_at=now()-interval '2 minutes'"); }
    private void assertMoney(JsonNode node, String field, String value) { assertThat(node.path(field).decimalValue()).isEqualByComparingTo(value); }
    private String token(String type, Instant expiry) {
        return Jwts.builder().issuer("marketplace").subject(UUID.randomUUID().toString()).claim("role", "USER").claim("type", type)
            .expiration(Date.from(expiry)).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
