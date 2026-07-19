package com.stocklens.market.client.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.common.exception.DataUnavailableException;
import com.stocklens.market.client.fmp.dto.FmpCompanyProfileResponse;
import com.stocklens.market.client.fmp.dto.FmpQuoteResponse;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FmpResponseMapperTest {

    private static final Instant RETRIEVED_AT = Instant.parse("2026-07-18T20:00:00Z");

    private final FmpResponseMapper mapper = new FmpResponseMapper();

    @Test
    void mapsCompanyProfileAndNormalizesOptionalValues() {
        FmpCompanyProfileResponse response = new FmpCompanyProfileResponse(
                "AAPL",
                " Apple Inc. ",
                "usd",
                "NASDAQ Global Select",
                "NASDAQ",
                "Consumer Electronics",
                "https://www.apple.com",
                " Apple description. ",
                "Technology",
                "US",
                "https://images.financialmodelingprep.com/symbol/AAPL.png");

        var result = mapper.toCompanyProfile(response, "AAPL", RETRIEVED_AT);

        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.name()).isEqualTo("Apple Inc.");
        assertThat(result.exchange()).isEqualTo("NASDAQ");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.websiteUrl()).isEqualTo("https://www.apple.com");
        assertThat(result.logoUrl()).contains("AAPL.png");
        assertThat(result.retrievedAt()).isEqualTo(RETRIEVED_AT);
    }

    @Test
    void mapsQuoteUsingBigDecimalAndUnixTimestamp() {
        FmpQuoteResponse response = quote(
                "AAPL",
                new BigDecimal("268.47000000"),
                new BigDecimal("-0.481890"),
                new BigDecimal("-1.30000000"),
                new BigDecimal("3967020108000.00"),
                1762549202L);

        var result = mapper.toMarketSnapshot(response, "AAPL", RETRIEVED_AT);

        assertThat(result.price()).isEqualByComparingTo("268.47000000");
        assertThat(result.priceChange()).isEqualByComparingTo("-1.30000000");
        assertThat(result.priceChangePercent()).isEqualByComparingTo("-0.481890");
        assertThat(result.marketCap()).isEqualByComparingTo("3967020108000.00");
        assertThat(result.quoteTimestamp()).isEqualTo(Instant.ofEpochSecond(1762549202L));
        assertThat(result.currency()).isNull();
        assertThat(result.providerName()).isEqualTo("FMP");
    }

    @Test
    void keepsNullableFieldsNullAndRejectsUnsafeUrls() {
        FmpCompanyProfileResponse response = new FmpCompanyProfileResponse(
                "AAPL",
                "Apple Inc.",
                null,
                "NASDAQ Global Select",
                " ",
                null,
                "javascript:alert(1)",
                null,
                null,
                null,
                "file:///tmp/logo.png");

        var result = mapper.toCompanyProfile(response, "AAPL", RETRIEVED_AT);

        assertThat(result.exchange()).isEqualTo("NASDAQ Global Select");
        assertThat(result.currency()).isNull();
        assertThat(result.websiteUrl()).isNull();
        assertThat(result.logoUrl()).isNull();
        assertThat(result.sector()).isNull();
    }

    @Test
    void rejectsMissingRequiredAndMismatchedFields() {
        assertThatThrownBy(() -> mapper.toCompanyProfile(
                        new FmpCompanyProfileResponse(
                                "AAPL", " ", null, null, null, null, null, null, null, null, null),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("company name");

        assertThatThrownBy(() -> mapper.toMarketSnapshot(
                        quote("MSFT", BigDecimal.ONE, null, null, null, 1L),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("unexpected symbol");

        assertThatThrownBy(() -> mapper.toMarketSnapshot(
                        quote("AAPL", null, null, null, null, 1L),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("price");

        assertThatThrownBy(() -> mapper.toMarketSnapshot(
                        quote("AAPL", BigDecimal.ONE, null, null, null, null),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("quote timestamp");
    }

    @Test
    void rejectsValuesOutsidePersistencePrecisionAndTimestampRange() {
        assertThatThrownBy(() -> mapper.toMarketSnapshot(
                        quote("AAPL", new BigDecimal("1.000000001"), null, null, null, 1L),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("price");

        assertThatThrownBy(() -> mapper.toMarketSnapshot(
                        quote("AAPL", BigDecimal.ONE, null, null, new BigDecimal("1E+29"), 1L),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("market capitalization");

        assertThatThrownBy(() -> mapper.toMarketSnapshot(
                        quote("AAPL", BigDecimal.ONE, null, null, null, Long.MAX_VALUE),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("quote timestamp");
    }

    @Test
    void rejectsTextThatCannotFitTheNormalizedSchema() {
        assertThatThrownBy(() -> mapper.toCompanyProfile(
                        new FmpCompanyProfileResponse(
                                "AAPL",
                                "A".repeat(256),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        "AAPL",
                        RETRIEVED_AT))
                .isInstanceOf(DataUnavailableException.class)
                .hasMessageContaining("company name");
    }

    private FmpQuoteResponse quote(
            String symbol,
            BigDecimal price,
            BigDecimal percentage,
            BigDecimal change,
            BigDecimal marketCap,
            Long timestamp) {
        return new FmpQuoteResponse(
                symbol, "Apple Inc.", price, percentage, change, marketCap, "NASDAQ", timestamp);
    }
}
