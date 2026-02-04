package com.geastalt.member.repository;

import com.geastalt.member.entity.MemberPendingAction;
import com.geastalt.member.entity.PendingActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPendingActionRepository extends JpaRepository<MemberPendingAction, Long> {

    /**
     * Check if a pending action exists for the given member and action type.
     */
    boolean existsByMemberIdAndActionType(Long memberId, PendingActionType actionType);

    /**
     * Find a pending action by member ID and action type.
     */
    Optional<MemberPendingAction> findByMemberIdAndActionType(Long memberId, PendingActionType actionType);

    /**
     * Find all pending actions for a member.
     */
    List<MemberPendingAction> findAllByMemberId(Long memberId);

    /**
     * Delete a pending action by member ID and action type.
     */
    void deleteByMemberIdAndActionType(Long memberId, PendingActionType actionType);
}
