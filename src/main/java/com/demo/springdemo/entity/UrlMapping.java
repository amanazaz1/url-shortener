package com.demo.springdemo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Not @Data on purpose: generated equals/hashCode/toString on JPA entities cause subtle bugs.
 * The unique constraint is the real guard against duplicate codes (it also creates the lookup index).
 */
@Entity
@Table(name = "url_mapping",
        uniqueConstraints = @UniqueConstraint(name = "uk_url_mapping_short_code", columnNames = "short_code"))
@Getter
@Setter
@NoArgsConstructor
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;
}
