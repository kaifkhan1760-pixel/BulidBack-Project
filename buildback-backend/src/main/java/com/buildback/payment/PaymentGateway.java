package com.buildback.payment;

import com.buildback.entity.PaymentProvider;

/**
 * What BuildBack needs from any payment gateway. Two implementations:
 * RazorpayPaymentGateway (real gateway, TEST mode) and SimulatedPaymentGateway.
 * Which one is used is chosen in PaymentConfig from application.properties.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Public key the browser needs to open the checkout (never the secret). null if not needed. */
    String publicKey();

    /** Registers the amount with the gateway and returns its checkout/order id. */
    String createCheckout(long amountInPaise, String receipt);

    /**
     * Checks the signature the gateway returned to the browser:
     * HMAC-SHA256(providerOrderId + "|" + providerPaymentId, secret).
     * Only the gateway and our server know the secret, so a browser cannot fake it.
     */
    boolean signatureMatches(String providerOrderId, String providerPaymentId, String signature);

    /** Extra server-to-gateway check of the payment's order, amount and status. Throws if it does not match. */
    void confirmWithProvider(String providerOrderId, String providerPaymentId, long amountInPaise);

    /**
     * Gives the money back for a completed payment (the buyer cancelled a paid order).
     * Returns the gateway's refund id. Throws PaymentGatewayException if the refund is refused.
     */
    String refund(String providerPaymentId, long amountInPaise);
}
