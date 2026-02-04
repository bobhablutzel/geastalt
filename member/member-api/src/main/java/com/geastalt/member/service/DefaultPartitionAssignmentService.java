package com.geastalt.member.service;

import org.springframework.stereotype.Service;

@Service
public class DefaultPartitionAssignmentService implements PartitionAssignmentService {

    @Override
    public int assignPartition(MemberPartitionContext context) {
        if ("humana".equalsIgnoreCase(context.carrierName())) {
            return 1;
        }
        return 2;
    }
}
