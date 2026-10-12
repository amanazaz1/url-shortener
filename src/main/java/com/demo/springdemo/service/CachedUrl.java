package com.demo.springdemo.service;

import java.io.Serializable;
import java.time.Instant;

/** What we cache: just enough to redirect and to enforce expiry. Serializable for Redis. */
public record CachedUrl(String originalUrl, Instant expiresAt) implements Serializable {
}
