package com.stocklens.market.client.fmp;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.market.client.fmp.dto.FmpCompanyProfileResponse;
import com.stocklens.market.client.fmp.dto.FmpFinancialGrowthResponse;
import com.stocklens.market.client.fmp.dto.FmpHistoricalPriceResponse;
import com.stocklens.market.client.fmp.dto.FmpKeyMetricsTtmResponse;
import com.stocklens.market.client.fmp.dto.FmpQuoteResponse;
import com.stocklens.market.client.fmp.dto.FmpRatiosTtmResponse;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.FinancialMetricsData;
import com.stocklens.market.client.model.HistoricalPriceData;
import com.stocklens.market.client.model.MarketSnapshotData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FmpResponseMapper {

    static final String PROVIDER_NAME = "FMP";

    public CompanyProfileData toCompanyProfile(
            FmpCompanyProfileResponse response, String requestedTicker, Instant retrievedAt) {
        String symbol = requiredSymbol(response.symbol(), requestedTicker);
        String name = requiredText(response.companyName(), "company name", 255);
        return new CompanyProfileData(
                requestedTicker,
                name,
                firstNonblank(response.exchange(), response.exchangeFullName(), "exchange", 64),
                optionalText(response.sector(), "sector", 128),
                optionalText(response.industry(), "industry", 128),
                optionalText(response.country(), "country", 128),
                safeHttpUrl(response.website()),
                optionalText(response.description()),
                safeHttpUrl(response.image()),
                symbol,
                currency(response.currency()),
                retrievedAt);
    }

    public MarketSnapshotData toMarketSnapshot(
            FmpQuoteResponse response, String requestedTicker, Instant retrievedAt) {
        requiredSymbol(response.symbol(), requestedTicker);
        BigDecimal price = decimal(response.price(), "price", 30, 8, false);
        if (price == null || price.signum() <= 0) {
            throw unavailable("price");
        }
        BigDecimal priceChange = decimal(response.change(), "price change", 30, 8, true);
        BigDecimal priceChangePercent =
                decimal(response.changePercentage(), "price change percent", 19, 6, true);
        BigDecimal marketCap = decimal(response.marketCap(), "market capitalization", 30, 2, true);
        if (marketCap != null && marketCap.signum() < 0) {
            throw unavailable("market capitalization");
        }
        if (response.timestamp() == null || response.timestamp() <= 0) {
            throw unavailable("quote timestamp");
        }
        Instant quoteTimestamp;
        try {
            quoteTimestamp = Instant.ofEpochSecond(response.timestamp());
        } catch (DateTimeException exception) {
            throw unavailable("quote timestamp");
        }

        return new MarketSnapshotData(
                requestedTicker,
                price,
                priceChange,
                priceChangePercent,
                marketCap,
                null,
                quoteTimestamp,
                retrievedAt,
                PROVIDER_NAME);
    }

    public FinancialMetricsData toFinancialMetrics(
            FmpRatiosTtmResponse ratios,
            FmpKeyMetricsTtmResponse keyMetrics,
            FmpFinancialGrowthResponse growth,
            String requestedTicker,
            Instant retrievedAt) {
        requiredSymbol(ratios.symbol(), requestedTicker);
        if (keyMetrics != null) {
            requiredSymbol(keyMetrics.symbol(), requestedTicker);
        }
        if (growth != null) {
            requiredSymbol(growth.symbol(), requestedTicker);
            if (growth.period() != null && !"FY".equalsIgnoreCase(growth.period())) {
                throw unavailable("annual growth period");
            }
        }
        if (ratios.priceToEarningsRatioTTM() == null
                && ratios.priceToEarningsGrowthRatioTTM() == null
                && ratios.priceToSalesRatioTTM() == null
                && ratios.grossProfitMarginTTM() == null
                && ratios.netProfitMarginTTM() == null
                && ratios.debtToEquityRatioTTM() == null
                && ratios.currentRatioTTM() == null) {
            throw unavailable("financial ratios");
        }
        return new FinancialMetricsData(
                requestedTicker,
                metricDecimal(ratios.priceToEarningsRatioTTM(), "P/E ratio"),
                null,
                metricDecimal(ratios.priceToEarningsGrowthRatioTTM(), "PEG ratio"),
                metricDecimal(ratios.priceToSalesRatioTTM(), "price-to-sales ratio"),
                null,
                metricDecimal(ratios.grossProfitMarginTTM(), "gross margin"),
                metricDecimal(ratios.netProfitMarginTTM(), "net margin"),
                keyMetrics == null ? null : metricDecimal(
                        keyMetrics.returnOnEquityTTM(), "return on equity"),
                growth == null ? null : metricDecimal(growth.revenueGrowth(), "revenue growth"),
                growth == null ? null : metricDecimal(growth.netIncomeGrowth(), "earnings growth"),
                metricDecimal(ratios.debtToEquityRatioTTM(), "debt-to-equity ratio"),
                metricDecimal(ratios.currentRatioTTM(), "current ratio"),
                null,
                growth == null ? null : currency(growth.reportedCurrency()),
                growth == null ? null : growth.date(),
                retrievedAt,
                PROVIDER_NAME);
    }

    public List<HistoricalPriceData> toHistoricalPrices(
            List<FmpHistoricalPriceResponse> responses,
            String requestedTicker,
            LocalDate from,
            LocalDate to,
            Instant retrievedAt) {
        List<HistoricalPriceData> result = new ArrayList<>();
        Set<LocalDate> dates = new HashSet<>();
        for (FmpHistoricalPriceResponse response : responses) {
            requiredSymbol(response.symbol(), requestedTicker);
            LocalDate date = response.date();
            if (date == null || (from != null && date.isBefore(from)) || date.isAfter(to) || !dates.add(date)) {
                throw unavailable("historical price date");
            }
            BigDecimal close = positivePrice(response.close(), "closing price");
            Long volume = response.volume();
            if (volume != null && volume < 0) {
                throw unavailable("historical volume");
            }
            result.add(new HistoricalPriceData(
                    requestedTicker,
                    date,
                    optionalPositivePrice(response.open(), "opening price"),
                    optionalPositivePrice(response.high(), "high price"),
                    optionalPositivePrice(response.low(), "low price"),
                    close,
                    null,
                    volume,
                    null,
                    PROVIDER_NAME,
                    retrievedAt));
        }
        result.sort(Comparator.comparing(HistoricalPriceData::tradingDate));
        return List.copyOf(result);
    }

    private BigDecimal positivePrice(BigDecimal value, String field) {
        BigDecimal price = decimal(value, field, 30, 8, false);
        if (price.signum() <= 0) {
            throw unavailable(field);
        }
        return price;
    }

    private BigDecimal optionalPositivePrice(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        return positivePrice(value, field);
    }

    private BigDecimal metricDecimal(BigDecimal value, String field) {
        BigDecimal rounded = value == null ? null : value.setScale(12, RoundingMode.HALF_UP);
        return decimal(rounded, field, 30, 12, true);
    }

    private String requiredSymbol(String value, String requestedTicker) {
        String symbol = requiredText(value, "symbol", 64).toUpperCase(Locale.ROOT);
        if (!symbol.equals(requestedTicker)) {
            throw new DataUnavailableException("Financial provider returned an unexpected symbol.");
        }
        return symbol;
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = optionalText(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw unavailable(field);
        }
        return normalized;
    }

    private String firstNonblank(
            String first, String second, String field, int maxLength) {
        String normalized = optionalText(first);
        normalized = normalized == null ? optionalText(second) : normalized;
        if (normalized != null && normalized.length() > maxLength) {
            throw unavailable(field);
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        String normalized = optionalText(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw unavailable(field);
        }
        return normalized;
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String currency(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.matches("^[A-Z]{3}$") ? normalized : null;
    }

    private String safeHttpUrl(String value) {
        String normalized = optionalText(value);
        if (normalized == null || normalized.length() > 2048) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private BigDecimal decimal(
            BigDecimal value, String field, int precision, int scale, boolean nullable) {
        if (value == null) {
            if (nullable) {
                return null;
            }
            throw unavailable(field);
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int fractionalDigits = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (fractionalDigits > scale || integerDigits > precision - scale) {
            throw unavailable(field);
        }
        return value;
    }

    private DataUnavailableException unavailable(String field) {
        return new DataUnavailableException("Financial provider response is missing required " + field + ".");
    }
}
