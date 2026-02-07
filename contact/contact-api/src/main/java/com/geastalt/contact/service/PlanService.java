package com.geastalt.contact.service;

import com.geastalt.contact.entity.Plan;
import com.geastalt.contact.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    @Transactional
    public Plan createPlan(String planName, Integer carrierId, String carrierName) {
        log.info("Creating plan: {} for carrier: {} ({})", planName, carrierName, carrierId);

        Plan plan = Plan.builder()
                .planName(planName)
                .carrierId(carrierId)
                .carrierName(carrierName)
                .build();

        Plan saved = planRepository.save(plan);
        log.info("Created plan with ID: {}", saved.getId());

        return saved;
    }

    @Transactional
    public Plan updatePlan(Long planId, String planName, Integer carrierId, String carrierName) {
        log.info("Updating plan: {}", planId);

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        plan.setPlanName(planName);
        plan.setCarrierId(carrierId);
        plan.setCarrierName(carrierName);

        Plan saved = planRepository.save(plan);
        log.info("Updated plan: {}", planId);

        return saved;
    }

    @Transactional(readOnly = true)
    public Plan getPlan(Long planId) {
        log.debug("Getting plan: {}", planId);
        return planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
    }

    @Transactional(readOnly = true)
    public List<Plan> getAllPlans() {
        log.debug("Getting all plans");
        return planRepository.findAll();
    }

    @Transactional
    public void deletePlan(Long planId) {
        log.info("Deleting plan: {}", planId);

        if (!planRepository.existsById(planId)) {
            throw new IllegalArgumentException("Plan not found: " + planId);
        }

        planRepository.deleteById(planId);
        log.info("Deleted plan: {}", planId);
    }
}
