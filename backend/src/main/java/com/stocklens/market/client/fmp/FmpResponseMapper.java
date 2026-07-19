package com.stocklens.market.client.fmp;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.market.client.fmp.dto.FmpCompanyProfileResponse;
import com.stocklens.market.client.fmp.dto.FmpQuoteResponse;
import com.stocklens.market.client.model.CompanyProfileData;
import com.stocklens.market.client.model.MarketSnapshotData;
import java.math.BigDecimal;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Locale;
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
