package com.geastalt.contact.repository;

import com.geastalt.contact.entity.ContactAlternateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactAlternateIdRepository extends JpaRepository<ContactAlternateId, Long> {

    /**
     * Find an alternate ID entry by type and alternate ID value.
     */
    Optional<ContactAlternateId> findByIdTypeAndAlternateId(String idType, String alternateId);

    /**
     * Find an alternate ID entry by contact ID and type.
     */
    @Query("SELECT mai FROM ContactAlternateId mai WHERE mai.contactLookup.contactId = :contactId AND mai.idType = :idType")
    Optional<ContactAlternateId> findByContactIdAndIdType(@Param("contactId") Long contactId, @Param("idType") String idType);

    /**
     * Find all alternate IDs for a contact.
     */
    @Query("SELECT mai FROM ContactAlternateId mai WHERE mai.contactLookup.contactId = :contactId")
    List<ContactAlternateId> findAllByContactId(@Param("contactId") Long contactId);

    /**
     * Check if an alternate ID exists for the given type and value.
     */
    boolean existsByIdTypeAndAlternateId(String idType, String alternateId);
}
