package com.geastalt.member.repository;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.MemberPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPhoneRepository extends JpaRepository<MemberPhone, Long> {

    List<MemberPhone> findByMemberId(Long memberId);

    Optional<MemberPhone> findByMemberIdAndPhoneType(Long memberId, AddressType phoneType);
}
