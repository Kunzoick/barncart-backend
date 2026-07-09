# ADR-006 — Stateful Refresh Tokens with httpOnly Cookies

## Status
Accepted

## Context
JWT-based auth systems commonly use stateless refresh tokens — a signed JWT stored in localStorage that can be validated without a database lookup. This is convenient but has a critical flaw: stateless tokens cannot be revoked. If a refresh token is stolen, it remains valid until it expires — up to 7 days. Logout is also meaningless because the token continues to work.

Storing tokens in localStorage exposes them to XSS attacks — any injected script can read and exfiltrate the token.

## Decision
Refresh tokens are stateful: stored in the `refresh_token` table, hashed with SHA-256 before storage. Only the hash is persisted — the raw token is never stored.

The raw refresh token is sent to the client as an `httpOnly` cookie:
- `HttpOnly` — invisible to JavaScript, immune to XSS theft
- `Secure` — HTTPS only in production
- `SameSite=None` — required for cross-origin requests (Vercel frontend + Render backend)
- `Path=/api/auth` — cookie transmitted only to auth endpoints, not every API request

Access tokens are kept in `window.__accessToken__` in memory only, with a 15-minute TTL. They are never written to localStorage or sessionStorage.

On every page load, `AuthContext` performs a silent refresh — calling `POST /api/auth/refresh` to restore the session if a valid refresh token cookie exists.

Token rotation is applied on every refresh — the old token is revoked and a new one issued. This limits the window of token theft to a single use.

```java
// Logout — actual revocation
public void logout(String rawRefreshToken) {
    String hash = jwtUtil.hashWithSha256(rawRefreshToken);
    refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    });
}
```

## Consequences
- Logout actually works — token is deleted from DB, subsequent refresh attempts fail
- XSS cannot steal the refresh token — httpOnly cookie is inaccessible to JavaScript
- Every refresh requires a DB lookup — acceptable overhead; access tokens handle most request auth
- `withCredentials: true` required on all Axios requests for cross-origin cookie transmission
- `APP_COOKIE_SECURE=true` must be set in production; `false` for local dev (HTTP)
- `SameSite=None` requires `Secure` — cannot use in non-HTTPS local dev without explicitly setting `SameSite=Lax`
- Silent refresh on page load adds ~200–500ms to initial render on cold backend — acceptable tradeoff
- The original implementation attempted to set `SameSite` via `jakarta.servlet.http.Cookie`, which has no such method — the attribute was silently never set, defaulting to `SameSite=Lax` and breaking cross-origin refresh on mobile browsers. Fixed by constructing the `Set-Cookie` header manually via `response.addHeader()`. See bug log entry BUG-021
