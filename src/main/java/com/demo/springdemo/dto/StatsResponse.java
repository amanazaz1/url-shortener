package com.demo.springdemo.dto;

import com.demo.springdemo.entity.UrlMapping;
import java.time.Instant;

public record StatsResponse(String shortCode, String originalUrl, long clickCount,
                            Instant createdAt, Instant expiresAt) {

    public static StatsResponse from(UrlMapping m) {
        return new StatsResponse(m.getShortCode(), m.getOriginalUrl(), m.getClickCount(),
                m.getCreatedAt(), m.getExpiresAt());
    }
}
