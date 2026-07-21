package com.stocklens.research.domain;

import com.stocklens.company.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "comparison_brief")
public class ComparisonBrief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "left_company_id", nullable = false)
    private Company leftCompany;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "right_company_id", nullable = false)
    private Company rightCompany;

    @Column(name = "overall_summary", nullable = false, columnDefinition = "text")
    private String overallSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "advantages_json", nullable = false, columnDefinition = "jsonb")
    private String advantagesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_risks_json", nullable = false, columnDefinition = "jsonb")
    private String keyRisksJson;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 128)
    private String promptVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "data_cutoff_at", nullable = false)
    private Instant dataCutoffAt;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    protected ComparisonBrief() {}

    public ComparisonBrief(
            Company leftCompany, Company rightCompany, String overallSummary,
            String advantagesJson, String keyRisksJson, String modelName, String promptVersion,
            Instant generatedAt, Instant dataCutoffAt, String inputHash) {
        this.leftCompany = leftCompany;
        this.rightCompany = rightCompany;
        this.overallSummary = overallSummary;
        this.advantagesJson = advantagesJson;
        this.keyRisksJson = keyRisksJson;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.generatedAt = generatedAt;
        this.dataCutoffAt = dataCutoffAt;
        this.inputHash = inputHash;
    }

    public Long getId() { return id; }
    public Company getLeftCompany() { return leftCompany; }
    public Company getRightCompany() { return rightCompany; }
    public String getOverallSummary() { return overallSummary; }
    public String getAdvantagesJson() { return advantagesJson; }
    public String getKeyRisksJson() { return keyRisksJson; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getDataCutoffAt() { return dataCutoffAt; }
    public String getInputHash() { return inputHash; }
}
