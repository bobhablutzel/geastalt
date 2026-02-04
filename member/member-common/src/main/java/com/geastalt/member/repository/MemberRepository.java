package com.geastalt.member.repository;

import com.geastalt.member.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("SELECT m FROM Member m WHERE " +
           "m.lastName LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR m.firstName LIKE :firstNamePattern)")
    List<Member> searchByName(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern,
        Pageable pageable);

    @Query("SELECT COUNT(m) FROM Member m WHERE " +
           "m.lastName LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR m.firstName LIKE :firstNamePattern)")
    long countByNameSearch(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern);

    // Prefix search using LIKE with varchar_pattern_ops index
    @Query(value = "SELECT * FROM members " +
           "WHERE last_name_lower LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR first_name_lower LIKE :firstNamePattern) " +
           "LIMIT :limit",
           nativeQuery = true)
    List<Member> searchByNamePrefix(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern,
        @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM members " +
           "WHERE last_name_lower LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR first_name_lower LIKE :firstNamePattern)",
           nativeQuery = true)
    long countByNamePrefix(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern);

    // Exact match using = for better index usage
    @Query(value = "SELECT * FROM members " +
           "WHERE last_name_lower = :lastName " +
           "AND (:firstName IS NULL OR first_name_lower = :firstName) " +
           "LIMIT :limit",
           nativeQuery = true)
    List<Member> searchByNameExact(
        @Param("lastName") String lastName,
        @Param("firstName") String firstName,
        @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM members " +
           "WHERE last_name_lower = :lastName " +
           "AND (:firstName IS NULL OR first_name_lower = :firstName)",
           nativeQuery = true)
    long countByNameExact(
        @Param("lastName") String lastName,
        @Param("firstName") String firstName);
}
