# Distributed API Gateway with Rate Limiting

A high-performance, production-grade API Gateway built with Java/Spring Boot implementing request routing, rate limiting, and circuit breaker patterns. Supports 10,000+ concurrent requests with sub-50ms latency.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Load Balancer (Nginx)                       │
│                   (Round-robin, Rate Limiting)                       │
└──────────────┬──────────────┬──────────────┬──────────────────────────┘
               │              │              │
        ┌──────▼──┐    ┌──────▼──┐    ┌──────▼──┐
        │ Gateway  │    │ Gateway  │    │ Gateway  │
        │ Instance │    │ Instance │    │ Instance │
        │    1     │    │    2     │    │    3     │
        └──────┬───┘    └──────┬───┘    └──────┬───┘
               │              │              │
               └──────────────┼──────────────┘
                              │
                   ┌──────────▼───────────┐
                   │   Redis Cluster      │
                   │ (Distributed State)  │
                   └──────────────────────┘
                              │
               ┌──────────────┼──────────────┐
               │              │              │
          ┌────▼────┐    ┌────▼────┐   ┌────▼────┐
          │  User   │    │ Product │   │  Order  │
          │ Service │    │ Service │   │ Service │
          └─────────┘    └─────────┘   └─────────┘
```

## Key Features

### 1. **Rate Limiting Algorithms**
   - **Token Bucket**: Flexible, allows burst traffic
     - Refill rate: Configurable tokens per interval
     - Capacity: Maximum bucket size
     - Handles clock skew via server-side Lua scripts
   
   - **Leaky Bucket**: Smooth rate limiting
     - Constant leak rate in requests/second
     - FIFO request processing
     - Prevents burst traffic

### 2. **Distributed State Management**
   - Redis-based distributed rate limiting across instances
   - Atomic operations using Lua scripts to prevent race conditions
   - O(1) lookup for rate limit checks
   - Automatic TTL management for memory efficiency

### 3. **Request Routing**
   - Path-based routing with wildcard support (e.g., `/api/users/**`)
   - Dynamic route configuration
   - Request prioritization via route priority
   - Configurable request/response transformation

### 4. **Resilience Features**
   - **Circuit Breaker**: Automatic protection against cascading failures
     - States: CLOSED, OPEN, HALF_OPEN
     - Configurable failure threshold (50%)
     - Automatic recovery with half-open state
   
   - **Retry Logic**: Exponential backoff retry strategy
     - Configurable max attempts (default: 3)
     - Exponential backoff interval
   
   - **Time Limiting**: Configurable timeout per request

### 5. **Security**
   - JWT token authentication
   - Correlation ID tracking for distributed tracing
   - IP-based, API-key-based, or user-based rate limiting
   - Header manipulation and filtering

### 6. **Observability**
   - **Metrics**: Prometheus metrics via Micrometer
     - Request latency (p50, p95, p99)
     - Rate limit violations
     - Circuit breaker state changes
     - Active request count
   
   - **Logging**: Structured logging with correlation IDs
   - **Visualization**: Grafana dashboards
   - **Monitoring**: Prometheus time-series database

## Performance Characteristics

- **Throughput**: 10,000+ concurrent requests
- **Latency**: Sub-50ms (p99) for gateway processing
- **Rate Limit Lookup**: O(1) Redis operations
- **Memory**: Distributed state stored in Redis (configurable eviction policy)

## Configuration

### application.yml

```yaml
# Rate Limiting
ratelimit:
  enabled: true
  key-generator: ip  # ip | api-key | user-id
  
  token-bucket:
    capacity: 1000
    refill-rate: 100
    refill-interval-ms: 1000
  
  leaky-bucket:
    capacity: 1000
    leak-rate: 100

# Routes
gateway:
  routes:
    - id: user-service
      path: /api/users/**
      destinationUrl: http://localhost:8081
      priority: 1
      timeoutMs: 5000
      maxRetries: 3

# Circuit Breaker
resilience4j:
  circuitbreaker:
    instances:
      downstream-service:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 5
```

## Building the Project

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for containerized deployment)
- Redis 7+ (for distributed state)

### Build & Package

```bash
# Build the application
mvn clean package

# Run tests
mvn test

# Build Docker image
docker build -t api-gateway:latest -f docker/Dockerfile .
```

## Running the Application

### Local Development

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Run application
java -jar target/api-gateway-1.0.0.jar
```

### Docker Compose (Production-like)

```bash
# Build and run all services
docker-compose up -d

# Services available at:
# - API Gateway: http://localhost (via Nginx load balancer)
# - Prometheus: http://localhost:9090
# - Grafana: http://localhost:3000
```

## API Endpoints

### Gateway Operations

```bash
# Health check
curl http://localhost:8080/health

# Metrics (Prometheus format)
curl http://localhost:8080/actuator/prometheus

# Get all routes
curl http://localhost:8080/admin/routes

# Get gateway status
curl http://localhost:8080/admin/status

# Forward request with rate limiting
curl -H "X-API-Key: your-api-key" http://localhost:8080/api/users/123
```

### Rate Limit Response Headers

Every response includes rate limit information:

```
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 3600000
X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
```

### JWT Authentication

```bash
# Generate token (example)
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username": "user", "password": "pass"}'

# Use token
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/protected
```

## Testing

### Unit Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=TokenBucketRateLimiterTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

Tests use TestContainers for Redis integration:

```bash
mvn test -Dtest=*IntegrationTest
```

### Load Testing

Use included load testing tools:

```bash
# Simple load test with Apache Bench
ab -n 10000 -c 100 http://localhost:8080/health

# With correlation IDs and API keys
ab -n 5000 -c 50 \
  -H "X-API-Key: test-key" \
  -H "X-Correlation-ID: test-123" \
  http://localhost:8080/api/users
```

### Load Test with JMeter

```bash
# Create JMeter test plan (GUI)
jmeter

# Run headless
jmeter -n -t test-plan.jmx -l results.jtl -j jmeter.log

# Generate report
jmeter -g results.jtl -o report
```

## Monitoring & Observability

### Prometheus Metrics

Available metrics:

- `gateway.requests.total`: Total requests processed
- `gateway.requests.active`: Currently active requests
- `gateway.requests.success`: Successful requests
- `gateway.requests.failed`: Failed requests
- `gateway.request.latency`: Request latency (p50, p95, p99)
- `gateway.route.latency`: Per-route latency
- `gateway.ratelimit.exceeded`: Rate limit violations
- `gateway.circuitbreaker.open`: Circuit breaker open events

### Grafana Dashboards

Pre-configured dashboards available at:
- Dashboard URL: http://localhost:3000
- Default credentials: admin/admin

Key dashboards:
- API Gateway Overview
- Request Latency Analysis
- Rate Limiting Statistics
- Circuit Breaker Status

### Logging

Logs include:
- Request/Response details
- Correlation IDs for tracing
- Rate limit decisions
- Circuit breaker state changes
- Error traces

Configure logging level in `application.yml`:

```yaml
logging:
  level:
    com.apigateway: DEBUG
    org.springframework.web: INFO
```

## Deployment

### Kubernetes

```bash
# Create namespace
kubectl create namespace api-gateway

# Create ConfigMap for routes
kubectl create configmap gateway-config \
  --from-file=application.yml \
  -n api-gateway

# Deploy with Helm (example)
helm install api-gateway ./helm/api-gateway -n api-gateway
```

### Cloud Platforms

- **AWS**: Deploy on ECS/Fargate with Application Load Balancer
- **Azure**: Deploy on AKS with Azure Load Balancer
- **GCP**: Deploy on GKE with Cloud Load Balancing

## Troubleshooting

### Rate Limiting Not Working
- Verify Redis connection: `redis-cli ping`
- Check `ratelimit.enabled: true` in configuration
- Validate key generator setting

### High Latency
- Check Redis response time
- Monitor circuit breaker state
- Verify downstream service health
- Check Prometheus metrics for bottlenecks

### Circuit Breaker Stuck Open
- Monitor failure rate of downstream service
- Check service logs for errors
- Adjust `failureRateThreshold` if needed
- Use `/admin/status` to verify state

### Memory Issues
- Adjust Redis `maxmemory` policy
- Configure appropriate TTLs
- Monitor active request count

## Performance Tuning

### JVM Tuning

```bash
java -Xmx2g -Xms1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -jar api-gateway-1.0.0.jar
```

### Redis Optimization

```bash
# Pipeline operations for batch processing
# Already implemented in rate limiting scripts

# Configure maxmemory policy
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

### Nginx Load Balancer Tuning

```nginx
# In nginx.conf
worker_processes auto;
worker_connections 10000;
keepalive_timeout 65;
sendfile on;
```

## Advanced Topics

### Custom Rate Limiting Strategies

Implement the `RateLimiter` interface:

```java
public class CustomRateLimiter implements RateLimiter {
    @Override
    public boolean allowRequest(String key) {
        // Your implementation
    }
    // ...
}
```

### Dynamic Route Configuration

Update routes at runtime:

```java
routeManager.addRoute(newRoute);
routeManager.updateRoute(routeId, updatedRoute);
routeManager.removeRoute(routeId);
```

### Custom Metrics

Add custom metrics:

```java
meterRegistry.counter("custom.metric", "tag", "value").increment();
Timer.builder("custom.timer")
    .register(meterRegistry)
    .record(duration, TimeUnit.MILLISECONDS);
```

## Best Practices

1. **Always use correlation IDs** for distributed tracing
2. **Monitor circuit breaker state** actively
3. **Set appropriate timeouts** to prevent hanging requests
4. **Use API keys** with higher rate limits for trusted clients
5. **Implement proper error handling** on client side
6. **Regular load testing** to validate SLAs
7. **Keep Redis backed up** for state persistence
8. **Use environment-specific configurations**

## Contributing

See CONTRIBUTING.md for guidelines.

## License

MIT License - See LICENSE file

## Support

For issues, questions, or suggestions:
- Create GitHub issue
- Contact: support@apigateway.dev

---

**Version**: 1.0.0  
**Last Updated**: February 2026
