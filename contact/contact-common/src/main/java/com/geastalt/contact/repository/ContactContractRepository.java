/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.repository;

import com.geastalt.contact.entity.ContactContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactContractRepository extends JpaRepository<ContactContract, Long> {

    List<ContactContract> findByContactId(Long contactId);

    @Query("SELECT mc FROM ContactContract mc JOIN FETCH mc.contract JOIN FETCH mc.contact WHERE mc.contact.id = :contactId")
    List<ContactContract> findByContactIdWithContract(@Param("contactId") Long contactId);

    @Query("SELECT mc FROM ContactContract mc JOIN FETCH mc.contract " +
           "WHERE mc.contact.id = :contactId " +
           "AND mc.effectiveDate <= :currentDateTime " +
           "AND mc.expirationDate > :currentDateTime")
    Optional<ContactContract> findCurrentContract(@Param("contactId") Long contactId, @Param("currentDateTime") OffsetDateTime currentDateTime);

    @Query("SELECT mc FROM ContactContract mc " +
           "WHERE mc.contact.id = :contactId " +
           "AND mc.id != :excludeId " +
           "AND mc.effectiveDate < :expirationDate " +
           "AND mc.expirationDate > :effectiveDate")
    List<ContactContract> findOverlappingContracts(
            @Param("contactId") Long contactId,
            @Param("excludeId") Long excludeId,
            @Param("effectiveDate") OffsetDateTime effectiveDate,
            @Param("expirationDate") OffsetDateTime expirationDate);

    @Query("SELECT mc FROM ContactContract mc " +
           "WHERE mc.contact.id = :contactId " +
           "AND mc.effectiveDate < :expirationDate " +
           "AND mc.expirationDate > :effectiveDate")
    List<ContactContract> findOverlappingContractsForNew(
            @Param("contactId") Long contactId,
            @Param("effectiveDate") OffsetDateTime effectiveDate,
            @Param("expirationDate") OffsetDateTime expirationDate);

    void deleteByContactIdAndId(Long contactId, Long id);
}
