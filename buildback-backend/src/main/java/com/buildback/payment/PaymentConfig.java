package com.buildback.payment;

import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Chooses the payment gateway from configuration:
 *   PAYMENT_PROVIDER=razorpay + RAZORPAY_KEY_ID + RAZORPAY_KEY_SECRET  -> Razorpay test mode
 *   PAYMENT_PROVIDER=simulator (default)                               -> built-in simulator
 * Live keys are refused on purpose: this academic prototype only uses test mode.
 */
@Configuration
public class PaymentConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    public PaymentGateway paymentGateway(
            @Value("${app.payments.provider:simulator}") String provider,
            @Value("${app.payments.razorpay.key-id:}") String keyId,
            @Value("${app.payments.razorpay.key-secret:}") String keySecret) {

        switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "razorpay" -> {
                if (keyId.isBlank() || keySecret.isBlank()) {
                    throw new IllegalStateException(
                            "PAYMENT_PROVIDER=razorpay needs RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
                }
                if (!keyId.startsWith("rzp_test_")) {
                    throw new IllegalStateException("Only Razorpay TEST keys (rzp_test_...) are allowed");
                }
                log.info("Payments: Razorpay TEST mode");
                SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
                timeouts.setConnectTimeout(Duration.ofSeconds(5));
                timeouts.setReadTimeout(Duration.ofSeconds(15));
                return new RazorpayPaymentGateway(keyId, keySecret, RestClient.builder().requestFactory(timeouts));
            }
            case "simulator" -> {
                log.info("Payments: built-in TEST SIMULATOR (no real gateway, no money)");
                return new SimulatedPaymentGateway();
            }
            default -> throw new IllegalStateException("Unknown app.payments.provider: " + provider);
        }
    }
}
