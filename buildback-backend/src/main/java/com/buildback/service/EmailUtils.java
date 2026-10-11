package com.buildback.service;

import java.util.Locale;

/**
 * One shared rule for emails, used by registration, login and admin bootstrap:
 * "  Ali@Example.COM " and "ali@example.com" are the same account.
 */
public final class EmailUtils {

    private EmailUtils() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        // Locale.ROOT avoids language-specific surprises (e.g. Turkish "I")
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
