package com.buildback.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.MaterialRequest;
import com.buildback.dto.PageResponse;
import com.buildback.dto.SellerMaterialResponse;
import com.buildback.security.AppUserDetails;
import com.buildback.service.MaterialService;

import jakarta.validation.Valid;

/**
 * A seller's own inventory (CRUD).
 * SecurityConfig only lets users with role SELLER reach /api/seller/**.
 * The seller is always taken from the session (@AuthenticationPrincipal),
 * never from the request, so nobody can act as another seller.
 */
@RestController
@RequestMapping("/api/seller/materials")
public class SellerMaterialController {

    private final MaterialService materialService;

    public SellerMaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /** GET /api/seller/materials?status=ACTIVE&page=0&size=10 */
    @GetMapping
    public PageResponse<SellerMaterialResponse> list(@AuthenticationPrincipal AppUserDetails seller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return materialService.listOwn(seller.getId(), status, page, size);
    }

    @GetMapping("/{id}")
    public SellerMaterialResponse get(@AuthenticationPrincipal AppUserDetails seller, @PathVariable Long id) {
        return materialService.getOwn(seller.getId(), id);
    }

    @PostMapping
    public ResponseEntity<SellerMaterialResponse> create(@AuthenticationPrincipal AppUserDetails seller,
            @Valid @RequestBody MaterialRequest request) {
        SellerMaterialResponse created = materialService.create(seller.getId(), request);
        return ResponseEntity.created(URI.create("/api/seller/materials/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public SellerMaterialResponse update(@AuthenticationPrincipal AppUserDetails seller, @PathVariable Long id,
            @Valid @RequestBody MaterialRequest request) {
        return materialService.update(seller.getId(), id, request);
    }

    /** Archives (soft delete). DELETE /api/seller/materials/5?version=3 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal AppUserDetails seller, @PathVariable Long id,
            @RequestParam Long version) {
        materialService.archive(seller.getId(), id, version);
        return ResponseEntity.noContent().build();
    }
}
