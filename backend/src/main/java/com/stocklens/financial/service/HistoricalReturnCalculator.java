package com.stocklens.financial.service;

import com.stocklens.financial.domain.HistoricalPrice;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HistoricalReturnCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext CALCULATION_CONTEXT = new MathContext(24, RoundingMode.HALF_UP);

    public BigDecimal calculate(List<HistoricalPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        BigDecimal first = prices.getFirst().returnValue();
        BigDecimal last = prices.getLast().returnValue();
        if (first == null || last == null || first.signum() == 0) {
            return null;
        }
        return last.divide(first, CALCULATION_CONTEXT)
                .subtract(BigDecimal.ONE, CALCULATION_CONTEXT)
                .multiply(ONE_HUNDRED, CALCULATION_CONTEXT);
    }

    public BigDecimal roundForApi(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }
}
