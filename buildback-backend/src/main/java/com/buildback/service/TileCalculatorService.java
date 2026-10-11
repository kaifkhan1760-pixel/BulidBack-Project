package com.buildback.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.dto.TileEstimateResponse;
import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.MaterialStatus;
import com.buildback.exception.InvalidRequestException;
import com.buildback.exception.ResourceNotFoundException;
import com.buildback.repository.MaterialRepository;

/**
 * Tile quantity calculator (version 1: rectangular tiles, feet, whole tiles).
 *
 *   tile area      = length x width
 *   area needed    = floor area x (100 + allowance%) / 100
 *   required tiles = round UP (area needed / tile area)
 *
 * Example from the proposal: 60 sq ft, 2 x 2 ft tiles (4 sq ft), 10% ->
 *   60 x 1.10 = 66 sq ft, 66 / 4 = 16.5 -> 17 tiles.
 *
 * BigDecimal keeps every step exact; the allowance covers cutting and breakage.
 * It is a planning aid, not a professional installation specification.
 */
@Service
public class TileCalculatorService {

    public static final int DEFAULT_ALLOWANCE = 10;
    public static final int MAX_ALLOWANCE = 25;
    public static final BigDecimal MAX_AREA = new BigDecimal("100000");

    private final MaterialRepository materialRepository;

    public TileCalculatorService(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Transactional(readOnly = true)
    public TileEstimateResponse estimate(Long materialId, String areaText, String allowanceText) {
        Map<String, String> errors = new LinkedHashMap<>();
        BigDecimal area = parseArea(areaText, errors);
        int allowance = parseAllowance(allowanceText, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        Material material = materialRepository.findById(materialId)
                .filter(m -> m.getStatus() == MaterialStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(MaterialService.NOT_FOUND));
        if (material.getCategory() != MaterialCategory.TILES) {
            throw new InvalidRequestException(Map.of("category", "The calculator only works for tile listings"));
        }

        BigDecimal tileArea = material.getTileLengthFeet().multiply(material.getTileWidthFeet());
        BigDecimal areaWithAllowance = withAllowance(area, allowance);
        long required = requiredTiles(area, allowance, material.getTileLengthFeet(), material.getTileWidthFeet());

        return new TileEstimateResponse(material.getId(), area, allowance,
                material.getTileLengthFeet(), material.getTileWidthFeet(), tidy(tileArea), tidy(areaWithAllowance),
                required, material.getAvailableQuantity(), required <= material.getAvailableQuantity(),
                material.getUnitPrice().multiply(BigDecimal.valueOf(required)));
    }

    /** The formula itself, kept separate so it is easy to test and explain. */
    public static long requiredTiles(BigDecimal floorArea, int allowancePercent, BigDecimal tileLength,
            BigDecimal tileWidth) {
        BigDecimal tileArea = tileLength.multiply(tileWidth);
        return withAllowance(floorArea, allowancePercent)
                .divide(tileArea, 0, RoundingMode.CEILING) // whole tiles: always round up
                .longValueExact();
    }

    /** For display only: 4.0000 -> 4, 66.275 stays 66.275 (exact value, no rounding). */
    private static BigDecimal tidy(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static BigDecimal withAllowance(BigDecimal area, int allowancePercent) {
        return area.multiply(BigDecimal.valueOf(100L + allowancePercent)).divide(BigDecimal.valueOf(100));
    }

    private static BigDecimal parseArea(String text, Map<String, String> errors) {
        String value = QueryParams.text(text);
        if (value == null) {
            errors.put("areaSqFt", "Enter the floor area in square feet");
            return null;
        }
        try {
            BigDecimal area = new BigDecimal(value);
            if (area.signum() <= 0) {
                errors.put("areaSqFt", "Area must be greater than 0");
            } else if (area.scale() > 2) {
                errors.put("areaSqFt", "Area can have at most 2 decimal places");
            } else if (area.compareTo(MAX_AREA) > 0) {
                errors.put("areaSqFt", "Area must be at most 1,00,000 sq ft");
            }
            return area;
        } catch (NumberFormatException e) {
            errors.put("areaSqFt", "Enter a number, e.g. 60 or 72.5");
            return null;
        }
    }

    private static int parseAllowance(String text, Map<String, String> errors) {
        String value = QueryParams.text(text);
        if (value == null) {
            return DEFAULT_ALLOWANCE;
        }
        try {
            int allowance = Integer.parseInt(value);
            if (allowance < 0 || allowance > MAX_ALLOWANCE) {
                errors.put("allowancePercent", "Allowance must be between 0 and " + MAX_ALLOWANCE + "%");
            }
            return allowance;
        } catch (NumberFormatException e) {
            errors.put("allowancePercent", "Allowance must be a whole number");
            return DEFAULT_ALLOWANCE;
        }
    }
}
