package com.geastalt.member.service;

import com.geastalt.member.entity.AddressType;
import com.geastalt.member.entity.Member;
import com.geastalt.member.entity.MemberEmail;
import com.geastalt.member.repository.MemberEmailRepository;
import com.geastalt.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEmailService {

    private final MemberRepository memberRepository;
    private final MemberEmailRepository memberEmailRepository;

    @Transactional
    public MemberEmail addEmailToMember(Long memberId, String email, AddressType emailType) {
        log.info("Adding email {} of type {} to member {}", email, emailType, memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        memberEmailRepository.findByMemberIdAndEmailType(memberId, emailType)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Member already has an email of type " + emailType + ". Use update instead.");
                });

        // First email for this member becomes preferred
        boolean isFirstEmail = !memberEmailRepository.existsByMemberId(memberId);

        MemberEmail memberEmail = MemberEmail.builder()
                .member(member)
                .email(email)
                .emailType(emailType)
                .preferred(isFirstEmail)
                .build();

        MemberEmail saved = memberEmailRepository.save(memberEmail);
        log.info("Added email to member {} with type {}", memberId, emailType);

        return saved;
    }

    @Transactional
    public MemberEmail updateMemberEmail(Long memberId, String email, AddressType emailType) {
        log.info("Updating email type {} for member {} to {}", emailType, memberId, email);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        MemberEmail memberEmail = memberEmailRepository.findByMemberIdAndEmailType(memberId, emailType)
                .orElseGet(() -> MemberEmail.builder()
                        .member(member)
                        .emailType(emailType)
                        .build());

        memberEmail.setEmail(email);
        MemberEmail saved = memberEmailRepository.save(memberEmail);

        log.info("Updated member {} email type {} to {}", memberId, emailType, email);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MemberEmail> getMemberEmails(Long memberId) {
        log.debug("Getting emails for member {}", memberId);
        return memberEmailRepository.findByMemberId(memberId);
    }

    @Transactional
    public void removeMemberEmail(Long memberId, AddressType emailType) {
        log.info("Removing email type {} from member {}", emailType, memberId);

        memberEmailRepository.findByMemberIdAndEmailType(memberId, emailType)
                .ifPresentOrElse(
                        memberEmailRepository::delete,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Member " + memberId + " does not have an email of type " + emailType);
                        }
                );
    }
}
