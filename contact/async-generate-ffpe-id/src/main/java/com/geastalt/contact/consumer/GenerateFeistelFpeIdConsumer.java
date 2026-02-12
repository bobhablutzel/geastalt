/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.consumer;

import com.geastalt.contact.service.GenerateFeistelFpeIdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateFeistelFpeIdConsumer {

    private final GenerateFeistelFpeIdService generateFeistelFpeIdService;

    @KafkaListener(
            topics = "${contact.pending-actions.topics.generate-external-identifiers}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String contactId, Acknowledgment acknowledgment) {
        log.info("Received generate external identifiers event for contact: {}", contactId);

        try {
            Long id = Long.parseLong(contactId);
            generateFeistelFpeIdService.processGenerateExternalIds(id);

            // Only acknowledge (commit offset) after successful transaction
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged generate external identifiers for contact: {}", contactId);
        } catch (NumberFormatException e) {
            log.error("Invalid contact ID format: {}, acknowledging to skip", contactId, e);
            // Acknowledge invalid messages to avoid infinite retry
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing generate external identifiers for contact: {}", contactId, e);
            // Don't acknowledge - message will be redelivered
            throw e;
        }
    }
}
