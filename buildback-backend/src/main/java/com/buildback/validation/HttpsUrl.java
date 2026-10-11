package com.buildback.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The value must be an absolute https:// URL with a host name.
 * Rejects http:, data:, javascript: and file: links, and user:password@ parts.
 * The backend only stores the text; it never downloads the image.
 */
@Documented
@Constraint(validatedBy = HttpsUrlValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpsUrl {

    String message() default "Enter a full image link starting with https://";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
