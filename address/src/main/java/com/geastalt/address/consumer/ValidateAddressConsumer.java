package com.geastalt.address.consumer;

import com.geastalt.address.service.ValidateAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateAddressConsumer {

    private final ValidateAddressService validateAddressService;

    @KafkaListener(
            topics = "${address.kafka.topics.validate-address}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String contactId, Acknowledgment acknowledgment) {
        log.info("Received validate address event for contact: {}", contactId);

        try {
            Long id = Long.parseLong(contactId);
            validateAddressService.processValidateAddress(id);

            // Only acknowledge (commit offset) after successful transaction
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged validate address for contact: {}", contactId);
        } catch (NumberFormatException e) {
            log.error("Invalid contact ID format: {}, acknowledging to skip", contactId, e);
            // Acknowledge invalid messages to avoid infinite retry
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing validate address for contact: {}", contactId, e);
            // Don't acknowledge - message will be redelivered
            throw e;
        }
    }
}
