package com.geastalt.member.service;

import com.geastalt.member.dto.usps.AddressRequest;
import com.geastalt.member.dto.usps.StandardizedAddressResult;
import com.geastalt.member.entity.Member;
import com.geastalt.member.entity.MemberAddress;
import com.geastalt.member.entity.MemberPendingAction;
import com.geastalt.member.entity.PendingActionType;
import com.geastalt.member.entity.StandardizedAddress;
import com.geastalt.member.repository.MemberAddressRepository;
import com.geastalt.member.repository.MemberPendingActionRepository;
import com.geastalt.member.repository.MemberRepository;
import com.geastalt.member.repository.StandardizedAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateAddressService {

    private final MemberRepository memberRepository;
    private final MemberAddressRepository memberAddressRepository;
    private final MemberPendingActionRepository memberPendingActionRepository;
    private final StandardizedAddressRepository standardizedAddressRepository;
    private final AddressStandardizationService addressStandardizationService;

    @Transactional
    public void processValidateAddress(Long memberId) {
        log.debug("Processing validate address for member: {}", memberId);

        // Check if pending action exists
        Optional<MemberPendingAction> pendingAction = memberPendingActionRepository
                .findByMemberIdAndActionType(memberId, PendingActionType.VALIDATE_ADDRESS);

        if (pendingAction.isEmpty()) {
            log.warn("No pending action found for member: {}, may have already been processed", memberId);
            return;
        }

        // Check if member exists
        Optional<Member> memberOpt = memberRepository.findById(memberId);
        if (memberOpt.isEmpty()) {
            log.error("Member not found: {}", memberId);
            return;
        }

        // Get all addresses for this member
        List<MemberAddress> addresses = memberAddressRepository.findByMemberId(memberId);

        if (addresses.isEmpty()) {
            log.info("No addresses found for member: {}, marking pending action as complete", memberId);
        } else {
            // Validate and standardize each address
            for (MemberAddress memberAddress : addresses) {
                try {
                    validateAndStandardizeAddress(memberAddress);
                } catch (Exception e) {
                    log.error("Error validating address {} for member {}: {}",
                            memberAddress.getId(), memberId, e.getMessage());
                    // Continue with other addresses even if one fails
                }
            }
        }

        // Remove the pending action
        memberPendingActionRepository.delete(pendingAction.get());
        log.debug("Removed pending action VALIDATE_ADDRESS for member: {}", memberId);
    }

    private void validateAndStandardizeAddress(MemberAddress memberAddress) {
        StandardizedAddress currentAddress = memberAddress.getAddress();

        if (currentAddress == null) {
            log.warn("Address {} has no linked standardized address, skipping", memberAddress.getId());
            return;
        }

        log.info("Validating address {} for member {}: {}, {}, {} {}",
                memberAddress.getId(),
                memberAddress.getMember().getId(),
                currentAddress.getStreetAddress(),
                currentAddress.getCity(),
                currentAddress.getState(),
                currentAddress.getZipCode());

        // Build request from current address
        AddressRequest request = AddressRequest.builder()
                .streetAddress(currentAddress.getStreetAddress())
                .secondaryAddress(currentAddress.getSecondaryAddress())
                .city(currentAddress.getCity())
                .state(currentAddress.getState())
                .zipCode(currentAddress.getZipCode())
                .zipPlus4(currentAddress.getZipPlus4())
                .build();

        // Call USPS to standardize the address
        StandardizedAddressResult result = addressStandardizationService.standardizeAndSaveAddress(request);

        // Check if the standardized address is different from the current one
        if (!result.getId().equals(currentAddress.getId())) {
            log.info("Address {} standardized: updating from address {} to {}",
                    memberAddress.getId(), currentAddress.getId(), result.getId());

            // Get the new standardized address
            StandardizedAddress newAddress = standardizedAddressRepository.findById(result.getId())
                    .orElseThrow(() -> new RuntimeException("Standardized address not found: " + result.getId()));

            // Update the member address to point to the USPS-standardized address
            memberAddress.setAddress(newAddress);
            memberAddressRepository.save(memberAddress);

            log.info("Updated member address {} to use standardized address {}",
                    memberAddress.getId(), newAddress.getId());
        } else {
            log.info("Address {} is already standardized (address ID: {})",
                    memberAddress.getId(), currentAddress.getId());
        }
    }
}
