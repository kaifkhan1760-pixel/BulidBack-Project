package com.buildback.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.entity.Material;
import com.buildback.entity.MaterialCategory;
import com.buildback.entity.OrderStatus;
import com.buildback.entity.Payment;
import com.buildback.entity.PaymentStatus;
import com.buildback.entity.PurchaseOrder;
import com.buildback.entity.User;
import com.buildback.exception.ConflictException;
import com.buildback.exception.ResourceNotFoundException;
import com.buildback.repository.PaymentRepository;
import com.buildback.repository.PurchaseOrderRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Builds the invoice of a paid order as a PDF file (same content as the website
 * invoice page). Attached to the buyer's "Order confirmed" email and downloadable
 * from the invoice page.
 *
 * The built-in PDF fonts have no rupee sign, so amounts are written "Rs. 1,200.00".
 */
@Service
public class InvoicePdfService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final Color GREEN = new Color(0x16, 0x7a, 0x59);
    private static final Color AMBER = new Color(0xe8, 0xa3, 0x3d);
    private static final Color GREY = new Color(0x5a, 0x65, 0x70);
    private static final Color LIGHT = new Color(0xf1, 0xf3, 0xf5);
    private static final Color LINE = new Color(0xe1, 0xe5, 0xe9);

    private static final Font TITLE = new Font(Font.HELVETICA, 22, Font.BOLD, GREEN);
    private static final Font BRAND = new Font(Font.HELVETICA, 18, Font.BOLD, GREEN);
    private static final Font BRAND_ACCENT = new Font(Font.HELVETICA, 18, Font.BOLD, AMBER);
    private static final Font SMALL = new Font(Font.HELVETICA, 9, Font.NORMAL, GREY);
    private static final Font LABEL = new Font(Font.HELVETICA, 8, Font.BOLD, GREY);
    private static final Font TEXT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font TOTAL = new Font(Font.HELVETICA, 13, Font.BOLD, Color.BLACK);

    private final PurchaseOrderRepository orders;
    private final PaymentRepository payments;

    public InvoicePdfService(PurchaseOrderRepository orders, PaymentRepository payments) {
        this.orders = orders;
        this.payments = payments;
    }

    /** For GET /api/buyer/orders/{id}/invoice.pdf: only the buyer's own, paid order. */
    @Transactional(readOnly = true)
    public byte[] forBuyer(Long buyerId, Long orderId) {
        PurchaseOrder order = orders.findWithDetailsById(orderId)
                .filter(o -> o.getBuyer().getId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(OrderService.ORDER_NOT_FOUND));
        if (order.getPaidAt() == null
                || (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.COLLECTED)) {
            throw new ConflictException("An invoice is available once the order has been paid.");
        }
        Payment payment = payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS).findFirst().orElse(null);
        return render(order, payment);
    }

    public static String fileName(PurchaseOrder order) {
        return "Invoice-" + OrderEmailService.invoiceNumber(order) + ".pdf";
    }

    /** The PDF bytes. Call inside a transaction (it reads the buyer, seller and material). */
    public byte[] render(PurchaseOrder o, Payment payment) {
        Material m = o.getMaterial();
        User buyer = o.getBuyer();
        User seller = m.getSeller();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Invoice " + OrderEmailService.invoiceNumber(o));
            doc.addAuthor("BuildBack");
            doc.open();

            // ---- header: brand left, "INVOICE" + numbers right ----
            PdfPTable head = table(2, new float[] { 1, 1 });
            PdfPCell brand = cell(Rectangle.NO_BORDER);
            Paragraph name = new Paragraph();
            name.add(new Phrase("Build", BRAND));
            name.add(new Phrase("Back", BRAND_ACCENT));
            brand.addElement(name);
            brand.addElement(new Paragraph("Construction surplus marketplace", SMALL));
            head.addCell(brand);

            PdfPCell meta = cell(Rectangle.NO_BORDER);
            meta.addElement(right(new Paragraph("INVOICE", TITLE)));
            meta.addElement(right(new Paragraph("Invoice no.  " + OrderEmailService.invoiceNumber(o), BOLD)));
            meta.addElement(right(new Paragraph("Order  #" + o.getId(), TEXT)));
            meta.addElement(right(new Paragraph("Paid on  " + DATE.format(o.getPaidAt().atZone(INDIA)), TEXT)));
            head.addCell(meta);
            doc.add(head);
            doc.add(rule());

            // ---- billed to / sold by ----
            PdfPTable parties = table(2, new float[] { 1, 1 });
            parties.addCell(party("BILLED TO", buyer.getName(), buyer.getEmail(), buyer.getPhone(), null));
            parties.addCell(party("SOLD BY", seller.getName(), seller.getEmail(), seller.getPhone(),
                    "Pickup: " + m.getCity()));
            parties.setSpacingAfter(16);
            doc.add(parties);

            // ---- item table ----
            PdfPTable items = table(4, new float[] { 5, 1.2f, 2, 2 });
            for (String h : new String[] { "ITEM", "QTY", "RATE", "AMOUNT" }) {
                PdfPCell c = new PdfPCell(new Phrase(h, LABEL));
                c.setBackgroundColor(LIGHT);
                c.setBorder(Rectangle.NO_BORDER);
                c.setPadding(8);
                c.setHorizontalAlignment(h.equals("ITEM") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                items.addCell(c);
            }
            PdfPCell item = bottomLine();
            item.addElement(new Paragraph(m.getTitle(), TEXT));
            item.addElement(new Paragraph(categoryLabel(m.getCategory()) + " - per "
                    + m.getUnit().name().toLowerCase(Locale.ROOT), SMALL));
            items.addCell(item);
            items.addCell(amountCell(String.valueOf(o.getQuantity()), TEXT));
            items.addCell(amountCell(rupees(o.getUnitPrice()), TEXT));
            items.addCell(amountCell(rupees(o.getTotalAmount()), TEXT));

            PdfPCell totalLabel = new PdfPCell(new Phrase("Total paid", TOTAL));
            totalLabel.setColspan(3);
            totalLabel.setBorder(Rectangle.NO_BORDER);
            totalLabel.setPadding(10);
            items.addCell(totalLabel);
            PdfPCell totalValue = new PdfPCell(new Phrase(rupees(o.getTotalAmount()), TOTAL));
            totalValue.setBorder(Rectangle.NO_BORDER);
            totalValue.setPadding(10);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            items.addCell(totalValue);
            items.setSpacingAfter(16);
            doc.add(items);

            // ---- payment ----
            doc.add(new Paragraph("PAYMENT", LABEL));
            String how = payment == null ? "Paid online"
                    : "Paid online via " + (payment.getProvider().name().equals("RAZORPAY") ? "Razorpay" : "the BuildBack simulator")
                            + " - Payment ID " + payment.getProviderPaymentId();
            doc.add(new Paragraph(how, TEXT));
            if (payment != null && payment.isRefunded()) {
                doc.add(new Paragraph("Refunded (refund ID " + payment.getRefundId() + ")", BOLD));
            }
            doc.add(rule());
            doc.add(new Paragraph("Prices are as listed by the seller. This is a final-year project: payments run in "
                    + "Razorpay test mode and no real money is charged.", SMALL));
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not create the invoice PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ---------------- small helpers ----------------

    private static PdfPTable table(int columns, float[] widths) {
        PdfPTable t = new PdfPTable(columns);
        t.setWidthPercentage(100);
        try {
            t.setWidths(widths);
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        return t;
    }

    private static PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        c.setPadding(0);
        return c;
    }

    private static PdfPCell party(String label, String name, String email, String phone, String extra) {
        PdfPCell c = cell(Rectangle.NO_BORDER);
        c.setPaddingTop(12);
        c.addElement(new Paragraph(label, LABEL));
        c.addElement(new Paragraph(name, BOLD));
        c.addElement(new Paragraph(email, TEXT));
        if (phone != null) {
            c.addElement(new Paragraph(phone, TEXT));
        }
        if (extra != null) {
            c.addElement(new Paragraph(extra, TEXT));
        }
        return c;
    }

    private static PdfPCell bottomLine() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINE);
        c.setPadding(8);
        return c;
    }

    private static PdfPCell amountCell(String text, Font font) {
        PdfPCell c = bottomLine();
        c.setPhrase(new Phrase(text, font));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private static Paragraph right(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static PdfPTable rule() {
        PdfPTable line = table(1, new float[] { 1 });
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GREEN);
        c.setBorderWidthBottom(1.5f);
        c.setFixedHeight(10);
        line.addCell(c);
        line.setSpacingAfter(4);
        return line;
    }

    /** Same names as the website (materialRules.js). */
    private static String categoryLabel(MaterialCategory category) {
        return switch (category) {
            case TILES -> "Tiles";
            case PAINT -> "Paint";
            case PLYWOOD -> "Plywood";
            case FITTINGS -> "Fittings";
            case FALSE_CEILING -> "POP & False Ceiling";
            case WATERPROOFING -> "Waterproofing";
            case INTERIOR -> "Interior & Decor";
        };
    }

    private static String rupees(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "Rs. " + f.format(amount);
    }
}
