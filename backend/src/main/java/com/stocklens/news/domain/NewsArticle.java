package com.stocklens.news.domain;

import com.stocklens.company.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "news_article")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(nullable = false, length = 1000)
    private String headline;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "article_url", nullable = false, length = 2048)
    private String articleUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    private String urlHash;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "news_article_company",
            joinColumns = @JoinColumn(name = "news_article_id"),
            inverseJoinColumns = @JoinColumn(name = "company_id"))
    private Set<Company> companies = new LinkedHashSet<>();

    protected NewsArticle() {}

    public NewsArticle(
            String externalId,
            String headline,
            String sourceName,
            String articleUrl,
            String description,
            Instant publishedAt,
            Instant retrievedAt,
            String urlHash,
            String providerName) {
        this.externalId = externalId;
        this.articleUrl = articleUrl;
        this.urlHash = urlHash;
        this.providerName = providerName;
        updateContent(headline, sourceName, description, publishedAt, retrievedAt);
    }

    public void updateContent(
            String headline,
            String sourceName,
            String description,
            Instant publishedAt,
            Instant retrievedAt) {
        this.headline = headline;
        this.sourceName = sourceName;
        this.description = description;
        this.publishedAt = publishedAt;
        this.retrievedAt = retrievedAt;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getHeadline() {
        return headline;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getArticleUrl() {
        return articleUrl;
    }

    public String getDescription() {
        return description;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public String getProviderName() {
        return providerName;
    }

    public Set<Company> getCompanies() {
        return Collections.unmodifiableSet(companies);
    }
}
