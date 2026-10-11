package com.buildback.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Turns raw text from the URL (?page=2&size=9&category=TILES) into checked
 * values. Each bad value adds a readable message to "errors" instead of
 * crashing, so the client gets one 400 response listing every problem.
 */
public final class QueryParams {

    public static final int MAX_PAGE = 100_000;
    public static final int MAX_PAGE_SIZE = 50;
    public static final BigDecimal MAX_PRICE = new BigDecimal("10000000.00");

    private QueryParams() {
    }

    /** null for missing or blank values, otherwise trimmed text. */
    public static String text(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    public static int page(String raw, Map<String, String> errors) {
        String value = text(raw);
        if (value == null) {
            return 0;
        }
        try {
            int page = Integer.parseInt(value);
            if (page < 0) {
                errors.put("page", "Page cannot be negative");
            } else if (page > MAX_PAGE) {
                errors.put("page", "Page is too large");
            }
            return page;
        } catch (NumberFormatException e) {
            errors.put("page", "Page must be a whole number");
            return 0;
        }
    }

    public static int size(String raw, int defaultSize, Map<String, String> errors) {
        String value = text(raw);
        if (value == null) {
            return defaultSize;
        }
        try {
            int size = Integer.parseInt(value);
            if (size < 1 || size > MAX_PAGE_SIZE) {
                errors.put("size", "Size must be between 1 and " + MAX_PAGE_SIZE);
            }
            return size;
        } catch (NumberFormatException e) {
            errors.put("size", "Size must be a whole number");
            return defaultSize;
        }
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field, Map<String, String> errors) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.put(field, "Unknown " + field + " '" + value + "'");
            return null;
        }
    }

    /** A price filter: 0 or more, at most 2 decimal places, within the supported maximum. */
    public static BigDecimal price(String raw, String field, Map<String, String> errors) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.signum() < 0) {
                errors.put(field, "Price cannot be negative");
            } else if (price.scale() > 2) {
                errors.put(field, "Price can have at most 2 decimal places");
            } else if (price.compareTo(MAX_PRICE) > 0) {
                errors.put(field, "Price is too large");
            }
            return price;
        } catch (NumberFormatException e) {
            errors.put(field, "Enter a valid price");
            return null;
        }
    }

    /** Only the exact words true/false are accepted. */
    public static Boolean bool(String raw, String field, Map<String, String> errors) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        errors.put(field, field + " must be true or false");
        return null;
    }
}
