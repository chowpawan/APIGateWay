# API Gateway - Development Setup & Quick Start

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- curl (for testing)

### Steps

1. **Clone/Navigate to project**
```bash
cd /Users/venkatapavankalyanpoka/ApiGateWay
```

2. **Build the project**
```bash
mvn clean package
```

3. **Option A: Run locally with Docker Compose**
```bash
docker-compose up -d
# Services:
# - API Gateway: http://localhost (via Nginx)
# - Prometheus: http://localhost:9090
# - Grafana: http://localhost:3000
```

4. **Option B: Run standalone (requires Redis)**
```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Run application
java -jar target/api-gateway-1.0.0.jar
# Available at: http://localhost:8080
```

## Testing

### Health Check
```bash
curl http://localhost:8080/health
```

### Get Routes
```bash
curl http://localhost:8080/admin/routes
```

### Test with API Key
```bash
curl -H "X-API-Key: test-key" http://localhost:8080/health
```

### Run Tests
```bash
mvn test
```

### Load Testing
```bash
chmod +x load-test.sh
./load-test.sh http://localhost:8080 1000 100
```

## Configuration

Key files:
- `src/main/resources/application.yml` - Main configuration
- `docker/nginx.conf` - Nginx load balancer config
- `docker-compose.yml` - Docker Compose setup

### Quick Config Changes

**Change rate limit capacity:**
Edit `application.yml`:
```yaml
ratelimit:
  token-bucket:
    capacity: 5000  # increased from 1000
```

**Add new route:**
Edit `application.yml`:
```yaml
gateway:
  routes:
    - id: new-service
      path: /api/new/**
      destinationUrl: http://new-service:8080
      priority: 10
```

## Development

### IDE Setup (IntelliJ IDEA)
1. Open project folder
2. Maven should auto-sync
3. Run: `ApiGatewayApplication.main()`

### Code Structure
```
src/main/java/com/apigateway/
├── ApiGatewayApplication.java      # Entry point
├── ApiGatewayController.java        # Main controller
├── config/                          # Configuration
│   ├── WebConfig.java
│   ├── RedisConfig.java
│   └── Resilience4jConfig.java
├── ratelimit/                       # Rate limiting
│   ├── RateLimiter.java
│   ├── TokenBucketRateLimiter.java
│   └── LeakyBucketRateLimiter.java
├── router/                          # Routing
│   ├── Route.java
│   ├── RouteManager.java
│   └── RequestForwarder.java
├── filter/                          # Interceptors
│   ├── CorrelationIdInterceptor.java
│   └── RateLimitingInterceptor.java
├── auth/                            # Authentication
│   ├── JwtTokenProvider.java
│   └── JwtAuthenticationInterceptor.java
├── transformer/                     # Request transformation
│   ├── RequestTransformation.java
│   └── RequestTransformer.java
└── metrics/                         # Observability
    └── GatewayMetrics.java
```

### Common Tasks

**Debug rate limiting:**
```bash
# Enable debug logging
# In application.yml set: logging.level.com.apigateway: DEBUG

# Check Redis state
redis-cli KEYS "tb_*"
redis-cli HGETALL "tb_127.0.0.1"
```

**Monitor in real-time:**
```bash
# Terminal 1: Run gateway
java -jar target/api-gateway-1.0.0.jar

# Terminal 2: Send requests
while true; do curl http://localhost:8080/health; sleep 0.5; done

# Terminal 3: Check metrics
watch -n 1 'curl -s http://localhost:8080/actuator/prometheus | grep gateway_'
```

**View circuit breaker state:**
```bash
curl http://localhost:8080/admin/status
```

## Docker Compose Services

```
├── redis          (Port 6379)
├── api-gateway-1  (Port 8080)
├── api-gateway-2  (Port 8081)
├── api-gateway-3  (Port 8082)
├── nginx          (Port 80)
├── prometheus     (Port 9090)
└── grafana        (Port 3000, admin/admin)
```

## Debugging

### Enable verbose logging
Add to `application.yml`:
```yaml
logging:
  level:
    com.apigateway: DEBUG
    org.springframework.web: DEBUG
    org.springframework.data.redis: DEBUG
```

### Check Redis connectivity
```bash
# Inside container
docker-compose exec redis redis-cli ping

# Locally (if Redis exposed)
redis-cli ping
```

### View Docker logs
```bash
docker-compose logs -f api-gateway-1
docker-compose logs -f nginx
docker-compose logs -f redis
```

## Performance Testing

### Use Apache Bench
```bash
# 10,000 requests, 100 concurrent
ab -n 10000 -c 100 http://localhost/health

# With correlation ID
ab -n 5000 -c 50 \
  -H "X-Correlation-ID: test-123" \
  http://localhost/health
```

### Use wrk (if installed)
```bash
# 4 threads, 100 connections, 30 second duration
wrk -t4 -c100 -d30s http://localhost/health
```

## Production Checklist

- [ ] Change JWT secret in environment variables
- [ ] Configure proper routes for your services
- [ ] Set appropriate rate limits for your use case
- [ ] Configure Redis persistence (AOF/RDB)
- [ ] Set up monitoring alerts in Prometheus
- [ ] Configure Grafana dashboards
- [ ] Enable HTTPS/TLS in Nginx
- [ ] Set up centralized logging (ELK)
- [ ] Configure backup strategy for Redis
- [ ] Load test to verify SLAs
- [ ] Document custom configurations
- [ ] Set up auto-scaling policies

## Support & Resources

- **Documentation**: See [README.md](README.md)
- **Issues**: GitHub Issues
- **Logs**: Check application logs for detailed errors
- **Metrics**: Prometheus at localhost:9090
- **Visualization**: Grafana at localhost:3000

---

Happy coding! 🚀
