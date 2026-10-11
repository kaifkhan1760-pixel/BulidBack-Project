package com.buildback.dto;

import java.util.Map;

/**
 * Every error response has the same shape, e.g.
 * {"message":"Validation failed","errors":{"email":"Enter a valid email address"}}
 */
public record ApiError(String message, Map<String, String> errors) {

    public static ApiError of(String message) {
        return new ApiError(message, Map.of());
    }
}
