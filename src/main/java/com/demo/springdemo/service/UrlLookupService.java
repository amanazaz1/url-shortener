package com.demo.springdemo.service;

import com.demo.springdemo.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-through cache, in its own bean on purpose: @Cacheable works via a proxy, so a call from
 * another method of the same class would silently bypass it.
 * Only the lookup is cached; click counting happens outside (see UrlService.resolve).
 */
@Service
@RequiredArgsConstructor
public class UrlLookupService {

    private final UrlMappingRepository repository;

    @Cacheable(cacheNames = "urls", key = "#shortCode", unless = "#result == null")
    @Transactional(readOnly = true)
    public CachedUrl find(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(m -> new CachedUrl(m.getOriginalUrl(), m.getExpiresAt()))
                .orElse(null);
    }
}
