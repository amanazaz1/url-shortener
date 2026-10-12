package com.demo.springdemo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.springdemo.config.AppProperties;
import com.demo.springdemo.dto.ShortenRequest;
import com.demo.springdemo.entity.UrlMapping;
import com.demo.springdemo.exception.ApiException;
import com.demo.springdemo.repository.UrlMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class UrlServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private static final String URL = "https://example.com/page";

    private UrlMappingRepository repo;
    private UrlLookupService lookup;
    private ShortCodeGenerator generator;
    private UrlService service;

    @BeforeEach
    void setUp() {
        repo = mock(UrlMappingRepository.class);
        lookup = mock(UrlLookupService.class);
        generator = mock(ShortCodeGenerator.class);
        AppProperties props = new AppProperties("https://sho.rt", 7, 2048, 60, false);
        service = new UrlService(repo, lookup, new UrlValidator(props), generator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void assertApi(HttpStatus expected, Runnable r) {
        ApiException ex = assertThrows(ApiException.class, r::run);
        assertEquals(expected, ex.getStatus());
    }

    @Test
    void shortenSavesGeneratedCode() {
        when(generator.next()).thenReturn("aaaaaaa");
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        UrlMapping m = service.shorten(new ShortenRequest(URL, null, null));
        assertEquals("aaaaaaa", m.getShortCode());
        assertEquals(null, m.getExpiresAt());
    }

    @Test
    void shortenRetriesOnCollision() {
        when(generator.next()).thenReturn("aaaaaaa", "bbbbbbb");
        when(repo.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(i -> i.getArgument(0));
        assertEquals("bbbbbbb", service.shorten(new ShortenRequest(URL, null, null)).getShortCode());
    }

    @Test
    void shortenGivesUpAfterMaxAttempts() {
        when(generator.next()).thenReturn("aaaaaaa");
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertApi(HttpStatus.SERVICE_UNAVAILABLE, () -> service.shorten(new ShortenRequest(URL, null, null)));
        verify(repo, times(5)).saveAndFlush(any());
    }

    @Test
    void ttlSetsExpiryRelativeToClock() {
        when(generator.next()).thenReturn("aaaaaaa");
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        UrlMapping m = service.shorten(new ShortenRequest(URL, null, 60L));
        assertEquals(NOW.plusSeconds(60), m.getExpiresAt());
    }

    @Test
    void aliasConflictReturns409() {
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertApi(HttpStatus.CONFLICT, () -> service.shorten(new ShortenRequest(URL, "promo", null)));
    }

    @Test
    void reservedAliasRejected() {
        assertApi(HttpStatus.BAD_REQUEST, () -> service.shorten(new ShortenRequest(URL, "API", null)));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void invalidUrlNeverReachesDatabase() {
        assertApi(HttpStatus.BAD_REQUEST,
                () -> service.shorten(new ShortenRequest("javascript:alert(1)", "evil", null)));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void resolveReturnsTargetAndCountsClick() {
        when(lookup.find("abc")).thenReturn(new CachedUrl(URL, null));
        assertEquals(URL, service.resolve("abc"));
        verify(repo).incrementClickCount("abc");
    }

    @Test
    void resolveUnknownIs404() {
        when(lookup.find("abc")).thenReturn(null);
        assertApi(HttpStatus.NOT_FOUND, () -> service.resolve("abc"));
    }

    @Test
    void resolveExpiredIs410AndDoesNotCountClick() {
        when(lookup.find("abc")).thenReturn(new CachedUrl(URL, NOW)); // boundary: exactly now => expired
        assertApi(HttpStatus.GONE, () -> service.resolve("abc"));
        verify(repo, never()).incrementClickCount(any());
    }

    @Test
    void clickRecordingFailureDoesNotBreakRedirect() {
        when(lookup.find("abc")).thenReturn(new CachedUrl(URL, null));
        when(repo.incrementClickCount("abc")).thenThrow(new RuntimeException("db down"));
        assertEquals(URL, service.resolve("abc"));
    }
}
