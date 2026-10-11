package com.buildback.entity;

/**
 * Life of an order:
 *
 *   PENDING_PAYMENT --paid--> PAID --seller marks collected--> COLLECTED
 *         |
 *         +--buyer cancels--> CANCELLED   (reserved stock goes back)
 *         +--time runs out--> EXPIRED     (reserved stock goes back)
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    COLLECTED,
    CANCELLED,
    EXPIRED
}
