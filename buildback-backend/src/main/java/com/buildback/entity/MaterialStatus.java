package com.buildback.entity;

/**
 * ACTIVE = visible in the public catalogue.
 * ARCHIVED = "deleted" by the seller: hidden from buyers but kept for history.
 * Only the backend changes this value.
 */
public enum MaterialStatus {
    ACTIVE,
    ARCHIVED
}
