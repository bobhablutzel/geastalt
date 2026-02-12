/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.service;

import com.geastalt.contact.entity.PendingActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionEventPublisher {

    private final KafkaTemplate<Long, String> kafkaTemplate;

    @Value("${contact.pending-actions.topics.generate-external-identifiers}")
    private String generateExternalIdentifiersTopic;

    public void publishPendingAction(Long contactId, PendingActionType actionType) {
        String topic = getTopicForActionType(actionType);
        String payload = String.valueOf(contactId);

        log.info("Publishing pending action to Kafka: topic={}, contactId={}", topic, contactId);

        kafkaTemplate.send(topic, contactId, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish pending action: topic={}, contactId={}", topic, contactId, ex);
                    } else {
                        log.debug("Successfully published pending action: topic={}, contactId={}, offset={}",
                                topic, contactId, result.getRecordMetadata().offset());
                    }
                });
    }

    private String getTopicForActionType(PendingActionType actionType) {
        return switch (actionType) {
            case GENERATE_EXTERNAL_IDENTIFIERS -> generateExternalIdentifiersTopic;
        };
    }
}
