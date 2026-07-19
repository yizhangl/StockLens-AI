package com.stocklens.financial.domain;

import com.stocklens.company.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "historical_price",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_historical_price_company_date_provider",
                columnNames = {"company_id", "trading_date", "provider_name"}))
public class HistoricalPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "open_price", precision = 30, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 30, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 30, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 30, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "adjusted_close", precision = 30, scale = 8)
    private BigDecimal adjustedClose;

    private Long volume;

    @Column(length = 3)
    private String currency;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    protected HistoricalPrice() {}

    public HistoricalPrice(
            Company company,
            LocalDate tradingDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal adjustedClose,
            Long volume,
            String currency,
            String providerName,
            Instant retrievedAt) {
        this.company = company;
        this.tradingDate = tradingDate;
        this.providerName = providerName;
        update(openPrice, highPrice, lowPrice, closePrice, adjustedClose, volume, currency, retrievedAt);
    }

    public void update(
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal adjustedClose,
            Long volume,
            String currency,
            Instant retrievedAt) {
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.adjustedClose = adjustedClose;
        this.volume = volume;
        this.currency = currency;
        this.retrievedAt = retrievedAt;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public LocalDate getTradingDate() { return tradingDate; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getAdjustedClose() { return adjustedClose; }
    public Long getVolume() { return volume; }
    public String getCurrency() { return currency; }
    public String getProviderName() { return providerName; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public BigDecimal returnValue() { return adjustedClose == null ? closePrice : adjustedClose; }
}
