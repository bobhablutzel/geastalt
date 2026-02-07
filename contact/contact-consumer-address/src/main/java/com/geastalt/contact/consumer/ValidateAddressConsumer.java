package com.geastalt.member.consumer;

import com.geastalt.member.service.ValidateAddressService;
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
            topics = "${member.pending-actions.topics.validate-address}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String memberId, Acknowledgment acknowledgment) {
        log.info("Received validate address event for member: {}", memberId);

        try {
            Long id = Long.parseLong(memberId);
            validateAddressService.processValidateAddress(id);

            // Only acknowledge (commit offset) after successful transaction
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged validate address for member: {}", memberId);
        } catch (NumberFormatException e) {
            log.error("Invalid member ID format: {}, acknowledging to skip", memberId, e);
            // Acknowledge invalid messages to avoid infinite retry
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing validate address for member: {}", memberId, e);
            // Don't acknowledge - message will be redelivered
            throw e;
        }
    }
}
