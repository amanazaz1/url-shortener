package com.demo.springdemo.service;

import com.demo.springdemo.dto.ShortenRequest;
import com.demo.springdemo.entity.UrlMapping;
import com.demo.springdemo.exception.ApiException;
import com.demo.springdemo.repository.UrlMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Business logic. Deliberately NOT @Transactional at class level: when a unique-constraint violation
 * happens inside a transaction the transaction becomes rollback-only, which would break our retry loop.
 * Each repository call runs in its own short transaction instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final Set<String> RESERVED_ALIASES =
            Set.of("api", "actuator", "h2-console", "swagger-ui", "error", "health");

    private final UrlMappingRepository repository;
    private final UrlLookupService lookupService;
    private final UrlValidator validator;
    private final ShortCodeGenerator codeGenerator;
    private final Clock clock;

    public UrlMapping shorten(ShortenRequest request) {
        String url = validator.validate(request.url());
        Instant expiresAt = request.ttlSeconds() == null ? null : clock.instant().plusSeconds(request.ttlSeconds());

        if (request.customAlias() != null) {
            if (RESERVED_ALIASES.contains(request.customAlias().toLowerCase(Locale.ROOT))) {
                throw ApiException.invalidRequest("customAlias is reserved");
            }
            try {
                return repository.saveAndFlush(newMapping(url, request.customAlias(), expiresAt));
            } catch (DataIntegrityViolationException e) {
                throw ApiException.aliasTaken();
            }
        }

        // Insert-and-retry instead of check-then-insert: the latter is a race between concurrent requests.
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            try {
                return repository.saveAndFlush(newMapping(url, codeGenerator.next(), expiresAt));
            } catch (DataIntegrityViolationException e) {
                log.warn("Short code collision (attempt {}/{})", attempt, MAX_CODE_ATTEMPTS);
            }
        }
        throw ApiException.codeGenerationFailed();
    }

    /** Resolve for redirect: cached lookup -> expiry check -> count the click (every click, cache hit or not). */
    public String resolve(String shortCode) {
        CachedUrl cached = lookupService.find(shortCode);
        if (cached == null) {
            throw ApiException.notFound();
        }
        if (cached.expiresAt() != null && !cached.expiresAt().isAfter(clock.instant())) {
            throw ApiException.expired();
        }
        recordClick(shortCode);
        return cached.originalUrl();
    }

    public UrlMapping getStats(String shortCode) {
        return repository.findByShortCode(shortCode).orElseThrow(ApiException::notFound);
    }

    /** Best effort: analytics must never break the redirect. */
    private void recordClick(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
        } catch (RuntimeException e) {
            log.warn("Failed to record click for {}", shortCode, e);
        }
    }

    private static UrlMapping newMapping(String url, String code, Instant expiresAt) {
        UrlMapping m = new UrlMapping();
        m.setOriginalUrl(url);
        m.setShortCode(code);
        m.setExpiresAt(expiresAt);
        return m;
    }
}
