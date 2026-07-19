package com.stocklens.market.client.fmp;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FmpProperties.class)
public class FmpClientConfiguration {

    @Bean
    @Qualifier("fmpRestClient")
    RestClient fmpRestClient(FmpProperties properties) {
        validate(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    private void validate(FmpProperties properties) {
        if (properties.getBaseUrl() == null
                || properties.getBaseUrl().getScheme() == null
                || !(properties.getBaseUrl().getScheme().equals("http")
                        || properties.getBaseUrl().getScheme().equals("https"))) {
            throw new IllegalStateException("FMP base URL must use HTTP or HTTPS.");
        }
        requirePositive(properties.getConnectTimeout(), "FMP connect timeout");
        requirePositive(properties.getReadTimeout(), "FMP read timeout");
        if (properties.getMaxAttempts() < 1 || properties.getMaxAttempts() > 3) {
            throw new IllegalStateException("FMP max attempts must be between 1 and 3.");
        }
    }

    private void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be positive.");
        }
    }
}
