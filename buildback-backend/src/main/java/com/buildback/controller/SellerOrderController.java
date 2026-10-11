package com.buildback.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.OrderResponse;
import com.buildback.dto.PageResponse;
import com.buildback.security.AppUserDetails;
import com.buildback.service.OrderService;

/** Orders placed on the logged-in seller's listings. */
@RestController
@RequestMapping("/api/seller/orders")
public class SellerOrderController {

    private final OrderService orderService;

    public SellerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal AppUserDetails seller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return orderService.listForSeller(seller.getId(), status, page, size);
    }

    /** Fulfilment update: PAID -> COLLECTED */
    @PostMapping("/{id}/collect")
    public OrderResponse collect(@AuthenticationPrincipal AppUserDetails seller, @PathVariable Long id) {
        return orderService.markCollected(seller.getId(), id);
    }
}
