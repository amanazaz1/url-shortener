package com.demo.springdemo.dto;

import com.demo.springdemo.entity.UrlMapping;
import java.time.Instant;

public record ShortenResponse(String shortUrl, String shortCode, String originalUrl,
                              Instant createdAt, Instant expiresAt) {

    public static ShortenResponse from(UrlMapping m, String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new ShortenResponse(base + "/" + m.getShortCode(), m.getShortCode(), m.getOriginalUrl(),
                m.getCreatedAt(), m.getExpiresAt());
    }
}
