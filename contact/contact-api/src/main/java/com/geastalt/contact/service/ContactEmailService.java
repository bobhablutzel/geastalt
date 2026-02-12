/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.service;

import com.geastalt.contact.entity.AddressKind;
import com.geastalt.contact.entity.Contact;
import com.geastalt.contact.entity.ContactEmail;
import com.geastalt.contact.repository.ContactEmailRepository;
import com.geastalt.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactEmailService {

    private final ContactRepository contactRepository;
    private final ContactEmailRepository contactEmailRepository;

    @Transactional
    public ContactEmail addEmailToContact(Long contactId, String email, AddressKind emailType) {
        log.info("Adding email {} of type {} to contact {}", email, emailType, contactId);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        contactEmailRepository.findByContactIdAndEmailType(contactId, emailType)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Contact already has an email of type " + emailType + ". Use update instead.");
                });

        // First email for this contact becomes preferred
        boolean isFirstEmail = !contactEmailRepository.existsByContactId(contactId);

        ContactEmail contactEmail = ContactEmail.builder()
                .contact(contact)
                .email(email)
                .emailType(emailType)
                .preferred(isFirstEmail)
                .build();

        ContactEmail saved = contactEmailRepository.save(contactEmail);
        log.info("Added email to contact {} with type {}", contactId, emailType);

        return saved;
    }

    @Transactional
    public ContactEmail updateContactEmail(Long contactId, String email, AddressKind emailType) {
        log.info("Updating email type {} for contact {} to {}", emailType, contactId, email);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        ContactEmail contactEmail = contactEmailRepository.findByContactIdAndEmailType(contactId, emailType)
                .orElseGet(() -> ContactEmail.builder()
                        .contact(contact)
                        .emailType(emailType)
                        .build());

        contactEmail.setEmail(email);
        ContactEmail saved = contactEmailRepository.save(contactEmail);

        log.info("Updated contact {} email type {} to {}", contactId, emailType, email);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ContactEmail> getContactEmails(Long contactId) {
        log.debug("Getting emails for contact {}", contactId);
        return contactEmailRepository.findByContactId(contactId);
    }

    @Transactional
    public void removeContactEmail(Long contactId, AddressKind emailType) {
        log.info("Removing email type {} from contact {}", emailType, contactId);

        contactEmailRepository.findByContactIdAndEmailType(contactId, emailType)
                .ifPresentOrElse(
                        contactEmailRepository::delete,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Contact " + contactId + " does not have an email of type " + emailType);
                        }
                );
    }
}
