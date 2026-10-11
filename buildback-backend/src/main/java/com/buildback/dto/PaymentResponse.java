package com.buildback.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.buildback.entity.Payment;
import com.buildback.entity.PaymentProvider;
import com.buildback.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

/** One payment attempt as shown to the buyer (no secrets, no signatures). */
public record PaymentResponse(
        Long id,
        PaymentProvider provider,
        PaymentStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String providerPaymentId,
        String failureReason,
        boolean refundRequired,
        Instant createdAt,
        Instant verifiedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getProvider(), p.getStatus(), p.getAmount(),
                p.getProviderPaymentId(), p.getFailureReason(), p.isRefundRequired(), p.getCreatedAt(),
                p.getVerifiedAt());
    }
}
