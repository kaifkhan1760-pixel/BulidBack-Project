package com.buildback.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Limits a text value by its size in UTF-8 bytes (not characters).
 *
 * BCrypt only uses the first 72 bytes of a password. Without this check two
 * different long passwords could share a hash. Emoji or Arabic letters use more
 * than one byte each, so a character limit alone is not enough.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Value is too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
