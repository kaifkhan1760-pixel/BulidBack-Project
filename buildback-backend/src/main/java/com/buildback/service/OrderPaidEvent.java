package com.buildback.service;

/** Published when an order becomes PAID. Emails are sent only after the payment is saved for good. */
public record OrderPaidEvent(Long orderId, Long paymentId) {
}
