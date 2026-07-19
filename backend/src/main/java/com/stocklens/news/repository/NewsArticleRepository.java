package com.stocklens.news.repository;

import com.stocklens.news.domain.NewsArticle;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Optional<NewsArticle> findByUrlHash(String urlHash);

    Optional<NewsArticle> findByProviderNameAndExternalId(String providerName, String externalId);

    @Query("""
            select article.id
            from NewsArticle article
            join article.companies company
            where company.id = :companyId
            order by article.publishedAt desc, article.id desc
            """)
    List<Long> findRecentIdsByCompanyId(
            @Param("companyId") Long companyId, Pageable pageable);

    @Query("""
            select distinct article
            from NewsArticle article
            left join fetch article.companies
            where article.id in :ids
            """)
    List<NewsArticle> findAllByIdsWithCompanies(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO news_article (
                external_id, headline, source_name, article_url, description,
                published_at, retrieved_at, url_hash, provider_name
            ) VALUES (
                :externalId, :headline, :sourceName, :articleUrl, :description,
                :publishedAt, :retrievedAt, :urlHash, :providerName
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("externalId") String externalId,
            @Param("headline") String headline,
            @Param("sourceName") String sourceName,
            @Param("articleUrl") String articleUrl,
            @Param("description") String description,
            @Param("publishedAt") Instant publishedAt,
            @Param("retrievedAt") Instant retrievedAt,
            @Param("urlHash") String urlHash,
            @Param("providerName") String providerName);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO news_article_company (news_article_id, company_id)
            VALUES (:articleId, :companyId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int associateWithCompany(
            @Param("articleId") Long articleId, @Param("companyId") Long companyId);
}
