package com.buildback.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.dto.AdminResponses;
import com.buildback.dto.OrderResponse;
import com.buildback.dto.PageResponse;
import com.buildback.entity.Material;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.OrderStatus;
import com.buildback.entity.Role;
import com.buildback.entity.User;
import com.buildback.exception.InvalidRequestException;
import com.buildback.exception.ResourceNotFoundException;
import com.buildback.repository.MaterialRepository;
import com.buildback.repository.PurchaseOrderRepository;
import com.buildback.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;

/** Admin: review users, moderate listings, review orders and totals. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final PurchaseOrderRepository orderRepository;

    public AdminService(UserRepository userRepository, MaterialRepository materialRepository,
            PurchaseOrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public AdminResponses.Summary summary() {
        return new AdminResponses.Summary(
                userRepository.countByRole(Role.BUYER), userRepository.countByRole(Role.SELLER),
                userRepository.countByRole(Role.ADMIN),
                materialRepository.countByStatus(MaterialStatus.ACTIVE),
                materialRepository.countByStatus(MaterialStatus.ARCHIVED),
                orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT),
                orderRepository.countByStatus(OrderStatus.PAID),
                orderRepository.countByStatus(OrderStatus.COLLECTED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.countByStatus(OrderStatus.EXPIRED),
                orderRepository.sumTotalByStatusIn(EnumSet.of(OrderStatus.PAID, OrderStatus.COLLECTED)));
    }

    /** Users, optionally filtered by role and by text in name or email. */
    @Transactional(readOnly = true)
    public PageResponse<AdminResponses.UserRow> users(String keyword, String role, String page, String size) {
        Map<String, String> errors = new LinkedHashMap<>();
        String text = QueryParams.text(keyword);
        Role roleFilter = QueryParams.enumValue(Role.class, role, "role", errors);
        PageRequest pageable = pageRequest(page, size, errors);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (roleFilter != null) {
                where.add(cb.equal(root.get("role"), roleFilter));
            }
            if (text != null) {
                String pattern = "%" + MaterialSpecifications.escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '!'),
                        cb.like(cb.lower(root.get("email")), pattern, '!')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.from(userRepository.findAll(spec, pageable).map(AdminResponses.UserRow::from));
    }

    /** All listings (active and archived) with their seller. */
    @Transactional(readOnly = true)
    public PageResponse<AdminResponses.MaterialRow> materials(String keyword, String status, String page,
            String size) {
        Map<String, String> errors = new LinkedHashMap<>();
        String text = QueryParams.text(keyword);
        MaterialStatus statusFilter = QueryParams.enumValue(MaterialStatus.class, status, "status", errors);
        PageRequest pageable = pageRequest(page, size, errors);

        Specification<Material> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (statusFilter != null) {
                where.add(cb.equal(root.get("status"), statusFilter));
            }
            if (text != null) {
                String pattern = "%" + MaterialSpecifications.escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.like(cb.lower(root.get("title")), pattern, '!'));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.from(materialRepository.findAll(spec, pageable).map(AdminResponses.MaterialRow::from));
    }

    /** Moderation: hide a listing from buyers. Its order history stays intact. */
    @Transactional
    public AdminResponses.MaterialRow archiveMaterial(Long id) {
        Material material = materialRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(MaterialService.NOT_FOUND));
        material.archive();
        materialRepository.flush();
        return AdminResponses.MaterialRow.from(material);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> orders(String status, String page, String size) {
        Map<String, String> errors = new LinkedHashMap<>();
        OrderStatus statusFilter = QueryParams.enumValue(OrderStatus.class, status, "status", errors);
        PageRequest pageable = pageRequest(page, size, errors);
        var orders = statusFilter == null
                ? orderRepository.findAllBy(pageable)
                : orderRepository.findByStatus(statusFilter, pageable);
        return PageResponse.from(orders.map(OrderResponse::forAdmin));
    }

    private static PageRequest pageRequest(String page, String size, Map<String, String> errors) {
        int cleanPage = QueryParams.page(page, errors);
        int cleanSize = QueryParams.size(size, 10, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return PageRequest.of(cleanPage, cleanSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
