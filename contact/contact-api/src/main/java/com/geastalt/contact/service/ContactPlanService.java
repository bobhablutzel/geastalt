package com.geastalt.contact.service;

import com.geastalt.contact.entity.Contact;
import com.geastalt.contact.entity.ContactPlan;
import com.geastalt.contact.entity.Plan;
import com.geastalt.contact.repository.ContactPlanRepository;
import com.geastalt.contact.repository.ContactRepository;
import com.geastalt.contact.repository.PlanRepository;
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
public class ContactPlanService {

    private final ContactRepository contactRepository;
    private final PlanRepository planRepository;
    private final ContactPlanRepository contactPlanRepository;

    @Transactional
    public ContactPlan addContactPlan(Long contactId, Long planId, OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        log.info("Adding plan {} to contact {} with dates {} - {}", planId, contactId, effectiveDate, expirationDate);

        validateDates(effectiveDate, expirationDate);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        // Check for overlapping plans
        List<ContactPlan> overlapping = contactPlanRepository.findOverlappingPlansForNew(
                contactId, effectiveDate, expirationDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException(
                    "Contact already has a plan during this date range. Overlapping plan IDs: " +
                            overlapping.stream().map(mp -> mp.getId().toString()).toList());
        }

        ContactPlan contactPlan = ContactPlan.builder()
                .contact(contact)
                .plan(plan)
                .effectiveDate(effectiveDate)
                .expirationDate(expirationDate)
                .build();

        ContactPlan saved = contactPlanRepository.save(contactPlan);
        log.info("Added plan {} to contact {} with ID {}", planId, contactId, saved.getId());

        return saved;
    }

    @Transactional
    public ContactPlan updateContactPlan(Long contactPlanId, Long planId, OffsetDateTime effectiveDate, OffsetDateTime expirationDate) {
        log.info("Updating contact plan {} with plan {} and dates {} - {}", contactPlanId, planId, effectiveDate, expirationDate);

        validateDates(effectiveDate, expirationDate);

        ContactPlan contactPlan = contactPlanRepository.findById(contactPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Contact plan not found: " + contactPlanId));

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        // Check for overlapping plans (excluding this one)
        List<ContactPlan> overlapping = contactPlanRepository.findOverlappingPlans(
                contactPlan.getContact().getId(), contactPlanId, effectiveDate, expirationDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException(
                    "Contact already has a plan during this date range. Overlapping plan IDs: " +
                            overlapping.stream().map(mp -> mp.getId().toString()).toList());
        }

        contactPlan.setPlan(plan);
        contactPlan.setEffectiveDate(effectiveDate);
        contactPlan.setExpirationDate(expirationDate);

        ContactPlan saved = contactPlanRepository.save(contactPlan);
        log.info("Updated contact plan {}", contactPlanId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ContactPlan> getContactPlans(Long contactId) {
        log.debug("Getting plans for contact {}", contactId);
        return contactPlanRepository.findByContactIdWithPlan(contactId);
    }

    @Transactional(readOnly = true)
    public Optional<ContactPlan> getCurrentContactPlan(Long contactId) {
        log.debug("Getting current plan for contact {}", contactId);
        return contactPlanRepository.findCurrentPlan(contactId, OffsetDateTime.now());
    }

    @Transactional
    public void removeContactPlan(Long contactId, Long contactPlanId) {
        log.info("Removing contact plan {} from contact {}", contactPlanId, contactId);

        ContactPlan contactPlan = contactPlanRepository.findById(contactPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Contact plan not found: " + contactPlanId));

        if (!contactPlan.getContact().getId().equals(contactId)) {
            throw new IllegalArgumentException("Contact plan " + contactPlanId + " does not belong to contact " + contactId);
        }

        contactPlanRepository.delete(contactPlan);
        log.info("Removed contact plan {} from contact {}", contactPlanId, contactId);
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
