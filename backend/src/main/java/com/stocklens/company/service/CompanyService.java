package com.stocklens.company.service;

import com.stocklens.company.domain.Company;
import com.stocklens.company.repository.CompanyRepository;
import com.stocklens.market.client.model.CompanyProfileData;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    private final CompanyRepository repository;

    public CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    public Company upsert(CompanyProfileData profile) {
        Company company = repository.findByTicker(profile.ticker()).map(existing -> {
            update(existing, profile);
            return existing;
        }).orElseGet(() -> new Company(
                profile.ticker(),
                profile.name(),
                profile.exchange(),
                profile.sector(),
                profile.industry(),
                profile.country(),
                profile.websiteUrl(),
                profile.description(),
                profile.logoUrl(),
                profile.providerSymbol(),
                profile.retrievedAt(),
                profile.retrievedAt()));

        try {
            return repository.saveAndFlush(company);
        } catch (DataIntegrityViolationException exception) {
            Company concurrent = repository.findByTicker(profile.ticker()).orElseThrow(() -> exception);
            update(concurrent, profile);
            return repository.saveAndFlush(concurrent);
        }
    }

    private void update(Company company, CompanyProfileData profile) {
        company.updateProfile(
                profile.name(),
                profile.exchange(),
                profile.sector(),
                profile.industry(),
                profile.country(),
                profile.websiteUrl(),
                profile.description(),
                profile.logoUrl(),
                profile.providerSymbol(),
                profile.retrievedAt());
    }
}
