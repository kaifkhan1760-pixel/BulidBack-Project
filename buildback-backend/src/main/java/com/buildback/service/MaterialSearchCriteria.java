package com.buildback.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.buildback.entity.MaterialCategory;
import com.buildback.exception.InvalidRequestException;

/**
 * The checked filters for GET /api/materials.
 *
 *  keyword  - case-insensitive text inside the title OR the description (max 100 chars)
 *  category - TILES, PAINT, PLYWOOD or FITTINGS
 *  city     - case-insensitive EXACT city name ("pune" matches "Pune", not "Pune East")
 *  minPrice / maxPrice - INR per unit, inclusive
 *  inStock  - true = only quantity > 0; false or missing = no stock filter
 *  page (from 0), size (1..50, default 9), sort (newest | priceAsc | priceDesc)
 */
public record MaterialSearchCriteria(
        String keyword,
        MaterialCategory category,
        String city,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly,
        int page,
        int size,
        String sort) {

    public static final int DEFAULT_SIZE = 9;
    public static final int MAX_KEYWORD_LENGTH = 100;

    /** Reads raw URL values; throws a 400 listing every invalid value. */
    public static MaterialSearchCriteria parse(String keyword, String category, String city, String minPrice,
            String maxPrice, String inStock, String page, String size, String sort) {

        Map<String, String> errors = new LinkedHashMap<>();

        String cleanKeyword = QueryParams.text(keyword);
        if (cleanKeyword != null && cleanKeyword.length() > MAX_KEYWORD_LENGTH) {
            errors.put("keyword", "Search text must be at most " + MAX_KEYWORD_LENGTH + " characters");
        }

        MaterialCategory cleanCategory = QueryParams.enumValue(MaterialCategory.class, category, "category", errors);
        String cleanCity = QueryParams.text(city);
        if (cleanCity != null && cleanCity.length() > 100) {
            errors.put("city", "City must be at most 100 characters");
        }

        BigDecimal min = QueryParams.price(minPrice, "minPrice", errors);
        BigDecimal max = QueryParams.price(maxPrice, "maxPrice", errors);
        if (min != null && max != null && !errors.containsKey("minPrice") && !errors.containsKey("maxPrice")
                && min.compareTo(max) > 0) {
            errors.put("minPrice", "Minimum price cannot be more than maximum price");
        }

        Boolean stock = QueryParams.bool(inStock, "inStock", errors);
        int cleanPage = QueryParams.page(page, errors);
        int cleanSize = QueryParams.size(size, DEFAULT_SIZE, errors);

        String cleanSort = QueryParams.text(sort) == null ? "newest" : QueryParams.text(sort);
        if (sortOrder(cleanSort) == null) {
            errors.put("sort", "Sort must be newest, priceAsc or priceDesc");
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return new MaterialSearchCriteria(cleanKeyword, cleanCategory, cleanCity, min, max,
                Boolean.TRUE.equals(stock), cleanPage, cleanSize, cleanSort);
    }

    public Pageable pageable() {
        return PageRequest.of(page, size, sortOrder(sort));
    }

    /**
     * Only these three sort options exist (an "allowlist"), so users cannot sort
     * by internal columns. The id is always the last tie-breaker, so two items
     * with the same price always come back in the same order.
     */
    static Sort sortOrder(String sort) {
        return switch (sort) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "priceAsc" -> Sort.by(Sort.Order.asc("unitPrice"), Sort.Order.asc("id"));
            case "priceDesc" -> Sort.by(Sort.Order.desc("unitPrice"), Sort.Order.desc("id"));
            default -> null;
        };
    }
}
