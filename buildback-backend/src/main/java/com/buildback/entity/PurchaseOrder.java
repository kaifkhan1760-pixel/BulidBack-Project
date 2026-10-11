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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One purchase of ONE listing by one buyer (version 1: no multi-seller cart).
 * Named PurchaseOrder because "Order" is also a common Java/SQL word.
 *
 * The unit price is copied ("snapshot") when the order is created, so a later
 * price change by the seller never changes what the buyer agreed to pay.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_buyer_created", columnList = "buyer_id, created_at"),
        @Index(name = "idx_orders_material", columnList = "material_id"),
        @Index(name = "idx_orders_status_expires", columnList = "status, expires_at")
})
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_buyer"))
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_material"))
    private Material material;

    @Column(nullable = false)
    private Integer quantity;

    /** Price per unit at the moment of ordering (INR). */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** quantity x unitPrice, calculated on the server (INR). */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Unpaid orders release their reserved stock after this moment. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(User buyer, Material material, int quantity, Instant expiresAt) {
        this.buyer = buyer;
        this.material = material;
        this.quantity = quantity;
        this.unitPrice = material.getUnitPrice();
        this.totalAmount = material.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        this.status = OrderStatus.PENDING_PAYMENT;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isOverdue(Instant now) {
        return isPending() && now.isAfter(expiresAt);
    }

    public void markPaid(Instant now) {
        this.status = OrderStatus.PAID;
        this.paidAt = now;
    }

    public void markCollected(Instant now) {
        this.status = OrderStatus.COLLECTED;
        this.collectedAt = now;
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
    }

    public void markExpired() {
        this.status = OrderStatus.EXPIRED;
    }

    /** Amount in paise (1 rupee = 100 paise), as payment gateways expect. */
    public long totalInPaise() {
        return totalAmount.movePointRight(2).longValueExact();
    }

    public Long getId() {
        return id;
    }

    public User getBuyer() {
        return buyer;
    }

    public Material getMaterial() {
        return material;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public Long getVersion() {
        return version;
    }
}
