package com.buildback.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One payment ATTEMPT for an order. An order can have several attempts
 * (e.g. a declined card, then a successful retry).
 *
 * providerPaymentId is UNIQUE, so the same gateway payment can never be
 * counted twice, even if the confirmation arrives more than once.
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_provider_order", columnNames = "provider_order_id"),
        @UniqueConstraint(name = "uk_payments_provider_payment", columnNames = "provider_payment_id")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payments_order"))
    private PurchaseOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    /** The gateway's id for this checkout (Razorpay "order_..."). */
    @Column(name = "provider_order_id", nullable = false, length = 100)
    private String providerOrderId;

    /** The gateway's id for the actual payment (Razorpay "pay_..."), known after checkout. */
    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** True if money arrived after the order had already expired or was cancelled. */
    @Column(name = "refund_required", nullable = false)
    private boolean refundRequired;

    /** Set when the money was given back (the buyer cancelled the paid order). */
    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected Payment() {
    }

    public Payment(PurchaseOrder order, PaymentProvider provider, String providerOrderId) {
        this.order = order;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.amount = order.getTotalAmount();
        this.currency = "INR";
        this.status = PaymentStatus.CREATED;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markSucceeded(String providerPaymentId, Instant now, boolean refundRequired) {
        this.providerPaymentId = providerPaymentId;
        this.status = PaymentStatus.SUCCESS;
        this.verifiedAt = now;
        this.refundRequired = refundRequired;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrder getOrder() {
        return order;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean isRefundRequired() {
        return refundRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void markRefunded(String refundId, Instant now) {
        this.refundId = refundId;
        this.refundedAt = now;
        this.refundRequired = false;
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }

    public String getRefundId() {
        return refundId;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
