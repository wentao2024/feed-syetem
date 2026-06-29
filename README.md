# Feed System


A high-performance personalized feed backend built with Spring Boot microservices. The system simulates a Twitter/Instagram-style home timeline and focuses on core backend challenges such as fan-out write amplification, cursor pagination, Redis caching, large-account handling, asynchronous messaging, and load-test-driven optimization.

## Highlights

- **Hybrid Push/Pull Feed Architecture**: normal users are pushed into followers' timelines, while large accounts skip push and are pulled during feed reads.
- **Redis ZSet Timeline**: each user's timeline is stored as `feed:{userId}`, where the member is `postId` and the score is the post timestamp.
- **Cursor-Based Pagination**: timestamp-based cursor pagination avoids duplicate or missing items caused by offset pagination on dynamic feeds.
- **Multi-Level Cache**: `post:{postId}`, `largev_followees:{userId}`, and `lv_recent:{authorId}` reduce dependency on downstream services during feed reads.
- **RabbitMQ-Based Async Processing**: post creation and follow events are processed asynchronously to keep user-facing APIs fast.
- **Redis Pipeline + Dedicated Executor**: fan-out writes are batched through Redis Pipeline, and PostDTO cache warm-up is isolated in a dedicated thread pool.
- **Production-Oriented Tooling**: Docker Compose, Flyway migrations, Testcontainers integration tests, JMeter load tests, and seed-data automation are included.

## Architecture

```text
Client / Frontend
       |
       | Authorization: Bearer <JWT>
       v
API Gateway :8080
       |-- JWT validation
       |-- Injects X-User-Id / X-Username
       |
       |--> user-service :8081
       |      PostgreSQL(user_db)
       |      register / login / follow / profile
       |      publishes FollowEvent
       |
       |--> post-service :8082
       |      PostgreSQL(post_db)
       |      create post / like / image upload / query
       |      publishes PostCreatedEvent
       |
       |--> feed-service :8083
              Redis
              RabbitMQ consumer
              getFeed / fanOut / backfill
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

Infrastructure:
       Eureka Server :8761
       Redis :6379
       RabbitMQ :5672 / 15672
       PostgreSQL user_db :5432
       PostgreSQL post_db :5433
```

## Core Workflows

### 1. Authentication

1. The frontend calls `POST /api/users/login` to get a JWT.
2. All protected requests include `Authorization: Bearer <token>`.
3. API Gateway validates the JWT and injects:

```http
X-User-Id: current user id
X-Username: current username
```

4. Downstream services read identity from headers instead of parsing JWT repeatedly.

### 2. Normal User Post Creation

```text
POST /api/posts
  -> post-service persists the post
  -> publishes PostCreatedEvent
  -> feed-service consumes the event
  -> fetches follower ids
  -> writes postId into feed:{followerId} through Redis Pipeline
  -> asynchronously pre-warms post:{postId}
```

### 3. Large Account Post Creation

```text
POST /api/posts
  -> post-service persists the post
  -> publishes PostCreatedEvent
  -> feed-service detects follower count >= 1000
  -> skips push fan-out
  -> asynchronously pre-warms post:{postId}
  -> invalidates lv_recent:{authorId}
```

### 4. Feed Read

```text
GET /api/feed?size=20&cursor=<nextCursor>
  -> reads feed:{userId} ZSet
  -> reads largev_followees:{userId}
  -> pulls recent posts from followed large accounts
  -> merges pushed posts and pulled posts
  -> sorts by timestamp
  -> resolves PostDTOs in batch
  -> returns posts / nextCursor / hasMore
```

## Redis Data Model

| Key | Type | Value | TTL |
|---|---|---|---|
| `feed:{userId}` | ZSet | User timeline, member=`postId`, score=timestamp | no explicit TTL |
| `post:{postId}` | JSON String | Full `PostDTO` | 24 hours |
| `largev_followees:{userId}` | String | Large account ids followed by this user | 5 minutes |
| `lv_recent:{authorId}` | String | Recent post ids for a large account | 2 minutes |


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

| Area | Technology |
|---|---|
| Backend | Spring Boot 3.2.3, Java 17 |
| Microservices | Spring Cloud Gateway, Eureka, OpenFeign |
| Database | PostgreSQL 15 |
| ORM | MyBatis-Plus |
| Cache | Redis 7, ZSet, Pipeline |
| Messaging | RabbitMQ 3.12 |
| Auth | JWT (jjwt 0.12.3), Gateway validates + forwards X-User-Id|
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5, Mockito, Testcontainers |
| Load Testing | Apache JMeter |
| Deployment | Docker, Docker Compose |

## Quick Start

### Option A: Full Docker Compose

```bash
docker-compose up -d
```

### Option B: Infrastructure with Local Services

```bash
docker-compose up -d postgres-user postgres-post redis rabbitmq
```

Then start the services:

```text
1. eureka-server
2. api-gateway
3. user-service
4. post-service
5. feed-service
```

## Useful URLs

| Service | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Eureka | http://localhost:8761 |
| RabbitMQ UI | http://localhost:15672 |
| User Swagger | http://localhost:8081/swagger-ui.html |
| Post Swagger | http://localhost:8082/swagger-ui.html |
| Feed Swagger | http://localhost:8083/swagger-ui.html |



## API Overview

### Auth

```http
POST /api/users/register
POST /api/users/login
```

### User

```http
GET    /api/users/{userId}
PUT    /api/users/{userId}
POST   /api/users/{userId}/follow
DELETE /api/users/{userId}/follow
GET    /api/users/{userId}/followers
GET    /api/users/{userId}/following
```

### Post

```http
POST   /api/posts
GET    /api/posts/{postId}
GET    /api/posts/user/{userId}
DELETE /api/posts/{postId}
POST   /api/posts/{postId}/like
DELETE /api/posts/{postId}/like
```

### Feed

```http
GET /api/feed?size=20
GET /api/feed?size=20&cursor={nextCursor}
```

## Load Testing

Prepare seed data:

```bash
bash scripts/seed-data.sh
```

Run JMeter:

```bash
jmeter -n -t scripts/feed-load-test.jmx -l scripts/result.jtl -e -o scripts/report/
```

Covered scenarios:

- Feed warm-up
- High-concurrency feed reads
- Normal-user fan-out writes
- Redis cold-start reads
- Large-account skip-push writes
- Regular post creation throughput

## Project Value

This project goes beyond CRUD by implementing the core engineering trade-offs of a real feed system:

- RabbitMQ decouples write-path events.
- Redis ZSet provides low-latency timeline reads.
- Hybrid Push/Pull handles large-account write amplification.
- Multi-level caching reduces feed read-path P99 latency.
- JMeter load testing validates bottlenecks and optimizations.

