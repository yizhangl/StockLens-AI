package com.stocklens.news.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CanonicalArticleUrlService {

    private static final int MAX_URL_LENGTH = 2048;

    public CanonicalArticleUrl canonicalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidArticleUrlException();
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null
                    || host == null
                    || uri.isOpaque()
                    || uri.getRawUserInfo() != null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new InvalidArticleUrlException();
            }

            String canonical = buildCanonicalUrl(uri, scheme, host);
            if (canonical.length() > MAX_URL_LENGTH) {
                throw new InvalidArticleUrlException();
            }
            return new CanonicalArticleUrl(canonical, sha256(canonical));
        } catch (IllegalArgumentException exception) {
            if (exception instanceof InvalidArticleUrlException invalid) {
                throw invalid;
            }
            throw new InvalidArticleUrlException();
        }
    }

    private String buildCanonicalUrl(URI uri, String scheme, String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.contains(":")) {
            normalizedHost = "[" + normalizedHost + "]";
        }

        StringBuilder canonical = new StringBuilder()
                .append(scheme.toLowerCase(Locale.ROOT))
                .append("://")
                .append(normalizedHost);
        if (uri.getPort() >= 0) {
            canonical.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            canonical.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            canonical.append('?').append(uri.getRawQuery());
        }
        return canonical.toString();
    }

    private String sha256(String canonicalUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record CanonicalArticleUrl(String url, String hash) {}

    public static final class InvalidArticleUrlException extends IllegalArgumentException {
        private InvalidArticleUrlException() {
            super("Article URL must be an absolute HTTP or HTTPS URL without user information.");
        }
    }
}
