package com.stocklens.financial.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.common.exception.InvalidPeriodException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PricePeriodTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void parsesSupportedPeriodsAndCalculatesInclusiveBounds() {
        assertThat(PricePeriod.parse(" 1m ").range(CLOCK).from()).isEqualTo(LocalDate.parse("2026-06-18"));
        assertThat(PricePeriod.parse("6M").range(CLOCK).from()).isEqualTo(LocalDate.parse("2026-01-18"));
        assertThat(PricePeriod.parse("1Y").range(CLOCK).from()).isEqualTo(LocalDate.parse("2025-07-18"));
        assertThat(PricePeriod.parse("5Y").range(CLOCK).from()).isEqualTo(LocalDate.parse("2021-07-18"));
        assertThat(PricePeriod.parse("MAX").range(CLOCK).from()).isNull();
        assertThat(PricePeriod.ONE_YEAR.range(CLOCK).to()).isEqualTo(LocalDate.parse("2026-07-18"));
    }

    @Test
    void rejectsMissingAndUnsupportedPeriods() {
        assertThatThrownBy(() -> PricePeriod.parse("YTD")).isInstanceOf(InvalidPeriodException.class);
        assertThatThrownBy(() -> PricePeriod.parse(" ")).isInstanceOf(InvalidPeriodException.class);
    }
}
