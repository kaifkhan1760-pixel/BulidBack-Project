package com.buildback.exception;

/** 409. The request is valid, but the record changed or is in a state that does not allow it. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
