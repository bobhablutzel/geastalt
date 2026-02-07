package com.geastalt.contact.repository;

import com.geastalt.contact.entity.AddressType;
import com.geastalt.contact.entity.ContactPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactPhoneRepository extends JpaRepository<ContactPhone, Long> {

    List<ContactPhone> findByContactId(Long contactId);

    Optional<ContactPhone> findByContactIdAndPhoneType(Long contactId, AddressType phoneType);
}
