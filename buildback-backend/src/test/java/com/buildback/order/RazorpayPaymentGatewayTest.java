package com.buildback.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.buildback.payment.HmacSignatures;
import com.buildback.payment.PaymentGatewayException;
import com.buildback.payment.RazorpayPaymentGateway;

/**
 * Razorpay integration without the internet: a fake Razorpay server
 * (MockRestServiceServer) checks what we send and returns canned answers.
 */
class RazorpayPaymentGatewayTest {

    private static final String KEY_ID = "rzp_test_ABC123";
    private static final String SECRET = "test_secret_value";

    private MockRestServiceServer razorpay;
    private RazorpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        razorpay = MockRestServiceServer.bindTo(builder).build();
        gateway = new RazorpayPaymentGateway(KEY_ID, SECRET, builder);
    }

    @Test
    void createsAnOrderInPaiseWithBasicAuth() {
        String auth = "Basic " + Base64.getEncoder()
                .encodeToString((KEY_ID + ":" + SECRET).getBytes(StandardCharsets.UTF_8));
        razorpay.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", auth))
                .andExpect(jsonPath("$.amount").value(76500))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andRespond(withSuccess("{\"id\":\"order_TEST1\",\"amount\":76500,\"status\":\"created\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.createCheckout(76500, "buildback_order_1")).isEqualTo("order_TEST1");
        assertThat(gateway.publicKey()).isEqualTo(KEY_ID);
        razorpay.verify();
    }

    @Test
    void signatureIsHmacOfOrderAndPaymentIds() {
        String good = HmacSignatures.sign(SECRET, "order_TEST1|pay_TEST1");
        assertThat(gateway.signatureMatches("order_TEST1", "pay_TEST1", good)).isTrue();
        assertThat(gateway.signatureMatches("order_TEST1", "pay_OTHER", good)).isFalse();
        assertThat(gateway.signatureMatches("order_TEST1", "pay_TEST1", "forged")).isFalse();
        assertThat(gateway.signatureMatches("order_TEST1", "pay_TEST1", null)).isFalse();
    }

    @Test
    void confirmsOrderAmountAndStatusWithRazorpay() {
        razorpay.expect(requestTo("https://api.razorpay.com/v1/payments/pay_TEST1"))
                .andRespond(withSuccess("{\"id\":\"pay_TEST1\",\"order_id\":\"order_TEST1\",\"amount\":76500,"
                        + "\"currency\":\"INR\",\"status\":\"captured\"}", MediaType.APPLICATION_JSON));

        gateway.confirmWithProvider("order_TEST1", "pay_TEST1", 76500); // no exception = confirmed
        razorpay.verify();
    }

    @Test
    void rejectsAPaymentForADifferentAmount() {
        razorpay.expect(requestTo("https://api.razorpay.com/v1/payments/pay_A"))
                .andRespond(withSuccess("{\"order_id\":\"order_1\",\"amount\":100,\"currency\":\"INR\","
                        + "\"status\":\"captured\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.confirmWithProvider("order_1", "pay_A", 76500))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void rejectsAFailedPayment() {
        razorpay.expect(requestTo("https://api.razorpay.com/v1/payments/pay_B"))
                .andRespond(withSuccess("{\"order_id\":\"order_1\",\"amount\":76500,\"currency\":\"INR\","
                        + "\"status\":\"failed\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.confirmWithProvider("order_1", "pay_B", 76500))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void gatewayErrorsBecomeAPaymentGatewayException() {
        razorpay.expect(requestTo("https://api.razorpay.com/v1/orders")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.createCheckout(100, "r")).isInstanceOf(PaymentGatewayException.class);
    }
}
