package com.buildback.service;

/** Published when a buyer cancels a PAID order and the payment is refunded. */
public record OrderCancelledEvent(Long orderId, Long paymentId) {
}
