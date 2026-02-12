/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity representing an alternate identifier for a contact.
 * Each contact can have one identifier per type.
 */
@Entity
@Table(name = "contact_alternate_ids", schema = "public", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contact_alternate_ids_contact_type", columnNames = {"contact_id", "id_type"}),
        @UniqueConstraint(name = "uk_contact_alternate_ids_type_alternate", columnNames = {"id_type", "alternate_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactAlternateId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ContactLookup contactLookup;

    @Column(name = "id_type", nullable = false, length = 50)
    private String idType;

    @Column(name = "alternate_id", nullable = false, length = 255)
    private String alternateId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
