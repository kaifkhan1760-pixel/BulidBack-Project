package com.buildback.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import com.buildback.dto.OrderRequest;
import com.buildback.entity.Material;
import com.buildback.entity.OrderStatus;
import com.buildback.entity.Role;
import com.buildback.exception.InvalidRequestException;
import com.buildback.repository.MaterialRepository;
import com.buildback.repository.PurchaseOrderRepository;
import com.buildback.service.OrderService;
import com.buildback.support.ApiTestSupport;

/**
 * Orders, stock reservation, payments (built-in simulator) and fulfilment.
 * The first test is the exact demo from the project proposal:
 * seller lists 35 tiles, calculator says 17, buyer pays, 18 remain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPaymentIntegrationTest extends ApiTestSupport {

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private PurchaseOrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    // ---------------- helpers ----------------

    private long createTiles(TestUser seller, int quantity) throws Exception {
        return idOf(postJson(seller, "/api/seller/materials", map(
                "title", "Vitrified floor tiles 2x2", "description", "Ivory vitrified tiles, unused.",
                "category", "TILES", "unitPrice", "45.00", "availableQuantity", quantity, "unit", "PIECE",
                "city", "Pune", "tileLengthFeet", "2.00", "tileWidthFeet", "2.00",
                "imageUrls", List.of("https://example.com/tiles.jpg")))
                .andExpect(status().isCreated()));
    }

    private long createPaint(TestUser seller) throws Exception {
        return idOf(postJson(seller, "/api/seller/materials", map(
                "title", "Emulsion paint", "description", "20 litre cans, sealed.", "category", "PAINT",
                "unitPrice", "1200.00", "availableQuantity", 4, "unit", "CAN", "city", "Pune",
                "imageUrls", List.of("https://example.com/paint.jpg")))
                .andExpect(status().isCreated()));
    }

    private ResultActions order(TestUser buyer, long materialId, int quantity) throws Exception {
        return postJson(buyer, "/api/buyer/orders", map("materialId", materialId, "quantity", quantity));
    }

    private Material material(long id) {
        return materialRepository.findById(id).orElseThrow();
    }

    /** Starts a payment and asks the simulator to approve it; returns {paymentId, verify body}. */
    private record Approved(long paymentId, Object verifyBody) {
    }

    private Approved approve(TestUser buyer, long orderId) throws Exception {
        ResultActions start = postJson(buyer, "/api/buyer/orders/" + orderId + "/payments", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("SIMULATOR"))
                .andExpect(jsonPath("$.publicKey").doesNotExist());
        long paymentId = ((Number) read(start, "$.paymentId")).longValue();

        ResultActions sim = postJson(buyer, "/api/buyer/payments/" + paymentId + "/simulate",
                map("outcome", "SUCCESS")).andExpect(status().isOk());
        return new Approved(paymentId, map(
                "providerOrderId", read(sim, "$.providerOrderId"),
                "providerPaymentId", read(sim, "$.providerPaymentId"),
                "signature", read(sim, "$.signature")));
    }

    // ---------------- the proposal demo ----------------

    @Test
    void proposalDemo_35Tiles_calculator17_paid_18Left_thenCollected() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 35);

        // Calculator (public): 60 sq ft, 2x2 ft tiles, 10% allowance -> 17
        mockMvc.perform(get("/api/materials/" + tiles + "/tile-estimate?areaSqFt=60&allowancePercent=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredTiles").value(17))
                .andExpect(jsonPath("$.tileAreaSqFt").value("4"))
                .andExpect(jsonPath("$.areaWithAllowanceSqFt").value("66"))
                .andExpect(jsonPath("$.enoughStock").value(true))
                .andExpect(jsonPath("$.estimatedCost").value("765.00"));

        // Reserve 17
        long orderId = idOf(order(buyer, tiles, 17)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.unitPrice").value("45.00"))
                .andExpect(jsonPath("$.totalAmount").value("765.00"))
                .andExpect(jsonPath("$.sellerEmail").doesNotExist())); // not shared before payment
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(18);
        assertThat(material(tiles).getReservedQuantity()).isEqualTo(17);

        // Pay and verify on the server
        Approved approved = approve(buyer, orderId);
        postJson(buyer, "/api/buyer/payments/" + approved.paymentId() + "/verify", approved.verifyBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.sellerEmail").exists()) // shared after payment, to arrange collection
                .andExpect(jsonPath("$.payments[0].status").value("SUCCESS"));
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(18);
        assertThat(material(tiles).getReservedQuantity()).isZero();

        // Duplicate confirmation changes nothing
        postJson(buyer, "/api/buyer/payments/" + approved.paymentId() + "/verify", approved.verifyBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(18);
        assertThat(material(tiles).getReservedQuantity()).isZero();

        // Public listing shows 18
        mockMvc.perform(get("/api/materials/" + tiles)).andExpect(jsonPath("$.availableQuantity").value(18));

        // Seller sees the order with buyer contact, marks it collected
        getAs(seller, "/api/seller/orders")
                .andExpect(jsonPath("$.content[0].id").value(orderId))
                .andExpect(jsonPath("$.content[0].buyerEmail").value(buyer.email()));
        postJson(seller, "/api/seller/orders/" + orderId + "/collect", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"));

        getAs(buyer, "/api/buyer/dashboard")
                .andExpect(jsonPath("$.stats[2].label").value("Collected"))
                .andExpect(jsonPath("$.stats[2].value").value("1"));
    }

    // ---------------- stock rules ----------------

    @Test
    void excessQuantityAndBadQuantitiesAreRejected() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 18);

        order(buyer, tiles, 19)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("Only 18 available. Reduce the quantity."));
        order(buyer, tiles, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.quantity").exists());
        postJson(buyer, "/api/buyer/orders", map("materialId", tiles))
                .andExpect(status().isBadRequest());
        order(buyer, 99_999_999L, 1).andExpect(status().isNotFound());
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(18);

        order(buyer, tiles, 18).andExpect(status().isCreated());
        order(buyer, tiles, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("This listing is out of stock"));
    }

    @Test
    void archivedListingsCannotBeOrdered() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 5);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/seller/materials/" + tiles).param("version", "0")
                .session(seller.session()).header("X-CSRF-TOKEN", csrf(seller.session())))
                .andExpect(status().isNoContent());

        order(buyer, tiles, 1).andExpect(status().isNotFound());
    }

    @Test
    void twoBuyersRacingForTheLastStockCannotOversell() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        long tiles = createTiles(seller, 10);
        Long buyerA = loggedIn(Role.BUYER).id();
        Long buyerB = loggedIn(Role.BUYER).id();

        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Long buyer : List.of(buyerA, buyerB)) {
            Callable<Boolean> attempt = () -> {
                startTogether.await();
                try {
                    orderService.create(buyer, new OrderRequest(tiles, 7));
                    return true;
                } catch (InvalidRequestException notEnough) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }
        startTogether.countDown();
        int succeeded = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1); // 7 + 7 > 10, so only one order can win
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(3);
        assertThat(material(tiles).getReservedQuantity()).isEqualTo(7);
    }

    // ---------------- payments ----------------

    @Test
    void failedPaymentKeepsTheReservationAndTheBuyerCanRetry() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 10);
        long orderId = idOf(order(buyer, tiles, 4).andExpect(status().isCreated()));

        ResultActions start = postJson(buyer, "/api/buyer/orders/" + orderId + "/payments", null);
        long firstPayment = ((Number) read(start, "$.paymentId")).longValue();
        postJson(buyer, "/api/buyer/payments/" + firstPayment + "/simulate", map("outcome", "FAILURE"))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.signature").doesNotExist());
        postJson(buyer, "/api/buyer/payments/" + firstPayment + "/failure", map("reason", "Card declined"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payments[0].status").value("FAILED"))
                .andExpect(jsonPath("$.payments[0].failureReason").value("Card declined"));
        assertThat(material(tiles).getReservedQuantity()).isEqualTo(4);

        Approved retry = approve(buyer, orderId);
        postJson(buyer, "/api/buyer/payments/" + retry.paymentId() + "/verify", retry.verifyBody())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(2));
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(6);
        assertThat(material(tiles).getReservedQuantity()).isZero();
    }

    @Test
    void forgedOrMismatchedConfirmationsAreRejected() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 10);
        long orderId = idOf(order(buyer, tiles, 2).andExpect(status().isCreated()));
        Approved approved = approve(buyer, orderId);
        @SuppressWarnings("unchecked")
        var good = (java.util.Map<String, Object>) approved.verifyBody();

        postJson(buyer, "/api/buyer/payments/" + approved.paymentId() + "/verify",
                map("providerOrderId", good.get("providerOrderId"), "providerPaymentId", good.get("providerPaymentId"),
                        "signature", "0".repeat(64)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.signature").exists());
        postJson(buyer, "/api/buyer/payments/" + approved.paymentId() + "/verify",
                map("providerOrderId", good.get("providerOrderId"), "providerPaymentId", "sim_pay_other",
                        "signature", good.get("signature")))
                .andExpect(status().isBadRequest());

        getAs(buyer, "/api/buyer/orders/" + orderId)
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payments[0].status").value("CREATED"));
        assertThat(material(tiles).getReservedQuantity()).isEqualTo(2);
    }

    // ---------------- expiry and cancel ----------------

    @Test
    void expiryReleasesStockExactlyOnce() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 10);
        long orderId = idOf(order(buyer, tiles, 5).andExpect(status().isCreated()));
        Instant later = Instant.now().plus(Duration.ofMinutes(16));

        assertThat(orderService.findOverdueOrderIds(later)).contains(orderId);
        assertThat(orderService.expireIfOverdue(orderId, later)).isTrue();
        assertThat(orderService.expireIfOverdue(orderId, later)).isFalse(); // second run: nothing
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(10);
        assertThat(material(tiles).getReservedQuantity()).isZero();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);

        postJson(buyer, "/api/buyer/orders/" + orderId + "/payments", null).andExpect(status().isConflict());
        postJson(buyer, "/api/buyer/orders/" + orderId + "/cancel", null).andExpect(status().isConflict());
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    void paymentArrivingAfterExpiryIsRecordedForRefundWithoutTouchingStock() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 10);
        long orderId = idOf(order(buyer, tiles, 3).andExpect(status().isCreated()));
        Approved approved = approve(buyer, orderId);

        orderService.expireIfOverdue(orderId, Instant.now().plus(Duration.ofMinutes(16)));

        postJson(buyer, "/api/buyer/payments/" + approved.paymentId() + "/verify", approved.verifyBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.payments[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.payments[0].refundRequired").value(true));
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(10);
        assertThat(material(tiles).getReservedQuantity()).isZero();
    }

    @Test
    void buyerCanCancelAnUnpaidOrderOnce() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 10);
        long orderId = idOf(order(buyer, tiles, 6).andExpect(status().isCreated()));

        postJson(buyer, "/api/buyer/orders/" + orderId + "/cancel", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        postJson(buyer, "/api/buyer/orders/" + orderId + "/cancel", null).andExpect(status().isConflict());
        assertThat(material(tiles).getAvailableQuantity()).isEqualTo(10);
        assertThat(material(tiles).getReservedQuantity()).isZero();
    }

    // ---------------- who may do what ----------------

    @Test
    void ordersAndPaymentsAreOnlyVisibleToTheRightPeople() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        TestUser otherSeller = loggedIn(Role.SELLER);
        TestUser buyer = loggedIn(Role.BUYER);
        TestUser otherBuyer = loggedIn(Role.BUYER);
        long tiles = createTiles(seller, 10);
        long orderId = idOf(order(buyer, tiles, 2).andExpect(status().isCreated()));
        ResultActions start = postJson(buyer, "/api/buyer/orders/" + orderId + "/payments", null);
        long paymentId = ((Number) read(start, "$.paymentId")).longValue();

        order(seller, tiles, 1).andExpect(status().isForbidden()); // sellers cannot buy
        getAs(otherBuyer, "/api/buyer/orders/" + orderId).andExpect(status().isNotFound());
        postJson(otherBuyer, "/api/buyer/orders/" + orderId + "/cancel", null).andExpect(status().isNotFound());
        postJson(otherBuyer, "/api/buyer/orders/" + orderId + "/payments", null).andExpect(status().isNotFound());
        postJson(otherBuyer, "/api/buyer/payments/" + paymentId + "/simulate", map("outcome", "SUCCESS"))
                .andExpect(status().isNotFound());
        getAs(otherBuyer, "/api/buyer/orders").andExpect(jsonPath("$.totalElements").value(0));

        getAs(otherSeller, "/api/seller/orders").andExpect(jsonPath("$.totalElements").value(0));
        postJson(otherSeller, "/api/seller/orders/" + orderId + "/collect", null).andExpect(status().isNotFound());
        postJson(seller, "/api/seller/orders/" + orderId + "/collect", null)
                .andExpect(status().isConflict()); // not paid yet

        getAs(buyer, "/api/admin/summary").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/buyer/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialId\":" + tiles + ",\"quantity\":1}").session(buyer.session()))
                .andExpect(status().isForbidden()); // no CSRF token
        mockMvc.perform(get("/api/buyer/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesTotalsUsersOrdersAndCanModerateListings() throws Exception {
        TestUser admin = loggedIn(Role.ADMIN);
        TestUser seller = loggedIn(Role.SELLER);
        long tiles = createTiles(seller, 5);

        getAs(admin, "/api/admin/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeListings").isNumber())
                .andExpect(jsonPath("$.paidRevenue").isString());
        String users = getAs(admin, "/api/admin/users?keyword=" + seller.email())
                .andExpect(jsonPath("$.content[0].email").value(seller.email()))
                .andReturn().getResponse().getContentAsString();
        assertThat(users).doesNotContain("password").doesNotContain("$2a$");
        getAs(admin, "/api/admin/orders?status=PAID").andExpect(status().isOk());
        getAs(admin, "/api/admin/orders?status=WRONG").andExpect(status().isBadRequest());

        postJson(admin, "/api/admin/materials/" + tiles + "/archive", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/materials/" + tiles)).andExpect(status().isNotFound());
        postJson(seller, "/api/admin/materials/" + tiles + "/archive", null).andExpect(status().isForbidden());
    }

    // ---------------- calculator input rules ----------------

    @Test
    void calculatorValidatesInputAndOnlyWorksForTiles() throws Exception {
        TestUser seller = loggedIn(Role.SELLER);
        long tiles = createTiles(seller, 5);
        long paint = createPaint(seller);
        String base = "/api/materials/" + tiles + "/tile-estimate";

        mockMvc.perform(get(base)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.areaSqFt").exists());
        mockMvc.perform(get(base + "?areaSqFt=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "?areaSqFt=abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "?areaSqFt=60&allowancePercent=30"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.allowancePercent").exists());
        mockMvc.perform(get(base + "?areaSqFt=60")) // default allowance 10%, needs 17 but only 5 in stock
                .andExpect(jsonPath("$.allowancePercent").value(10))
                .andExpect(jsonPath("$.requiredTiles").value(17))
                .andExpect(jsonPath("$.enoughStock").value(false));
        mockMvc.perform(get("/api/materials/" + paint + "/tile-estimate?areaSqFt=60"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.category").exists());
    }
}
