/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.service;

import com.geastalt.contact.entity.Contact;
import com.geastalt.contact.entity.ContactContract;
import com.geastalt.contact.entity.Contract;
import com.geastalt.contact.repository.ContactContractRepository;
import com.geastalt.contact.repository.ContactRepository;
import com.geastalt.contact.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactContractService {

    private final ContactRepository contactRepository;
    private final ContractRepository contractRepository;
    private final ContactContractRepository contactContractRepository;

    @Transactional
    public ContactContract addContactContract(Long contactId, UUID contractId, OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        log.info("Adding contract {} to contact {} with dates {} - {}", contractId, contactId, effectiveDate, expirationDate);

        validateDates(effectiveDate, expirationDate);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        // Check for overlapping contracts
        List<ContactContract> overlapping = contactContractRepository.findOverlappingContractsForNew(
                contactId, effectiveDate, expirationDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException(
                    "Contact already has a contract during this date range. Overlapping contract IDs: " +
                            overlapping.stream().map(mc -> mc.getId().toString()).toList());
        }

        ContactContract contactContract = ContactContract.builder()
                .contact(contact)
                .contract(contract)
                .effectiveDate(effectiveDate)
                .expirationDate(expirationDate)
                .build();

        ContactContract saved = contactContractRepository.save(contactContract);
        log.info("Added contract {} to contact {} with ID {}", contractId, contactId, saved.getId());

        return saved;
    }

    @Transactional
    public ContactContract updateContactContract(Long contactContractId, UUID contractId, OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        log.info("Updating contact contract {} with contract {} and dates {} - {}", contactContractId, contractId, effectiveDate, expirationDate);

        validateDates(effectiveDate, expirationDate);

        ContactContract contactContract = contactContractRepository.findById(contactContractId)
                .orElseThrow(() -> new IllegalArgumentException("Contact contract not found: " + contactContractId));

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        // Check for overlapping contracts (excluding this one)
        List<ContactContract> overlapping = contactContractRepository.findOverlappingContracts(
                contactContract.getContact().getId(), contactContractId, effectiveDate, expirationDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException(
                    "Contact already has a contract during this date range. Overlapping contract IDs: " +
                            overlapping.stream().map(mc -> mc.getId().toString()).toList());
        }

        contactContract.setContract(contract);
        contactContract.setEffectiveDate(effectiveDate);
        contactContract.setExpirationDate(expirationDate);

        ContactContract saved = contactContractRepository.save(contactContract);
        log.info("Updated contact contract {}", contactContractId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ContactContract> getContactContracts(Long contactId) {
        log.debug("Getting contracts for contact {}", contactId);
        return contactContractRepository.findByContactIdWithContract(contactId);
    }

    @Transactional(readOnly = true)
    public Optional<ContactContract> getCurrentContactContract(Long contactId) {
        log.debug("Getting current contract for contact {}", contactId);
        return contactContractRepository.findCurrentContract(contactId, OffsetDateTime.now());
    }

    @Transactional
    public void removeContactContract(Long contactId, Long contactContractId) {
        log.info("Removing contact contract {} from contact {}", contactContractId, contactId);

        ContactContract contactContract = contactContractRepository.findById(contactContractId)
                .orElseThrow(() -> new IllegalArgumentException("Contact contract not found: " + contactContractId));

        if (!contactContract.getContact().getId().equals(contactId)) {
            throw new IllegalArgumentException("Contact contract " + contactContractId + " does not belong to contact " + contactId);
        }

        contactContractRepository.delete(contactContract);
        log.info("Removed contact contract {} from contact {}", contactContractId, contactId);
    }

    private void validateDates(OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        if (effectiveDate == null) {
            throw new IllegalArgumentException("Effective date is required");
        }
        if (expirationDate == null) {
            throw new IllegalArgumentException("Expiration date is required");
        }
        if (!expirationDate.isAfter(effectiveDate)) {
            throw new IllegalArgumentException("Expiration date must be after effective date");
        }
    }
}
