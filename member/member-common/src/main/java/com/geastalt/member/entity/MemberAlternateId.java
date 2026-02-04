package com.geastalt.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entity representing an alternate identifier for a member.
 * Each member can have one identifier per type.
 */
@Entity
@Table(name = "member_alternate_ids", schema = "public", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_alternate_ids_member_type", columnNames = {"member_id", "id_type"}),
        @UniqueConstraint(name = "uk_member_alternate_ids_type_alternate", columnNames = {"id_type", "alternate_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberAlternateId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MemberLookup memberLookup;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 20)
    private AlternateIdType idType;

    @Column(name = "alternate_id", nullable = false, length = 255)
    private String alternateId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
