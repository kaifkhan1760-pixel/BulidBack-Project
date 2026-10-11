package com.buildback.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.dto.OrderRequest;
import com.buildback.dto.OrderResponse;
import com.buildback.dto.PageResponse;
import com.buildback.dto.PaymentResponse;
import com.buildback.entity.Material;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.OrderStatus;
import com.buildback.entity.PurchaseOrder;
import com.buildback.exception.ConflictException;
import com.buildback.exception.InvalidRequestException;
import com.buildback.exception.ResourceNotFoundException;
import com.buildback.repository.MaterialRepository;
import com.buildback.repository.PaymentRepository;
import com.buildback.repository.PurchaseOrderRepository;
import com.buildback.repository.UserRepository;

/**
 * Orders and stock reservation.
 *
 * Every change to stock happens inside one database transaction while the
 * material row is locked, so stock is always correct even when many buyers
 * order at the same time. Rows are always locked in the same order
 * (payment -> order -> material) so two transactions cannot deadlock.
 */
@Service
public class OrderService {

    static final String ORDER_NOT_FOUND = "Order not found";

    private final PurchaseOrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final Duration reservationTime;

    public OrderService(PurchaseOrderRepository orderRepository, MaterialRepository materialRepository,
            UserRepository userRepository, PaymentRepository paymentRepository,
            @Value("${app.orders.reservation-minutes:15}") long reservationMinutes) {
        this.orderRepository = orderRepository;
        this.materialRepository = materialRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.reservationTime = Duration.ofMinutes(reservationMinutes);
    }

    // ===================== buyer =====================

    /** Step 2 of the workflow: create a pending order and reserve the stock. */
    @Transactional
    public OrderResponse create(Long buyerId, OrderRequest request) {
        Material material = materialRepository.findByIdForUpdate(request.materialId())
                .filter(m -> m.getStatus() == MaterialStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(MaterialService.NOT_FOUND));

        int available = material.getAvailableQuantity();
        if (request.quantity() > available) {
            throw new InvalidRequestException(Map.of("quantity", available == 0
                    ? "This listing is out of stock"
                    : "Only " + available + " available. Reduce the quantity."));
        }

        material.reserve(request.quantity());
        PurchaseOrder order = new PurchaseOrder(userRepository.getReferenceById(buyerId), material,
                request.quantity(), Instant.now().plus(reservationTime));
        orderRepository.saveAndFlush(order);
        return OrderResponse.forBuyer(order, List.of());
    }

    /** One of the buyer's own orders, with its payment attempts. Expires it first if its time is up. */
    @Transactional
    public OrderResponse getForBuyer(Long buyerId, Long orderId) {
        PurchaseOrder locked = lockOwnedByBuyer(buyerId, orderId);
        expireIfOverdue(locked, Instant.now());
        return buyerView(orderId);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listForBuyer(Long buyerId, String status, String page, String size) {
        Paging p = paging(status, page, size);
        var orders = p.status() == null
                ? orderRepository.findByBuyerId(buyerId, p.pageable())
                : orderRepository.findByBuyerIdAndStatus(buyerId, p.status(), p.pageable());
        return PageResponse.from(orders.map(o -> OrderResponse.forBuyer(o, null)));
    }

    /** Buyer changes their mind before paying: the reserved stock goes back. */
    @Transactional
    public OrderResponse cancel(Long buyerId, Long orderId) {
        PurchaseOrder order = lockOwnedByBuyer(buyerId, orderId);
        if (expireIfOverdue(order, Instant.now())) {
            return buyerView(orderId); // time was already up: it is now EXPIRED, stock released
        }
        if (!order.isPending()) {
            throw new ConflictException("Only unpaid orders can be cancelled.");
        }
        releaseStock(order);
        order.markCancelled();
        orderRepository.flush();
        return buyerView(orderId);
    }

    // ===================== seller =====================

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listForSeller(Long sellerId, String status, String page, String size) {
        Paging p = paging(status, page, size);
        var orders = p.status() == null
                ? orderRepository.findByMaterialSellerId(sellerId, p.pageable())
                : orderRepository.findByMaterialSellerIdAndStatus(sellerId, p.status(), p.pageable());
        return PageResponse.from(orders.map(OrderResponse::forSeller));
    }

    /** Fulfilment: the buyer picked up the material. Only for PAID orders of the seller's own listing. */
    @Transactional
    public OrderResponse markCollected(Long sellerId, Long orderId) {
        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getMaterial().getSeller().getId().equals(sellerId))
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.COLLECTED) {
            return OrderResponse.forSeller(order); // already done: repeating is harmless
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Only paid orders can be marked as collected.");
        }
        order.markCollected(Instant.now());
        orderRepository.flush();
        return OrderResponse.forSeller(order);
    }

    // ===================== expiry =====================

    /**
     * Called by OrderExpiryJob for each overdue order, in its own transaction.
     * Re-checks the status while the row is locked, so stock is released only once
     * even if a buyer cancels or pays at the same moment.
     */
    @Transactional
    public boolean expireIfOverdue(Long orderId, Instant now) {
        return orderRepository.findByIdForUpdate(orderId)
                .map(order -> expireIfOverdue(order, now))
                .orElse(false);
    }

    /** Order must already be locked by the caller. */
    boolean expireIfOverdue(PurchaseOrder lockedOrder, Instant now) {
        if (!lockedOrder.isOverdue(now)) {
            return false;
        }
        releaseStock(lockedOrder);
        lockedOrder.markExpired();
        orderRepository.flush();
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> findOverdueOrderIds(Instant now) {
        return orderRepository.findIdsByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, now);
    }

    // ===================== helpers used by PaymentService =====================

    PurchaseOrder lockOwnedByBuyer(Long buyerId, Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getBuyer().getId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
    }

    /** Payment verified: the reserved stock is now sold. Order must be locked. */
    void completeSale(PurchaseOrder lockedOrder, Instant now) {
        Material material = lockMaterial(lockedOrder);
        material.completeSale(lockedOrder.getQuantity());
        lockedOrder.markPaid(now);
        orderRepository.flush();
    }

    OrderResponse buyerView(Long orderId) {
        PurchaseOrder order = orderRepository.findWithDetailsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
        List<PaymentResponse> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(PaymentResponse::from).toList();
        return OrderResponse.forBuyer(order, payments);
    }

    private void releaseStock(PurchaseOrder lockedOrder) {
        lockMaterial(lockedOrder).releaseReservation(lockedOrder.getQuantity());
    }

    private Material lockMaterial(PurchaseOrder order) {
        return materialRepository.findByIdForUpdate(order.getMaterial().getId())
                .orElseThrow(() -> new IllegalStateException("Material of order " + order.getId() + " is missing"));
    }

    private record Paging(OrderStatus status, Pageable pageable) {
    }

    private static Paging paging(String status, String page, String size) {
        Map<String, String> errors = new LinkedHashMap<>();
        OrderStatus statusFilter = QueryParams.enumValue(OrderStatus.class, status, "status", errors);
        int cleanPage = QueryParams.page(page, errors);
        int cleanSize = QueryParams.size(size, 10, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return new Paging(statusFilter, PageRequest.of(cleanPage, cleanSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }
}
