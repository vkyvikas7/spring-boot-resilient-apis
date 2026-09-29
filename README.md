# spring-boot-resilient-apis

[![ci](https://github.com/vkyvikas7/spring-boot-resilient-apis/actions/workflows/ci.yml/badge.svg)](https://github.com/vkyvikas7/spring-boot-resilient-apis/actions/workflows/ci.yml)

Spring Boot API that hardens against flaky dependencies with Resilience4j — shows debugging failure modes and production-minded feature design.

A small product-availability service calls an inventory dependency. The dependency is an in-process stub whose behaviour you can force, so the failure modes are reproducible. Circuit breaker, retry, bulkhead, and rate limiter sit on that one call. Actuator exposes health, readiness, and the Resilience4j registries.

## Quick start

Requires JDK 17 or newer and Maven 3.8+.

```bash
git clone https://github.com/vkyvikas7/spring-boot-resilient-apis.git
cd spring-boot-resilient-apis
mvn verify
mvn spring-boot:run
```

The app listens on `http://localhost:8080`. [Run locally](#run-locally) has the demo curls for each failure mode.

## Problem

A slow or failing downstream service becomes your outage.

This API's only business route reads inventory. If that call blocks or errors and nothing stops it:

- Tomcat threads sit inside the downstream call.
- Clients time out and retry, so the failing dependency receives more traffic, not less.
- Unrelated work in the same process cannot get a thread.
- The outage cascades from inventory into this service and then into its callers.

The boundary around that dependency is the circuit breaker, the retry budget, the bulkhead, and the rate limiter. When the dependency is unhealthy the API fails fast with a stable error document instead of tying up workers. Readiness goes `DOWN` while the circuit is open, so a load balancer can stop sending traffic to an instance that cannot serve its only route.

## Patterns

All four patterns decorate `InventoryClient.getAvailability`. Aspect order is set explicitly (lower order is the outer decorator):

```
CircuitBreaker → Retry → RateLimiter → Bulkhead → inventory stub
```

Resilience4j 2.3's defaults put retry *outside* the breaker, which would record every attempt as a separate failure. The orders in `application.yml` reverse that. One HTTP call is one circuit-breaker result. An open circuit returns before retry, the rate limiter, the bulkhead, or the stub run.

| Pattern | Instance | What it does here |
| --- | --- | --- |
| Circuit breaker | `inventory` | Opens at a 50% failure rate once 5 calls are in the window. With a fully failing dependency that is the 5th failed call. Stays open for 10s, then one successful trial call closes it. |
| Retry | `inventory` | 3 attempts, 50ms apart, only for `DownstreamCallException`. A missing product is not retried. |
| Rate limiter | `inventory` | 100 calls per 30s, no waiting. Extra calls get `429 RATE_LIMITED` and do not count as breaker failures. |
| Bulkhead | `inventory` | At most 5 concurrent inventory calls. A full bulkhead gets `503 BULKHEAD_FULL` and does not count as a breaker failure. |

`SLOW` mode sleeps until the client timeout (400ms) and then fails, so a hung dependency is a recorded failure rather than an unbounded block. `FLAKY` mode fails the first 2 attempts and succeeds on the 3rd, which is inside the retry budget.

Business 404s (`ProductNotFoundException`) are ignored by the breaker and the retry. A bad SKU must not open the circuit.

## When to use

**Circuit breaker.** The dependency fails often enough that more calls will not help, and you would rather fail fast and let it recover. Put it on a specific client, not on your whole process. Include it in readiness only when that dependency is required for the instance to do useful work. This service's only route needs inventory, so an open circuit fails readiness. A process with other healthy routes should leave the breaker out of readiness and degrade only the affected route.

**Retry.** The failure is transient (a dropped connection, a short 503) and the call is safe to repeat. This template retries inventory reads. Do not retry a non-idempotent write with the same policy. Cap the attempts; the breaker above is what stops a retry storm.

**Rate limiter.** You need to protect the downstream system from this process, or protect this process from a burst of callers. Limiting only at the gateway does not stop one instance from hammering a dependency after a deploy or a retry storm.

**Bulkhead.** A slow dependency should not be able to occupy every request thread. Size it from the downstream's concurrency limit and your own thread pool, not from a round number. A bulkhead of 5 with a 200-thread container means at most 5 threads can be stuck in inventory.

**Skip them** when the call is local and cheap, when you have no metric for the threshold you are about to invent, or when a fallback would hide a failure operators need to see. Thresholds in this repo are demo values. In production they come from latency and error dashboards, and they get revised.

Demo routes under `/api/v1/demo` exist to force these states. They are not registered when the `prod` profile is active.

## Run locally

Requires JDK 17 or newer and Maven 3.8+.

```bash
mvn -B -ntp verify
mvn -B -ntp spring-boot:run
```

The app listens on `http://localhost:8080`. `jq` is optional; without it, drop the pipe.

### 1. Healthy call

```bash
curl -sS -D - http://localhost:8080/api/v1/demo/success -o /tmp/success.json
cat /tmp/success.json
```

`200` and a body for `SKU-1001` (Trail Running Shoe, warehouse `WEST-1`). The same payload is served by the product route while the downstream mode is `SUCCESS` (the default):

```bash
curl -sS http://localhost:8080/api/v1/products/SKU-1001/availability
curl -sS http://localhost:8080/api/v1/products/SKU-1004/availability
```

`SKU-1004` is in stock count `0`, so `available` is `false`. That is still `200`. An unknown SKU is `404 PRODUCT_NOT_FOUND` and does not move the breaker:

```bash
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/products/SKU-404/availability
```

### 2. Open the circuit

Reset first so the window and the stub counter start clean.

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/resilience/reset
```

Five failed calls open the breaker. Each call is retried 3 times, so the stub sees 15 invocations and the breaker records 5 failures. Responses are `503` with code `DOWNSTREAM_FAILED`.

```bash
for i in 1 2 3 4 5; do
  echo "call $i"
  curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/demo/failure
done
```

The sixth call does not touch the stub. The code is `CIRCUIT_OPEN`, and `Retry-After` is the open-state wait (10 seconds).

```bash
curl -sS -D - http://localhost:8080/api/v1/demo/failure -o /tmp/open.json
cat /tmp/open.json
```

Confirm the breaker, the stub counter, and readiness:

```bash
curl -sS http://localhost:8080/api/v1/demo/resilience
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/actuator/health/readiness
curl -sS http://localhost:8080/actuator/circuitbreakers
curl -sS http://localhost:8080/actuator/health/liveness
```

`/api/v1/demo/resilience` shows `state` `OPEN`, `failedCalls` `5`, and `downstreamInvocations` `15`. Readiness is `503` with status `DOWN` because `allow-health-indicator-to-fail` is on for this breaker. Liveness stays `200`.

Wait 10 seconds and call success once. The breaker moves to half-open, allows a trial, and a successful trial closes it:

```bash
sleep 10
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/demo/success
curl -sS http://localhost:8080/api/v1/demo/resilience
```

Or skip the wait and reset:

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/resilience/reset
```

The log line `Circuit breaker 'inventory' transitioned from CLOSED to OPEN` is the state change. The request id in that line is also the `X-Request-Id` response header and the `requestId` field on error bodies.

### 3. Retry a flaky dependency

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/resilience/reset
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/demo/flaky
curl -sS http://localhost:8080/api/v1/demo/resilience
curl -sS http://localhost:8080/actuator/retryevents
```

One client call returns `200`. `downstreamInvocations` is `3`: two forced failures and the successful attempt. `successfulCallsWithRetry` is `1`. The breaker stays `CLOSED` because the retried call ultimately succeeded.

### 4. Slow dependency

`/api/v1/demo/slow` blocks for the 400ms client timeout and then fails. Three attempts take a bit over a second and return `503 DOWNSTREAM_FAILED`.

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/resilience/reset
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/demo/slow
```

### 5. Bulkhead

Sequential curl cannot fill a bulkhead. The demo endpoint acquires every permit on the same bulkhead the client uses:

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/resilience/reset
curl -sS -X POST http://localhost:8080/api/v1/demo/bulkhead/saturate
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/demo/success
curl -sS -X POST http://localhost:8080/api/v1/demo/bulkhead/release
```

Saturate reports `heldPermits` `5` and `availableConcurrentCalls` `0`. The following call is `503 BULKHEAD_FULL` and does not increment `downstreamInvocations`. Release puts the permits back. Reset also releases them.

### 6. Rate limiter

The budget is 100 permissions per 30 seconds, which a bash loop will not reliably exhaust. Drain the current window, then call:

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/rate-limiter/drain
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/demo/success
```

The call is `429 RATE_LIMITED` with `Retry-After: 30`. It does not reach the stub and does not count as a breaker failure. Permissions also return on the next 30s refresh. `POST /api/v1/demo/resilience/reset` refills them immediately by rebuilding the limiter. Retry counters (`successfulCallsWithRetry`, `failedCallsWithRetry`) are process lifetime and are not cleared by reset.

### 7. Drive the product route

Demo success and failure routes pass the mode themselves. The product route uses the configured mode:

```bash
curl -sS -X POST http://localhost:8080/api/v1/demo/resilience/reset
curl -sS -X PUT http://localhost:8080/api/v1/demo/downstream/mode \
  -H 'Content-Type: application/json' \
  -d '{"mode":"FAILURE"}'
curl -sS -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/products/SKU-1002/availability
curl -sS -X PUT http://localhost:8080/api/v1/demo/downstream/mode \
  -H 'Content-Type: application/json' \
  -d '{"mode":"SUCCESS"}'
```

An unknown mode is `400 MALFORMED_REQUEST`. A missing `mode` is `400 VALIDATION_ERROR`.

### 8. Prod profile

```bash
mvn -B -ntp spring-boot:run -Dspring-boot.run.profiles=prod
```

`/api/v1/demo/**` is not registered (`404 NOT_FOUND`). `/api/v1/products/{sku}/availability` still calls the stub in `SUCCESS` mode, with the same four resilience policies.

## API

| Method | Path | Response |
| --- | --- | --- |
| `GET` | `/api/v1/products/{sku}/availability` | Availability, using the configured downstream mode |
| `GET` | `/api/v1/demo/success` | Forces a successful inventory call for `SKU-1001` |
| `GET` | `/api/v1/demo/failure` | Forces a failed inventory call |
| `GET` | `/api/v1/demo/flaky` | Fails two attempts, then succeeds |
| `GET` | `/api/v1/demo/slow` | Hits the client timeout |
| `GET` | `/api/v1/demo/downstream/mode` | Current mode for the product route |
| `PUT` | `/api/v1/demo/downstream/mode` | Body `{"mode":"SUCCESS\|FAILURE\|FLAKY\|SLOW"}` |
| `GET` | `/api/v1/demo/resilience` | Breaker, retry, bulkhead, and rate-limiter snapshot |
| `POST` | `/api/v1/demo/resilience/reset` | Closes the breaker, refills the rate limiter, clears stub counters, releases demo bulkhead permits |
| `POST` | `/api/v1/demo/bulkhead/saturate` | Holds every bulkhead permit |
| `POST` | `/api/v1/demo/bulkhead/release` | Releases permits held by saturate |
| `POST` | `/api/v1/demo/rate-limiter/drain` | Drops permissions for the current period |

Catalog:

| SKU | Name | On hand | Warehouse |
| --- | --- | --- | --- |
| `SKU-1001` | Trail Running Shoe | 42 | `WEST-1` |
| `SKU-1002` | Merino Beanie | 18 | `EAST-2` |
| `SKU-1003` | Stainless Bottle | 7 | `CENTRAL-3` |
| `SKU-1004` | Wool Socks | 0 | `EAST-2` |

### Error document

Every error uses the same fields. `requestId` matches the `X-Request-Id` response header. Send your own id (`[A-Za-z0-9-]{1,64}`) or the service generates one.

```json
{
  "timestamp": "2026-09-29T19:00:00.000Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "CIRCUIT_OPEN",
  "message": "Inventory circuit breaker is open; failing fast to avoid cascading failures.",
  "path": "/api/v1/demo/failure",
  "requestId": "circuit-open-1"
}
```

| HTTP | `code` | When |
| --- | --- | --- |
| 404 | `PRODUCT_NOT_FOUND` | SKU is not in the catalog |
| 404 | `NOT_FOUND` | No such route |
| 400 | `MALFORMED_REQUEST` | Body is not valid JSON for the endpoint |
| 400 | `VALIDATION_ERROR` | Bean validation failed |
| 429 | `RATE_LIMITED` | Rate limiter rejected the call |
| 503 | `DOWNSTREAM_FAILED` | Attempts exhausted |
| 503 | `CIRCUIT_OPEN` | Breaker is open; stub was not called |
| 503 | `BULKHEAD_FULL` | No bulkhead permit |
| 500 | `INTERNAL_ERROR` | Anything else, with the stack only in the log |

`CIRCUIT_OPEN`, `RATE_LIMITED`, `BULKHEAD_FULL`, and `DOWNSTREAM_FAILED` include `Retry-After`.

### Actuator

| Path | What it tells you |
| --- | --- |
| `/actuator/health/liveness` | Process is up |
| `/actuator/health/readiness` | `readinessState` plus circuit breakers. `503` while `inventory` is open |
| `/actuator/health` | Includes `inventoryStub` (mode and invocation count) |
| `/actuator/circuitbreakers` | State, failure rate, buffered calls |
| `/actuator/circuitbreakerevents` | Recent breaker events |
| `/actuator/retries`, `/actuator/retryevents` | Retry counters and events |
| `/actuator/ratelimiters`, `/actuator/ratelimiterevents` | Permissions |
| `/actuator/bulkheads`, `/actuator/bulkheadevents` | Available concurrent calls |
| `/actuator/metrics` | Micrometer, including Resilience4j meters |
| `/actuator/info` | App name and the positioning line's description |

## Tests

```bash
mvn -B -ntp test
```

`CircuitBreakerOpenTest` is the proof that the breaker opens after 5 failures: the state is still `CLOSED` after 4, `OPEN` after 5, the stub was invoked `5 × 3` times, and the next call returns `CIRCUIT_OPEN` without another invocation. The same test asserts readiness is `DOWN`.

Other tests cover retry recovery, rate-limiter rejection, a real concurrent bulkhead rejection (not only the saturate endpoint), the error document, actuator shape, and the `prod` profile hiding demo routes.

GitHub Actions runs `mvn -B -ntp verify` on Temurin 17.

## Layout

```
src/main/java/com/portfolio/resilient
├── api            Product and demo controllers, response records
├── config         Inventory properties, request id filter, breaker event log
├── demo           Reset, bulkhead hold, rate-limiter drain
├── downstream     Stub, client, and the four annotations
├── error          One error document for advice and container errors
└── health         Stub health details
```

The stub stands in for a remote client. Swap `InventoryStub` for a `RestClient` (or WebClient) call and keep the annotations, the recorded exception, and the timeouts.

## Docker

```bash
docker build -t spring-boot-resilient-apis .
docker run --rm -p 8080:8080 spring-boot-resilient-apis
```

The image builds with Maven on Temurin 17 and runs as a non-root user.

## License

[MIT](LICENSE)
