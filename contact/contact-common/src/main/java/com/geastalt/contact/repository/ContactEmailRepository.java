/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.repository;

import com.geastalt.contact.entity.AddressKind;
import com.geastalt.contact.entity.ContactEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactEmailRepository extends JpaRepository<ContactEmail, Long> {

    List<ContactEmail> findByContactId(Long contactId);

    Optional<ContactEmail> findByContactIdAndEmailType(Long contactId, AddressKind emailType);

    boolean existsByContactId(Long contactId);
}
