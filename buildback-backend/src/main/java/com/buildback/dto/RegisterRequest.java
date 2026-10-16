package com.buildback.dto;

import com.buildback.service.EmailUtils;
import com.buildback.service.PhoneUtils;
import com.buildback.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON body of POST /api/auth/register.
 * Only these five fields are accepted; id, passwordHash or createdAt sent by a
 * client are simply ignored because they do not exist here.
 * The React form checks the same rules first, but only these checks are trusted.
 */
public record RegisterRequest(

        // Letters (any language) separated by spaces, dots, apostrophes, hyphens or "&":
        // "Kaif Khan", "R. K. D'Souza", "Anne-Marie", "Patel Tiles & Sanitary". No digits or other symbols.
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters")
        @Pattern(regexp = "^[\\p{L}\\p{M}]+(?:[ .'&-]{1,3}[\\p{L}\\p{M}]+)*\\.?$",
                message = "Use letters only. Spaces, dots, apostrophes, hyphens and & are allowed between words")
        String name,

        // @Email alone accepts "a@b"; the pattern also needs a domain ending such as .com or .in
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = "^[a-z0-9_%+-]+(?:\\.[a-z0-9_%+-]+)*@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}$",
                message = "Enter a valid email address, like name@example.com")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        // Stored as +91XXXXXXXXXX (see PhoneUtils)
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = PhoneUtils.INDIAN_MOBILE, message = "Enter a valid 10-digit Indian mobile number")
        String phone,

        // Not trimmed: the user types exactly what they will log in with.
        // At least one uppercase letter, one lowercase letter and one number;
        // no space at the start or end (a common copy-paste mistake).
        @NotNull(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "Password is too long (maximum 72 bytes)")
        @Pattern(regexp = "^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\d)\\S(?:.*\\S)?$",
                message = "Use upper and lower case letters and a number, with no space at the start or end")
        String password,

        @NotNull(message = "Choose Buyer or Seller")
        RegistrationRole role) {

    /** Runs before validation: clean up name, email and phone. */
    public RegisterRequest {
        name = name == null ? null : name.trim().replaceAll("\\s+", " ");
        email = EmailUtils.normalize(email);
        phone = PhoneUtils.normalize(phone);
    }
}
