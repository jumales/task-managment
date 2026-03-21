package com.demo.common.web;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Logs all input parameters of every {@code @RestController} method at DEBUG level.
 * Sensitive parameters (e.g. password, token, secret) are masked before logging.
 * Enabled by setting {@code logging.level.com.demo.common.web=DEBUG}.
 */
@Aspect
@Component
public class ControllerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerLoggingAspect.class);

    private static final String MASKED = "***";

    /** Parameter name substrings that indicate sensitive data and must never be logged. */
    private static final Set<String> SENSITIVE_PARAMS = Set.of(
            "password", "token", "secret", "authorization", "credential"
    );

    /**
     * Intercepts all public methods in any {@code @RestController} class and logs
     * the controller name, method name, and all parameter name-value pairs at DEBUG level.
     */
    @Before("within(@org.springframework.web.bind.annotation.RestController *)")
    public void logInputParameters(JoinPoint joinPoint) {
        if (!log.isDebugEnabled()) return;

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            String name = paramNames[i];
            params.put(name, isSensitive(name) ? MASKED : args[i]);
        }

        log.debug("[{}] {}() params: {}",
                joinPoint.getTarget().getClass().getSimpleName(),
                signature.getMethod().getName(),
                params);
    }

    /** Returns true if the parameter name indicates sensitive data that should not be logged. */
    private boolean isSensitive(String paramName) {
        String lower = paramName.toLowerCase();
        return SENSITIVE_PARAMS.stream().anyMatch(lower::contains);
    }
}
