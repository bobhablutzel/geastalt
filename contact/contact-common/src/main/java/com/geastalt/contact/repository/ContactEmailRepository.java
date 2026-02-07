package com.geastalt.contact.repository;

import com.geastalt.contact.entity.AddressType;
import com.geastalt.contact.entity.ContactEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactEmailRepository extends JpaRepository<ContactEmail, Long> {

    List<ContactEmail> findByContactId(Long contactId);

    Optional<ContactEmail> findByContactIdAndEmailType(Long contactId, AddressType emailType);

    boolean existsByContactId(Long contactId);
}
