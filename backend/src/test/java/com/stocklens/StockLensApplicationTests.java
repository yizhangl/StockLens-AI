package com.stocklens;

import com.stocklens.support.IntegrationTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestContainers.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockLensApplicationTests {

    @Test
    void contextLoads() {
    }
}
