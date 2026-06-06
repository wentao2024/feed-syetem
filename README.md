# Feed System

A high-performance social feed system built with Spring Boot microservices, implementing a **Push-Pull hybrid fan-out** strategy to handle both normal users and large accounts (high-follower "influencers") efficiently.

---

## Highlights

- **Push-Pull hybrid fan-out** — normal accounts push to follower ZSets via RabbitMQ; accounts with ≥ 1,000 followers skip push entirely, followers pull on read
- **Cursor-based pagination** — ZSet `ZREVRANGEBYSCORE` with timestamp score; O(log N) per page, no offset drift on concurrent writes
- **Multi-level cache** — feed ZSet → post cache (24 h) → lv_recent cache (2 min) → large-V followee list (5 min)
- **Dedicated async thread pool** — isolates blocking I/O from ForkJoinPool common pool; eliminated a 2,000 ms p95 bottleneck under 200-thread concurrent load

---

## Architecture

```
Client
  │
  ▼
API Gateway :8080  (JWT auth filter, Spring Cloud Gateway)
  │
  ├── user-service  :8081  (register / login / follow)
  ├── post-service  :8082  (create post / query / like)
  └── feed-service  :8083  (getFeed / fanOut / backfill)
        │                │
        │          RabbitMQ (PostCreatedEvent, FollowEvent)
        │
      Redis (feed ZSet, post cache, multi-level K-V)

Eureka Server :8761  (service registry)
PostgreSQL ×2        (user_db :5432 / post_db :5433)
```

### Fan-out Flow

```
POST /api/posts
       │
       ├─ follower count < 1,000 (normal account)
       │       └─ RabbitMQ → feed-service
       │               └─ Redis Pipeline: ZADD feed:{followerId} score postId  (all followers)
       │                                  ZREMRANGEBYRANK  (cap at 500 entries)
       │               └─ async: pre-warm post:{postId} cache
       │
       └─ follower count ≥ 1,000 (large-V / influencer)
               └─ skip push fan-out entirely
               └─ async: pre-warm post:{postId} cache
                         invalidate lv_recent:{authorId}

GET /api/feed
       │
       ├─ push path  → ZREVRANGEBYSCORE feed:{userId}
       ├─ pull path  → largev_followees:{userId} cache (5 min)
       │                  └─ per large-V: lv_recent cache → post cache → post-service
       └─ merge by score → cursor page → batch resolve PostDTOs
```

---

## Performance

JMeter load test — 5 thread groups, serialized execution, 0% error rate.

### GET /api/feed — 200 concurrent threads × 50 loops (10,000 requests)

| Metric | Before (ForkJoinPool) | After (dedicated pool) | Delta |
|--------|-----------------------|------------------------|-------|
| avg    | 265 ms                | **10 ms**              | −96 % |
| p50    | 184 ms                | 9 ms                   | −95 % |
| p90    | 614 ms                | 23 ms                  | −96 % |
| p95    | 2,081 ms              | **32 ms**              | −98 % |
| max    | 5,179 ms              | 63 ms                  | −99 % |
| TPS    | 235 req/s             | **948 req/s**          | +4×   |

### Cold-start read (Redis FLUSHALL → 200 threads)

| Metric | Before | After  | Delta |
|--------|--------|--------|-------|
| avg    | 163 ms | 14 ms  | −91 % |
| p95    | 438 ms | 56 ms  | −87 % |

### Post creation

| Scenario                        | avg before | avg after | Delta |
|---------------------------------|-----------|-----------|-------|
| Normal account (fan-out push)   | 21 ms     | 16 ms     | −24 % |
| Large-V (skip push)             | 13 ms     | **7 ms**  | −46 % |

**Root cause of the improvement:** `CompletableFuture.runAsync` defaults to the JVM ForkJoinPool common pool. Under 200-thread concurrent reads, blocking HTTP + Redis calls in async cache warm-up tasks saturated the pool, queuing subsequent tasks and spiking p95 to 2,000 ms+. Switching to a dedicated `ThreadPoolTaskExecutor` (core 10 / max 50 / queue 500 / CallerRunsPolicy) isolated the I/O load and restored linear throughput.

---

## Tech Stack

| Component         | Technology                                      |
|-------------------|-------------------------------------------------|
| Framework         | Spring Boot 3.2.3 · Spring Cloud 2023.0.1       |
| Service registry  | Netflix Eureka                                  |
| API Gateway       | Spring Cloud Gateway + JWT filter               |
| Inter-service RPC | OpenFeign + Resilience4j circuit breaker        |
| Message queue     | RabbitMQ 3.12                                   |
| Cache             | Redis 7 (ZSet, String, Pipeline, multi-level)   |
| Database          | PostgreSQL 15 × 2 (DB-per-service)              |
| ORM               | MyBatis-Plus                                    |
| Auth              | JWT (jjwt 0.12.3), Gateway validates + forwards X-User-Id |
| DB migration      | Flyway                                          |
| Docs              | SpringDoc OpenAPI 3 (Swagger UI)                |
| Load testing      | Apache JMeter 5.6.3                             |
| Containerization  | Docker · Docker Compose                         |

---

## Quick Start

### Option A — Full Docker (one command)

```bash
docker-compose up -d
```

All 9 containers start automatically (infra + services). Wait ~30 s for health checks to pass.

| Service       | URL                          |
|---------------|------------------------------|
| API Gateway   | http://localhost:8080        |
| Eureka        | http://localhost:8761        |
| RabbitMQ UI   | http://localhost:15672 (guest/guest) |
| Swagger (user)| http://localhost:8081/swagger-ui.html |
| Swagger (post)| http://localhost:8082/swagger-ui.html |
| Swagger (feed)| http://localhost:8083/swagger-ui.html |

### Option B — IDEA + Docker infra (recommended for development)

Start only infrastructure:

```bash
docker-compose up -d postgres-user postgres-post redis rabbitmq
```

Then run the 5 Spring Boot applications from IDEA in this order:

```
1. eureka-server
2. api-gateway
3. user-service  ┐
4. post-service  ├ these three can start concurrently
5. feed-service  ┘
```

No environment variables needed — all `application.yml` defaults point to `localhost` and match the Docker port mappings.

---

## API Reference

### Auth (no JWT required)

```
POST /api/users/register   { "username", "email", "password" }
POST /api/users/login      { "username", "password" }
```

Response includes a `token` field. Pass it as `Authorization: Bearer <token>` on all subsequent requests.

### Users

```
GET    /api/users/{userId}
POST   /api/users/{userId}/follow
DELETE /api/users/{userId}/follow
GET    /api/users/{userId}/followers?page=1&size=20
GET    /api/users/{userId}/following?page=1&size=20
```

### Posts

```
POST /api/posts              multipart/form-data: content, images (optional)
GET  /api/posts/{postId}
GET  /api/posts/user/{userId}?page=1&size=20
POST /api/posts/{postId}/like
```

### Feed

```
GET /api/feed?size=20
GET /api/feed?size=20&cursor={nextCursor}   # cursor-based pagination
```

---

## Load Testing

Seed test data (requires services running):

```bash
bash scripts/seed-data.sh
```

The script registers three test accounts (`testuser`, `viewer`, `large_v`), bulk-inserts 1,000 fake followers for `large_v` via SQL, creates seed posts, and sets up follow relationships with backfill.

Run JMeter:

```bash
# GUI mode
jmeter -t scripts/feed-load-test.jmx

# CLI mode (for accurate numbers)
jmeter -n -t scripts/feed-load-test.jmx -l scripts/result.jtl -e -o scripts/report/
```

Test plan accounts:

| Variable         | Username   | Password | Role                              |
|------------------|------------|----------|-----------------------------------|
| `WRITER_USER`    | testuser   | test123  | Normal writer (< 1,000 followers) |
| `READER_USER`    | viewer     | test123  | Reader (follows both writers)     |
| `FANOUT_LARGE_USER` | large_v | test123  | Large-V writer (≥ 1,000 followers)|

---

## Redis Key Design

| Key                          | Type   | Content                          | TTL     |
|------------------------------|--------|----------------------------------|---------|
| `feed:{userId}`              | ZSet   | postId → timestamp (ms) score    | capped at 500 entries |
| `post:{postId}`              | String | PostDTO JSON                     | 24 h    |
| `largev_followees:{userId}`  | String | comma-separated followee IDs     | 5 min   |
| `lv_recent:{authorId}`       | String | comma-separated recent post IDs  | 2 min   |

---

## Project Structure

```
feed-system/
├── api-gateway/          Spring Cloud Gateway + JwtAuthFilter
├── eureka-server/        Service registry
├── user-service/         Users, follows, JWT issuance
├── post-service/         Posts, images, likes
├── feed-service/         Feed read/write, fan-out, backfill
├── common/               Shared DTOs and events
├── scripts/
│   ├── feed-load-test.jmx   JMeter test plan
│   └── seed-data.sh         One-click test data seeder
└── docker-compose.yml
```
