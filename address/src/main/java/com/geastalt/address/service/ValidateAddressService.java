package com.geastalt.address.service;

import com.geastalt.address.dto.usps.AddressRequest;
import com.geastalt.address.dto.usps.StandardizedAddressResult;
import com.geastalt.address.entity.Contact;
import com.geastalt.address.entity.ContactAddress;
import com.geastalt.address.entity.ContactPendingAction;
import com.geastalt.address.entity.PendingActionType;
import com.geastalt.address.entity.StandardizedAddress;
import com.geastalt.address.repository.ContactAddressRepository;
import com.geastalt.address.repository.ContactPendingActionRepository;
import com.geastalt.address.repository.ContactRepository;
import com.geastalt.address.repository.StandardizedAddressRepository;
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

    private final ContactRepository contactRepository;
    private final ContactAddressRepository contactAddressRepository;
    private final ContactPendingActionRepository contactPendingActionRepository;
    private final StandardizedAddressRepository standardizedAddressRepository;
    private final AddressStandardizationService addressStandardizationService;

    @Transactional
    public void processValidateAddress(Long contactId) {
        log.debug("Processing validate address for contact: {}", contactId);

        // Check if pending action exists
        Optional<ContactPendingAction> pendingAction = contactPendingActionRepository
                .findByContactIdAndActionType(contactId, PendingActionType.VALIDATE_ADDRESS);

        if (pendingAction.isEmpty()) {
            log.warn("No pending action found for contact: {}, may have already been processed", contactId);
            return;
        }

        // Check if contact exists
        Optional<Contact> contactOpt = contactRepository.findById(contactId);
        if (contactOpt.isEmpty()) {
            log.error("Contact not found: {}", contactId);
            return;
        }

        // Get all addresses for this contact
        List<ContactAddress> addresses = contactAddressRepository.findByContactId(contactId);

        if (addresses.isEmpty()) {
            log.info("No addresses found for contact: {}, marking pending action as complete", contactId);
        } else {
            // Validate and standardize each address
            for (ContactAddress contactAddress : addresses) {
                try {
                    validateAndStandardizeAddress(contactAddress);
                } catch (Exception e) {
                    log.error("Error validating address {} for contact {}: {}",
                            contactAddress.getId(), contactId, e.getMessage());
                    // Continue with other addresses even if one fails
                }
            }
        }

        // Remove the pending action
        contactPendingActionRepository.delete(pendingAction.get());
        log.debug("Removed pending action VALIDATE_ADDRESS for contact: {}", contactId);
    }

    private void validateAndStandardizeAddress(ContactAddress contactAddress) {
        StandardizedAddress currentAddress = contactAddress.getAddress();

        if (currentAddress == null) {
            log.warn("Address {} has no linked standardized address, skipping", contactAddress.getId());
            return;
        }

        log.info("Validating address {} for contact {}: {}, {}, {} {}",
                contactAddress.getId(),
                contactAddress.getContact().getId(),
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
                    contactAddress.getId(), currentAddress.getId(), result.getId());

            // Get the new standardized address
            StandardizedAddress newAddress = standardizedAddressRepository.findById(result.getId())
                    .orElseThrow(() -> new RuntimeException("Standardized address not found: " + result.getId()));

            // Update the contact address to point to the USPS-standardized address
            contactAddress.setAddress(newAddress);
            contactAddressRepository.save(contactAddress);

            log.info("Updated contact address {} to use standardized address {}",
                    contactAddress.getId(), newAddress.getId());
        } else {
            log.info("Address {} is already standardized (address ID: {})",
                    contactAddress.getId(), currentAddress.getId());
        }
    }
}
