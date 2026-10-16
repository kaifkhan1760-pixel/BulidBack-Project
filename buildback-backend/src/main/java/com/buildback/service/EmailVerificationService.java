package com.buildback.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buildback.entity.EmailVerificationToken;
import com.buildback.entity.User;
import com.buildback.exception.InvalidRequestException;
import com.buildback.repository.EmailVerificationTokenRepository;
import com.buildback.repository.UserRepository;

/**
 * "Confirm your email" links.
 *
 * Flow: register -> email with a link -> the link page calls verify(code) ->
 * the account is confirmed and can log in.
 *
 * Security:
 *  - the code is 32 random bytes from SecureRandom (impossible to guess);
 *  - only its SHA-256 hash is stored;
 *  - a link works once, for 24 hours; a new link cancels older ones;
 *  - "resend" answers the same way whether or not the email exists, so it
 *    cannot be used to find out who has an account, and it waits 60 seconds
 *    between emails to the same person.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    static final Duration LINK_VALID_FOR = Duration.ofHours(24);
    static final Duration RESEND_WAIT = Duration.ofSeconds(60);
    static final String INVALID_LINK = "This confirmation link is not valid. It may have been used already "
            + "or replaced by a newer email.";
    static final String EXPIRED_LINK = "This confirmation link has expired. Ask for a new one below.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository users;
    private final MailService mail;
    private final Clock clock;
    private final String frontendUrl;
    private final List<String> autoVerifyDomains;

    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository users, MailService mail,
            Clock clock,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.email-verification.auto-verify-domains:}") String autoVerifyDomains) {
        this.tokens = tokens;
        this.users = users;
        this.mail = mail;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.autoVerifyDomains = Arrays.stream(autoVerifyDomains.split(","))
                .map(String::trim).filter(d -> !d.isEmpty()).map(String::toLowerCase).toList();
    }

    /**
     * Called right after a new account is saved. Sends the confirmation email,
     * except for demo addresses on a reserved test domain that can never receive mail.
     * Returns false if the email could not be sent (the user can ask for a new one).
     */
    @Transactional
    public boolean afterRegistration(Long userId) {
        User user = users.findById(userId).orElseThrow();
        if (autoVerifyDomains.stream().anyMatch(domain -> user.getEmail().endsWith("@" + domain))) {
            user.markEmailVerified();
            return true;
        }
        return sendLink(user);
    }

    /** The page behind the email link sends the code here. */
    @Transactional
    public void verify(String code) {
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new InvalidRequestException(Map.of("token", INVALID_LINK));
        }
        EmailVerificationToken token = tokens.findByTokenHash(sha256(code.trim()))
                .orElseThrow(() -> new InvalidRequestException(Map.of("token", INVALID_LINK)));
        Instant now = clock.instant();
        if (token.isUsed()) {
            throw new InvalidRequestException(Map.of("token", INVALID_LINK));
        }
        if (token.isExpired(now)) {
            throw new InvalidRequestException(Map.of("token", EXPIRED_LINK));
        }
        token.markUsed(now);
        token.getUser().markEmailVerified();
    }

    /** "Send me a new link". Always looks the same to the caller (see class comment). */
    @Transactional
    public void resend(String email) {
        users.findByEmail(EmailUtils.normalize(email))
                .filter(user -> !user.isEmailVerified())
                .filter(this::allowedToResend)
                .ifPresent(this::sendLink);
    }

    private boolean allowedToResend(User user) {
        return tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(last -> last.getCreatedAt().plus(RESEND_WAIT).isBefore(clock.instant()))
                .orElse(true);
    }

    private boolean sendLink(User user) {
        tokens.deleteUnusedForUser(user.getId());
        String code = newCode();
        Instant now = clock.instant();
        tokens.save(new EmailVerificationToken(user, sha256(code), now, now.plus(LINK_VALID_FOR)));

        // The code goes after "#": browsers never send that part to any server,
        // so it cannot leak through logs or "Referer" headers.
        String link = frontendUrl + "/verify-email#token=" + code;
        try {
            mail.send(user.getEmail(), "Confirm your BuildBack account", html(user.getName(), link),
                    text(user.getName(), link));
            return true;
        } catch (RuntimeException e) {
            // The account still exists; the user can press "resend". Never log the link itself.
            log.warn("Could not send the confirmation email: {}", e.getMessage());
            return false;
        }
    }

    private static String newCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }

    private static String text(String name, String link) {
        return "Hi " + name + ",\n\nWelcome to BuildBack! Please confirm your email address by opening this link:\n\n"
                + link + "\n\nThe link works once and expires in 24 hours.\n"
                + "If you did not create a BuildBack account, you can ignore this email.\n\n- BuildBack";
    }

    private static String html(String name, String link) {
        String safeLink = MailService.escape(link);
        return """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#1a1f24">
                  <h2 style="color:#167a59;margin:0 0 16px">Build<span style="color:#e8a33d">Back</span></h2>
                  <p>Hi %s,</p>
                  <p>Welcome to BuildBack, India's marketplace for surplus building material.
                     Please confirm your email address to activate your account.</p>
                  <p style="margin:28px 0">
                    <a href="%s" style="background:#167a59;color:#fff;padding:12px 24px;border-radius:6px;
                       text-decoration:none;font-weight:bold;display:inline-block">Confirm my email</a>
                  </p>
                  <p style="font-size:13px;color:#5a6570">The link works once and expires in 24 hours.
                     If the button does not work, copy this address into your browser:<br>
                     <span style="word-break:break-all">%s</span></p>
                  <p style="font-size:13px;color:#5a6570">If you did not create a BuildBack account,
                     you can ignore this email.</p>
                </div>
                """.formatted(MailService.escape(name), safeLink, safeLink);
    }
}
