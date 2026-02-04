package com.geastalt.member.repository;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.MemberEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberEmailRepository extends JpaRepository<MemberEmail, Long> {

    List<MemberEmail> findByMemberId(Long memberId);

    Optional<MemberEmail> findByMemberIdAndEmailType(Long memberId, AddressType emailType);

    boolean existsByMemberId(Long memberId);
}
