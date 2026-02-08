# API Gateway – Development Setup & Quick Start

## 🚀 Quick Start

### Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker Desktop** (or Homebrew Redis for macOS)
- **curl** & **Apache Bench (ab)** for testing

---

## 1. Build the Project

```bash
mvn clean package
```

---

## 2. Start Redis

### Option A: Docker (Standard)

> **Note:** If Docker Engine fails to start on macOS, ensure **Full Disk Access** is enabled in **System Settings → Privacy & Security**.

```bash
docker run -d --name gateway-redis -p 6379:6379 redis:7-alpine
```

### Option B: Homebrew (macOS Native Fallback)

```bash
brew install redis
brew services start redis
```

---

## 3. Run the Application

### Via Maven

```bash
mvn spring-boot:run
```

### Via JAR

```bash
java -jar target/api-gateway-1.0.0.jar
```

Application available at: [**http://localhost:8080**](http://localhost:8080)

---

## 🛠 Troubleshooting & Common Fixes

### 1. "2 Beans Found" Startup Error

**Symptom:**

```
NoUniqueBeanDefinitionException: expected single matching bean but found 2
```

**Cause:** Multiple `RateLimiter` implementations (Token Bucket & Leaky Bucket).

**Fix:** Use a qualifier in `RateLimitingInterceptor`:

```java
@Qualifier("tokenBucketRateLimiter")
```

---

### 2. Empty Redis Keys (`(empty array)`)

If Redis does not show rate-limit keys after testing:

**✔ Registration Check**

- Verify `src/main/java/com/apigateway/config/WebMvcConfig.java` exists
- Ensure the interceptor is added to `InterceptorRegistry`

**✔ The 404 Trap**

- Interceptors only fire for valid mapped routes
- Ensure a controller exists:

```java
@RestController
@RequestMapping("/health")
```

**✔ IPv6 Loopback Keys**

- Keys like:

```
tb_0:0:0:0:0:0:0:1
```

Indicate the `X-API-Key` header is missing or misspelled.

---

### 3. Docker "Permission Denied" (macOS)

**Fix:** Grant **Full Disk Access** to Docker under:

```
System Settings → Privacy & Security → Full Disk Access
```

---

## 🧪 Testing

### Health Check

```bash
curl -i http://localhost:8080/health
```

### Test Rate Limiting

```bash
# Single request with API Key (case-sensitive)
curl -H "X-API-Key: test-key" http://localhost:8080/health
```

Verify Redis keys:

```bash
redis-cli keys "tb_*"
```

---

### Load Testing

#### Using Provided Script

```bash
chmod +x load-test.sh
./load-test.sh http://localhost:8080 1000 100
```

#### Manual Stress Test (Apache Bench)

```bash
ab -n 1000 -c 50 -H "X-API-Key: test-key" http://localhost:8080/health
```

---

## 📂 Code Structure

```
src/main/java/com/apigateway/
├── config/
│   ├── WebMvcConfig.java            # Registers interceptors (required)
│   └── RedisConfig.java             # Redis serializer & connection setup
├── ratelimit/
│   ├── TokenBucketRateLimiter.java  # Token-based rate limiting
│   └── LeakyBucketRateLimiter.java  # Queue-based rate limiting
├── filter/
│   └── RateLimitingInterceptor.java # Key extraction & limiter trigger
└── router/
    └── RequestForwarder.java        # Downstream proxy logic
```

---

## ⚙️ Configuration (`application.yml`)

```yaml
ratelimit:
  enabled: true
  key-generator: api-key   # Options: ip | api-key | user-id
  token-bucket:
    capacity: 1000         # Max burst size
    refill-rate: 100       # Tokens per second
```

---

## 📊 Monitoring & Debugging

- **Prometheus:** [http://localhost:9090](http://localhost:9090)
- **Grafana:** [http://localhost:3000](http://localhost:3000)\
  Default credentials: `admin / admin`

### Live Redis Monitor

```bash
redis-cli monitor
```

### Debug Logging

```yaml
logging:
  level:
    com.apigateway: DEBUG
```

---
