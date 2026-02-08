package com.apigateway.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Route definition for API Gateway
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Route {
    private String id;
    private String path;              // e.g., "/api/users/**"
    private String destinationUrl;    // e.g., "http://user-service:8081"
    private String name;
    private boolean enabled;
    private int priority;             // Lower number = higher priority
    private long timeoutMs;           // Request timeout
    private int maxRetries;           // Number of retries on failure
    private boolean stripPathPrefix;  // Remove matching path from forwarded request
}
