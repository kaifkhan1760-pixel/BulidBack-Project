package com.buildback.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialUnit;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Public detail page. Shows the seller's display name only:
 * never their email, id or anything else from the User table.
 */
public record MaterialDetailResponse(
        Long id,
        String title,
        String description,
        MaterialCategory category,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
        MaterialUnit unit,
        int availableQuantity,
        boolean inStock,
        String city,
        String imageUrl,
        List<String> imageUrls,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileLengthFeet,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileWidthFeet,
        String sellerName,
        Instant createdAt) {

    public static MaterialDetailResponse from(Material m) {
        return new MaterialDetailResponse(m.getId(), m.getTitle(), m.getDescription(), m.getCategory(),
                m.getUnitPrice(), m.getUnit(), m.getAvailableQuantity(), m.getAvailableQuantity() > 0, m.getCity(),
                m.getImageUrl(), m.getPhotos(), m.getTileLengthFeet(), m.getTileWidthFeet(), m.getSeller().getName(),
                m.getCreatedAt());
    }
}
