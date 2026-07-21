package com.stocklens.news.domain;
import com.stocklens.company.domain.Company;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "news_retrieval")
public class NewsRetrieval {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="company_id", nullable=false) private Company company;
 @Column(name="retrieved_at", nullable=false) private Instant retrievedAt;
 @Column(name="result_count", nullable=false) private int resultCount;
 @Column(name="provider_name", nullable=false) private String providerName;
 protected NewsRetrieval() {}
 public NewsRetrieval(Company company, Instant retrievedAt, int resultCount, String providerName) { this.company=company; this.retrievedAt=retrievedAt; this.resultCount=resultCount; this.providerName=providerName; }
 public Instant getRetrievedAt(){return retrievedAt;} public int getResultCount(){return resultCount;} public String getProviderName(){return providerName;}
}
