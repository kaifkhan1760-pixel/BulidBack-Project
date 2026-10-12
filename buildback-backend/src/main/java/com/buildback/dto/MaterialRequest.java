package com.buildback.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialUnit;
import com.buildback.validation.ImageLink;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body for creating (POST) or editing (PUT) a listing.
 *
 * Only seller-editable fields exist here. There is no id, sellerId, status,
 * createdAt or updatedAt, so a client cannot set them (no "mass assignment").
 * version is only used by PUT, to detect edits based on old data.
 *
 * Rules that involve several fields (unit must match category, tile sizes
 * only for TILES, quantity >= 1 on create) are checked in MaterialService.
 */
public record MaterialRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 120, message = "Title must be 3 to 120 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 2000, message = "Description must be 10 to 2000 characters")
        String description,

        @NotNull(message = "Choose a category")
        MaterialCategory category,

        // @Digits rejects 3+ decimal places instead of silently rounding
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        @DecimalMax(value = "10000000.00", message = "Price must be at most 1,00,00,000")
        @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal unitPrice,

        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative")
        @Max(value = 1_000_000, message = "Quantity must be at most 1,000,000")
        Integer availableQuantity,

        @NotNull(message = "Choose a unit")
        MaterialUnit unit,

        @NotBlank(message = "City is required")
        @Size(min = 2, max = 100, message = "City must be 2 to 100 characters")
        String city,

        /** 1 to 5 photos; the first one is the cover shown on listing cards. */
        @NotNull(message = "Add at least one photo")
        @Size(min = 1, max = 5, message = "Add 1 to 5 photos")
        List<@NotBlank(message = "Photo link is empty")
             @Size(max = 2048, message = "Image link must be at most 2048 characters")
             @ImageLink String> imageUrls,

        @DecimalMin(value = "0.00", inclusive = false, message = "Tile length must be greater than 0")
        @DecimalMax(value = "10.00", message = "Tile length must be at most 10 feet")
        @Digits(integer = 2, fraction = 2, message = "Tile length can have at most 2 decimal places")
        BigDecimal tileLengthFeet,

        @DecimalMin(value = "0.00", inclusive = false, message = "Tile width must be greater than 0")
        @DecimalMax(value = "10.00", message = "Tile width must be at most 10 feet")
        @Digits(integer = 2, fraction = 2, message = "Tile width can have at most 2 decimal places")
        BigDecimal tileWidthFeet,

        Long version) {

    /** Clean up text before validation. */
    public MaterialRequest {
        title = trim(title);
        description = trim(description);
        city = trim(city);
        if (imageUrls != null) {
            // Trim each link and drop exact duplicates, keeping the seller's order
            imageUrls = imageUrls.stream().map(MaterialRequest::trim).filter(Objects::nonNull).distinct().toList();
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
