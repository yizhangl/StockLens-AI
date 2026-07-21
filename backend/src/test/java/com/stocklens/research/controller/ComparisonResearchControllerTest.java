package com.stocklens.research.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stocklens.common.time.TimeConfiguration;
import com.stocklens.common.web.GlobalExceptionHandler;
import com.stocklens.common.web.RequestIdFilter;
import com.stocklens.research.dto.AdvantageResponse;
import com.stocklens.research.dto.AdvantagesResponse;
import com.stocklens.research.dto.ComparisonResearchResponse;
import com.stocklens.research.service.ComparisonResearchService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ComparisonResearchController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, TimeConfiguration.class})
class ComparisonResearchControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ComparisonResearchService service;

    @Test
    void returnsOnlyThePublicGroundedContract() throws Exception {
        when(service.generate(" aapl ", "msft")).thenReturn(response());
        mockMvc.perform(post("/api/v1/comparisons/research").contentType("application/json")
                        .content("{\"leftTicker\":\" aapl \",\"rightTicker\":\"msft\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leftTicker").value("AAPL"))
                .andExpect(jsonPath("$.advantages.valuation.winner").value("AAPL"))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.rawPrompt").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    private ComparisonResearchResponse response() {
        Instant now = Instant.parse("2026-07-21T00:00:00Z");
        AdvantageResponse advantage = new AdvantageResponse("AAPL", "Reported metrics differ.", List.of("M1"));
        return new ComparisonResearchResponse(1L, "AAPL", "MSFT", "Grounded summary.",
                new AdvantagesResponse(advantage, advantage, advantage, advantage), List.of(), List.of(),
                "gpt-test", "stock-comparison-v1", now, now, false);
    }
}
