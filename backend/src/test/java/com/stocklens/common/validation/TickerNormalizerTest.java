package com.stocklens.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stocklens.common.exception.InvalidTickerException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TickerNormalizerTest {

    private final TickerNormalizer normalizer = new TickerNormalizer();

    @Test
    void trimsAndNormalizesWithLocaleRoot() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(normalizer.normalize(" aapl ")).isEqualTo("AAPL");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void acceptsSupportedTickerForms() {
        assertThat(normalizer.normalize("brk.b")).isEqualTo("BRK.B");
        assertThat(normalizer.normalize("rds-a")).isEqualTo("RDS-A");
        assertThat(normalizer.normalize("abc123")).isEqualTo("ABC123");
        assertThat(normalizer.normalize("A123456789")).isEqualTo("A123456789");
    }

    @Test
    void rejectsUnsupportedTickerForms() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
        assertInvalid("1AAPL");
        assertInvalid("AAPL US");
        assertInvalid("AAPL/USD");
        assertInvalid("AAPL_");
        assertInvalid("A1234567890");
        assertInvalid("ÅAPL");
    }

    private void assertInvalid(String ticker) {
        assertThatThrownBy(() -> normalizer.normalize(ticker))
                .isInstanceOf(InvalidTickerException.class)
                .hasMessageContaining(TickerNormalizer.SUPPORTED_FORMAT);
    }
}
