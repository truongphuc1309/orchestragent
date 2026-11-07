package com.orchestragent.platform.web;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.DomainException;
import com.orchestragent.shared.error.ForbiddenException;
import com.orchestragent.shared.error.NotFoundException;
import com.orchestragent.shared.error.RateLimitedException;
import com.orchestragent.shared.error.UnauthorizedException;
import com.orchestragent.shared.error.ValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place that turns exceptions into RFC 7807 {@link ProblemDetail} responses. Spring MVC's own
 * exceptions (bad body, validation, unsupported media type...) are handled by the parent class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException e) {
        return problem(statusOf(e), e.getMessage(), e.code());
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException e) {
        ProblemDetail problem = problemDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e.code());
        problem.setProperty("retryAfterSeconds", e.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "validation");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", "forbidden");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", "unauthorized");
    }

    /** Last resort. Never leaks the exception message, which may contain internal details. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "internal_error");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status).body(problemDetail(status, detail, code));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }

    private static HttpStatus statusOf(DomainException e) {
        return switch (e) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case ValidationException ignored -> HttpStatus.BAD_REQUEST;
            case UnauthorizedException ignored -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }
}
