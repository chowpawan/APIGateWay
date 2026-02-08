package com.apigateway.transformer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request transformation configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestTransformation {
    private String path;
    private Map<String, String> headerAdditions; // Headers to add
    private Map<String, String> headerRemovals;  // Headers to remove
    private String pathPrefix;                    // Prefix to add to path
    private boolean preserveOriginalPath;
}
