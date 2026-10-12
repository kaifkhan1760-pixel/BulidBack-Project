package com.buildback.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A listing photo must be EITHER
 *  - a photo uploaded to BuildBack: "/api/images/{random name}", OR
 *  - an absolute https:// link with a host name.
 * Rejects http:, data:, javascript: and file: links, and user:password@ parts.
 * For https links the backend only stores the text; it never downloads the image.
 */
@Documented
@Constraint(validatedBy = ImageLinkValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ImageLink {

    String message() default "Upload a photo, or use a full image link starting with https://";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
