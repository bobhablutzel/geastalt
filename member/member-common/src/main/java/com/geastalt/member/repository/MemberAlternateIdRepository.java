package com.geastalt.member.repository;

import com.geastalt.member.entity.AlternateIdType;
import com.geastalt.member.entity.MemberAlternateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberAlternateIdRepository extends JpaRepository<MemberAlternateId, Long> {

    /**
     * Find an alternate ID entry by type and alternate ID value.
     */
    Optional<MemberAlternateId> findByIdTypeAndAlternateId(AlternateIdType idType, String alternateId);

    /**
     * Find an alternate ID entry by member ID and type.
     */
    @Query("SELECT mai FROM MemberAlternateId mai WHERE mai.memberLookup.memberId = :memberId AND mai.idType = :idType")
    Optional<MemberAlternateId> findByMemberIdAndIdType(@Param("memberId") Long memberId, @Param("idType") AlternateIdType idType);

    /**
     * Find all alternate IDs for a member.
     */
    @Query("SELECT mai FROM MemberAlternateId mai WHERE mai.memberLookup.memberId = :memberId")
    List<MemberAlternateId> findAllByMemberId(@Param("memberId") Long memberId);

    /**
     * Check if an alternate ID exists for the given type and value.
     */
    boolean existsByIdTypeAndAlternateId(AlternateIdType idType, String alternateId);
}
