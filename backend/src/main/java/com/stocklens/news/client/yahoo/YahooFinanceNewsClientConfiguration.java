package com.stocklens.news.client.yahoo;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YahooFinanceNewsProperties.class)
public class YahooFinanceNewsClientConfiguration {

    static final String USER_AGENT = "StockLens-AI/1.0 (educational project)";

    @Bean
    @Qualifier("yahooFinanceNewsRestClient")
    RestClient yahooFinanceNewsRestClient(YahooFinanceNewsProperties properties) {
        validate(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    private void validate(YahooFinanceNewsProperties properties) {
        if (properties.getBaseUrl() == null
                || properties.getBaseUrl().getScheme() == null
                || properties.getBaseUrl().getHost() == null
                || properties.getBaseUrl().getRawUserInfo() != null
                || !(properties.getBaseUrl().getScheme().equalsIgnoreCase("http")
                        || properties.getBaseUrl().getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalStateException(
                    "Yahoo Finance news base URL must be an absolute HTTP or HTTPS URL "
                            + "without credentials.");
        }
        requirePositive(properties.getConnectTimeout(), "Yahoo Finance news connect timeout");
        requirePositive(properties.getReadTimeout(), "Yahoo Finance news read timeout");
        if (properties.getMaxAttempts() < 1 || properties.getMaxAttempts() > 2) {
            throw new IllegalStateException(
                    "Yahoo Finance news max attempts must be between 1 and 2.");
        }
    }

    private void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be positive.");
        }
    }
}
