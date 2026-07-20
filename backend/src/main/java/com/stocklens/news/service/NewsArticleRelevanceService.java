package com.stocklens.news.service;

import com.stocklens.company.domain.Company;
import com.stocklens.news.client.model.NewsArticleData;
import com.stocklens.news.domain.NewsArticle;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NewsArticleRelevanceService {

    static final int MINIMUM_RELEVANCE_SCORE = 2;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "co",
            "company",
            "corp",
            "corporation",
            "inc",
            "incorporated",
            "limited",
            "llc",
            "ltd",
            "plc");

    public List<NewsArticleData> filterRelevant(
            Company company, List<NewsArticleData> articles) {
        return articles.stream()
                .filter(article -> assess(company, article).isRelevant())
                .toList();
    }

    public RelevanceAssessment assess(Company company, NewsArticleData article) {
        if (article == null) {
            return RelevanceAssessment.noEvidence();
        }
        return assess(
                company,
                article.headline(),
                article.description(),
                article.relatedSymbols());
    }

    public RelevanceAssessment assess(Company company, NewsArticle article) {
        Set<String> relatedSymbols = article.getCompanies().stream()
                .map(Company::getTicker)
                .collect(Collectors.toUnmodifiableSet());
        return assess(
                company,
                article.getHeadline(),
                article.getDescription(),
                relatedSymbols);
    }

    private RelevanceAssessment assess(
            Company company,
            String headline,
            String description,
            Set<String> relatedSymbols) {
        Set<String> aliases = aliases(company);
        boolean headlineMatch = containsAlias(headline, aliases);
        boolean descriptionMatch = containsAlias(description, aliases);
        boolean relatedTickerMatch = containsTicker(relatedSymbols, company.getTicker());
        int score = (headlineMatch ? 2 : 0)
                + (descriptionMatch ? 1 : 0)
                + (relatedTickerMatch ? 1 : 0);
        return new RelevanceAssessment(
                score, headlineMatch, descriptionMatch, relatedTickerMatch);
    }

    private Set<String> aliases(Company company) {
        Set<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, company.getTicker());
        String normalizedName = normalizeTokens(company.getName());
        if (!normalizedName.isEmpty()) {
            aliases.add(normalizedName);
            String shortName = shortCompanyName(normalizedName);
            if (shortName.length() >= 3) {
                aliases.add(shortName);
            }
        }
        return Set.copyOf(aliases);
    }

    private String shortCompanyName(String normalizedName) {
        List<String> tokens = new ArrayList<>(List.of(normalizedName.split(" ")));
        boolean removedSuffix = false;
        while (!tokens.isEmpty() && LEGAL_SUFFIXES.contains(tokens.getLast())) {
            tokens.removeLast();
            removedSuffix = true;
        }
        return removedSuffix ? String.join(" ", tokens) : "";
    }

    private void addAlias(Set<String> aliases, String value) {
        String normalized = normalizeTokens(value);
        if (!normalized.isEmpty()) {
            aliases.add(normalized);
        }
    }

    private boolean containsAlias(String text, Set<String> aliases) {
        String normalizedText = normalizeTokens(text);
        if (normalizedText.isEmpty()) {
            return false;
        }
        String paddedText = " " + normalizedText + " ";
        return aliases.stream().anyMatch(alias -> paddedText.contains(" " + alias + " "));
    }

    private boolean containsTicker(Set<String> relatedSymbols, String ticker) {
        if (relatedSymbols == null) {
            return false;
        }
        return relatedSymbols.stream()
                .filter(symbol -> symbol != null)
                .map(String::trim)
                .anyMatch(symbol -> symbol.equalsIgnoreCase(ticker));
    }

    private String normalizeTokens(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Matcher matcher = TOKEN_PATTERN.matcher(
                Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return String.join(" ", tokens);
    }

    public record RelevanceAssessment(
            int score,
            boolean headlineAliasMatch,
            boolean descriptionAliasMatch,
            boolean relatedTickerMatch) {

        public boolean isRelevant() {
            return score >= MINIMUM_RELEVANCE_SCORE;
        }

        private static RelevanceAssessment noEvidence() {
            return new RelevanceAssessment(0, false, false, false);
        }
    }
}
