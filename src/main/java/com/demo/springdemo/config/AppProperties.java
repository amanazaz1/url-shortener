package com.demo.springdemo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Typed, validated configuration: the app fails fast at startup on bad values. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") @NotBlank String baseUrl,
        @DefaultValue("7") @Min(4) @Max(16) int codeLength,
        @DefaultValue("2048") @Positive int maxUrlLength,
        @DefaultValue("60") @Positive int rateLimitPerMinute,
        @DefaultValue("false") boolean allowPrivateHosts) {
}
