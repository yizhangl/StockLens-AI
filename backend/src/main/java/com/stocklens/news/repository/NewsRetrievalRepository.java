package com.stocklens.news.repository;

import com.stocklens.news.domain.NewsRetrieval;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRetrievalRepository extends JpaRepository<NewsRetrieval, Long> {
    Optional<NewsRetrieval> findFirstByCompany_IdOrderByRetrievedAtDescIdDesc(Long companyId);
}
