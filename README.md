# URL Shortener (Spring Boot 3, Java 17)

Shortens URLs, redirects (302), counts every click, supports custom aliases and expiry,
validates targets against abuse (SSRF / open-redirect style inputs), rate-limits writes.

## Run
```bash
mvn spring-boot:run                                   # H2 file DB + in-process Caffeine cache
mvn spring-boot:run -Dspring-boot.run.profiles=redis  # Redis cache (needs Redis on localhost:6379)
mvn verify                                            # unit + integration tests
```
Swagger UI: http://localhost:8080/swagger-ui.html · Health: /actuator/health

## API
| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/shorten` `{"url","customAlias"?,"ttlSeconds"?}` | 201 `{shortUrl, shortCode, ...}` |
| GET | `/{shortCode}` | 302 redirect · 404 unknown · 410 expired |
| GET | `/api/v1/urls/{shortCode}/stats` | 200 `{clickCount, createdAt, expiresAt, ...}` |

Errors are RFC 7807 `application/problem+json` with a stable `code` field
(`invalid_request`, `alias_taken`, `link_not_found`, `link_expired`, `rate_limited`, ...).

```bash
curl -s -X POST localhost:8080/api/v1/shorten -H 'Content-Type: application/json' \
     -d '{"url":"https://example.com/docs","ttlSeconds":3600}'
curl -i localhost:8080/<shortCode>
```

## Layout
`controller` (HTTP only) → `service` (rules: UrlService, UrlValidator, ShortCodeGenerator, cached UrlLookupService)
→ `repository` (JPA). `config` holds typed properties and the rate-limit filter.

## Known limitations
Single-node H2 and in-memory rate limiting (use Postgres + Redis/gateway limiting to scale out); no auth on
stats endpoint; hostnames are not DNS-resolved during validation (DNS-rebinding not covered); `ddl-auto=update`
should become Flyway migrations in production.
