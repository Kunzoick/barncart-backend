# ADR-014 — Rate Limiting with Bucket4j In-Memory

## Status
Accepted

## Context
Without rate limiting, auth endpoints are vulnerable to brute-force attacks. The login endpoint in particular can be hammered to guess passwords at high speed. Registration endpoints can be spammed to generate noise or exhaust resources. Rate limiting at the filter level — before any business logic executes — is the correct layer for this protection.

## Decision
`RateLimitFilter` implements `OncePerRequestFilter` and uses Bucket4j with a Caffeine in-memory cache to maintain rate limit buckets per IP address.

Each IP gets a token bucket with a configured capacity and refill rate. Requests that exceed the limit receive a `429 Too Many Requests` response immediately, before hitting Spring Security or any controller.

```java
Bucket bucket = cache.get(clientIp, key -> Bucket.builder()
    .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
    .build());

if (!bucket.tryConsume(1)) {
    response.setStatus(429);
    return;
}
```

Caffeine handles cache eviction automatically — idle IP buckets are evicted after inactivity, preventing unbounded memory growth.

## Consequences
- Auth endpoints are protected against brute-force at no significant performance cost — bucket check is nanoseconds
- Rate limiting applies to all endpoints, not just auth — this is intentional but could be refined to apply different limits per endpoint type
- **Known limitation:** In-memory rate limiting does not work correctly across multiple instances. Each instance maintains its own counters independently. An attacker distributing requests across instances can exceed the intended limit. For a single-instance portfolio deployment this is acceptable
- **Production multi-instance fix:** Replace Caffeine with a Redis-backed Bucket4j cache — all instances share one counter per IP
- Caffeine eviction means short-burst attackers who pause get a fresh bucket — this is standard token bucket behaviour and acceptable for the threat model here
