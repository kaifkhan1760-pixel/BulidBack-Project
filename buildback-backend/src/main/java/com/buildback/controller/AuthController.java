package com.buildback.controller;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.buildback.dto.ApiError;
import com.buildback.dto.CsrfResponse;
import com.buildback.dto.RegisterRequest;
import com.buildback.dto.UserResponse;
import com.buildback.security.AppUserDetails;
import com.buildback.service.EmailVerificationService;
import com.buildback.service.UserService;

import jakarta.validation.Valid;

/**
 * Account endpoints.
 *
 * Login  (POST /api/auth/login)  and
 * Logout (POST /api/auth/logout) are NOT here: Spring Security's own filters
 * handle them (configured in SecurityConfig), so session handling stays correct.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final EmailVerificationService emailVerification;

    public AuthController(UserService userService, EmailVerificationService emailVerification) {
        this.userService = userService;
        this.emailVerification = emailVerification;
    }

    /** Body of the two email-confirmation calls below. */
    public record VerifyEmailRequest(String token) {
    }

    public record ResendRequest(String email) {
    }

    /**
     * Gives React the CSRF token for this session. Spring injects the token;
     * reading getToken() also saves it in the session if it is new.
     */
    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
    }

    /**
     * Creates a BUYER or SELLER account and emails a confirmation link.
     * Does not log the user in: they must confirm their email first.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = userService.register(request);
        emailVerification.afterRegistration(created.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** POST /api/auth/verify-email {"token": "..."}: the code from the email link. */
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody VerifyEmailRequest request) {
        emailVerification.verify(request.token());
        return ResponseEntity.ok(Map.of("message", "Your email is confirmed. You can log in now."));
    }

    /**
     * POST /api/auth/resend-verification {"email": "..."}.
     * Always the same answer, so nobody can use it to check who has an account.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resend(@RequestBody ResendRequest request) {
        if (request.email() != null && request.email().length() <= 254) {
            emailVerification.resend(request.email());
        }
        return ResponseEntity.ok(Map.of("message",
                "If that account is waiting for confirmation, a new link is on its way. Check your inbox and spam folder."));
    }

    /** Who am I? Used by React after a page refresh. */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AppUserDetails principal) {
        // Read fresh data from the database in case the account changed or was removed
        return userService.findById(principal.getId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiError.of("Please log in to continue")));
    }
}
