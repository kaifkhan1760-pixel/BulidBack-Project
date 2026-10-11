package com.buildback.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.MaterialDetailResponse;
import com.buildback.dto.MaterialSummaryResponse;
import com.buildback.dto.PageResponse;
import com.buildback.dto.TileEstimateResponse;
import com.buildback.service.MaterialSearchCriteria;
import com.buildback.service.MaterialService;
import com.buildback.service.TileCalculatorService;

/**
 * Public catalogue: anyone may browse ACTIVE listings (read-only).
 * Example: GET /api/materials?keyword=tile&category=TILES&city=Pune&inStock=true&sort=priceAsc&page=0
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;
    private final TileCalculatorService tileCalculatorService;

    public MaterialController(MaterialService materialService, TileCalculatorService tileCalculatorService) {
        this.materialService = materialService;
        this.tileCalculatorService = tileCalculatorService;
    }

    /** Parameters arrive as text and are checked in MaterialSearchCriteria (400 if invalid). */
    @GetMapping
    public PageResponse<MaterialSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String inStock,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {

        return materialService.search(MaterialSearchCriteria.parse(
                keyword, category, city, minPrice, maxPrice, inStock, page, size, sort));
    }

    /** 404 for archived or missing listings. */
    @GetMapping("/{id}")
    public MaterialDetailResponse detail(@PathVariable Long id) {
        return materialService.getPublic(id);
    }

    /** GET /api/materials/5/tile-estimate?areaSqFt=60&allowancePercent=10 (tile listings only) */
    @GetMapping("/{id}/tile-estimate")
    public TileEstimateResponse tileEstimate(@PathVariable Long id,
            @RequestParam(required = false) String areaSqFt,
            @RequestParam(required = false) String allowancePercent) {
        return tileCalculatorService.estimate(id, areaSqFt, allowancePercent);
    }
}
