package com.stocklens;

import com.stocklens.research.ai.AiComparisonProperties;
import com.stocklens.common.cache.StockLensCacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties({AiComparisonProperties.class, StockLensCacheProperties.class})
public class StockLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockLensApplication.class, args);
    }
}
