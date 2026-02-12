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
import com.geastalt.contact.entity.ContactPhone;
import com.geastalt.contact.repository.ContactPhoneRepository;
import com.geastalt.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactPhoneService {

    private final ContactRepository contactRepository;
    private final ContactPhoneRepository contactPhoneRepository;

    @Transactional
    public ContactPhone addPhoneToContact(Long contactId, String phoneNumber, AddressKind phoneType) {
        log.info("Adding phone {} of type {} to contact {}", phoneNumber, phoneType, contactId);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        contactPhoneRepository.findByContactIdAndPhoneType(contactId, phoneType)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Contact already has a phone of type " + phoneType + ". Use update instead.");
                });

        ContactPhone contactPhone = ContactPhone.builder()
                .contact(contact)
                .phoneNumber(phoneNumber)
                .phoneType(phoneType)
                .build();

        ContactPhone saved = contactPhoneRepository.save(contactPhone);
        log.info("Added phone to contact {} with type {}", contactId, phoneType);

        return saved;
    }

    @Transactional
    public ContactPhone updateContactPhone(Long contactId, String phoneNumber, AddressKind phoneType) {
        log.info("Updating phone type {} for contact {} to {}", phoneType, contactId, phoneNumber);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        ContactPhone contactPhone = contactPhoneRepository.findByContactIdAndPhoneType(contactId, phoneType)
                .orElseGet(() -> ContactPhone.builder()
                        .contact(contact)
                        .phoneType(phoneType)
                        .build());

        contactPhone.setPhoneNumber(phoneNumber);
        ContactPhone saved = contactPhoneRepository.save(contactPhone);

        log.info("Updated contact {} phone type {} to {}", contactId, phoneType, phoneNumber);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ContactPhone> getContactPhones(Long contactId) {
        log.debug("Getting phones for contact {}", contactId);
        return contactPhoneRepository.findByContactId(contactId);
    }

    @Transactional
    public void removeContactPhone(Long contactId, AddressKind phoneType) {
        log.info("Removing phone type {} from contact {}", phoneType, contactId);

        contactPhoneRepository.findByContactIdAndPhoneType(contactId, phoneType)
                .ifPresentOrElse(
                        contactPhoneRepository::delete,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Contact " + contactId + " does not have a phone of type " + phoneType);
                        }
                );
    }
}
