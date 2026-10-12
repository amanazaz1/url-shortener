package com.demo.springdemo.service;

import com.demo.springdemo.config.AppProperties;
import com.demo.springdemo.exception.ApiException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Decides what the service is willing to redirect to. Blocks non-http(s) schemes (javascript:, data:, file:),
 * credentials in URLs, private/loopback/link-local targets (SSRF-style abuse) and links back to ourselves.
 * Limitation: hostnames are not resolved, so DNS-rebinding style targets are out of scope (documented).
 */
@Component
public class UrlValidator {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    // 2130706433, 0x7f.1, 017700000001 ... are read as IPv4 by browsers/libc
    private static final Pattern NUMERIC_HOST =
            Pattern.compile("^(0x[0-9a-f]+|\\d+)(\\.(0x[0-9a-f]+|\\d+))*$", Pattern.CASE_INSENSITIVE);
    private static final List<String> BLOCKED_SUFFIXES = List.of(".localhost", ".local", ".internal", ".lan");

    private final AppProperties props;
    private final String ownHost;

    public UrlValidator(AppProperties props) {
        this.props = props;
        String host = URI.create(props.baseUrl()).getHost();
        this.ownHost = host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.invalidRequest("url is required");
        }
        String url = raw.strip();
        if (url.length() > props.maxUrlLength()) {
            throw ApiException.invalidRequest("url exceeds " + props.maxUrlLength() + " characters");
        }
        URI uri;
        try {
            uri = new URI(url); // rejects spaces / control characters (header-injection attempts)
        } catch (URISyntaxException e) {
            throw ApiException.invalidRequest("url is malformed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw ApiException.invalidRequest("only http and https urls are allowed");
        }
        if (uri.getHost() == null) {
            throw ApiException.invalidRequest("url must include a valid host");
        }
        if (uri.getUserInfo() != null) {
            throw ApiException.invalidRequest("credentials in url are not allowed");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals(ownHost)) {
            throw ApiException.invalidRequest("url must not point back at this service");
        }
        if (!props.allowPrivateHosts() && isPrivateHost(host)) {
            throw ApiException.invalidRequest("target host is not allowed");
        }
        return url;
    }

    private boolean isPrivateHost(String host) {
        if (host.equals("localhost") || BLOCKED_SUFFIXES.stream().anyMatch(host::endsWith)) {
            return true;
        }
        boolean ipv6 = host.startsWith("[");
        String literal = ipv6 ? host.substring(1, host.length() - 1) : host;
        if (ipv6 || IPV4.matcher(literal).matches()) {
            try {
                // Literal only => no DNS lookup happens here.
                return isNonPublic(InetAddress.getByName(literal));
            } catch (UnknownHostException e) {
                return true;
            }
        }
        return NUMERIC_HOST.matcher(host).matches();
    }

    private static boolean isNonPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC; // fc00::/7 unique-local
        }
        return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10 carrier-grade NAT
    }
}
