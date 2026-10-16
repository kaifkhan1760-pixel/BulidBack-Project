package com.buildback.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Every error response has the same shape, e.g.
 * {"message":"Validation failed","errors":{"email":"Enter a valid email address"}}
 * code is only present when React must react to a specific case, e.g. "EMAIL_NOT_VERIFIED".
 */
public record ApiError(String message, Map<String, String> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code) {

    public ApiError(String message, Map<String, String> errors) {
        this(message, errors, null);
    }

    public static ApiError of(String message) {
        return new ApiError(message, Map.of());
    }

    public static ApiError withCode(String message, String code) {
        return new ApiError(message, Map.of(), code);
    }
}
