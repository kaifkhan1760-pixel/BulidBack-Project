package com.buildback.dto;

import com.buildback.service.EmailUtils;
import com.buildback.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body of POST /api/auth/register.
 * Only these four fields are accepted; id, passwordHash or createdAt sent by a
 * client are simply ignored because they do not exist here.
 */
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        // Not trimmed: spaces are allowed characters in a password
        @NotNull(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "Password is too long (maximum 72 bytes)")
        String password,

        @NotNull(message = "Choose Buyer or Seller")
        RegistrationRole role) {

    /** Runs before validation: clean up name and email. */
    public RegisterRequest {
        name = name == null ? null : name.trim();
        email = EmailUtils.normalize(email);
    }
}
