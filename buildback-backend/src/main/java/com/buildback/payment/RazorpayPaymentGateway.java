package com.buildback.payment;

import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.buildback.entity.PaymentProvider;

/**
 * Razorpay in TEST mode (key ids start with "rzp_test_", no real money moves).
 *
 * Flow:
 *  1. Server: POST https://api.razorpay.com/v1/orders  (amount in paise)  -> "order_xxx"
 *  2. Browser: Razorpay Checkout popup with that order id; buyer pays with a test card/UPI
 *  3. Browser receives payment id + signature and sends them to our server
 *  4. Server: checks the HMAC signature with the SECRET key, then asks Razorpay
 *     (GET /v1/payments/{id}) that the payment belongs to that order and amount
 *
 * The key secret is only used here on the server; the browser only gets the key id.
 */
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Set<String> OK_STATUSES = Set.of("authorized", "captured");

    private final String keyId;
    private final String keySecret;
    private final RestClient restClient;

    public RazorpayPaymentGateway(String keyId, String keySecret, RestClient.Builder restClientBuilder) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.restClient = restClientBuilder
                .baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
                .build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createCheckout(long amountInPaise, String receipt) {
        try {
            Map<String, Object> order = restClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountInPaise, "currency", "INR", "receipt", receipt))
                    .retrieve()
                    .body(Map.class);
            Object id = order == null ? null : order.get("id");
            if (!(id instanceof String orderId) || orderId.isBlank()) {
                throw new PaymentGatewayException("Razorpay did not return an order id");
            }
            return orderId;
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Could not start the Razorpay checkout", e);
        }
    }

    @Override
    public boolean signatureMatches(String providerOrderId, String providerPaymentId, String signature) {
        return HmacSignatures.matches(keySecret, providerOrderId + "|" + providerPaymentId, signature);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void confirmWithProvider(String providerOrderId, String providerPaymentId, long amountInPaise) {
        Map<String, Object> payment;
        try {
            payment = restClient.get().uri("/payments/{id}", providerPaymentId).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Could not confirm the payment with Razorpay", e);
        }
        if (payment == null
                || !providerOrderId.equals(payment.get("order_id"))
                || !(payment.get("amount") instanceof Number amount) || amount.longValue() != amountInPaise
                || !"INR".equals(payment.get("currency"))
                || !OK_STATUSES.contains(payment.get("status"))) {
            throw new PaymentGatewayException("Razorpay payment details do not match this order");
        }
    }
}
