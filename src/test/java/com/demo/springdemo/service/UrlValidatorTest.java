package com.demo.springdemo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.demo.springdemo.config.AppProperties;
import com.demo.springdemo.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private final UrlValidator validator =
            new UrlValidator(new AppProperties("https://sho.rt", 7, 2048, 60, false));

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com", "http://example.com/a?b=c#d", "https://sub.example.co.uk:8443/x", "https://8.8.8.8/"})
    void acceptsValidUrls(String url) {
        assertEquals(url, validator.validate(url));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("https://example.com", validator.validate("  https://example.com  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "javascript:alert(1)", "data:text/html,<script>1</script>", "file:///etc/passwd",
            "ftp://example.com", "//example.com", "example.com", "https://", "https://exa mple.com",
            "https://example.com/\r\nSet-Cookie: a=b", "https://user:pw@example.com",
            "http://localhost/admin", "http://app.localhost/", "http://127.0.0.1/", "http://10.0.0.5/",
            "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data", "http://[::1]/",
            "http://[::ffff:127.0.0.1]/", "http://[fd00::1]/", "http://0.0.0.0/", "http://100.64.0.1/",
            "http://999.1.1.1/", "http://2130706433/", "http://0x7f.0.0.1/", "http://db.internal/",
            "http://printer.local/", "https://sho.rt/abc"})
    void rejectsDangerousOrMalformedUrls(String url) {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(url));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void rejectsNull() {
        assertThrows(ApiException.class, () -> validator.validate(null));
    }

    @Test
    void rejectsOverlongUrl() {
        UrlValidator small = new UrlValidator(new AppProperties("https://sho.rt", 7, 30, 60, false));
        assertThrows(ApiException.class, () -> small.validate("https://example.com/" + "a".repeat(50)));
    }

    @Test
    void privateHostsAllowedWhenExplicitlyEnabled() {
        UrlValidator dev = new UrlValidator(new AppProperties("https://sho.rt", 7, 2048, 60, true));
        assertEquals("http://localhost:3000", dev.validate("http://localhost:3000"));
    }
}
