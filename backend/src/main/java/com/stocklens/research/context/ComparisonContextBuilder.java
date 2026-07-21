package com.stocklens.research.context;

import com.stocklens.company.domain.Company;
import com.stocklens.financial.domain.FinancialMetricSnapshot;
import com.stocklens.financial.domain.HistoricalPrice;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricDefinition;
import com.stocklens.financial.metric.MetricDefinitionRegistry;
import com.stocklens.financial.metric.MetricUnit;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.news.domain.NewsArticle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class ComparisonContextBuilder {

    private static final int COMPANY_DESCRIPTION_MAX = 1000;
    private static final int NEWS_DESCRIPTION_MAX = 500;
    private static final MathContext RETURN_CONTEXT = new MathContext(24, RoundingMode.HALF_UP);

    private final MetricDefinitionRegistry metricRegistry;

    public ComparisonContextBuilder(MetricDefinitionRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public BuiltComparisonContext build(PersistedComparisonSourceData data) {
        List<GroundedSource> sources = new ArrayList<>();
        addCompany(sources, "C1", data.leftCompany());
        addCompany(sources, "C2", data.rightCompany());
        addMarket(sources, "Q1", data.leftCompany(), data.leftMarket());
        addMarket(sources, "Q2", data.rightCompany(), data.rightMarket());
        int metricIndex = 1;
        for (MetricDefinition definition : metricRegistry.all()) {
            BigDecimal left = metricValue(data.leftMetrics(), definition.code());
            BigDecimal right = metricValue(data.rightMetrics(), definition.code());
            if (left != null) addMetric(sources, "M" + metricIndex++, data.leftCompany(), data.leftMetrics(), definition, left);
            if (right != null) addMetric(sources, "M" + metricIndex++, data.rightCompany(), data.rightMetrics(), definition, right);
        }
        addHistoricalPerformance(sources, data);
        int newsIndex = 1;
        for (NewsArticle article : newestFirst(data.leftNews())) addNews(sources, "N" + newsIndex++, data.leftCompany(), article);
        for (NewsArticle article : newestFirst(data.rightNews())) addNews(sources, "N" + newsIndex++, data.rightCompany(), article);

        Map<String, GroundedSource> byId = new LinkedHashMap<>();
        sources.forEach(source -> byId.put(source.id(), source));
        Instant cutoff = sources.stream().map(GroundedSource::asOf).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(data.leftCompany().getUpdatedAt());
        String canonical = sources.stream().map(this::canonicalLine).reduce("", (left, right) -> left + "\n" + right);
        return new BuiltComparisonContext(data.leftCompany(), data.rightCompany(), List.copyOf(sources),
                Map.copyOf(byId), cutoff, canonical);
    }

    private void addCompany(List<GroundedSource> sources, String id, Company company) {
        String description = bounded(company.getDescription(), COMPANY_DESCRIPTION_MAX);
        String label = company.getName() + " (" + company.getTicker() + ")"
                + fields("sector", company.getSector(), "industry", company.getIndustry(), "description", description);
        sources.add(new GroundedSource(id, GroundedSourceType.COMPANY_PROFILE, company.getTicker(), label,
                "StockLens", null, company.getUpdatedAt(), company.getId(), null, null, null));
    }

    private void addMarket(List<GroundedSource> sources, String id, Company company, MarketSnapshot market) {
        if (market == null) return;
        String label = "Latest market snapshot: price=" + market.getPrice()
                + fields("currency", market.getCurrency(), "market cap", string(market.getMarketCap()),
                "daily change percent", string(market.getPriceChangePercent()));
        sources.add(new GroundedSource(id, GroundedSourceType.MARKET_SNAPSHOT, company.getTicker(), label,
                market.getProviderName(), null, market.getRetrievedAt(), company.getId(), market, null, null));
    }

    private void addMetric(List<GroundedSource> sources, String id, Company company,
            FinancialMetricSnapshot snapshot, MetricDefinition definition, BigDecimal value) {
        String unit = switch (definition.unit()) {
            case DECIMAL_FRACTION_PERCENT -> "decimal fraction";
            case CURRENCY_AMOUNT -> "currency amount";
            case RATIO -> "ratio";
        };
        String label = definition.displayName() + ": " + value.toPlainString() + " (" + unit + ")";
        sources.add(new GroundedSource(id, GroundedSourceType.FINANCIAL_METRIC, company.getTicker(), label,
                snapshot.getProviderName(), null, snapshot.getRetrievedAt(), company.getId(), null, snapshot, null));
    }

    private void addHistoricalPerformance(List<GroundedSource> sources, PersistedComparisonSourceData data) {
        Map<LocalDate, HistoricalPrice> left = byDate(data.leftHistory());
        Map<LocalDate, HistoricalPrice> right = byDate(data.rightHistory());
        List<LocalDate> common = left.keySet().stream().filter(right::containsKey).sorted().toList();
        if (common.size() < 2) return;
        LocalDate start = common.getFirst();
        LocalDate end = common.getLast();
        BigDecimal leftReturn = returnPercent(value(left.get(end)), value(left.get(start)));
        BigDecimal rightReturn = returnPercent(value(right.get(end)), value(right.get(start)));
        if (leftReturn == null || rightReturn == null) return;
        Instant asOf = List.of(left.get(end).getRetrievedAt(), right.get(end).getRetrievedAt()).stream()
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String label = "1Y common-date return summary from " + start + " to " + end + ": "
                + data.leftCompany().getTicker() + "=" + leftReturn.toPlainString() + " percentage points; "
                + data.rightCompany().getTicker() + "=" + rightReturn.toPlainString() + " percentage points";
        sources.add(new GroundedSource("P1", GroundedSourceType.HISTORICAL_PERFORMANCE,
                data.leftCompany().getTicker() + "," + data.rightCompany().getTicker(), label,
                "StockLens", null, asOf, null, null, null, null));
    }

    private void addNews(List<GroundedSource> sources, String id, Company company, NewsArticle article) {
        String label = "Headline: " + bounded(article.getHeadline(), 1000)
                + fields("description", bounded(article.getDescription(), NEWS_DESCRIPTION_MAX));
        sources.add(new GroundedSource(id, GroundedSourceType.NEWS_ARTICLE, company.getTicker(), label,
                article.getSourceName() == null ? article.getProviderName() : article.getSourceName(),
                article.getArticleUrl(), article.getPublishedAt(), company.getId(), null, null, article));
    }

    private List<NewsArticle> newestFirst(List<NewsArticle> articles) {
        return articles.stream().sorted(Comparator.comparing(NewsArticle::getPublishedAt).reversed()
                .thenComparing(NewsArticle::getId, Comparator.reverseOrder())).limit(5).toList();
    }

    private Map<LocalDate, HistoricalPrice> byDate(List<HistoricalPrice> prices) {
        Map<LocalDate, HistoricalPrice> byDate = new TreeMap<>();
        for (HistoricalPrice price : prices) byDate.putIfAbsent(price.getTradingDate(), price);
        return byDate;
    }

    private BigDecimal value(HistoricalPrice price) { return price == null ? null : price.returnValue(); }

    private BigDecimal returnPercent(BigDecimal end, BigDecimal start) {
        if (end == null || start == null || start.signum() <= 0) return null;
        return end.divide(start, RETURN_CONTEXT).subtract(BigDecimal.ONE, RETURN_CONTEXT)
                .multiply(BigDecimal.valueOf(100), RETURN_CONTEXT).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal metricValue(FinancialMetricSnapshot snapshot, MetricCode code) {
        return switch (code) {
            case PE_TTM -> snapshot.getPeTtm(); case FORWARD_PE -> snapshot.getForwardPe();
            case PEG_RATIO -> snapshot.getPegRatio(); case PRICE_TO_SALES -> snapshot.getPriceToSales();
            case REVENUE_TTM -> snapshot.getRevenueTtm(); case GROSS_MARGIN -> snapshot.getGrossMargin();
            case NET_MARGIN -> snapshot.getNetMargin(); case RETURN_ON_EQUITY -> snapshot.getReturnOnEquity();
            case REVENUE_GROWTH -> snapshot.getRevenueGrowth(); case EARNINGS_GROWTH -> snapshot.getEarningsGrowth();
            case DEBT_TO_EQUITY -> snapshot.getDebtToEquity(); case CURRENT_RATIO -> snapshot.getCurrentRatio();
            case BETA -> snapshot.getBeta();
        };
    }

    private String bounded(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    private String fields(String... pairs) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < pairs.length; index += 2) {
            if (pairs[index + 1] != null && !pairs[index + 1].isBlank()) result.append("; ").append(pairs[index]).append('=').append(pairs[index + 1]);
        }
        return result.toString();
    }

    private String string(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private String canonicalLine(GroundedSource source) { return source.id() + '|' + source.type() + '|'
            + source.ticker() + '|' + source.label() + '|' + source.sourceName() + '|'
            + source.asOf() + '|' + source.companyId() + '|'
            + id(source.marketSnapshot()) + '|' + id(source.financialSnapshot()) + '|' + id(source.newsArticle()); }
    private Object id(Object entity) {
        if (entity instanceof MarketSnapshot value) return value.getId();
        if (entity instanceof FinancialMetricSnapshot value) return value.getId();
        if (entity instanceof NewsArticle value) return value.getId();
        return null;
    }
}
