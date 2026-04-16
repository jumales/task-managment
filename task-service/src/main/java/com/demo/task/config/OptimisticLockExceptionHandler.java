package com.demo.task.config;

import com.demo.common.dto.ErrorResponse;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Handles optimistic-lock conflicts thrown by Hibernate when a concurrent update is detected.
 * Placed in task-service (not common) because common has no JPA dependency.
 *
 * <p>Both exception types are caught:
 * <ul>
 *   <li>{@link OptimisticLockException} — raw JPA exception, thrown when the transaction
 *       boundary is inside the service method itself.</li>
 *   <li>{@link ObjectOptimisticLockingFailureException} — Spring's wrapper, thrown when
 *       the transaction boundary is at the controller proxy level.</li>
 * </ul>
 */
@RestControllerAdvice
public class OptimisticLockExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockExceptionHandler.class);

    /**
     * Returns HTTP 409 Conflict when a concurrent modification is detected on a task.
     * The client should re-fetch the resource and retry the update with the new version.
     */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(Exception ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "The resource was modified by another user. Re-fetch and retry.",
                request.getRequestURI(),
                LocalDateTime.now());
    }
}
