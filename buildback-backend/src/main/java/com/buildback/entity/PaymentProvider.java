package com.buildback.entity;

/**
 * RAZORPAY  = real gateway in TEST mode (no real money).
 * SIMULATOR = built-in stand-in for demos without gateway keys; clearly labelled in the UI.
 */
public enum PaymentProvider {
    RAZORPAY,
    SIMULATOR
}
