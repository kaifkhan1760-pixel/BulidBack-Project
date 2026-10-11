package com.buildback.exception;

/** 404. Used for "does not exist", "not yours" and "archived" alike, so nothing leaks. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
