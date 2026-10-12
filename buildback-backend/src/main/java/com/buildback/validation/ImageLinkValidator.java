package com.buildback.validation;

import java.net.URI;
import java.net.URISyntaxException;

import com.buildback.service.ImageStorageService;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ImageLinkValidator implements ConstraintValidator<ImageLink, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // "missing" is reported by @NotBlank, not here
        }
        if (ImageStorageService.isUploadedImageUrl(value)) {
            return true;
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
