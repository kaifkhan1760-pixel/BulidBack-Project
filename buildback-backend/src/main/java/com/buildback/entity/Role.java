package com.buildback.entity;

/**
 * The three kinds of BuildBack users.
 * Stored in MySQL as text ("BUYER", "SELLER", "ADMIN"), never as a number.
 */
public enum Role {
    BUYER,
    SELLER,
    ADMIN
}
