package com.geastalt.contact.service;

import com.geastalt.contact.entity.ContactAlternateId;
import com.geastalt.contact.validation.AlternateIdTypeValidator;
import com.geastalt.contact.entity.ContactLookup;
import com.geastalt.contact.entity.ContactPendingAction;
import com.geastalt.contact.entity.PendingActionType;
import com.geastalt.contact.repository.ContactAlternateIdRepository;
import com.geastalt.contact.repository.ContactLookupRepository;
import com.geastalt.contact.repository.ContactPendingActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateExternalIdsService {

    private final ContactLookupRepository contactLookupRepository;
    private final ContactAlternateIdRepository contactAlternateIdRepository;
    private final ContactPendingActionRepository contactPendingActionRepository;
    private final ExternalIdService externalIdService;

    @Transactional
    public void processGenerateExternalIds(Long contactId) {
        log.debug("Processing generate external identifiers for contact: {}", contactId);

        // Check if pending action exists
        Optional<ContactPendingAction> pendingAction = contactPendingActionRepository
                .findByContactIdAndActionType(contactId, PendingActionType.GENERATE_EXTERNAL_IDENTIFIERS);

        if (pendingAction.isEmpty()) {
            log.warn("No pending action found for contact: {}, may have already been processed", contactId);
            return;
        }

        // Check if contact lookup exists
        Optional<ContactLookup> lookupOpt = contactLookupRepository.findById(contactId);
        if (lookupOpt.isEmpty()) {
            log.error("ContactLookup not found for contact: {}", contactId);
            return;
        }

        ContactLookup lookup = lookupOpt.get();

        // Check if NEW_NATIONS alternate ID already exists
        boolean hasNewNationsId = contactAlternateIdRepository
                .findByContactIdAndIdType(contactId, AlternateIdTypeValidator.DEFAULT_TYPE)
                .isPresent();

        if (hasNewNationsId) {
            log.info("Contact {} already has NEW_NATIONS alternate ID, removing pending action", contactId);
        } else {
            // Generate and save the NEW_NATIONS alternate ID
            String alternateIdValue = externalIdService.generateExternalId(contactId);
            ContactAlternateId alternateId = ContactAlternateId.builder()
                    .contactLookup(lookup)
                    .idType(AlternateIdTypeValidator.DEFAULT_TYPE)
                    .alternateId(alternateIdValue)
                    .build();
            contactAlternateIdRepository.save(alternateId);
            log.info("Generated NEW_NATIONS alternate ID for contact {}: {}", contactId, alternateIdValue);
        }

        // Remove the pending action
        contactPendingActionRepository.delete(pendingAction.get());
        log.debug("Removed pending action GENERATE_EXTERNAL_IDENTIFIERS for contact: {}", contactId);
    }
}
