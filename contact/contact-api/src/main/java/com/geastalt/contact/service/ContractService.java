/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.service;

import com.geastalt.contact.entity.Contract;
import com.geastalt.contact.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;

    @Transactional
    public Contract createContract(String contractName, UUID companyId, String companyName) {
        log.info("Creating contract: {} for company: {} ({})", contractName, companyName, companyId);

        Contract contract = Contract.builder()
                .contractName(contractName)
                .companyId(companyId)
                .companyName(companyName)
                .build();

        Contract saved = contractRepository.save(contract);
        log.info("Created contract with ID: {}", saved.getId());

        return saved;
    }

    @Transactional
    public Contract updateContract(UUID contractId, String contractName, UUID companyId, String companyName) {
        log.info("Updating contract: {}", contractId);

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        contract.setContractName(contractName);
        contract.setCompanyId(companyId);
        contract.setCompanyName(companyName);

        Contract saved = contractRepository.save(contract);
        log.info("Updated contract: {}", contractId);

        return saved;
    }

    @Transactional(readOnly = true)
    public Contract getContract(UUID contractId) {
        log.debug("Getting contract: {}", contractId);
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));
    }

    @Transactional(readOnly = true)
    public List<Contract> getAllContracts() {
        log.debug("Getting all contracts");
        return contractRepository.findAll();
    }

    @Transactional
    public void deleteContract(UUID contractId) {
        log.info("Deleting contract: {}", contractId);

        if (!contractRepository.existsById(contractId)) {
            throw new IllegalArgumentException("Contract not found: " + contractId);
        }

        contractRepository.deleteById(contractId);
        log.info("Deleted contract: {}", contractId);
    }
}
