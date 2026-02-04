package com.geastalt.member.service;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.Member;
import com.geastalt.member.entity.MemberPhone;
import com.geastalt.member.repository.MemberPhoneRepository;
import com.geastalt.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPhoneService {

    private final MemberRepository memberRepository;
    private final MemberPhoneRepository memberPhoneRepository;

    @Transactional
    public MemberPhone addPhoneToMember(Long memberId, String phoneNumber, AddressType phoneType) {
        log.info("Adding phone {} of type {} to member {}", phoneNumber, phoneType, memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        memberPhoneRepository.findByMemberIdAndPhoneType(memberId, phoneType)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Member already has a phone of type " + phoneType + ". Use update instead.");
                });

        MemberPhone memberPhone = MemberPhone.builder()
                .member(member)
                .phoneNumber(phoneNumber)
                .phoneType(phoneType)
                .build();

        MemberPhone saved = memberPhoneRepository.save(memberPhone);
        log.info("Added phone to member {} with type {}", memberId, phoneType);

        return saved;
    }

    @Transactional
    public MemberPhone updateMemberPhone(Long memberId, String phoneNumber, AddressType phoneType) {
        log.info("Updating phone type {} for member {} to {}", phoneType, memberId, phoneNumber);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        MemberPhone memberPhone = memberPhoneRepository.findByMemberIdAndPhoneType(memberId, phoneType)
                .orElseGet(() -> MemberPhone.builder()
                        .member(member)
                        .phoneType(phoneType)
                        .build());

        memberPhone.setPhoneNumber(phoneNumber);
        MemberPhone saved = memberPhoneRepository.save(memberPhone);

        log.info("Updated member {} phone type {} to {}", memberId, phoneType, phoneNumber);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MemberPhone> getMemberPhones(Long memberId) {
        log.debug("Getting phones for member {}", memberId);
        return memberPhoneRepository.findByMemberId(memberId);
    }

    @Transactional
    public void removeMemberPhone(Long memberId, AddressType phoneType) {
        log.info("Removing phone type {} from member {}", phoneType, memberId);

        memberPhoneRepository.findByMemberIdAndPhoneType(memberId, phoneType)
                .ifPresentOrElse(
                        memberPhoneRepository::delete,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Member " + memberId + " does not have a phone of type " + phoneType);
                        }
                );
    }
}
