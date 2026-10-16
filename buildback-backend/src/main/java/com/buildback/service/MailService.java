package com.buildback.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends emails.
 *
 * Two modes, chosen automatically:
 *  - SMTP: when SPRING_MAIL_HOST (+ username/password) is set, Spring creates a
 *    JavaMailSender and real emails are sent (e.g. through Gmail).
 *  - Outbox: when no mail server is configured (local development), each email
 *    is saved as an .html file in the "mail-outbox" folder instead. Open the file
 *    in a browser to read it and click its link. The folder is git-ignored.
 *
 * Email contents (which contain one-time links) are never written to the log.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** "BuildBack <me@gmail.com>" */
    private static final Pattern NAME_AND_ADDRESS = Pattern.compile("^(.*?)\s*<([^<>\s]+@[^<>\s]+)>$");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String smtpLogin;
    private final Path outbox;

    public MailService(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpLogin,
            @Value("${app.mail.outbox-dir}") String outboxDir) {
        this.mailSender = mailSender;
        this.from = from == null ? "" : from.trim();
        this.smtpLogin = smtpLogin == null ? "" : smtpLogin.trim();
        this.outbox = Path.of(outboxDir).toAbsolutePath().normalize();
    }

    /**
     * The "From" of every email. MAIL_FROM may be written as "BuildBack <me@gmail.com>",
     * "BuildBack me@gmail.com" or just "me@gmail.com". Gmail only sends as the account
     * that logged in, so when a login is configured its address is always used.
     */
    InternetAddress sender() throws UnsupportedEncodingException {
        String name = "BuildBack";
        String address = null;
        Matcher m = NAME_AND_ADDRESS.matcher(from);
        if (m.matches()) {
            name = m.group(1).replace("\"", "").trim();
            address = m.group(2);
        } else {
            int space = from.lastIndexOf(' ');
            String last = from.substring(space + 1);
            if (last.contains("@")) {
                address = last;
                name = space > 0 ? from.substring(0, space).trim() : name;
            }
        }
        if (!smtpLogin.isEmpty()) {
            address = smtpLogin;
        }
        if (address == null) {
            throw new IllegalStateException("MAIL_FROM does not contain an email address");
        }
        return new InternetAddress(address, name.isEmpty() ? "BuildBack" : name, StandardCharsets.UTF_8.name());
    }

    /** Sends one email with an HTML body and a plain-text body (for simple mail apps). */
    public void send(String to, String subject, String html, String text) {
        send(to, subject, html, text, null, null);
    }

    /** Same, with one PDF attached (e.g. the invoice). attachmentName/pdf may be null. */
    public void send(String to, String subject, String html, String text, String attachmentName, byte[] pdf) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            saveToOutbox(to, subject, html, attachmentName, pdf);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(sender());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            if (pdf != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(pdf), "application/pdf");
            }
            sender.send(message);
            log.info("Email \"{}\" sent", subject);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            // The most specific cause, e.g. Gmail's "Username and Password not accepted" (never the password itself)
            throw new IllegalStateException("Could not send the email: "
                    + NestedExceptionUtils.getMostSpecificCause(e).getMessage(), e);
        }
    }

    private void saveToOutbox(String to, String subject, String html, String attachmentName, byte[] pdf) {
        try {
            Files.createDirectories(outbox);
            String name = LocalDateTime.now().format(FILE_TIME) + "-" + UUID.randomUUID().toString().substring(0, 8)
                    + ".html";
            String page = "<!doctype html><meta charset=\"utf-8\"><title>" + escape(subject) + "</title>"
                    + "<p style=\"font:14px sans-serif;color:#555\">To: " + escape(to) + "<br>Subject: "
                    + escape(subject) + "<br><i>Saved by BuildBack because no mail server is configured.</i></p><hr>"
                    + html;
            if (pdf != null) {
                // The attachment is saved next to the email, e.g. "...-a1b2c3d4.html.Invoice-BB-2026-000004.pdf"
                Files.write(outbox.resolve(name + "." + attachmentName), pdf);
                page += "<hr><p style=\"font:14px sans-serif\">Attachment: <a href=\"" + escape(name + "." + attachmentName)
                        + "\">" + escape(attachmentName) + "</a></p>";
            }
            Files.writeString(outbox.resolve(name), page, StandardCharsets.UTF_8);
            log.info("No mail server configured: email \"{}\" saved to {}", subject, outbox.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the email to the outbox", e);
        }
    }

    /** Makes text safe to put inside HTML (names can contain ' and &). */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
