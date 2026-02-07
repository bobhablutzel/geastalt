package com.geastalt.contact.repository;

import com.geastalt.contact.entity.ContactPendingAction;
import com.geastalt.contact.entity.PendingActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactPendingActionRepository extends JpaRepository<ContactPendingAction, Long> {

    /**
     * Check if a pending action exists for the given contact and action type.
     */
    boolean existsByContactIdAndActionType(Long contactId, PendingActionType actionType);

    /**
     * Find a pending action by contact ID and action type.
     */
    Optional<ContactPendingAction> findByContactIdAndActionType(Long contactId, PendingActionType actionType);

    /**
     * Find all pending actions for a contact.
     */
    List<ContactPendingAction> findAllByContactId(Long contactId);

    /**
     * Delete a pending action by contact ID and action type.
     */
    void deleteByContactIdAndActionType(Long contactId, PendingActionType actionType);
}
