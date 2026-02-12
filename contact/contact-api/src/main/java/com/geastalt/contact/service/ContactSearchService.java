/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.service;

import com.geastalt.contact.dto.ContactSearchResult;
import com.geastalt.contact.repository.ContactSearchJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactSearchService {

    private final ContactSearchJdbcRepository contactSearchRepository;

    private static final int DEFAULT_MAX_RESULTS = 25;
    private static final int MAX_RESULTS_LIMIT = 1000;

    public SearchResult searchContacts(String lastName, String firstName, int maxResults, boolean includeTotalCount) {
        log.debug("Searching contacts: lastName={}, firstName={}, maxResults={}, includeTotalCount={}",
                lastName, firstName, maxResults, includeTotalCount);

        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName is required");
        }

        int effectiveMaxResults = maxResults <= 0 ? DEFAULT_MAX_RESULTS : Math.min(maxResults, MAX_RESULTS_LIMIT);

        // Convert to lowercase for case-insensitive search
        String lastNamePattern = convertWildcard(lastName.toLowerCase());
        String firstNamePattern = convertWildcard(firstName != null ? firstName.toLowerCase() : null);

        // Determine if this is an exact match or prefix search
        boolean isExactMatch = !lastNamePattern.contains("%") &&
                               (firstNamePattern == null || !firstNamePattern.contains("%"));

        // Search query
        List<ContactSearchResult> contacts;
        if (isExactMatch) {
            contacts = contactSearchRepository.searchByNameExact(lastNamePattern, firstNamePattern, effectiveMaxResults);
        } else {
            contacts = contactSearchRepository.searchByNamePrefix(lastNamePattern, firstNamePattern, effectiveMaxResults);
        }

        // Count query - only if requested
        long totalCount = 0;
        if (includeTotalCount) {
            if (isExactMatch) {
                totalCount = contactSearchRepository.countByNameExact(lastNamePattern, firstNamePattern);
            } else {
                totalCount = contactSearchRepository.countByNamePrefix(lastNamePattern, firstNamePattern);
            }
        }

        log.debug("Found {} contacts (total count: {})", contacts.size(), includeTotalCount ? totalCount : "skipped");

        return new SearchResult(contacts, totalCount);
    }

    private String convertWildcard(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        if (pattern.endsWith("*")) {
            return pattern.substring(0, pattern.length() - 1) + "%";
        }
        return pattern;
    }

    public record SearchResult(List<ContactSearchResult> contacts, long totalCount) {}
}
