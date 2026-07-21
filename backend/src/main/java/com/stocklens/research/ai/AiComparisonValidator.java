package com.stocklens.research.ai;

import com.stocklens.research.context.BuiltComparisonContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiComparisonValidator {

    public ValidationResult validate(AiComparisonResult result, BuiltComparisonContext context) {
        List<String> failures = new ArrayList<>();
        if (result == null) return ValidationResult.invalid(List.of("empty response"));
        result = normalize(result);
        text(result.overallSummary(), 180, "overall summary", failures);
        if (result.advantages() == null) failures.add("missing advantages");
        else {
            validateAdvantage(result.advantages().valuation(), context, failures, "valuation");
            validateAdvantage(result.advantages().profitability(), context, failures, "profitability");
            validateAdvantage(result.advantages().growth(), context, failures, "growth");
            validateAdvantage(result.advantages().financialHealth(), context, failures, "financialHealth");
        }
        List<AiRiskResult> risks = result.keyRisks();
        if (risks == null) failures.add("missing risks list");
        else if (risks.size() > 6) failures.add("too many risks");
        else for (AiRiskResult risk : risks) validateRisk(risk, context, failures);
        if (result.sourceIds() != null
                && result.sourceIds().stream().anyMatch(id -> !context.sourcesById().containsKey(id))) {
            failures.add("unknown source ID");
        }
        if (containsProhibited(result)) failures.add("prohibited investment advice");
        if (!failures.isEmpty()) return ValidationResult.invalid(failures);
        List<String> union = stableUnion(result);
        if (union.size() > 15) return ValidationResult.invalid(List.of("too many source IDs"));
        if (union.stream().anyMatch(id -> !context.sourcesById().containsKey(id))) {
            return ValidationResult.invalid(List.of("unknown source ID"));
        }
        return ValidationResult.valid(new AiComparisonResult(result.overallSummary().trim(), result.advantages(),
                List.copyOf(risks), union));
    }

    private void validateAdvantage(AiAdvantageResult value, BuiltComparisonContext context,
            List<String> failures, String category) {
        if (value == null) { failures.add("missing " + category); return; }
        Set<String> allowed = Set.of(context.leftCompany().getTicker(), context.rightCompany().getTicker(),
                "NEUTRAL", "INSUFFICIENT_DATA");
        if (value.winner() == null || !allowed.contains(value.winner())) failures.add("invalid winner");
        text(value.explanation(), 80, category + " explanation", failures);
        sources(value.sourceIds(), context, failures, category);
    }

    private void validateRisk(AiRiskResult risk, BuiltComparisonContext context, List<String> failures) {
        if (risk == null) { failures.add("null risk"); return; }
        if (!context.leftCompany().getTicker().equals(risk.ticker())
                && !context.rightCompany().getTicker().equals(risk.ticker())) failures.add("invalid risk ticker");
        text(risk.text(), 80, "risk text", failures);
        sources(risk.sourceIds(), context, failures, "risk");
    }

    private void sources(List<String> ids, BuiltComparisonContext context, List<String> failures, String label) {
        if (ids == null || ids.isEmpty()) {
            failures.add(label + " missing sources");
            return;
        }
        if (ids.size() > 6) failures.add(label + " invalid sources");
        if (ids.stream().anyMatch(id -> !context.sourcesById().containsKey(id))) failures.add("unknown source ID");
    }

    private void text(String value, int maxWords, String label, List<String> failures) {
        if (value == null || value.isBlank()) failures.add("blank " + label);
        else if (value.trim().split("\\s+").length > maxWords) failures.add("long " + label);
    }

    private boolean containsProhibited(AiComparisonResult result) {
        String text = (result.overallSummary() + " " + result.keyRisks() + " " + result.advantages()).toLowerCase(Locale.ROOT);
        return text.matches(".*\\b(buy|sell)\\s+(this|the|shares|stock).*")
                || text.matches(".*\\bguaranteed?\\s+(return|profit|gain).*")
                || text.matches(".*\\b(price target|will reach|will trade at)\\b.*");
    }

    private List<String> stableUnion(AiComparisonResult result) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        add(ids, result.advantages().valuation()); add(ids, result.advantages().profitability());
        add(ids, result.advantages().growth()); add(ids, result.advantages().financialHealth());
        result.keyRisks().forEach(risk -> ids.addAll(risk.sourceIds()));
        return List.copyOf(ids);
    }
    private void add(Set<String> ids, AiAdvantageResult value) { ids.addAll(value.sourceIds()); }

    private AiComparisonResult normalize(AiComparisonResult result) {
        AiAdvantages advantages = result.advantages() == null ? null : new AiAdvantages(
                normalize(result.advantages().valuation()),
                normalize(result.advantages().profitability()),
                normalize(result.advantages().growth()),
                normalize(result.advantages().financialHealth()));
        List<AiRiskResult> risks = result.keyRisks() == null ? null : result.keyRisks().stream()
                .map(this::normalize)
                .toList();
        return new AiComparisonResult(
                result.overallSummary(), advantages, risks, normalizeSourceIds(result.sourceIds()));
    }

    private AiAdvantageResult normalize(AiAdvantageResult value) {
        return value == null ? null : new AiAdvantageResult(
                value.winner(), value.explanation(), normalizeSourceIds(value.sourceIds()));
    }

    private AiRiskResult normalize(AiRiskResult value) {
        return value == null ? null : new AiRiskResult(
                value.ticker(), value.text(), normalizeSourceIds(value.sourceIds()));
    }

    private List<String> normalizeSourceIds(List<String> ids) {
        if (ids == null) return null;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (!trimmed.isBlank()) normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    public record ValidationResult(AiComparisonResult result, List<String> failures) {
        static ValidationResult valid(AiComparisonResult result) { return new ValidationResult(result, List.of()); }
        static ValidationResult invalid(List<String> failures) { return new ValidationResult(null, List.copyOf(failures)); }
        public boolean isValid() { return result != null; }
        public String sanitizedFailures() { return String.join(", ", failures.stream().distinct().toList()); }
    }
}
