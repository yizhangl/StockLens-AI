package com.stocklens.comparison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.common.exception.DuplicateTickersException;
import com.stocklens.common.exception.FinancialProviderException;
import com.stocklens.common.exception.InvalidComparisonModeException;
import com.stocklens.common.exception.InvalidPeriodException;
import com.stocklens.common.exception.NewsProviderException;
import com.stocklens.common.exception.StockNotFoundException;
import com.stocklens.common.validation.TickerNormalizer;
import com.stocklens.comparison.model.ComparisonMode;
import com.stocklens.comparison.model.ComparisonWarningSection;
import com.stocklens.comparison.model.ComparisonWarningSide;
import com.stocklens.company.domain.Company;
import com.stocklens.company.service.StockQueryService;
import com.stocklens.company.service.StockQueryService.CompanyResolution;
import com.stocklens.financial.dto.FinancialMetricsResponse;
import com.stocklens.financial.dto.HistoricalPricePointResponse;
import com.stocklens.financial.dto.HistoricalPriceResponse;
import com.stocklens.financial.dto.MetricValueResponse;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricDefinition;
import com.stocklens.financial.metric.MetricDefinitionRegistry;
import com.stocklens.financial.service.FinancialMetricsQueryService;
import com.stocklens.financial.service.HistoricalPriceQueryService;
import com.stocklens.market.domain.MarketSnapshot;
import com.stocklens.news.dto.NewsArticleResponse;
import com.stocklens.news.dto.NewsResponse;
import com.stocklens.news.dto.NewsWarningResponse;
import com.stocklens.news.service.NewsQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    private static final Instant BASE = Instant.parse("2026-07-19T20:00:00Z");

    @Mock private StockQueryService stockQueryService;
    @Mock private FinancialMetricsQueryService metricsQueryService;
    @Mock private HistoricalPriceQueryService historyQueryService;
    @Mock private NewsQueryService newsQueryService;

    private MetricDefinitionRegistry registry;
    private ComparisonService service;
    private Company apple;
    private Company microsoft;

    @BeforeEach
    void setUp() {
        registry = new MetricDefinitionRegistry();
        service = new ComparisonService(
                new TickerNormalizer(),
                stockQueryService,
                metricsQueryService,
                historyQueryService,
                newsQueryService,
                new HistoricalSeriesAligner(),
                new MetricComparisonService(registry));
        apple = company("AAPL", "Apple Inc.", BASE);
        microsoft = company("MSFT", "Microsoft Corporation", BASE.plusSeconds(1));
    }

    @Test
    void assemblesCompleteDashboardAndPreservesDisplayOrder() {
        stubComplete();

        var response = service.compare(" msft ", "aapl", "1y", "return");

        assertThat(response.comparisonId()).isEqualTo("AAPL:MSFT:1Y:RETURN");
        assertThat(response.left().ticker()).isEqualTo("MSFT");
        assertThat(response.right().ticker()).isEqualTo("AAPL");
        assertThat(response.left().companyName()).isEqualTo("Microsoft Corporation");
        assertThat(response.left().price()).isEqualByComparingTo("400");
        assertThat(response.left().peTtm()).isEqualByComparingTo("30");
        assertThat(response.right().revenueTtm()).isEqualByComparingTo("1000");
        assertThat(response.pricePerformance().mode()).isEqualTo(ComparisonMode.RETURN);
        assertThat(response.pricePerformance().series().getFirst().leftValue())
                .isEqualByComparingTo("0.0000");
        assertThat(response.pricePerformance().leftReturnPercent()).isEqualByComparingTo("10.0000");
        assertThat(response.metricGroups()).hasSize(4);
        assertThat(response.news().left()).hasSize(3);
        assertThat(response.news().left()).extracting(article -> article.headline())
                .containsExactly("MSFT newest", "MSFT middle", "MSFT older");
        assertThat(response.aiBrief()).isNull();
        assertThat(response.provenance().financialProvider()).isEqualTo("FMP");
        assertThat(response.provenance().newsProvider()).isEqualTo("YAHOO_FINANCE");
        assertThat(response.provenance().lastUpdatedAt()).isEqualTo(BASE.plusSeconds(60));
        assertThat(response.provenance().cached()).isFalse();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void convertsOnlyKnownOptionalFailuresToTypedWarnings() {
        stubResolutions();
        when(stockQueryService.refreshMarketSnapshot(new CompanyResolution(apple, "USD")))
                .thenThrow(new FinancialProviderException("secret"));
        when(stockQueryService.refreshMarketSnapshot(new CompanyResolution(microsoft, "USD")))
                .thenReturn(market(microsoft, "400", "USD", BASE.plusSeconds(10)));
        when(metricsQueryService.getMetrics("AAPL"))
                .thenThrow(new DataUnavailableException("secret"));
        when(metricsQueryService.getMetrics("MSFT")).thenReturn(metrics("MSFT", "30", "2000"));
        when(historyQueryService.getHistory("AAPL", "1Y"))
                .thenThrow(new FinancialProviderException("secret"));
        when(historyQueryService.getHistory("MSFT", "1Y")).thenReturn(history("MSFT", "USD"));
        when(newsQueryService.getRecentNews("AAPL", 3)).thenReturn(news("AAPL"));
        when(newsQueryService.getRecentNews("MSFT", 3))
                .thenThrow(new NewsProviderException("secret"));

        var response = service.compare("AAPL", "MSFT", "1Y", "PRICE");

        assertThat(response.left().price()).isNull();
        assertThat(response.left().peTtm()).isNull();
        assertThat(response.news().right()).isEmpty();
        assertThat(response.pricePerformance().series()).isEmpty();
        assertThat(response.warnings()).extracting(
                        warning -> warning.section() + ":" + warning.side() + ":" + warning.code())
                .containsExactly(
                        "MARKET:LEFT:FINANCIAL_PROVIDER_ERROR",
                        "METRICS:LEFT:DATA_UNAVAILABLE",
                        "HISTORY:LEFT:FINANCIAL_PROVIDER_ERROR",
                        "NEWS:RIGHT:NEWS_PROVIDER_ERROR");
        assertThat(response.warnings()).allSatisfy(warning ->
                assertThat(warning.message()).doesNotContain("secret"));
    }

    @Test
    void emitsCurrencyAlignmentAndSkippedNewsWarningsWithoutDuplicates() {
        stubComplete();
        when(historyQueryService.getHistory("AAPL", "1Y")).thenReturn(history("AAPL", "USD"));
        when(historyQueryService.getHistory("MSFT", "1Y")).thenReturn(history("MSFT", "CAD"));
        NewsResponse warned = new NewsResponse(
                "AAPL", 3, "YAHOO_FINANCE", BASE.plusSeconds(60), List.of(),
                List.of(
                        new NewsWarningResponse("INVALID_PROVIDER_RECORDS_SKIPPED", "safe", 1),
                        new NewsWarningResponse("INVALID_PROVIDER_RECORDS_SKIPPED", "safe", 2)));
        when(newsQueryService.getRecentNews("AAPL", 3)).thenReturn(warned);

        var response = service.compare("AAPL", "MSFT", "1Y", "PRICE");

        assertThat(response.warnings()).filteredOn(
                warning -> warning.section() == ComparisonWarningSection.NEWS
                        && warning.side() == ComparisonWarningSide.LEFT).hasSize(1);
        assertThat(response.warnings()).anySatisfy(warning -> {
            assertThat(warning.code()).isEqualTo("CURRENCY_MISMATCH");
            assertThat(warning.side()).isEqualTo(ComparisonWarningSide.BOTH);
        });
    }

    @Test
    void preservesUnavailableIndividualSummaryMetricAsNull() {
        stubComplete();
        when(metricsQueryService.getMetrics("AAPL"))
                .thenReturn(metrics("AAPL", "20", null));

        var response = service.compare("AAPL", "MSFT", "1Y", "RETURN");

        assertThat(response.left().peTtm()).isEqualByComparingTo("20");
        assertThat(response.left().revenueTtm()).isNull();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void criticalIdentityFailureAndUnexpectedOptionalFailurePropagate() {
        when(stockQueryService.resolveCompany("UNKNOWN"))
                .thenThrow(new StockNotFoundException("UNKNOWN"));
        assertThatThrownBy(() -> service.compare("UNKNOWN", "MSFT", "1Y", "RETURN"))
                .isInstanceOf(StockNotFoundException.class);

        stubResolutions();
        when(stockQueryService.refreshMarketSnapshot(new CompanyResolution(apple, "USD")))
                .thenThrow(new IllegalStateException("bug"));
        assertThatThrownBy(() -> service.compare("AAPL", "MSFT", "1Y", "RETURN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
    }

    @Test
    void rejectsDuplicateInvalidPeriodAndInvalidModeBeforeIdentityWork() {
        assertThatThrownBy(() -> service.compare("aapl", " AAPL ", "1Y", "RETURN"))
                .isInstanceOf(DuplicateTickersException.class);
        assertThatThrownBy(() -> service.compare("AAPL", "MSFT", "2Y", "RETURN"))
                .isInstanceOf(InvalidPeriodException.class);
        assertThatThrownBy(() -> service.compare("AAPL", "MSFT", "1Y", "GAIN"))
                .isInstanceOf(InvalidComparisonModeException.class);
        verifyNoInteractions(stockQueryService);
    }

    private void stubComplete() {
        stubResolutions();
        when(stockQueryService.refreshMarketSnapshot(new CompanyResolution(apple, "USD")))
                .thenReturn(market(apple, "200", "USD", BASE.plusSeconds(10)));
        when(stockQueryService.refreshMarketSnapshot(new CompanyResolution(microsoft, "USD")))
                .thenReturn(market(microsoft, "400", "USD", BASE.plusSeconds(11)));
        when(metricsQueryService.getMetrics("AAPL")).thenReturn(metrics("AAPL", "20", "1000"));
        when(metricsQueryService.getMetrics("MSFT")).thenReturn(metrics("MSFT", "30", "2000"));
        when(historyQueryService.getHistory("AAPL", "1Y")).thenReturn(history("AAPL", "USD"));
        when(historyQueryService.getHistory("MSFT", "1Y")).thenReturn(history("MSFT", "USD"));
        when(newsQueryService.getRecentNews("AAPL", 3)).thenReturn(news("AAPL"));
        when(newsQueryService.getRecentNews("MSFT", 3)).thenReturn(news("MSFT"));
    }

    private void stubResolutions() {
        when(stockQueryService.resolveCompany("AAPL")).thenReturn(new CompanyResolution(apple, "USD"));
        when(stockQueryService.resolveCompany("MSFT")).thenReturn(new CompanyResolution(microsoft, "USD"));
    }

    private Company company(String ticker, String name, Instant updatedAt) {
        return new Company(
                ticker, name, "NASDAQ", "Technology", "Software", "US",
                "https://example.com", "Description", "https://example.com/logo.png",
                ticker, updatedAt, updatedAt);
    }

    private MarketSnapshot market(
            Company company, String price, String currency, Instant retrievedAt) {
        return new MarketSnapshot(
                company, new BigDecimal(price), BigDecimal.ONE, new BigDecimal("0.5"),
                new BigDecimal("1000000"), currency, retrievedAt.minusSeconds(1),
                retrievedAt, "FMP", null);
    }

    private FinancialMetricsResponse metrics(String ticker, String pe, String revenue) {
        return new FinancialMetricsResponse(
                ticker, "USD", null, BASE.plusSeconds(20), "FMP",
                List.of(
                        metric(MetricCode.PE_TTM, pe),
                        metric(MetricCode.REVENUE_TTM, revenue),
                        metric(MetricCode.GROSS_MARGIN, "0.4")),
                List.of());
    }

    private MetricValueResponse metric(MetricCode code, String value) {
        MetricDefinition definition = registry.get(code);
        return new MetricValueResponse(
                code, definition.displayName(), definition.category(), definition.unit(),
                definition.comparisonStrategy(),
                value == null ? null : new BigDecimal(value),
                definition.description());
    }

    private HistoricalPriceResponse history(String ticker, String currency) {
        return new HistoricalPriceResponse(
                ticker, "1Y", LocalDate.parse("2025-07-19"), LocalDate.parse("2026-07-19"),
                currency, null, "FMP", BASE.plusSeconds(30),
                List.of(
                        point("2026-07-18", ticker.equals("AAPL") ? "100" : "200"),
                        point("2026-07-19", ticker.equals("AAPL") ? "110" : "220")));
    }

    private HistoricalPricePointResponse point(String date, String close) {
        return new HistoricalPricePointResponse(
                LocalDate.parse(date), null, null, null, new BigDecimal(close), null, null);
    }

    private NewsResponse news(String ticker) {
        return new NewsResponse(
                ticker, 3, "YAHOO_FINANCE", BASE.plusSeconds(60),
                List.of(
                        article(ticker, "oldest", 1),
                        article(ticker, "newest", 4),
                        article(ticker, "older", 2),
                        article(ticker, "middle", 3)),
                List.of());
    }

    private NewsArticleResponse article(String ticker, String label, long seconds) {
        return new NewsArticleResponse(
                seconds, ticker + " " + label, "Wire", "https://example.com/" + ticker + label,
                BASE.plusSeconds(seconds), null, List.of(ticker));
    }
}
