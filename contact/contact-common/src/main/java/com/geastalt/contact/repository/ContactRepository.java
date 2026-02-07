package com.geastalt.contact.repository;

import com.geastalt.contact.entity.Contact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    @Query("SELECT m FROM Contact m WHERE " +
           "m.lastName LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR m.firstName LIKE :firstNamePattern)")
    List<Contact> searchByName(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern,
        Pageable pageable);

    @Query("SELECT COUNT(m) FROM Contact m WHERE " +
           "m.lastName LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR m.firstName LIKE :firstNamePattern)")
    long countByNameSearch(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern);

    // Prefix search using LIKE with varchar_pattern_ops index
    @Query(value = "SELECT * FROM contacts " +
           "WHERE last_name_lower LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR first_name_lower LIKE :firstNamePattern)",
           nativeQuery = true)
    List<Contact> searchByNamePrefix(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern,
        Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM contacts " +
           "WHERE last_name_lower LIKE :lastNamePattern " +
           "AND (:firstNamePattern IS NULL OR first_name_lower LIKE :firstNamePattern)",
           nativeQuery = true)
    long countByNamePrefix(
        @Param("lastNamePattern") String lastNamePattern,
        @Param("firstNamePattern") String firstNamePattern);

    // Exact match using = for better index usage
    @Query(value = "SELECT * FROM contacts " +
           "WHERE last_name_lower = :lastName " +
           "AND (:firstName IS NULL OR first_name_lower = :firstName)",
           nativeQuery = true)
    List<Contact> searchByNameExact(
        @Param("lastName") String lastName,
        @Param("firstName") String firstName,
        Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM contacts " +
           "WHERE last_name_lower = :lastName " +
           "AND (:firstName IS NULL OR first_name_lower = :firstName)",
           nativeQuery = true)
    long countByNameExact(
        @Param("lastName") String lastName,
        @Param("firstName") String firstName);
}
