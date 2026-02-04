package com.geastalt.member.repository;

import com.geastalt.member.entity.MemberPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPlanRepository extends JpaRepository<MemberPlan, Long> {

    List<MemberPlan> findByMemberId(Long memberId);

    @Query("SELECT mp FROM MemberPlan mp JOIN FETCH mp.plan WHERE mp.member.id = :memberId")
    List<MemberPlan> findByMemberIdWithPlan(@Param("memberId") Long memberId);

    @Query("SELECT mp FROM MemberPlan mp JOIN FETCH mp.plan " +
           "WHERE mp.member.id = :memberId " +
           "AND mp.effectiveDate <= :currentDateTime " +
           "AND mp.expirationDate > :currentDateTime")
    Optional<MemberPlan> findCurrentPlan(@Param("memberId") Long memberId, @Param("currentDateTime") OffsetDateTime currentDateTime);

    @Query("SELECT mp FROM MemberPlan mp " +
           "WHERE mp.member.id = :memberId " +
           "AND mp.id != :excludeId " +
           "AND mp.effectiveDate < :expirationDate " +
           "AND mp.expirationDate > :effectiveDate")
    List<MemberPlan> findOverlappingPlans(
            @Param("memberId") Long memberId,
            @Param("excludeId") Long excludeId,
            @Param("effectiveDate") OffsetDateTime effectiveDate,
            @Param("expirationDate") OffsetDateTime expirationDate);

    @Query("SELECT mp FROM MemberPlan mp " +
           "WHERE mp.member.id = :memberId " +
           "AND mp.effectiveDate < :expirationDate " +
           "AND mp.expirationDate > :effectiveDate")
    List<MemberPlan> findOverlappingPlansForNew(
            @Param("memberId") Long memberId,
            @Param("effectiveDate") OffsetDateTime effectiveDate,
            @Param("expirationDate") OffsetDateTime expirationDate);

    void deleteByMemberIdAndId(Long memberId, Long id);
}
