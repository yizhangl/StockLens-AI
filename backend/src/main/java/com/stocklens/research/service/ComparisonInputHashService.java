package com.stocklens.research.service;

import com.stocklens.research.ai.AiComparisonProperties;
import com.stocklens.research.ai.AiPromptTemplate;
import com.stocklens.research.context.BuiltComparisonContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class ComparisonInputHashService {

    private final AiComparisonProperties properties;

    public ComparisonInputHashService(AiComparisonProperties properties) { this.properties = properties; }

    public String hash(BuiltComparisonContext context) {
        String left = context.leftCompany().getTicker();
        String right = context.rightCompany().getTicker();
        String pair = left.compareTo(right) <= 0 ? left + '|' + right : right + '|' + left;
        String input = pair + "\n" + context.canonicalInput() + "\n" + AiPromptTemplate.VERSION
                + "\n" + properties.model();
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte value : bytes) hash.append(String.format("%02x", value));
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
