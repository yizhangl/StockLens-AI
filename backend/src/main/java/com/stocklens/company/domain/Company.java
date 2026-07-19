package com.stocklens.company.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 64)
    private String exchange;

    @Column(length = 128)
    private String sector;

    @Column(length = 128)
    private String industry;

    @Column(length = 128)
    private String country;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "provider_symbol", nullable = false, length = 64)
    private String providerSymbol;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {}

    public Company(
            String ticker,
            String name,
            String exchange,
            String sector,
            String industry,
            String country,
            String websiteUrl,
            String description,
            String logoUrl,
            String providerSymbol,
            Instant createdAt,
            Instant updatedAt) {
        this.ticker = ticker;
        this.createdAt = createdAt;
        updateProfile(
                name,
                exchange,
                sector,
                industry,
                country,
                websiteUrl,
                description,
                logoUrl,
                providerSymbol,
                updatedAt);
    }

    public void updateProfile(
            String name,
            String exchange,
            String sector,
            String industry,
            String country,
            String websiteUrl,
            String description,
            String logoUrl,
            String providerSymbol,
            Instant updatedAt) {
        this.name = name;
        this.exchange = exchange;
        this.sector = sector;
        this.industry = industry;
        this.country = country;
        this.websiteUrl = websiteUrl;
        this.description = description;
        this.logoUrl = logoUrl;
        this.providerSymbol = providerSymbol;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public String getExchange() {
        return exchange;
    }

    public String getSector() {
        return sector;
    }

    public String getIndustry() {
        return industry;
    }

    public String getCountry() {
        return country;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getProviderSymbol() {
        return providerSymbol;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
