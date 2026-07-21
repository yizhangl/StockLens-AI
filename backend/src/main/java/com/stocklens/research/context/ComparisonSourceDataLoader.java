package com.stocklens.research.context;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.DuplicateTickersException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.financial.repository.FinancialMetricSnapshotRepository;
import com.stocklens.financial.repository.HistoricalPriceRepository;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.market.repository.MarketSnapshotRepository;
import com.stocklens.news.domain.NewsArticle;
import com.stocklens.news.repository.NewsArticleRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads only persisted StockLens data. It deliberately has no provider dependencies. */
@Service
public class ComparisonSourceDataLoader {

    private static final int AI_NEWS_LIMIT = 5;

    private final TickerNormalizer tickerNormalizer;
    private final CompanyRepository companyRepository;
    private final MarketSnapshotRepository marketRepository;
    private final FinancialMetricSnapshotRepository metricsRepository;
    private final HistoricalPriceRepository historyRepository;
    private final NewsArticleRepository newsRepository;

    public ComparisonSourceDataLoader(
            TickerNormalizer tickerNormalizer,
            CompanyRepository companyRepository,
            MarketSnapshotRepository marketRepository,
            FinancialMetricSnapshotRepository metricsRepository,
            HistoricalPriceRepository historyRepository,
            NewsArticleRepository newsRepository) {
        this.tickerNormalizer = tickerNormalizer;
        this.companyRepository = companyRepository;
        this.marketRepository = marketRepository;
        this.metricsRepository = metricsRepository;
        this.historyRepository = historyRepository;
        this.newsRepository = newsRepository;
    }

    @Transactional(readOnly = true)
    public PersistedComparisonSourceData load(String rawLeftTicker, String rawRightTicker) {
        String leftTicker = tickerNormalizer.normalize(rawLeftTicker);
        String rightTicker = tickerNormalizer.normalize(rawRightTicker);
        if (leftTicker.equals(rightTicker)) {
            throw new DuplicateTickersException();
        }
        Company left = companyRepository.findByTicker(leftTicker)
                .orElseThrow(() -> new StockNotFoundException(leftTicker));
        Company right = companyRepository.findByTicker(rightTicker)
                .orElseThrow(() -> new StockNotFoundException(rightTicker));
        FinancialMetricSnapshot leftMetrics = metricsRepository
                .findFirstByCompany_IdOrderByRetrievedAtDescIdDesc(left.getId()).orElse(null);
        FinancialMetricSnapshot rightMetrics = metricsRepository
                .findFirstByCompany_IdOrderByRetrievedAtDescIdDesc(right.getId()).orElse(null);
        PersistedComparisonSourceData data = new PersistedComparisonSourceData(
                left,
                right,
                marketRepository.findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc(left.getId())
                        .orElse(null),
                marketRepository.findFirstByCompany_IdOrderByQuoteTimestampDescRetrievedAtDescIdDesc(right.getId())
                        .orElse(null),
                leftMetrics,
                rightMetrics,
                historyRepository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(left.getId()),
                historyRepository.findByCompany_IdOrderByTradingDateAscRetrievedAtDescIdDesc(right.getId()),
                newsRepository.findRecentByCompanyId(left.getId(), PageRequest.of(0, AI_NEWS_LIMIT)),
                newsRepository.findRecentByCompanyId(right.getId(), PageRequest.of(0, AI_NEWS_LIMIT)));
        requireSufficient(data);
        return data;
    }

    private void requireSufficient(PersistedComparisonSourceData data) {
        if (data.leftMetrics() == null || data.rightMetrics() == null) {
            throw unavailable("financial metrics");
        }
        if (!hasComparableMetric(data.leftMetrics(), data.rightMetrics())) {
            throw unavailable("comparable financial metrics");
        }
        boolean hasAdditionalCategory = data.leftMarket() != null || data.rightMarket() != null
                || hasCommonHistory(data.leftHistory(), data.rightHistory())
                || !data.leftNews().isEmpty() || !data.rightNews().isEmpty();
        if (!hasAdditionalCategory) {
            throw unavailable("market data, historical performance, or recent news");
        }
    }

    private DataUnavailableException unavailable(String missing) {
        return new DataUnavailableException(
                "Insufficient persisted source data is available for this comparison: missing " + missing + ".");
    }

    private boolean hasComparableMetric(FinancialMetricSnapshot left, FinancialMetricSnapshot right) {
        for (MetricCode code : MetricCode.values()) {
            if (value(left, code) != null && value(right, code) != null) return true;
        }
        return false;
    }

    private BigDecimal value(FinancialMetricSnapshot snapshot, MetricCode code) {
        return switch (code) {
            case PE_TTM -> snapshot.getPeTtm();
            case FORWARD_PE -> snapshot.getForwardPe();
            case PEG_RATIO -> snapshot.getPegRatio();
            case PRICE_TO_SALES -> snapshot.getPriceToSales();
            case REVENUE_TTM -> snapshot.getRevenueTtm();
            case GROSS_MARGIN -> snapshot.getGrossMargin();
            case NET_MARGIN -> snapshot.getNetMargin();
            case RETURN_ON_EQUITY -> snapshot.getReturnOnEquity();
            case REVENUE_GROWTH -> snapshot.getRevenueGrowth();
            case EARNINGS_GROWTH -> snapshot.getEarningsGrowth();
            case DEBT_TO_EQUITY -> snapshot.getDebtToEquity();
            case CURRENT_RATIO -> snapshot.getCurrentRatio();
            case BETA -> snapshot.getBeta();
        };
    }

    private boolean hasCommonHistory(List<HistoricalPrice> left, List<HistoricalPrice> right) {
        return left.stream().map(HistoricalPrice::getTradingDate)
                .anyMatch(leftDate -> right.stream()
                        .map(HistoricalPrice::getTradingDate).anyMatch(leftDate::equals));
    }
}
