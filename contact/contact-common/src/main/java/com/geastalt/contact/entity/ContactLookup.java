package com.geastalt.contact.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contact_lookup", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactLookup {

    @Id
    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "contactLookup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<ContactAlternateId> alternateIds = new ArrayList<>();
}
