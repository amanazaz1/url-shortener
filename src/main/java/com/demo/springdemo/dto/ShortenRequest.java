package com.demo.springdemo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "customAlias must be 3-32 chars of A-Z a-z 0-9 _ -")
        String customAlias,

        @Min(value = 1, message = "ttlSeconds must be >= 1")
        @Max(value = 31_536_000, message = "ttlSeconds must be <= 1 year")
        Long ttlSeconds) {
}
