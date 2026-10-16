package com.buildback.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.buildback.entity.Material;
import com.buildback.entity.Payment;
import com.buildback.entity.PurchaseOrder;
import com.buildback.entity.User;
import com.buildback.repository.PaymentRepository;
import com.buildback.repository.PurchaseOrderRepository;

/**
 * Emails after a successful payment:
 *  - buyer: "Order confirmed" with a full invoice (also viewable/printable on the website);
 *  - seller: "New paid order" so they can prepare the material for pickup.
 *
 * AFTER_COMMIT: the emails go out only once the payment is safely saved. If an
 * email fails, the payment is NOT undone; the failure is only logged.
 */
@Service
public class OrderEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final PurchaseOrderRepository orders;
    private final PaymentRepository payments;
    private final MailService mail;
    private final InvoicePdfService invoicePdf;
    private final String frontendUrl;

    public OrderEmailService(PurchaseOrderRepository orders, PaymentRepository payments, MailService mail,
            InvoicePdfService invoicePdf, @Value("${app.frontend-url}") String frontendUrl) {
        this.orders = orders;
        this.payments = payments;
        this.mail = mail;
        this.invoicePdf = invoicePdf;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** "BB-2026-000042": the same number is shown on the website invoice. */
    public static String invoiceNumber(PurchaseOrder order) {
        return "BB-" + order.getPaidAt().atZone(INDIA).getYear() + "-" + String.format("%06d", order.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            PurchaseOrder order = orders.findWithDetailsById(event.orderId()).orElseThrow();
            Payment payment = payments.findById(event.paymentId()).orElseThrow();
            User buyer = order.getBuyer();
            User seller = order.getMaterial().getSeller();

            mail.send(buyer.getEmail(), "Order confirmed: invoice " + invoiceNumber(order),
                    buyerHtml(order, payment), buyerText(order, payment),
                    InvoicePdfService.fileName(order), invoicePdf.render(order, payment));
            mail.send(seller.getEmail(), "New paid order #" + order.getId() + " on BuildBack",
                    sellerHtml(order), sellerText(order));
        } catch (RuntimeException e) {
            log.warn("Could not send the order emails for order {}: {}", event.orderId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderCancelled(OrderCancelledEvent event) {
        try {
            PurchaseOrder o = orders.findWithDetailsById(event.orderId()).orElseThrow();
            Payment p = payments.findById(event.paymentId()).orElseThrow();
            Material m = o.getMaterial();
            User buyer = o.getBuyer();
            String total = rupees(o.getTotalAmount());

            mail.send(buyer.getEmail(), "Order #" + o.getId() + " cancelled: refund of " + total, """
                    <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;color:#1a1f24">
                      <h2 style="color:#167a59;margin:0 0 16px">Build<span style="color:#e8a33d">Back</span></h2>
                      <h3 style="margin:0 0 8px">Your order has been cancelled</h3>
                      <p>Hi %s, order #%d (%d x %s) is cancelled as you asked.</p>
                      <p>A refund of <b>%s</b> has been started (refund id %s). Banks usually show it
                         within 5 to 7 working days.</p>
                      <p style="font-size:12px;color:#5a6570">Payments on this final-year project run in
                         Razorpay test mode, so no real money was charged or refunded.</p>
                    </div>
                    """.formatted(esc(buyer.getName()), o.getId(), o.getQuantity(), esc(m.getTitle()), total,
                    esc(p.getRefundId())),
                    "Hi " + buyer.getName() + ",\n\nOrder #" + o.getId() + " is cancelled. A refund of " + total
                            + " has been started (refund id " + p.getRefundId() + ").\n\n- BuildBack");

            mail.send(m.getSeller().getEmail(), "Order #" + o.getId() + " was cancelled by the buyer", """
                    <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;color:#1a1f24">
                      <h2 style="color:#167a59;margin:0 0 16px">Build<span style="color:#e8a33d">Back</span></h2>
                      <p>Hi %s, the buyer cancelled order #%d (%d x %s) before pickup and was refunded.</p>
                      <p>The %d units are back on sale in your listing. No action is needed.</p>
                    </div>
                    """.formatted(esc(m.getSeller().getName()), o.getId(), o.getQuantity(), esc(m.getTitle()),
                    o.getQuantity()),
                    "Order #" + o.getId() + " was cancelled by the buyer and refunded. The quantity is back on sale.");
        } catch (RuntimeException e) {
            log.warn("Could not send the cancellation emails for order {}: {}", event.orderId(), e.getMessage());
        }
    }

    // ---------------- buyer: confirmation + invoice ----------------

    private String buyerHtml(PurchaseOrder o, Payment p) {
        Material m = o.getMaterial();
        User buyer = o.getBuyer();
        User seller = m.getSeller();
        String unit = m.getUnit().name().toLowerCase(Locale.ROOT);
        return """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;color:#1a1f24">
                  <h2 style="color:#167a59;margin:0 0 4px">Build<span style="color:#e8a33d">Back</span></h2>
                  <p style="margin:0 0 20px;color:#5a6570">Construction surplus marketplace</p>
                  <h3 style="margin:0 0 8px">Thank you, your order is confirmed!</h3>
                  <p>Hi %s, we have received your payment. Please collect the material from the seller in %s.</p>

                  <table style="width:100%%;border-collapse:collapse;margin:20px 0;font-size:14px">
                    <tr><td style="padding:4px 0;color:#5a6570">Invoice number</td><td style="text-align:right"><b>%s</b></td></tr>
                    <tr><td style="padding:4px 0;color:#5a6570">Order</td><td style="text-align:right">#%d</td></tr>
                    <tr><td style="padding:4px 0;color:#5a6570">Paid on</td><td style="text-align:right">%s</td></tr>
                    <tr><td style="padding:4px 0;color:#5a6570">Payment ID</td><td style="text-align:right">%s (%s)</td></tr>
                  </table>

                  <table style="width:100%%;border-collapse:collapse;font-size:14px">
                    <tr style="background:#f1f3f5">
                      <th style="text-align:left;padding:10px">Item</th>
                      <th style="text-align:right;padding:10px">Qty</th>
                      <th style="text-align:right;padding:10px">Rate</th>
                      <th style="text-align:right;padding:10px">Amount</th>
                    </tr>
                    <tr>
                      <td style="padding:10px;border-bottom:1px solid #e1e5e9">%s<br><span style="color:#5a6570;font-size:12px">Per %s</span></td>
                      <td style="padding:10px;text-align:right;border-bottom:1px solid #e1e5e9">%d</td>
                      <td style="padding:10px;text-align:right;border-bottom:1px solid #e1e5e9">%s</td>
                      <td style="padding:10px;text-align:right;border-bottom:1px solid #e1e5e9">%s</td>
                    </tr>
                    <tr>
                      <td colspan="3" style="padding:12px 10px;text-align:right"><b>Total paid</b></td>
                      <td style="padding:12px 10px;text-align:right;font-size:18px"><b>%s</b></td>
                    </tr>
                  </table>

                  <table style="width:100%%;margin:20px 0;font-size:14px"><tr>
                    <td style="vertical-align:top;width:50%%"><b>Billed to</b><br>%s<br>%s<br>%s</td>
                    <td style="vertical-align:top"><b>Sold by</b><br>%s<br>%s<br>%s<br>Pickup: %s</td>
                  </tr></table>

                  <p style="margin:28px 0">
                    <a href="%s" style="background:#167a59;color:#fff;padding:12px 24px;border-radius:6px;
                       text-decoration:none;font-weight:bold;display:inline-block">View or print invoice</a>
                  </p>
                  <p style="font-size:13px;color:#5a6570">Your invoice is attached to this email as a PDF.</p>
                  <p style="font-size:12px;color:#5a6570">Prices are as listed by the seller. Payments on this
                     final-year project run in Razorpay test mode, so no real money was charged.</p>
                </div>
                """.formatted(
                esc(buyer.getName()), esc(m.getCity()),
                invoiceNumber(o), o.getId(), DATE.format(o.getPaidAt().atZone(INDIA)),
                esc(nullToDash(p.getProviderPaymentId())), p.getProvider().name().toLowerCase(Locale.ROOT),
                esc(m.getTitle()), unit, o.getQuantity(), rupees(o.getUnitPrice()), rupees(o.getTotalAmount()),
                rupees(o.getTotalAmount()),
                esc(buyer.getName()), esc(buyer.getEmail()), esc(nullToDash(buyer.getPhone())),
                esc(seller.getName()), esc(seller.getEmail()), esc(nullToDash(seller.getPhone())), esc(m.getCity()),
                esc(invoiceLink(o)));
    }

    private String buyerText(PurchaseOrder o, Payment p) {
        Material m = o.getMaterial();
        User seller = m.getSeller();
        return "Hi " + o.getBuyer().getName() + ",\n\nYour BuildBack order is confirmed.\n\n"
                + "Invoice: " + invoiceNumber(o) + "\nOrder: #" + o.getId() + "\nPaid on: "
                + DATE.format(o.getPaidAt().atZone(INDIA)) + "\nPayment ID: " + nullToDash(p.getProviderPaymentId())
                + "\n\n" + m.getTitle() + "\n" + o.getQuantity() + " x " + rupees(o.getUnitPrice()) + " = "
                + rupees(o.getTotalAmount()) + "\n\nSeller: " + seller.getName() + ", " + seller.getEmail()
                + (seller.getPhone() == null ? "" : ", " + seller.getPhone()) + "\nPickup city: " + m.getCity()
                + "\n\nView or print the invoice: " + invoiceLink(o) + "\n\n- BuildBack";
    }

    // ---------------- seller: new paid order ----------------

    private String sellerHtml(PurchaseOrder o) {
        Material m = o.getMaterial();
        User buyer = o.getBuyer();
        return """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;color:#1a1f24">
                  <h2 style="color:#167a59;margin:0 0 16px">Build<span style="color:#e8a33d">Back</span></h2>
                  <h3 style="margin:0 0 8px">You have a new paid order</h3>
                  <p>Hi %s, %s has paid for <b>%d x %s</b> (total <b>%s</b>).</p>
                  <p>Please keep the material ready for pickup in %s. Buyer contact:<br>
                     %s<br>%s</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#167a59;color:#fff;padding:12px 24px;border-radius:6px;
                       text-decoration:none;font-weight:bold;display:inline-block">Open my orders</a>
                  </p>
                  <p style="font-size:13px;color:#5a6570">Mark the order as collected after the buyer picks it up.</p>
                </div>
                """.formatted(esc(m.getSeller().getName()), esc(buyer.getName()), o.getQuantity(), esc(m.getTitle()),
                rupees(o.getTotalAmount()), esc(m.getCity()), esc(buyer.getEmail()), esc(nullToDash(buyer.getPhone())),
                esc(frontendUrl + "/seller/orders"));
    }

    private String sellerText(PurchaseOrder o) {
        Material m = o.getMaterial();
        User buyer = o.getBuyer();
        return "Hi " + m.getSeller().getName() + ",\n\n" + buyer.getName() + " has paid for " + o.getQuantity()
                + " x " + m.getTitle() + " (" + rupees(o.getTotalAmount()) + ").\nPickup city: " + m.getCity()
                + "\nBuyer: " + buyer.getEmail() + (buyer.getPhone() == null ? "" : ", " + buyer.getPhone())
                + "\n\nYour orders: " + frontendUrl + "/seller/orders\n\n- BuildBack";
    }

    // ---------------- helpers ----------------

    private String invoiceLink(PurchaseOrder o) {
        return frontendUrl + "/buyer/orders/" + o.getId() + "/invoice";
    }

    private static String rupees(BigDecimal amount) {
        NumberFormat inr = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
        return inr.format(amount);
    }

    private static String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    private static String esc(String value) {
        return MailService.escape(value);
    }
}
