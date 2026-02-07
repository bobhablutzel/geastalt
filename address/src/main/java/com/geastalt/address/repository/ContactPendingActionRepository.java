package com.geastalt.address.repository;

import com.geastalt.address.entity.ContactPendingAction;
import com.geastalt.address.entity.PendingActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContactPendingActionRepository extends JpaRepository<ContactPendingAction, Long> {

    Optional<ContactPendingAction> findByContactIdAndActionType(Long contactId, PendingActionType actionType);
}
