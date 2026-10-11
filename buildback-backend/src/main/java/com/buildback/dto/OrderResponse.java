package com.buildback.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialUnit;
import com.buildback.entity.OrderStatus;
import com.buildback.entity.PurchaseOrder;
import com.buildback.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * An order as shown to the buyer, the seller or the admin.
 *
 * Privacy rule: buyer and seller only see each other's EMAIL after the order
 * is paid, so they can arrange collection. Before that, names only.
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        int quantity,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        Instant collectedAt,
        MaterialSummary material,
        String sellerName,
        String sellerEmail,
        String buyerName,
        String buyerEmail,
        List<PaymentResponse> payments) {

    public record MaterialSummary(Long id, String title, MaterialCategory category, MaterialUnit unit, String city) {
    }

    private static boolean contactShared(PurchaseOrder o) {
        return o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.COLLECTED;
    }

    private static OrderResponse build(PurchaseOrder o, boolean showSellerEmail, boolean showBuyerEmail,
            List<PaymentResponse> payments) {
        User seller = o.getMaterial().getSeller();
        User buyer = o.getBuyer();
        return new OrderResponse(o.getId(), o.getStatus(), o.getQuantity(), o.getUnitPrice(), o.getTotalAmount(),
                "INR", o.getCreatedAt(), o.getExpiresAt(), o.getPaidAt(), o.getCollectedAt(),
                new MaterialSummary(o.getMaterial().getId(), o.getMaterial().getTitle(),
                        o.getMaterial().getCategory(), o.getMaterial().getUnit(), o.getMaterial().getCity()),
                seller.getName(), showSellerEmail ? seller.getEmail() : null,
                buyer.getName(), showBuyerEmail ? buyer.getEmail() : null,
                payments);
    }

    public static OrderResponse forBuyer(PurchaseOrder o, List<PaymentResponse> payments) {
        return build(o, contactShared(o), false, payments);
    }

    public static OrderResponse forSeller(PurchaseOrder o) {
        return build(o, false, contactShared(o), null);
    }

    public static OrderResponse forAdmin(PurchaseOrder o) {
        return build(o, true, true, null);
    }
}
