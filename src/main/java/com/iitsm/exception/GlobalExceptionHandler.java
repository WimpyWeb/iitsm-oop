package com.iitsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Global @ControllerAdvice returning JSON for /api/** and friendly error pages for web requests. */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Handles domain ITSMException hierarchy with exact status codes. */
    @ExceptionHandler(ITSMException.class)
    public Object handleItsmException(ITSMException ex, HttpServletRequest request, HttpServletResponse response) {
        return buildResponse(ex.getHttpStatus(), ex.getClass().getSimpleName(), ex.getMessage(), request, response);
    }

    /** Handles Bean Validation errors on API or form payloads. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationException(MethodArgumentNotValidException ex,
                                            HttpServletRequest request, HttpServletResponse response) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(400, "ValidationFailed", msg, request, response);
    }

    /** Handles Spring Security @PreAuthorize access denials. */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex,
                                     HttpServletRequest request, HttpServletResponse response) {
        return buildResponse(403, "AccessDenied", "You do not have permission to perform this action.",
                request, response);
    }

    /** Handles missing static resources or invalid routes cleanly. */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException ex,
                                 HttpServletRequest request, HttpServletResponse response) {
        return buildResponse(404, "NotFound", ex.getMessage(), request, response);
    }

    /** Handles unwrapped Spring Data access errors as PersistenceFailureException without leaking SQL details. */
    @ExceptionHandler(DataAccessException.class)
    public Object handleDataAccess(DataAccessException ex,
                                   HttpServletRequest request, HttpServletResponse response) {
        PersistenceFailureException wrapped = new PersistenceFailureException(
                "A database persistence error occurred. Please try again.", ex);
        return buildResponse(wrapped.getHttpStatus(), "PersistenceFailureException",
                wrapped.getMessage(), request, response);
    }

    /** Fallback handler for unexpected server errors. */
    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        return buildResponse(500, "InternalError",
                ex.getMessage() == null ? "An unexpected error occurred." : ex.getMessage(),
                request, response);
    }

    private Object buildResponse(int statusCode, String errorTitle, String message,
                                 HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api")) {
            ApiErrorResponse body = new ApiErrorResponse(Instant.now(), statusCode, errorTitle, message, path);
            return ResponseEntity.status(statusCode).body(body);
        }
        response.setStatus(statusCode);
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.valueOf(statusCode));
        mav.addObject("statusCode", statusCode);
        mav.addObject("errorTitle", errorTitle);
        mav.addObject("errorMessage", message);
        mav.addObject("path", path);
        return mav;
    }
}
