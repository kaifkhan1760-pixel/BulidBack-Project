package com.buildback.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every minute: unpaid orders whose reservation time is over become
 * EXPIRED and their stock goes back on sale. Each order is handled in its own
 * transaction, so one problem cannot block the others.
 */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderService orderService;

    public OrderExpiryJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "${app.orders.expiry-check-ms:60000}",
            fixedDelayString = "${app.orders.expiry-check-ms:60000}")
    public void expireOverdueOrders() {
        Instant now = Instant.now();
        int expired = 0;
        for (Long orderId : orderService.findOverdueOrderIds(now)) {
            if (orderService.expireIfOverdue(orderId, now)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid order(s) and released their stock", expired);
        }
    }
}
