package com.buildback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Small request bodies used by the payment endpoints. */
public final class PaymentRequests {

    private PaymentRequests() {
    }

    /** What the gateway checkout gave the browser after a successful payment. */
    public record Verify(
            @NotBlank(message = "Missing gateway order id") @Size(max = 100) String providerOrderId,
            @NotBlank(message = "Missing gateway payment id") @Size(max = 100) String providerPaymentId,
            @NotBlank(message = "Missing payment signature") @Size(max = 200) String signature) {
    }

    /** The checkout reported a failure (declined card, closed popup...). Only recorded; stock stays reserved. */
    public record Failure(@Size(max = 500) String reason) {
    }

    /** Simulator only: which outcome to pretend the bank gave. */
    public record Simulate(
            @NotBlank @Pattern(regexp = "SUCCESS|FAILURE", message = "Outcome must be SUCCESS or FAILURE")
            String outcome) {
    }

    /** Simulator answer: success data in the same shape Razorpay returns, or an error text. */
    public record SimulatedResult(String providerOrderId, String providerPaymentId, String signature,
            String error) {
    }
}
