package com.buildback.exception;

import java.util.Map;

/** 400 Bad Request with one message per field, e.g. {"unit": "Paint is sold per CAN"}. */
public class InvalidRequestException extends RuntimeException {

    private final Map<String, String> errors;

    public InvalidRequestException(Map<String, String> errors) {
        super("Please correct the highlighted fields");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
