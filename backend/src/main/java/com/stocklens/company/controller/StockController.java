package com.stocklens.company.controller;

import com.stocklens.company.dto.StockResponse;
import com.stocklens.company.dto.StockResponseMapper;
import com.stocklens.company.service.StockQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockQueryService stockQueryService;
    private final StockResponseMapper responseMapper;

    public StockController(StockQueryService stockQueryService, StockResponseMapper responseMapper) {
        this.stockQueryService = stockQueryService;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/{ticker}")
    StockResponse getStock(@PathVariable String ticker) {
        return responseMapper.toResponse(stockQueryService.getStock(ticker));
    }
}
