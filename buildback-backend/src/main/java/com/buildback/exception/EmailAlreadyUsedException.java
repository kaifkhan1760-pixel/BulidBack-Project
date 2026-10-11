package com.buildback.exception;

/** Thrown when someone registers with an email that already has an account. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("An account with this email already exists");
    }
}
