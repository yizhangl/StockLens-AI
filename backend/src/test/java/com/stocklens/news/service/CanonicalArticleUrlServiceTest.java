package com.stocklens.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CanonicalArticleUrlServiceTest {

    private final CanonicalArticleUrlService service = new CanonicalArticleUrlService();

    @Test
    void trimsNormalizesSchemeAndHostAndRemovesFragment() {
        var result = service.canonicalize(
                "  HTTPS://Example.COM:8443/news/item?utm_source=kept#section  ");

        assertThat(result.url())
                .isEqualTo("https://example.com:8443/news/item?utm_source=kept");
        assertThat(result.hash()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void equivalentCanonicalUrlsHaveDeterministicHashes() {
        var first = service.canonicalize("HTTPS://EXAMPLE.COM/story#first");
        var second = service.canonicalize("https://example.com/story#second");

        assertThat(first.url()).isEqualTo(second.url());
        assertThat(first.hash()).isEqualTo(second.hash());
        assertThat(service.canonicalize(first.url()).hash()).isEqualTo(first.hash());
    }

    @Test
    void preservesQueryParametersAndDistinguishesDifferentUrls() {
        var first = service.canonicalize("https://example.com/story?a=1&b=2");
        var reordered = service.canonicalize("https://example.com/story?b=2&a=1");
        var differentPath = service.canonicalize("https://example.com/other?a=1&b=2");

        assertThat(first.url()).endsWith("?a=1&b=2");
        assertThat(first.hash()).isNotEqualTo(reordered.hash()).isNotEqualTo(differentPath.hash());
    }

    @Test
    void rejectsMalformedUnsafeAndCredentialBearingUrls() {
        assertInvalid(null);
        assertInvalid(" ");
        assertInvalid("not a url");
        assertInvalid("javascript:alert(1)");
        assertInvalid("file:///tmp/story");
        assertInvalid("https://user:password@example.com/story");
        assertInvalid("https:///missing-host");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> service.canonicalize(value))
                .isInstanceOf(CanonicalArticleUrlService.InvalidArticleUrlException.class)
                .hasMessageContaining("HTTP or HTTPS");
    }
}
