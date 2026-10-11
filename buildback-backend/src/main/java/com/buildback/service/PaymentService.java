package com.buildback.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.dto.CheckoutResponse;
import com.buildback.dto.OrderResponse;
import com.buildback.dto.PaymentRequests;
import com.buildback.entity.Payment;
import com.buildback.entity.PaymentStatus;
import com.buildback.entity.PurchaseOrder;
import com.buildback.entity.User;
import com.buildback.exception.ConflictException;
import com.buildback.exception.InvalidRequestException;
import com.buildback.exception.ResourceNotFoundException;
import com.buildback.payment.PaymentGateway;
import com.buildback.payment.SimulatedPaymentGateway;
import com.buildback.repository.PaymentRepository;

/**
 * Step 3 of the workflow: pay, and VERIFY on the server.
 *
 * The browser is never trusted to say "it's paid". An order only becomes PAID
 * after the server checks the gateway signature (and, for Razorpay, asks the
 * gateway directly). Confirming the same payment twice changes nothing the
 * second time, so stock can never be reduced twice.
 */
@Service
public class PaymentService {

    static final String PAYMENT_NOT_FOUND = "Payment not found";

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService, PaymentGateway gateway) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.gateway = gateway;
    }

    /**
     * Starts a payment attempt for a pending order.
     * noRollbackFor: if the order turns out to be expired, that expiry (and the
     * released stock) is still saved even though we answer 409.
     */
    @Transactional(noRollbackFor = ConflictException.class)
    public CheckoutResponse start(Long buyerId, Long orderId) {
        PurchaseOrder order = orderService.lockOwnedByBuyer(buyerId, orderId);
        if (orderService.expireIfOverdue(order, Instant.now())) {
            throw new ConflictException("This order expired and its stock was released. Please order again.");
        }
        if (!order.isPending()) {
            throw new ConflictException("This order is not waiting for payment.");
        }

        String providerOrderId = gateway.createCheckout(order.totalInPaise(), "buildback_order_" + order.getId());
        Payment payment = paymentRepository.save(new Payment(order, gateway.provider(), providerOrderId));

        User buyer = order.getBuyer();
        return new CheckoutResponse(payment.getId(), order.getId(), gateway.provider(), providerOrderId,
                order.totalInPaise(), "INR", gateway.publicKey(), "BuildBack order #" + order.getId(),
                buyer.getName(), buyer.getEmail(), order.getExpiresAt());
    }

    /** The browser reports success; the server decides whether it is real. */
    @Transactional
    public OrderResponse verify(Long buyerId, Long paymentId, PaymentRequests.Verify request) {
        Payment payment = lockOwnedPayment(buyerId, paymentId);
        Long orderId = payment.getOrder().getId();

        // Duplicate confirmation (double click, page refresh, retried request): nothing changes
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (request.providerPaymentId().equals(payment.getProviderPaymentId())) {
                return orderService.buyerView(orderId);
            }
            throw new ConflictException("This payment attempt was already completed.");
        }

        if (!payment.getProviderOrderId().equals(request.providerOrderId())
                || !gateway.signatureMatches(request.providerOrderId(), request.providerPaymentId(),
                        request.signature())) {
            // Possibly forged: refuse, and leave the attempt and the order unchanged
            throw new InvalidRequestException(Map.of("signature", "Payment could not be verified."));
        }
        if (paymentRepository.existsByProviderPaymentId(request.providerPaymentId())) {
            throw new ConflictException("This gateway payment was already used for another attempt.");
        }
        gateway.confirmWithProvider(request.providerOrderId(), request.providerPaymentId(),
                payment.getOrder().totalInPaise());

        Instant now = Instant.now();
        PurchaseOrder order = orderService.lockOwnedByBuyer(buyerId, orderId);
        orderService.expireIfOverdue(order, now);

        if (order.isPending()) {
            orderService.completeSale(order, now); // reserved stock is now sold
            payment.markSucceeded(request.providerPaymentId(), now, false);
        } else {
            // Money arrived but the order had already expired or been cancelled:
            // record it and flag it for a manual refund instead of losing track of it.
            payment.markSucceeded(request.providerPaymentId(), now, true);
        }
        paymentRepository.flush();
        return orderService.buyerView(orderId);
    }

    /** The checkout failed. The attempt is recorded; the order stays pending so the buyer can retry. */
    @Transactional
    public OrderResponse reportFailure(Long buyerId, Long paymentId, PaymentRequests.Failure request) {
        Payment payment = lockOwnedPayment(buyerId, paymentId);
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new ConflictException("This payment was already completed.");
        }
        if (payment.getStatus() == PaymentStatus.CREATED) {
            String reason = request == null || request.reason() == null || request.reason().isBlank()
                    ? "Payment was not completed"
                    : request.reason().trim();
            payment.markFailed(reason);
            paymentRepository.flush();
        }
        return orderService.buyerView(payment.getOrder().getId());
    }

    /** Simulator only: plays the role of the bank/gateway. */
    @Transactional(readOnly = true)
    public PaymentRequests.SimulatedResult simulate(Long buyerId, Long paymentId, String outcome) {
        if (!(gateway instanceof SimulatedPaymentGateway simulator)) {
            throw new ResourceNotFoundException("The payment simulator is not enabled.");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getOrder().getBuyer().getId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new ConflictException("This payment attempt is already finished.");
        }
        if ("FAILURE".equals(outcome)) {
            return new PaymentRequests.SimulatedResult(null, null, null, "Simulated: card declined by the test bank");
        }
        SimulatedPaymentGateway.SimulatedPayment approved = simulator.approve(payment.getProviderOrderId());
        return new PaymentRequests.SimulatedResult(approved.providerOrderId(), approved.providerPaymentId(),
                approved.signature(), null);
    }

    private Payment lockOwnedPayment(Long buyerId, Long paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .filter(p -> p.getOrder().getBuyer().getId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(PAYMENT_NOT_FOUND));
    }
}
