package com.apigateway.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.*;

/**
 * Request transformer for modifying headers and paths
 */
@Slf4j
@Component
public class RequestTransformer {

    /**
     * Apply transformations to request
     */
    public HttpServletRequestWrapper transform(HttpServletRequest request, RequestTransformation transformation) {
        return new TransformingRequestWrapper(request, transformation);
    }

    /**
     * Custom request wrapper that applies transformations
     */
    private static class TransformingRequestWrapper extends HttpServletRequestWrapper {
        private final RequestTransformation transformation;
        private final Map<String, String> modifiedHeaders;

        public TransformingRequestWrapper(HttpServletRequest request, RequestTransformation transformation) {
            super(request);
            this.transformation = transformation;
            this.modifiedHeaders = new HashMap<>();
            applyTransformations();
        }

        private void applyTransformations() {
            // Copy existing headers and apply removals/additions
            HttpServletRequest httpRequest = (HttpServletRequest) getRequest();
            Enumeration<String> headerNames = httpRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (transformation.getHeaderRemovals() == null || 
                    !transformation.getHeaderRemovals().containsKey(headerName)) {
                    modifiedHeaders.put(headerName, httpRequest.getHeader(headerName));
                }
            }

            // Add new headers
            if (transformation.getHeaderAdditions() != null) {
                modifiedHeaders.putAll(transformation.getHeaderAdditions());
            }
        }

        @Override
        public String getHeader(String name) {
            return modifiedHeaders.getOrDefault(name, super.getHeader(name));
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> values = new ArrayList<>();
            String value = getHeader(name);
            if (value != null) {
                values.add(value);
            }
            return Collections.enumeration(values);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(modifiedHeaders.keySet());
        }

        @Override
        public String getRequestURI() {
            String originalURI = super.getRequestURI();
            if (transformation.getPathPrefix() != null && !transformation.getPathPrefix().isBlank()) {
                return transformation.getPathPrefix() + originalURI;
            }
            return originalURI;
        }
    }
}
