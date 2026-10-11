package com.buildback.dto;

import java.math.BigDecimal;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialUnit;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One card in the public catalogue. No seller data at all, so listing pages
 * never need to load User rows (no extra query per card).
 * Money is sent as a string ("1250.50") so no decimal digits are lost.
 */
public record MaterialSummaryResponse(
        Long id,
        String title,
        MaterialCategory category,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
        MaterialUnit unit,
        int availableQuantity,
        boolean inStock,
        String city,
        String imageUrl) {

    public static MaterialSummaryResponse from(Material m) {
        return new MaterialSummaryResponse(m.getId(), m.getTitle(), m.getCategory(), m.getUnitPrice(), m.getUnit(),
                m.getAvailableQuantity(), m.getAvailableQuantity() > 0, m.getCity(), m.getImageUrl());
    }
}
