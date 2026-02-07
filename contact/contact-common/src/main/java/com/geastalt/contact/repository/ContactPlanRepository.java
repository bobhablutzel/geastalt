package com.geastalt.contact.repository;

import com.geastalt.contact.entity.ContactPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactPlanRepository extends JpaRepository<ContactPlan, Long> {

    List<ContactPlan> findByContactId(Long contactId);

    @Query("SELECT mp FROM ContactPlan mp JOIN FETCH mp.plan WHERE mp.contact.id = :contactId")
    List<ContactPlan> findByContactIdWithPlan(@Param("contactId") Long contactId);

    @Query("SELECT mp FROM ContactPlan mp JOIN FETCH mp.plan " +
           "WHERE mp.contact.id = :contactId " +
           "AND mp.effectiveDate <= :currentDateTime " +
           "AND mp.expirationDate > :currentDateTime")
    Optional<ContactPlan> findCurrentPlan(@Param("contactId") Long contactId, @Param("currentDateTime") OffsetDateTime currentDateTime);

    @Query("SELECT mp FROM ContactPlan mp " +
           "WHERE mp.contact.id = :contactId " +
           "AND mp.id != :excludeId " +
           "AND mp.effectiveDate < :expirationDate " +
           "AND mp.expirationDate > :effectiveDate")
    List<ContactPlan> findOverlappingPlans(
            @Param("contactId") Long contactId,
            @Param("excludeId") Long excludeId,
            @Param("effectiveDate") OffsetDateTime effectiveDate,
            @Param("expirationDate") OffsetDateTime expirationDate);

    @Query("SELECT mp FROM ContactPlan mp " +
           "WHERE mp.contact.id = :contactId " +
           "AND mp.effectiveDate < :expirationDate " +
           "AND mp.expirationDate > :effectiveDate")
    List<ContactPlan> findOverlappingPlansForNew(
            @Param("contactId") Long contactId,
            @Param("effectiveDate") OffsetDateTime effectiveDate,
            @Param("expirationDate") OffsetDateTime expirationDate);

    void deleteByContactIdAndId(Long contactId, Long id);
}
