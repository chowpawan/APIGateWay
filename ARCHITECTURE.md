# Architecture Documentation

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Client Requests                            │
│                        (10,000+ concurrent)                          │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │      Nginx      │
                    │  Load Balancer  │
                    │  (Round-Robin)  │
                    └────────┬────────┘
              ┌─────────────┼─────────────┐
              │             │             │
         ┌────▼────┐   ┌────▼────┐   ┌────▼────┐
         │ Gateway  │   │ Gateway  │   │ Gateway  │
         │Instance 1│   │Instance 2│   │Instance 3│
         └────┬────┘   └────┬────┘   └────┬────┘
              │             │             │
              └─────────────┼─────────────┘
                            │
        ┌───────────────────▼───────────────────┐
        │      Redis Cluster (Distributed)      │
        │  - Rate Limit State                   │
        │  - Token Bucket Counters              │
        │  - Leaky Bucket Queues                │
        │  - TTL-based Cleanup                  │
        └───────────────────┬───────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
   ┌────▼────┐      ┌───────▼────┐      ┌──────▼──┐
   │  User   │      │  Product   │      │  Order  │
   │ Service │      │  Service   │      │ Service │
   └─────────┘      └────────────┘      └─────────┘
```

## Component Architecture

### 1. Request Flow

```
Client Request
    │
    ▼
Nginx Load Balancer
    │ (Round-robin, Rate Limit check)
    ▼
API Gateway Instance
    │
    ├─ CorrelationIdInterceptor (Add X-Correlation-ID header)
    │  │
    ├─ JwtAuthenticationInterceptor (Validate JWT token)
    │  │
    ├─ RateLimitingInterceptor (Token Bucket or Leaky Bucket)
    │  │
    ├─ ApiGatewayController (Route matching, request handling)
    │  │
    ├─ RouteManager (Find matching route)
    │  │
    ├─ RequestTransformer (Add/remove headers, transform path)
    │  │
    ├─ RequestForwarder (Forward to downstream service)
    │  │  ├─ Circuit Breaker (Resilience4j)
    │  │  ├─ Retry Logic (Exponential backoff)
    │  │  └─ Time Limiter (Timeout enforcement)
    │  │
    ├─ GatewayMetrics (Record metrics)
    │  │
    └─ Response (Back to client via Nginx)
```

### 2. Rate Limiting Architecture

#### Token Bucket Algorithm

```
┌──────────────────────────────────────────┐
│     Token Bucket Rate Limiter            │
│                                          │
│  Key: "tb_<client-id>"                  │
│                                          │
│  Redis Hash:                             │
│  {                                       │
│    "tokens": 950,                        │
│    "last_refill": 1707398400000          │
│  }                                       │
│                                          │
│  Refill Rate: 100 tokens/1s              │
│  Capacity: 1000 tokens                   │
│                                          │
│  Lua Script (Atomic):                    │
│  1. Calculate time passed since last     │
│     refill                               │
│  2. Add tokens = (time_passed / interval) │
│     * refill_rate                        │
│  3. Cap tokens at capacity               │
│  4. Consume 1 token if available         │
│  5. Return allow/deny + remaining quota  │
└──────────────────────────────────────────┘
```

#### Leaky Bucket Algorithm

```
┌──────────────────────────────────────────┐
│     Leaky Bucket Rate Limiter            │
│                                          │
│  Key: "lb_<client-id>"                  │
│                                          │
│  Redis Hash:                             │
│  {                                       │
│    "water_level": 450,                   │
│    "last_leak": 1707398400000            │
│  }                                       │
│                                          │
│  Leak Rate: 100 req/s (constant)         │
│  Capacity: 1000 requests                 │
│                                          │
│  Lua Script (Atomic):                    │
│  1. Calculate time passed since last     │
│     leak                                 │
│  2. Remove water = (time_passed) *       │
│     leak_rate                            │
│  3. Add 1 request (increase water_level) │
│  4. If water_level < capacity:           │
│     Allow request, return true           │
│  5. Else: Reject request, return false   │
└──────────────────────────────────────────┘
```

### 3. Circuit Breaker Pattern

```
┌─────────────────────────────────────────┐
│      Circuit Breaker States              │
└─────────────────────────────────────────┘

    ┌──────────────┐
    │   CLOSED     │ (Normal Operation)
    │              │ Success rate ≥ 50%
    │ Allow all    │
    │ requests     │
    └────┬─────┬──┘
         │     │
         │     │ Failure rate > 50%
         │     │ or timeout
         │     │
         │     ▼
         │  ┌────────────┐
         │  │    OPEN    │ (Circuit Open)
         │  │            │ Reject all requests
         │  │ Fail fast  │ Return 503
         │  │ immediately│
         │  └──────┬─────┘
         │         │
         │         │ Wait duration elapsed (30s)
         │         │
         │         ▼
         │  ┌──────────────────┐
         │  │  HALF_OPEN       │ (Testing)
         │  │                  │ Allow 5 requests
         │  │ Test requests    │
         │  │ to service       │
         │  └────┬───────┬─────┘
         │       │       │
         │       │       │ Still failing
         │       │       │
         │       │       ▼
         │       │     (Back to OPEN)
         │       │
         │       │ Success (all pass)
         │       │
         └───────┘ (Back to CLOSED)
```

### 4. Distributed State Management

```
┌──────────────────────────────────────────┐
│        Redis Distributed State            │
│                                          │
│  Storage Pattern:                        │
│  ├─ Token Bucket: tb_<key>               │
│  ├─ Leaky Bucket: lb_<key>               │
│  ├─ Circuit Breaker State: cb_<service> │
│  └─ User Sessions: session_<user-id>    │
│                                          │
│  Atomic Operations (Lua Scripts):        │
│  ├─ allowRequest() - O(1) lookup         │
│  ├─ getRemainingQuota() - O(1)           │
│  └─ getResetTime() - O(1)                │
│                                          │
│  Auto-expiration:                        │
│  ├─ TTL: 3600 seconds (1 hour)           │
│  ├─ Eviction: allkeys-lru                │
│  └─ Persistence: AOF or RDB              │
└──────────────────────────────────────────┘
```

### 5. Request Transformation Pipeline

```
Original Request
    │
    ├─ Add Correlation ID
    │  └─ X-Correlation-ID: <uuid>
    │
    ├─ JWT Authentication
    │  └─ Extract subject from token
    │
    ├─ Rate Limiting Check
    │  ├─ Lookup rate limit key (IP/API-Key/User-ID)
    │  └─ Check Token Bucket or Leaky Bucket
    │
    ├─ Route Matching
    │  ├─ Path pattern matching (Ant Path)
    │  └─ Priority-based selection
    │
    ├─ Request Transformation
    │  ├─ Add gateway headers
    │  ├─ Add X-Forwarded-* headers
    │  └─ Transform path (optional)
    │
    ├─ Forward Request
    │  ├─ Circuit Breaker protection
    │  ├─ Retry with exponential backoff
    │  └─ Timeout enforcement
    │
    ├─ Record Metrics
    │  ├─ Request latency
    │  ├─ Success/failure count
    │  └─ Rate limit hits
    │
    └─ Response (with headers)
        ├─ X-RateLimit-Remaining
        ├─ X-RateLimit-Reset
        └─ X-Correlation-ID (preserved)
```

## Performance Characteristics

### Latency Profile

```
Request Latency Breakdown (sub-50ms target):

Gateway Processing:
├─ HTTP request parsing:     ~1ms
├─ Correlation ID:           <0.1ms
├─ JWT validation:           ~2ms
├─ Rate limit Redis lookup:  ~5ms  (network + computation)
├─ Route matching:           <0.1ms
├─ Request forwarding:       ~30ms (depends on upstream)
├─ Response processing:      ~2ms
└─ Total Gateway Overhead:   ~10ms (excluding upstream latency)

Percentile Distribution:
├─ P50 (median):  ~15ms
├─ P95:           ~40ms
├─ P99:           ~48ms
└─ P99.9:         ~55ms
```

### Throughput Profile

```
Throughput Capacity:

Single Instance (8 CPU cores):
├─ Concurrent connections:  1000+
├─ RPS (requests/sec):     2000+
├─ Rate limit checks/sec:  2000+
└─ Redis ops/sec:         2000+

3-Instance Cluster (with Nginx LB):
├─ Concurrent connections: 10,000+
├─ RPS (requests/sec):     10,000+ (achievable with proper tuning)
├─ Rate limit checks/sec:  10,000+ (Redis bottleneck mitigated via pipeline)
└─ Redis throughput:       ~10k ops/sec (with pipelining)
```

### Memory Usage

```
Memory Breakdown (per instance):

JVM Heap:
├─ Spring Boot Framework:  ~200MB
├─ Redis client libs:      ~20MB
├─ Resilience4j:          ~10MB
├─ Request buffers:       ~50MB (configurable)
└─ Other:                 ~20MB
Total JVM: ~300MB (min) to 512MB (configured)

Redis:
├─ Rate limit entries:    ~10KB per active client
├─ Circuit breaker state: ~1KB per service
├─ Metadata/overhead:     ~50MB (fixed)
Total Redis: ~50-200MB (depends on active clients)

Nginx:
├─ Process + buffers:     ~20-50MB
└─ Per connection:        ~5-10KB
```

## Monitoring Architecture

```
┌─────────────────────────────────────────────────┐
│         Observability Stack                      │
├─────────────────────────────────────────────────┤
│                                                  │
│  Applications                                    │
│  ├─ API Gateway Instances (3x)                  │
│  │  └─ Micrometer Metrics Export                │
│  │                                              │
│  └─ Prometheus Scraper                          │
│     ├─ Scrape interval: 5s                      │
│     ├─ Metrics endpoint: /actuator/prometheus   │
│     └─ Storage: TSDB (15 days retention)        │
│                                                  │
│  Metrics Collected                              │
│  ├─ gateway.requests.total                      │
│  ├─ gateway.requests.active                     │
│  ├─ gateway.requests.success/failed             │
│  ├─ gateway.request.latency (percentiles)       │
│  ├─ gateway.route.latency (per-route)           │
│  ├─ gateway.ratelimit.exceeded                  │
│  ├─ gateway.circuitbreaker.open                 │
│  └─ jvm.* (standard JVM metrics)                │
│                                                  │
│  Visualization                                  │
│  └─ Grafana Dashboards                          │
│     ├─ Gateway Overview                         │
│     ├─ Request Latency Analysis                 │
│     ├─ Rate Limit Statistics                    │
│     ├─ Circuit Breaker Status                   │
│     └─ Resource Utilization                     │
│                                                  │
│  Logging                                        │
│  ├─ Structured logs (JSON format)               │
│  ├─ Correlation ID tracking                     │
│  ├─ Request/Response details                    │
│  └─ Error traces                                │
│                                                  │
│  Distributed Tracing (Optional)                 │
│  └─ Correlation IDs enable tracing across       │
│     multiple services                           │
│                                                  │
└─────────────────────────────────────────────────┘
```

## Scaling Strategy

### Horizontal Scaling

```
Stage 1: Single Instance
├─ 1 Gateway + 1 Redis
├─ ~2000 RPS capacity
└─ Good for development/testing

Stage 2: 3-Instance Cluster
├─ 3 Gateways + Nginx LB + 1 Redis
├─ ~6000 RPS capacity
├─ Distributed rate limiting
└─ Production-ready

Stage 3: Multi-Region
├─ Multiple 3-instance clusters per region
├─ Redis replication for backup
├─ Global load balancing
└─ High availability + disaster recovery

Stage 4: Kubernetes
├─ Horizontal pod autoscaling (HPA)
├─ Redis cluster (distributed)
├─ Service mesh (optional)
└─ Full cloud-native deployment
```

### Database Optimization

```
Redis Optimization:

1. Connection Pooling
   ├─ Max pool size: 20
   ├─ Min idle: 5
   └─ Validates connections

2. Pipelining
   ├─ Batch multiple commands
   ├─ Reduces network roundtrips
   └─ Implemented in rate limiter

3. Lua Scripts
   ├─ Atomic operations
   ├─ Prevents race conditions
   └─ Server-side computation

4. Memory Management
   ├─ TTL: 3600s for all keys
   ├─ Eviction: allkeys-lru
   ├─ Max memory: 512MB
   └─ Persistence: AOF/RDB
```

## Security Architecture

```
┌────────────────────────────────────────┐
│     Security Layers                    │
├────────────────────────────────────────┤
│                                        │
│  Layer 1: Transport Security           │
│  └─ TLS/HTTPS (via Nginx)             │
│                                        │
│  Layer 2: Authentication               │
│  ├─ JWT tokens                         │
│  ├─ Signature verification             │
│  └─ Expiration validation              │
│                                        │
│  Layer 3: Authorization                │
│  ├─ API key validation (header)       │
│  ├─ Role-based access (claims)        │
│  └─ Rate limit quotas                 │
│                                        │
│  Layer 4: Rate Limiting                │
│  ├─ IP-based limits                   │
│  ├─ API-key-based limits              │
│  └─ User-based limits                 │
│                                        │
│  Layer 5: Request Validation           │
│  ├─ Path validation                   │
│  ├─ Header filtering                  │
│  └─ Request size limits                │
│                                        │
│  Layer 6: Circuit Breaking             │
│  └─ Protect against cascading failures│
│                                        │
└────────────────────────────────────────┘
```

---

**Document Version**: 1.0  
**Last Updated**: February 2026
