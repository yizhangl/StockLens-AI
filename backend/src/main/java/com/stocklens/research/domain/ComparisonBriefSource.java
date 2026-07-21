package com.stocklens.research.domain;

import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.news.domain.NewsArticle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "comparison_brief_source")
@IdClass(ComparisonBriefSource.Key.class)
public class ComparisonBriefSource {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comparison_brief_id", nullable = false)
    private ComparisonBrief comparisonBrief;

    @Id
    @Column(name = "source_reference", nullable = false, length = 32)
    private String sourceReference;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_article_id")
    private NewsArticle newsArticle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_snapshot_id")
    private FinancialMetricSnapshot financialSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_snapshot_id")
    private MarketSnapshot marketSnapshot;

    protected ComparisonBriefSource() {}

    public ComparisonBriefSource(ComparisonBrief brief, String reference, String type,
            NewsArticle newsArticle, FinancialMetricSnapshot financialSnapshot,
            MarketSnapshot marketSnapshot) {
        this.comparisonBrief = brief;
        this.sourceReference = reference;
        this.sourceType = type;
        this.newsArticle = newsArticle;
        this.financialSnapshot = financialSnapshot;
        this.marketSnapshot = marketSnapshot;
    }

    public static class Key implements Serializable {
        private Long comparisonBrief;
        private String sourceReference;

        public Key() {}

        public Key(Long comparisonBrief, String sourceReference) {
            this.comparisonBrief = comparisonBrief;
            this.sourceReference = sourceReference;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(comparisonBrief, key.comparisonBrief)
                    && Objects.equals(sourceReference, key.sourceReference);
        }

        @Override
        public int hashCode() { return Objects.hash(comparisonBrief, sourceReference); }
    }

    public String getSourceReference() { return sourceReference; }
}
