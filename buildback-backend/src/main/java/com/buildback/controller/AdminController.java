package com.buildback.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.AdminResponses;
import com.buildback.dto.OrderResponse;
import com.buildback.dto.PageResponse;
import com.buildback.service.AdminService;

/** Admin-only endpoints (/api/admin/** needs role ADMIN in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/summary")
    public AdminResponses.Summary summary() {
        return adminService.summary();
    }

    @GetMapping("/users")
    public PageResponse<AdminResponses.UserRow> users(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role, @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return adminService.users(keyword, role, page, size);
    }

    @GetMapping("/materials")
    public PageResponse<AdminResponses.MaterialRow> materials(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return adminService.materials(keyword, status, page, size);
    }

    @PostMapping("/materials/{id}/archive")
    public AdminResponses.MaterialRow archiveMaterial(@PathVariable Long id) {
        return adminService.archiveMaterial(id);
    }

    @GetMapping("/orders")
    public PageResponse<OrderResponse> orders(@RequestParam(required = false) String status,
            @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return adminService.orders(status, page, size);
    }
}
