package com.buildback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /api/buyer/orders. The buyer is taken from the session and the
 * price from the database, never from this request.
 */
public record OrderRequest(
        @NotNull(message = "Choose a material")
        Long materialId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1_000_000, message = "Quantity is too large")
        Integer quantity) {
}
