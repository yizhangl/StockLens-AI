package com.stocklens;

import com.stocklens.research.ai.AiComparisonProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(AiComparisonProperties.class)
public class StockLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockLensApplication.class, args);
    }
}
