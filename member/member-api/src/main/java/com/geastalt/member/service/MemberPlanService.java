package com.geastalt.member.service;

import com.geastalt.member.entity.Member;
import com.geastalt.member.entity.MemberPlan;
import com.geastalt.member.entity.Plan;
import com.geastalt.member.repository.MemberPlanRepository;
import com.geastalt.member.repository.MemberRepository;
import com.geastalt.member.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPlanService {

    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final MemberPlanRepository memberPlanRepository;

    @Transactional
    public MemberPlan addMemberPlan(Long memberId, Long planId, OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        log.info("Adding plan {} to member {} with dates {} - {}", planId, memberId, effectiveDate, expirationDate);

        validateDates(effectiveDate, expirationDate);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        // Check for overlapping plans
        List<MemberPlan> overlapping = memberPlanRepository.findOverlappingPlansForNew(
                memberId, effectiveDate, expirationDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException(
                    "Member already has a plan during this date range. Overlapping plan IDs: " +
                            overlapping.stream().map(mp -> mp.getId().toString()).toList());
        }

        MemberPlan memberPlan = MemberPlan.builder()
                .member(member)
                .plan(plan)
                .effectiveDate(effectiveDate)
                .expirationDate(expirationDate)
                .build();

        MemberPlan saved = memberPlanRepository.save(memberPlan);
        log.info("Added plan {} to member {} with ID {}", planId, memberId, saved.getId());

        return saved;
    }

    @Transactional
    public MemberPlan updateMemberPlan(Long memberPlanId, Long planId, OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        log.info("Updating member plan {} with plan {} and dates {} - {}", memberPlanId, planId, effectiveDate, expirationDate);

        validateDates(effectiveDate, expirationDate);

        MemberPlan memberPlan = memberPlanRepository.findById(memberPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Member plan not found: " + memberPlanId));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        // Check for overlapping plans (excluding this one)
        List<MemberPlan> overlapping = memberPlanRepository.findOverlappingPlans(
                memberPlan.getMember().getId(), memberPlanId, effectiveDate, expirationDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException(
                    "Member already has a plan during this date range. Overlapping plan IDs: " +
                            overlapping.stream().map(mp -> mp.getId().toString()).toList());
        }

        memberPlan.setPlan(plan);
        memberPlan.setEffectiveDate(effectiveDate);
        memberPlan.setExpirationDate(expirationDate);

        MemberPlan saved = memberPlanRepository.save(memberPlan);
        log.info("Updated member plan {}", memberPlanId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<MemberPlan> getMemberPlans(Long memberId) {
        log.debug("Getting plans for member {}", memberId);
        return memberPlanRepository.findByMemberIdWithPlan(memberId);
    }

    @Transactional(readOnly = true)
    public Optional<MemberPlan> getCurrentMemberPlan(Long memberId) {
        log.debug("Getting current plan for member {}", memberId);
        return memberPlanRepository.findCurrentPlan(memberId, OffsetDateTime.now());
    }

    @Transactional
    public void removeMemberPlan(Long memberId, Long memberPlanId) {
        log.info("Removing member plan {} from member {}", memberPlanId, memberId);

        MemberPlan memberPlan = memberPlanRepository.findById(memberPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Member plan not found: " + memberPlanId));

        if (!memberPlan.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("Member plan " + memberPlanId + " does not belong to member " + memberId);
        }

        memberPlanRepository.delete(memberPlan);
        log.info("Removed member plan {} from member {}", memberPlanId, memberId);
    }

    private void validateDates(OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        if (effectiveDate == null) {
            throw new IllegalArgumentException("Effective date is required");
        }
        if (expirationDate == null) {
            throw new IllegalArgumentException("Expiration date is required");
        }
        if (!expirationDate.isAfter(effectiveDate)) {
            throw new IllegalArgumentException("Expiration date must be after effective date");
        }
    }
}
