package com.buildback.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.OrderResponse;
import com.buildback.dto.PaymentRequests;
import com.buildback.security.AppUserDetails;
import com.buildback.service.PaymentService;

import jakarta.validation.Valid;

/** Payment attempts of the logged-in buyer. */
@RestController
@RequestMapping("/api/buyer/payments")
public class BuyerPaymentController {

    private final PaymentService paymentService;

    public BuyerPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** After checkout success: the server verifies the signature before marking the order PAID. */
    @PostMapping("/{id}/verify")
    public OrderResponse verify(@AuthenticationPrincipal AppUserDetails buyer, @PathVariable Long id,
            @Valid @RequestBody PaymentRequests.Verify request) {
        return paymentService.verify(buyer.getId(), id, request);
    }

    @PostMapping("/{id}/failure")
    public OrderResponse failure(@AuthenticationPrincipal AppUserDetails buyer, @PathVariable Long id,
            @Valid @RequestBody(required = false) PaymentRequests.Failure request) {
        return paymentService.reportFailure(buyer.getId(), id, request);
    }

    /** Only when the built-in simulator is the configured gateway (404 otherwise). */
    @PostMapping("/{id}/simulate")
    public PaymentRequests.SimulatedResult simulate(@AuthenticationPrincipal AppUserDetails buyer,
            @PathVariable Long id, @Valid @RequestBody PaymentRequests.Simulate request) {
        return paymentService.simulate(buyer.getId(), id, request.outcome());
    }
}
