# BarnCart Backend

A production-deployed Spring Boot backend for a farm-based e-commerce platform. Built as a portfolio project demonstrating real-world backend architecture — two-phase checkout, WebSocket inventory updates, Stripe payment integration, and a dispute resolution system.

**Live demo:** https://barncart-frontend.vercel.app  
**Author:** Kunzoick  
**Repository:** https://github.com/Kunzoick/barncart-backend

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 |
| Language | Java 21 |
| Database | MySQL 8.4 (Aiven, production) / MariaDB 10.4 (local) |
| Migrations | Flyway |
| Auth | JWT (access) + httpOnly cookie (refresh) |
| Payments | Stripe |
| Images | Cloudinary |
| Email | Resend |
| WebSockets | SockJS + STOMP |
| Rate Limiting | Bucket4j + Caffeine |
| Deployment | Render (Docker) |

---

## Architecture Highlights

- **Two-phase checkout** — DB operations and Stripe API call are deliberately separated to avoid holding a database connection open during external HTTP calls
- **Atomic stock deduction** — uses a single `UPDATE ... WHERE quantity_available >= :qty` query to eliminate race conditions without pessimistic locking
- **Slot booking at RESERVED** — delivery slot capacity is claimed at reservation time, not payment time, to prevent overbooking
- **WebSocket events via AFTER_COMMIT** — broadcasts only fire after the transaction commits, keeping UI state consistent with DB state
- **ID-passing in schedulers** — schedulers pass only IDs to processors, never entities, to avoid lazy proxy exceptions across transaction boundaries
- **Stateful refresh tokens** — stored in DB with httpOnly cookie delivery, enabling true logout and token revocation
- **Explicit flush in multi-item checkout** — `@Modifying(clearAutomatically = true)` queries detach the entire persistence context; the checkout loop explicitly flushes after each item to prevent silent data loss on unflushed reservations/order items

Full architectural decisions are documented in [`docs/adr/`](docs/adr/).

---

## Project Structure

```
src/
├── config/                  # Security, WebSocket, Stripe, rate limiting
├── domain/
│   ├── auth/                # JWT, refresh tokens, email verification, password reset
│   ├── cart/                # Cart and cart items
│   ├── delivery/            # Delivery slots and order delivery
│   ├── order/               # Orders, checkout, analytics, dispute flow
│   ├── payment/             # Stripe webhook handling
│   ├── produce/             # Produce, categories, harvest batches, listings
│   └── user/                # User entity and repository
├── infrastructure/
│   ├── scheduling/          # Reservation expiry and delivery confirmation jobs
│   └── websocket/           # Inventory and order status broadcast services
└── shared/
    └── exception/           # Global exception handler
```

---

## API Endpoints

### Auth
```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/verify-email
POST /api/auth/resend-verification
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/change-password
```

### Listings (public)
```
GET /api/listings?page=0&size=12
GET /api/listings/{id}
```

### Cart (authenticated)
```
GET    /api/cart
POST   /api/cart/items
PATCH  /api/cart/items/{cartItemId}
DELETE /api/cart/items/{cartItemId}
DELETE /api/cart
```

### Orders (authenticated)
```
POST /api/orders/checkout
GET  /api/orders
GET  /api/orders/{orderId}
POST /api/orders/{orderId}/cancel
POST /api/orders/{orderId}/confirm-delivery
POST /api/orders/{orderId}/dispute
GET  /api/orders/{orderId}/client-secret
```

### Admin
```
GET  /api/admin/orders?page=0&size=20
POST /api/admin/orders/{orderId}/fulfill
POST /api/admin/orders/{orderId}/resolve-dispute
GET  /api/admin/analytics
GET  /api/admin/batches
POST /api/admin/batches
GET  /api/listings
POST /api/listings
GET  /api/produce
POST /api/produce
POST /api/produce/{id}/image
GET  /api/admin/categories
POST /api/admin/categories
PUT  /api/admin/categories/{id}
POST /api/delivery-slots
GET  /api/delivery-slots/admin
```

### WebSocket
```
/ws                                    — SockJS endpoint
/topic/listing/{listingId}/inventory  — public inventory broadcast
/user/queue/orders                     — authenticated order status updates
```

### Health
```
GET /actuator/health
```

---

## Order Status Flow

```
PENDING → RESERVED → PAID → FULFILLED → DELIVERED
                   ↓              ↓
              EXPIRED        DISPUTED → (admin resolves) → FULFILLED
              PAYMENT_FAILED
              CANCELLED
```

---

## Running Locally

### Prerequisites
- Java 21
- Maven
- MariaDB or MySQL running locally
- Stripe CLI (for webhook forwarding)

### 1. Clone the repo
```bash
git clone https://github.com/Kunzoick/barncart-backend.git
cd barncart-backend
```

### 2. Create local environment file
Create `.env` in the project root (never committed):

```env
DB_URL=jdbc:mariadb://localhost/barncart_db?serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=
JWT_ACCESS_SECRET=<32 bytes base64>
JWT_REFRESH_SECRET=<32 bytes base64>
JWT_ACCESS_TTL_MINUTES=15
JWT_REFRESH_TTL_DAYS=7
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
WEBSOCKET_ALLOWED_ORIGINS=http://localhost:5173
ADMIN_EMAIL=admin@barncart.com
ADMIN_PASSWORD=<your password>
FRONTEND_URL=http://localhost:5173
RESEND_API_KEY=re_...
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
```

### 3. Set Spring profile for local dev
In IntelliJ run configuration, add environment variable:
```
SPRING_PROFILES_ACTIVE=local
```

This activates `application-local.yaml` which switches the JDBC driver to MariaDB.

### 4. Start Stripe CLI webhook forwarding
```bash
stripe listen --api-key sk_test_... --forward-to localhost:8080/api/webhooks/stripe
```

### 5. Run the application
```bash
./mvnw spring-boot:run
```

Flyway will run all migrations automatically on startup.

### 6. Health check
```
GET http://localhost:8080/actuator/health
```

---

## Environment Variables (Production)

| Variable | Description |
|---|---|
| `DB_URL` | Full JDBC connection string including SSL params |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_ACCESS_SECRET` | Base64-encoded 32-byte secret for access tokens |
| `JWT_REFRESH_SECRET` | Base64-encoded 32-byte secret for refresh tokens |
| `JWT_ACCESS_TTL_MINUTES` | Access token TTL (default: 15) |
| `JWT_REFRESH_TTL_DAYS` | Refresh token TTL (default: 7) |
| `STRIPE_SECRET_KEY` | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |
| `WEBSOCKET_ALLOWED_ORIGINS` | Comma-separated allowed origins for WebSocket |
| `ADMIN_EMAIL` | Seeded admin account email |
| `ADMIN_PASSWORD` | Seeded admin account password |
| `FRONTEND_URL` | Frontend URL for CORS and email links |
| `RESEND_API_KEY` | Resend API key for transactional email |

---

## Deployment

Deployed on Render using a multi-stage Dockerfile:

- Stage 1: Maven builds the JAR inside the container
- Stage 2: JRE Alpine image runs the JAR with `-XX:MaxRAMPercentage=75.0`

Database is hosted on Aiven MySQL 8.4. UptimeRobot pings `/actuator/health` every 5 minutes to prevent Render free tier cold starts.

---

## Database Migrations

Flyway manages all schema changes. Migration files are in `src/main/resources/db/migration/`.

| Version | Description |
|---|---|
| V1 | Initial schema |
| V2 | Indexes |
| V3 | Seed admin user |
| V4 | Refresh token table |
| V5 | Delivery confirmation fields |
| V6 | Payment client secret |
| V7 | Remove hardcoded admin |
| V8 | Produce unit and bag weight |
| V9 | Email verification |
| V10 | Password reset |
| V11 | Produce image URL |
| V12 | Dispute fields |
| V13 | Seed produce and categories |
| V14 | Seed batches and listings |
| V15 | Remove audit log FK on performed_by |
| V16 | Fix missing Vegetables category and Tomatoes |
| V17 | Fix produce image URLs and seed delivery slots |
| V18 | Extend batch expiry dates |

---

## Known Gaps

| Gap | Notes |
|---|---|
| Image upload streams file into heap | `file.getBytes()` loads entire image into memory. Production fix: streaming Cloudinary upload |
| Content-type validation bypassable | Magic byte validation not implemented |
| Cloudinary orphan on DB fail | If DB save fails after upload, image is stranded. Production fix: cleanup job |
| Refund flow | `refundStatus` field exists, no Stripe refund call or UI |
| Audit endpoint | `GET /api/admin/audit` planned, not built |
| Rate limiting is per-instance | Bucket4j uses in-memory Caffeine. Multi-instance deployments need Redis-backed counters |
| Historical order data | Orders placed before the multi-item checkout fix (ADR-015) may have incomplete `order_item`/`reservation` rows — no data repair performed |

---

## Architectural Decision Records

All major decisions are documented in [`docs/adr/`](docs/adr/). Key decisions covered:

- ADR-001: Two-phase checkout
- ADR-002: Atomic stock deduction
- ADR-003: Slot booking at RESERVED
- ADR-004: ID-passing pattern in schedulers
- ADR-005: WebSocket via AFTER_COMMIT
- ADR-006: Stateful refresh tokens
- ADR-007: Lazy proxy fix pattern
- ADR-008: N+1 query fix
- ADR-009: Analytics SQL aggregation
- ADR-010: Image upload outside transaction
- ADR-011: Dispute flow design
- ADR-012: Idempotent webhook processing
- ADR-013: Audit log FK removal
- ADR-014: Rate limiting with Bucket4j
- ADR-015: Explicit flush in checkout loop and list-based reservation handling
