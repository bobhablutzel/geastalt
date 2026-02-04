package com.geastalt.member.service;

import com.geastalt.member.dto.MemberSearchResult;
import com.geastalt.member.repository.MemberSearchJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSearchService {

    private final MemberSearchJdbcRepository memberSearchRepository;

    private static final int DEFAULT_MAX_RESULTS = 25;
    private static final int MAX_RESULTS_LIMIT = 1000;

    public SearchResult searchMembers(String lastName, String firstName, int maxResults, boolean includeTotalCount) {
        log.debug("Searching members: lastName={}, firstName={}, maxResults={}, includeTotalCount={}",
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
        List<MemberSearchResult> members;
        if (isExactMatch) {
            members = memberSearchRepository.searchByNameExact(lastNamePattern, firstNamePattern, effectiveMaxResults);
        } else {
            members = memberSearchRepository.searchByNamePrefix(lastNamePattern, firstNamePattern, effectiveMaxResults);
        }

        // Count query - only if requested
        long totalCount = 0;
        if (includeTotalCount) {
            if (isExactMatch) {
                totalCount = memberSearchRepository.countByNameExact(lastNamePattern, firstNamePattern);
            } else {
                totalCount = memberSearchRepository.countByNamePrefix(lastNamePattern, firstNamePattern);
            }
        }

        log.debug("Found {} members (total count: {})", members.size(), includeTotalCount ? totalCount : "skipped");

        return new SearchResult(members, totalCount);
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

    public record SearchResult(List<MemberSearchResult> members, long totalCount) {}
}
