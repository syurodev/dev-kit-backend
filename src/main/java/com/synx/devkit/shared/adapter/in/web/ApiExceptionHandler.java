package com.synx.devkit.shared.adapter.in.web;

import com.synx.devkit.shared.error.DomainException;
import com.synx.devkit.shared.error.ForbiddenException;
import com.synx.devkit.shared.error.QuotaExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps internal failures to a small response that never includes raw causes. */
@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiErrorResponse> forbidden(ForbiddenException error, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, error.code(), "Request is forbidden", request);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiErrorResponse> domain(DomainException error, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, error.code(), "Request is invalid", request);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ApiErrorResponse> tooLarge(PayloadTooLargeException error, HttpServletRequest request) {
        return response(HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "Request body is too large", request);
    }

    @ExceptionHandler(QuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> quotaExceeded(QuotaExceededException error, HttpServletRequest request) {
        return response(HttpStatus.INSUFFICIENT_STORAGE,
                error.code(), "Account storage quota is exhausted", request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiErrorResponse> badRequest(Exception error, HttpServletRequest request) {
        if (hasCause(error, PayloadTooLargeException.class)) {
            return response(HttpStatus.CONTENT_TOO_LARGE,
                    "payload_too_large", "Request body is too large", request);
        }
        return response(HttpStatus.BAD_REQUEST, "bad_request", "Request is invalid", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception error, HttpServletRequest request) {
        // Only the exception type is logged. Raw messages may contain SQL/JWT
        // details and are deliberately excluded from production diagnostics.
        LOG.error("Unhandled API failure type={}", error.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Request failed", request);
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        // Controller validation/domain exceptions are intentionally converted
        // to small public responses. Record their safe status/code here so an
        // operator can distinguish a rejected sync request from a local client
        // failure without logging a bearer token, cursor, or ciphertext.
        if (status.is4xxClientError()) {
            LOG.warn("API request rejected status={} code={} request_id={}",
                    status.value(), code, requestId == null ? "unknown" : requestId);
        }
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                requestId == null ? "unknown" : requestId.toString()));
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> expected) {
        Throwable current = error;
        while (current != null) {
            if (expected.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
