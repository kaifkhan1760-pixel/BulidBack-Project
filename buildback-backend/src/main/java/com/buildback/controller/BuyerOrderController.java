package com.buildback.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.CheckoutResponse;
import com.buildback.dto.OrderRequest;
import com.buildback.dto.OrderResponse;
import com.buildback.dto.PageResponse;
import com.buildback.security.AppUserDetails;
import com.buildback.service.OrderService;
import com.buildback.service.PaymentService;

import jakarta.validation.Valid;

/** Buyer orders (role BUYER only, see SecurityConfig). The buyer always comes from the session. */
@RestController
@RequestMapping("/api/buyer/orders")
public class BuyerOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public BuyerOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    /** Reserve stock: POST {"materialId": 5, "quantity": 17} -> 201 PENDING_PAYMENT order */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal AppUserDetails buyer,
            @Valid @RequestBody OrderRequest request) {
        OrderResponse order = orderService.create(buyer.getId(), request);
        return ResponseEntity.created(URI.create("/api/buyer/orders/" + order.id())).body(order);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal AppUserDetails buyer,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return orderService.listForBuyer(buyer.getId(), status, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AppUserDetails buyer, @PathVariable Long id) {
        return orderService.getForBuyer(buyer.getId(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AppUserDetails buyer, @PathVariable Long id) {
        return orderService.cancel(buyer.getId(), id);
    }

    /** Starts a payment attempt and returns what the checkout popup needs. */
    @PostMapping("/{id}/payments")
    public ResponseEntity<CheckoutResponse> startPayment(@AuthenticationPrincipal AppUserDetails buyer,
            @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.start(buyer.getId(), id));
    }
}
