package com.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Interceptor for adding correlation IDs to all requests
 * Enables distributed tracing across multiple services
 */
@Slf4j
@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        log.debug("Correlation ID: {} for request: {} {}", correlationId, request.getMethod(), request.getRequestURI());
        return true;
    }

    public static String getCorrelationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        return correlationId != null ? correlationId.toString() : null;
    }
}
