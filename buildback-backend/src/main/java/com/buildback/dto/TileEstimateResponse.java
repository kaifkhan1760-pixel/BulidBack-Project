package com.buildback.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Result of "does this listing cover my floor?".
 * Example: 60 sq ft, 2 ft x 2 ft tiles, 10% allowance -> 17 tiles.
 */
public record TileEstimateResponse(
        Long materialId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal areaSqFt,
        int allowancePercent,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileLengthFeet,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileWidthFeet,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tileAreaSqFt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal areaWithAllowanceSqFt,
        long requiredTiles,
        int availableQuantity,
        boolean enoughStock,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal estimatedCost) {
}
