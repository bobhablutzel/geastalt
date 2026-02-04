package com.geastalt.member.service;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.Member;
import com.geastalt.member.entity.MemberAddress;
import com.geastalt.member.entity.StandardizedAddress;
import com.geastalt.member.repository.MemberAddressRepository;
import com.geastalt.member.repository.MemberRepository;
import com.geastalt.member.repository.StandardizedAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAddressService {

    private final MemberRepository memberRepository;
    private final MemberAddressRepository memberAddressRepository;
    private final StandardizedAddressRepository standardizedAddressRepository;

    @Transactional
    public MemberAddress addAddressToMember(Long memberId, Long addressId, AddressType addressType) {
        log.info("Adding address {} of type {} to member {}", addressId, addressType, memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        StandardizedAddress address = standardizedAddressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        // Check if member already has this address type
        memberAddressRepository.findByMemberIdAndAddressType(memberId, addressType)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Member already has an address of type " + addressType + ". Use update instead.");
                });

        // First address for this member becomes preferred
        boolean isFirstAddress = !memberAddressRepository.existsByMemberId(memberId);

        MemberAddress memberAddress = MemberAddress.builder()
                .member(member)
                .address(address)
                .addressType(addressType)
                .preferred(isFirstAddress)
                .build();

        MemberAddress saved = memberAddressRepository.save(memberAddress);
        log.info("Added address {} to member {} with type {}", addressId, memberId, addressType);

        return saved;
    }

    @Transactional
    public MemberAddress updateMemberAddress(Long memberId, Long addressId, AddressType addressType) {
        log.info("Updating address type {} for member {} to address {}", addressType, memberId, addressId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        StandardizedAddress address = standardizedAddressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        MemberAddress memberAddress = memberAddressRepository.findByMemberIdAndAddressType(memberId, addressType)
                .orElseGet(() -> MemberAddress.builder()
                        .member(member)
                        .addressType(addressType)
                        .build());

        memberAddress.setAddress(address);
        MemberAddress saved = memberAddressRepository.save(memberAddress);

        log.info("Updated member {} address type {} to address {}", memberId, addressType, addressId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MemberAddress> getMemberAddresses(Long memberId) {
        log.debug("Getting addresses for member {}", memberId);
        return memberAddressRepository.findByMemberIdWithAddress(memberId);
    }

    @Transactional
    public void removeMemberAddress(Long memberId, AddressType addressType) {
        log.info("Removing address type {} from member {}", addressType, memberId);

        memberAddressRepository.findByMemberIdAndAddressType(memberId, addressType)
                .ifPresentOrElse(
                        memberAddressRepository::delete,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Member " + memberId + " does not have an address of type " + addressType);
                        }
                );
    }
}
