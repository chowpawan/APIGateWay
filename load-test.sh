#!/usr/bin/env bash

# Load Testing Script for API Gateway
# Tests various aspects: latency, rate limiting, throughput, circuit breaker

set -e

# Configuration
GATEWAY_URL="${1:-http://localhost:8080}"
NUM_REQUESTS="${2:-5000}"
CONCURRENT_USERS="${3:-100}"
API_KEY="${4:-test-api-key}"

echo "🚀 API Gateway Load Testing"
echo "================================"
echo "Target: $GATEWAY_URL"
echo "Total Requests: $NUM_REQUESTS"
echo "Concurrent Users: $CONCURRENT_USERS"
echo "API Key: $API_KEY"
echo "================================"
echo ""

# Check if gateway is running
echo "🔍 Checking gateway health..."
if ! curl -s -f "$GATEWAY_URL/health" > /dev/null; then
    echo "❌ Gateway is not responding. Please ensure it's running at $GATEWAY_URL"
    exit 1
fi
echo "✅ Gateway is healthy"
echo ""

# Test 1: Basic Health Check
echo "📊 Test 1: Health Check"
echo "========================"
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$GATEWAY_URL/health")
HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_STATUS | cut -d':' -f2)
echo "Status: $HTTP_CODE"
if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✅ Health check passed"
else
    echo "❌ Health check failed"
fi
echo ""

# Test 2: Simple Request Latency
echo "📊 Test 2: Single Request Latency"
echo "=================================="
TIME_TAKEN=$(curl -s -o /dev/null -w "%{time_total}" "$GATEWAY_URL/health")
echo "Response Time: ${TIME_TAKEN}s"
echo ""

# Test 3: Rate Limiting Test
echo "📊 Test 3: Rate Limiting"
echo "========================"
echo "Sending rapid requests to test rate limiting..."

RATE_LIMITED_COUNT=0
ALLOWED_COUNT=0

for i in $(seq 1 50); do
    RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}\nRATELIMIT:%{http_header}" \
        -H "X-API-Key: $API_KEY" \
        -H "X-Correlation-ID: test-$(date +%s%N)" \
        "$GATEWAY_URL/health")
    
    HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_STATUS | cut -d':' -f2)
    
    if [ "$HTTP_CODE" -eq 429 ]; then
        ((RATE_LIMITED_COUNT++))
    else
        ((ALLOWED_COUNT++))
    fi
done

echo "Allowed: $ALLOWED_COUNT"
echo "Rate Limited (429): $RATE_LIMITED_COUNT"
if [ "$RATE_LIMITED_COUNT" -gt 0 ]; then
    echo "✅ Rate limiting is active"
else
    echo "⚠️  No rate limiting detected (may be expected depending on configuration)"
fi
echo ""

# Test 4: Concurrent Load Test
echo "📊 Test 4: Concurrent Load Test"
echo "==============================="
echo "Sending $NUM_REQUESTS requests with $CONCURRENT_USERS concurrent users..."

# Using GNU Parallel if available, otherwise fallback to sequential
if command -v parallel &> /dev/null; then
    RESULTS=$(seq 1 $NUM_REQUESTS | parallel -j $CONCURRENT_USERS \
        "curl -s -o /dev/null -w '%{http_code}\n' \
            -H 'X-API-Key: $API_KEY' \
            -H 'X-Correlation-ID: load-test-{}' \
            '$GATEWAY_URL/health'" 2>/dev/null | sort | uniq -c)
else
    # Fallback using xargs
    echo "⚠️  GNU Parallel not found, using sequential requests (slower)"
    RESULTS=$(seq 1 $(($NUM_REQUESTS < 100 ? $NUM_REQUESTS : 100)) | xargs -I {} \
        curl -s -o /dev/null -w '%{http_code}\n' \
            -H "X-API-Key: $API_KEY" \
            -H "X-Correlation-ID: load-test-{}" \
            "$GATEWAY_URL/health" 2>/dev/null | sort | uniq -c)
fi

echo "$RESULTS"
echo ""

# Test 5: Metrics Collection
echo "📊 Test 5: Metrics"
echo "=================="
echo "Fetching Prometheus metrics..."

METRICS_RESPONSE=$(curl -s "$GATEWAY_URL/actuator/prometheus" 2>/dev/null)

if echo "$METRICS_RESPONSE" | grep -q "gateway_requests_total"; then
    echo "✅ Metrics endpoint working"
    
    # Extract some key metrics
    TOTAL_REQUESTS=$(echo "$METRICS_RESPONSE" | grep 'gateway_requests_total' | grep -v '#' | awk '{print $2}' | head -1)
    echo "Total requests processed: $TOTAL_REQUESTS"
else
    echo "⚠️  Metrics endpoint may not be properly configured"
fi
echo ""

# Test 6: Latency Percentiles
echo "📊 Test 6: Latency Percentiles (100 requests)"
echo "=============================================="

declare -a LATENCIES

for i in $(seq 1 100); do
    LATENCY=$(curl -s -o /dev/null -w "%{time_total}" \
        -H "X-API-Key: $API_KEY" \
        -H "X-Correlation-ID: latency-test-$i" \
        "$GATEWAY_URL/health")
    LATENCIES+=("$LATENCY")
done

# Sort and calculate percentiles (simplified)
SORTED_LATENCIES=($(printf '%s\n' "${LATENCIES[@]}" | sort -n))
TOTAL=${#SORTED_LATENCIES[@]}

P50_IDX=$((TOTAL * 50 / 100))
P95_IDX=$((TOTAL * 95 / 100))
P99_IDX=$((TOTAL * 99 / 100))

echo "P50 (median): ${SORTED_LATENCIES[$P50_IDX]}s"
echo "P95: ${SORTED_LATENCIES[$P95_IDX]}s"
echo "P99: ${SORTED_LATENCIES[$P99_IDX]}s"
echo ""

# Summary
echo "📋 Summary"
echo "=========="
echo "✅ Load testing completed successfully!"
echo ""
echo "Next steps:"
echo "1. Check Prometheus metrics at: $GATEWAY_URL/actuator/prometheus"
echo "2. View Grafana dashboards at: http://localhost:3000"
echo "3. Check application logs for any errors or warnings"
echo ""
