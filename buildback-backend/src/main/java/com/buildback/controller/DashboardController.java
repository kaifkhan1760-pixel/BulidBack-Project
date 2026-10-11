package com.buildback.controller;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.AdminResponses;
import com.buildback.dto.DashboardResponse;
import com.buildback.dto.DashboardResponse.Stat;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.OrderStatus;
import com.buildback.repository.MaterialRepository;
import com.buildback.repository.PurchaseOrderRepository;
import com.buildback.security.AppUserDetails;
import com.buildback.service.AdminService;

/**
 * One endpoint per role. The role check itself is in SecurityConfig
 * (/api/buyer/** needs BUYER, etc.), so a request only reaches a method
 * here if the logged-in user has the right role.
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private static final EnumSet<OrderStatus> SOLD = EnumSet.of(OrderStatus.PAID, OrderStatus.COLLECTED);

    private final PurchaseOrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final AdminService adminService;

    public DashboardController(PurchaseOrderRepository orderRepository, MaterialRepository materialRepository,
            AdminService adminService) {
        this.orderRepository = orderRepository;
        this.materialRepository = materialRepository;
        this.adminService = adminService;
    }

    @GetMapping("/buyer/dashboard")
    @Transactional(readOnly = true)
    public DashboardResponse buyer(@AuthenticationPrincipal AppUserDetails user) {
        Long id = user.getId();
        return build("BUYER", user, List.of(
                new Stat("Awaiting payment", count(orderRepository.countByBuyerIdAndStatus(id, OrderStatus.PENDING_PAYMENT))),
                new Stat("Paid, to collect", count(orderRepository.countByBuyerIdAndStatus(id, OrderStatus.PAID))),
                new Stat("Collected", count(orderRepository.countByBuyerIdAndStatus(id, OrderStatus.COLLECTED))),
                new Stat("Total spent", rupees(orderRepository.sumBuyerTotalByStatusIn(id, SOLD)))));
    }

    @GetMapping("/seller/dashboard")
    @Transactional(readOnly = true)
    public DashboardResponse seller(@AuthenticationPrincipal AppUserDetails user) {
        Long id = user.getId();
        return build("SELLER", user, List.of(
                new Stat("Active listings", count(materialRepository.countBySellerIdAndStatus(id, MaterialStatus.ACTIVE))),
                new Stat("Awaiting payment", count(orderRepository.countByMaterialSellerIdAndStatus(id, OrderStatus.PENDING_PAYMENT))),
                new Stat("Paid, to hand over", count(orderRepository.countByMaterialSellerIdAndStatus(id, OrderStatus.PAID))),
                new Stat("Sales (paid)", rupees(orderRepository.sumSellerTotalByStatusIn(id, SOLD)))));
    }

    @GetMapping("/admin/dashboard")
    @Transactional(readOnly = true)
    public DashboardResponse admin(@AuthenticationPrincipal AppUserDetails user) {
        AdminResponses.Summary s = adminService.summary();
        return build("ADMIN", user, List.of(
                new Stat("Users", count(s.buyers() + s.sellers() + s.admins())),
                new Stat("Active listings", count(s.activeListings())),
                new Stat("Paid orders", count(s.paidOrders() + s.collectedOrders())),
                new Stat("Paid value", rupees(s.paidRevenue()))));
    }

    private DashboardResponse build(String dashboard, AppUserDetails user, List<Stat> stats) {
        String name = user.toResponse().name();
        return new DashboardResponse(dashboard, "Welcome, " + name + "!", user.toResponse(), stats);
    }

    private static String count(long value) {
        return String.valueOf(value);
    }

    /** e.g. 1250.5 -> "₹1,250.50" in Indian number format */
    private static String rupees(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.of("en", "IN")).format(amount);
    }
}
