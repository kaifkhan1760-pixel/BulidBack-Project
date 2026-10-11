package com.buildback.dto;

import java.time.Instant;

import com.buildback.entity.PaymentProvider;

/**
 * Everything the browser needs to open the checkout. publicKey is Razorpay's
 * PUBLIC key id; the secret key never leaves the server.
 */
public record CheckoutResponse(
        Long paymentId,
        Long orderId,
        PaymentProvider provider,
        String providerOrderId,
        long amountInPaise,
        String currency,
        String publicKey,
        String description,
        String buyerName,
        String buyerEmail,
        Instant orderExpiresAt) {
}
