package com.demo.springdemo.controller;

import com.demo.springdemo.config.AppProperties;
import com.demo.springdemo.dto.ShortenRequest;
import com.demo.springdemo.dto.ShortenResponse;
import com.demo.springdemo.dto.StatsResponse;
import com.demo.springdemo.service.UrlService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP layer: validate input, delegate to the service, shape the response. */
@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;
    private final AppProperties props;

    @PostMapping("/api/v1/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse body = ShortenResponse.from(urlService.shorten(request), props.baseUrl());
        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    @GetMapping("/api/v1/urls/{shortCode}/stats")
    public StatsResponse stats(@PathVariable String shortCode) {
        return StatsResponse.from(urlService.getStats(shortCode));
    }

    // Regex keeps junk paths (favicon.ico, scanners) from ever reaching the database.
    @GetMapping("/{shortCode:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String target = urlService.resolve(shortCode);
        // 302 + no-store: browsers must not cache, otherwise clicks go uncounted and expiry is ignored.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
