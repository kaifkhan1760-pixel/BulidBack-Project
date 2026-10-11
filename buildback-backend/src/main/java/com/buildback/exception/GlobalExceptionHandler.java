package com.buildback.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.buildback.dto.ApiError;
import com.buildback.payment.PaymentGatewayException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Turns exceptions thrown by controllers into the same safe JSON shape (ApiError).
 * No stack traces or SQL messages are sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String FIX_FIELDS = "Please correct the highlighted fields";

    /** Friendly messages for enum fields that received an unknown value. */
    private static final Map<String, String> ENUM_FIELD_MESSAGES = Map.of(
            "role", "Choose Buyer or Seller",
            "category", "Choose Tiles, Paint, Plywood or Fittings",
            "unit", "Choose Piece, Can or Sheet");

    /** @Valid failed: return one message per field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiError(FIX_FIELDS, errors));
    }

    /** Rules checked in services (cross-field rules, query parameters). */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage(), e.getErrors()));
    }

    /**
     * The JSON could not be turned into our DTO: broken JSON, "role":"ADMIN",
     * "unitPrice":"abc", or 2.5 for a whole-number quantity.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String field = jackson.getPath().getLast().getPropertyName();
            if (field != null) {
                return ResponseEntity.badRequest()
                        .body(new ApiError(FIX_FIELDS, Map.of(field, messageFor(field, jackson))));
            }
        }
        return ResponseEntity.badRequest().body(ApiError.of("Request body is missing or is not valid JSON"));
    }

    private static String messageFor(String field, JacksonException e) {
        if (ENUM_FIELD_MESSAGES.containsKey(field)) {
            return ENUM_FIELD_MESSAGES.get(field);
        }
        Class<?> target = null;
        if (e instanceof MismatchedInputException mismatch) {
            target = mismatch.getTargetType();
        } else if (e instanceof InputCoercionException coercion) {
            target = coercion.getTargetType();
        }
        if (target != null && (Number.class.isAssignableFrom(target) || target.isPrimitive())) {
            return "Enter a valid number";
        }
        return "Invalid value";
    }

    /** e.g. DELETE without ?version= */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError(FIX_FIELDS, Map.of(e.getParameterName(), e.getParameterName() + " is required")));
    }

    /** e.g. /api/materials/abc or ?version=xyz */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ApiError(FIX_FIELDS, Map.of(e.getName(), "Invalid value")));
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(EmailAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(e.getMessage(), Map.of("email", e.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(e.getMessage()));
    }

    /** The payment gateway failed or answered unexpectedly. Details go to the log, not the browser. */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiError> handleGateway(PaymentGatewayException e) {
        log.warn("Payment gateway problem: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("The payment service is not responding correctly. Please try again."));
    }

    /** Two saves raced each other; @Version stopped the second one. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "This listing was changed after you opened it. Reload it to see the latest version, then try again."));
    }
}
