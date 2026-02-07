package com.geastalt.contact.service;

import org.springframework.stereotype.Service;

@Service
public class DefaultPartitionAssignmentService implements PartitionAssignmentService {

    @Override
    public int assignPartition(ContactPartitionContext context) {
        if ("humana".equalsIgnoreCase(context.carrierName())) {
            return 1;
        }
        return 2;
    }
}
