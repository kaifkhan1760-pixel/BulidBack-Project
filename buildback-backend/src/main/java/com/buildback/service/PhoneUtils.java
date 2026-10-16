package com.buildback.service;

/**
 * Indian mobile numbers. People type them in many ways:
 * "98765 43210", "098765-43210", "+91 98765 43210", "(+91) 9876543210".
 * We keep one format in the database: "+91" followed by the 10 digits.
 */
public final class PhoneUtils {

    /** What a cleaned-up number must look like (checked by RegisterRequest). */
    public static final String INDIAN_MOBILE = "^\\+91[6-9]\\d{9}$";

    private PhoneUtils() {
    }

    /**
     * "+91 98765-43210" -> "+919876543210".
     * Anything that does not reduce to 10 digits is returned with only spaces etc.
     * removed, so validation still rejects it (and the error message makes sense).
     */
    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[\\s().-]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.matches("\\d{10}") ? "+91" + digits : digits;
    }
}
