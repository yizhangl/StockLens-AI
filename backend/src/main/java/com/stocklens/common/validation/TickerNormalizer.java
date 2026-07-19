package com.stocklens.common.validation;

import com.stocklens.common.exception.InvalidTickerException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TickerNormalizer {

    public static final String SUPPORTED_FORMAT = "^[A-Z][A-Z0-9.-]{0,9}$";

    private static final Pattern SUPPORTED_TICKER = Pattern.compile(SUPPORTED_FORMAT);

    public String normalize(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new InvalidTickerException();
        }

        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TICKER.matcher(normalized).matches()) {
            throw new InvalidTickerException();
        }
        return normalized;
    }
}
