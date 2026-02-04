package com.geastalt.member.service;

import com.geastalt.member.entity.PendingActionType;
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

    @Value("${member.pending-actions.topics.generate-external-identifiers}")
    private String generateExternalIdentifiersTopic;

    @Value("${member.pending-actions.topics.validate-address}")
    private String validateAddressTopic;

    public void publishPendingAction(Long memberId, PendingActionType actionType) {
        String topic = getTopicForActionType(actionType);
        String payload = String.valueOf(memberId);

        log.info("Publishing pending action to Kafka: topic={}, memberId={}", topic, memberId);

        kafkaTemplate.send(topic, memberId, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish pending action: topic={}, memberId={}", topic, memberId, ex);
                    } else {
                        log.debug("Successfully published pending action: topic={}, memberId={}, offset={}",
                                topic, memberId, result.getRecordMetadata().offset());
                    }
                });
    }

    private String getTopicForActionType(PendingActionType actionType) {
        return switch (actionType) {
            case GENERATE_EXTERNAL_IDENTIFIERS -> generateExternalIdentifiersTopic;
            case VALIDATE_ADDRESS -> validateAddressTopic;
        };
    }
}
