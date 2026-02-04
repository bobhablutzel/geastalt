package com.geastalt.member.service;

import com.geastalt.member.entity.AlternateIdType;
import com.geastalt.member.entity.MemberAlternateId;
import com.geastalt.member.entity.MemberLookup;
import com.geastalt.member.entity.MemberPendingAction;
import com.geastalt.member.entity.PendingActionType;
import com.geastalt.member.repository.MemberAlternateIdRepository;
import com.geastalt.member.repository.MemberLookupRepository;
import com.geastalt.member.repository.MemberPendingActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateExternalIdsService {

    private final MemberLookupRepository memberLookupRepository;
    private final MemberAlternateIdRepository memberAlternateIdRepository;
    private final MemberPendingActionRepository memberPendingActionRepository;
    private final ExternalIdService externalIdService;

    @Transactional
    public void processGenerateExternalIds(Long memberId) {
        log.debug("Processing generate external identifiers for member: {}", memberId);

        // Check if pending action exists
        Optional<MemberPendingAction> pendingAction = memberPendingActionRepository
                .findByMemberIdAndActionType(memberId, PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS);

        if (pendingAction.isEmpty()) {
            log.warn("No pending action found for member: {}, may have already been processed", memberId);
            return;
        }

        // Check if member lookup exists
        Optional<MemberLookup> lookupOpt = memberLookupRepository.findById(memberId);
        if (lookupOpt.isEmpty()) {
            log.error("MemberLookup not found for member: {}", memberId);
            return;
        }

        MemberLookup lookup = lookupOpt.get();

        // Check if NEW_NATIONS alternate ID already exists
        boolean hasNewNationsId = memberAlternateIdRepository
                .findByMemberIdAndIdType(memberId, AlternateIdType.NEW_NATIONS)
                .isPresent();

        if (hasNewNationsId) {
            log.info("Member {} already has NEW_NATIONS alternate ID, removing pending action", memberId);
        } else {
            // Generate and save the NEW_NATIONS alternate ID
            String alternateIdValue = externalIdService.generateExternalId(memberId);
            MemberAlternateId alternateId = MemberAlternateId.builder()
                    .memberLookup(lookup)
                    .idType(AlternateIdType.NEW_NATIONS)
                    .alternateId(alternateIdValue)
                    .build();
            memberAlternateIdRepository.save(alternateId);
            log.info("Generated NEW_NATIONS alternate ID for member {}: {}", memberId, alternateIdValue);
        }

        // Remove the pending action
        memberPendingActionRepository.delete(pendingAction.get());
        log.debug("Removed pending action GENERATE_EXTERNAL_IDENTIFIERS for member: {}", memberId);
    }
}
