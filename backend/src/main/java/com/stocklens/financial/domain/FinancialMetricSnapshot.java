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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "financial_metric_snapshot")
public class FinancialMetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "pe_ttm", precision = 30, scale = 12)
    private BigDecimal peTtm;

    @Column(name = "forward_pe", precision = 30, scale = 12)
    private BigDecimal forwardPe;

    @Column(name = "peg_ratio", precision = 30, scale = 12)
    private BigDecimal pegRatio;

    @Column(name = "price_to_sales", precision = 30, scale = 12)
    private BigDecimal priceToSales;

    @Column(name = "revenue_ttm", precision = 30, scale = 2)
    private BigDecimal revenueTtm;

    @Column(name = "gross_margin", precision = 30, scale = 12)
    private BigDecimal grossMargin;

    @Column(name = "net_margin", precision = 30, scale = 12)
    private BigDecimal netMargin;

    @Column(name = "return_on_equity", precision = 30, scale = 12)
    private BigDecimal returnOnEquity;

    @Column(name = "revenue_growth", precision = 30, scale = 12)
    private BigDecimal revenueGrowth;

    @Column(name = "earnings_growth", precision = 30, scale = 12)
    private BigDecimal earningsGrowth;

    @Column(name = "debt_to_equity", precision = 30, scale = 12)
    private BigDecimal debtToEquity;

    @Column(name = "current_ratio", precision = 30, scale = 12)
    private BigDecimal currentRatio;

    @Column(precision = 30, scale = 12)
    private BigDecimal beta;

    @Column(length = 3)
    private String currency;

    @Column(name = "reported_at")
    private LocalDate reportedAt;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data_json", columnDefinition = "jsonb")
    private String rawDataJson;

    protected FinancialMetricSnapshot() {}

    public FinancialMetricSnapshot(
            Company company,
            BigDecimal peTtm,
            BigDecimal forwardPe,
            BigDecimal pegRatio,
            BigDecimal priceToSales,
            BigDecimal revenueTtm,
            BigDecimal grossMargin,
            BigDecimal netMargin,
            BigDecimal returnOnEquity,
            BigDecimal revenueGrowth,
            BigDecimal earningsGrowth,
            BigDecimal debtToEquity,
            BigDecimal currentRatio,
            BigDecimal beta,
            String currency,
            LocalDate reportedAt,
            Instant retrievedAt,
            String providerName,
            String rawDataJson) {
        this.company = company;
        this.peTtm = peTtm;
        this.forwardPe = forwardPe;
        this.pegRatio = pegRatio;
        this.priceToSales = priceToSales;
        this.revenueTtm = revenueTtm;
        this.grossMargin = grossMargin;
        this.netMargin = netMargin;
        this.returnOnEquity = returnOnEquity;
        this.revenueGrowth = revenueGrowth;
        this.earningsGrowth = earningsGrowth;
        this.debtToEquity = debtToEquity;
        this.currentRatio = currentRatio;
        this.beta = beta;
        this.currency = currency;
        this.reportedAt = reportedAt;
        this.retrievedAt = retrievedAt;
        this.providerName = providerName;
        this.rawDataJson = rawDataJson;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public BigDecimal getPeTtm() { return peTtm; }
    public BigDecimal getForwardPe() { return forwardPe; }
    public BigDecimal getPegRatio() { return pegRatio; }
    public BigDecimal getPriceToSales() { return priceToSales; }
    public BigDecimal getRevenueTtm() { return revenueTtm; }
    public BigDecimal getGrossMargin() { return grossMargin; }
    public BigDecimal getNetMargin() { return netMargin; }
    public BigDecimal getReturnOnEquity() { return returnOnEquity; }
    public BigDecimal getRevenueGrowth() { return revenueGrowth; }
    public BigDecimal getEarningsGrowth() { return earningsGrowth; }
    public BigDecimal getDebtToEquity() { return debtToEquity; }
    public BigDecimal getCurrentRatio() { return currentRatio; }
    public BigDecimal getBeta() { return beta; }
    public String getCurrency() { return currency; }
    public LocalDate getReportedAt() { return reportedAt; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public String getProviderName() { return providerName; }
    public String getRawDataJson() { return rawDataJson; }
}
