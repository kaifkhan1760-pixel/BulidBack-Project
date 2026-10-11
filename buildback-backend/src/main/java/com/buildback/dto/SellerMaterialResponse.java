package com.buildback.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialStatus;
import com.buildback.entity.MaterialUnit;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * What a seller sees about their OWN listing, including status and version.
 * The version must be sent back when editing or archiving.
 */
public record SellerMaterialResponse(
        Long id,
        String title,
        String description,
        MaterialCategory category,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
        MaterialUnit unit,
        int availableQuantity,
        int reservedQuantity,
        String city,
        String imageUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileLengthFeet,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileWidthFeet,
        MaterialStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static SellerMaterialResponse from(Material m) {
        return new SellerMaterialResponse(m.getId(), m.getTitle(), m.getDescription(), m.getCategory(),
                m.getUnitPrice(), m.getUnit(), m.getAvailableQuantity(), m.getReservedQuantity(), m.getCity(),
                m.getImageUrl(),
                m.getTileLengthFeet(), m.getTileWidthFeet(), m.getStatus(), m.getVersion(), m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
