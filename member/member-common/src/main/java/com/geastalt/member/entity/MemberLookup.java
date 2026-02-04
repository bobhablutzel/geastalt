package com.geastalt.member.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "member_lookup", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLookup {

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "partition_number", nullable = false)
    private Integer partitionNumber;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "memberLookup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<MemberAlternateId> alternateIds = new ArrayList<>();
}
