package com.buildback.payment;

import java.security.SecureRandom;
import java.util.HexFormat;

import com.buildback.entity.PaymentProvider;

/**
 * Stand-in gateway for classroom demos when no Razorpay test keys are set.
 * Clearly labelled "Test payment simulator" in the UI. No money, no card data.
 *
 * It deliberately behaves like Razorpay: it hands out a payment id plus an
 * HMAC signature made with a secret only the server knows, and the normal
 * verification code checks that signature. So the verify / duplicate /
 * failure logic being demonstrated is the same code used with Razorpay.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** New random secret every time the server starts; never sent to the browser. */
    private final String secret = randomHex(32);

    public record SimulatedPayment(String providerOrderId, String providerPaymentId, String signature) {
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.SIMULATOR;
    }

    @Override
    public String publicKey() {
        return null;
    }

    @Override
    public String createCheckout(long amountInPaise, String receipt) {
        return "sim_order_" + randomHex(10);
    }

    /** Acts as the "gateway" approving the payment. */
    public SimulatedPayment approve(String providerOrderId) {
        String paymentId = "sim_pay_" + randomHex(10);
        return new SimulatedPayment(providerOrderId, paymentId,
                HmacSignatures.sign(secret, providerOrderId + "|" + paymentId));
    }

    @Override
    public boolean signatureMatches(String providerOrderId, String providerPaymentId, String signature) {
        return HmacSignatures.matches(secret, providerOrderId + "|" + providerPaymentId, signature);
    }

    @Override
    public void confirmWithProvider(String providerOrderId, String providerPaymentId, long amountInPaise) {
        // Nothing external to ask: the signature check above is the verification.
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
