package com.buildback.entity;

/** One payment attempt: started (CREATED), then verified SUCCESS or FAILED. */
public enum PaymentStatus {
    CREATED,
    SUCCESS,
    FAILED
}
