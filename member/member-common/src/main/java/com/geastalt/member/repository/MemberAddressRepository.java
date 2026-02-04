package com.geastalt.member.repository;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.MemberAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    List<MemberAddress> findByMemberId(Long memberId);

    @Query("SELECT ma FROM MemberAddress ma JOIN FETCH ma.address WHERE ma.member.id = :memberId")
    List<MemberAddress> findByMemberIdWithAddress(@Param("memberId") Long memberId);

    Optional<MemberAddress> findByMemberIdAndAddressType(Long memberId, AddressType addressType);

    void deleteByMemberIdAndAddressType(Long memberId, AddressType addressType);

    boolean existsByMemberId(Long memberId);
}
