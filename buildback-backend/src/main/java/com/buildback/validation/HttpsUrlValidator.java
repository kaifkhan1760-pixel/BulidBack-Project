package com.buildback.validation;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // the image is optional
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null;
        } catch (URISyntaxException e) {
            return false; // spaces or other illegal characters
        }
    }
}
