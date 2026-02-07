package com.geastalt.contact.repository;

import com.geastalt.contact.entity.AddressType;
import com.geastalt.contact.entity.ContactAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactAddressRepository extends JpaRepository<ContactAddress, Long> {

    List<ContactAddress> findByContactId(Long contactId);

    @Query("SELECT ma FROM ContactAddress ma JOIN FETCH ma.address WHERE ma.contact.id = :contactId")
    List<ContactAddress> findByContactIdWithAddress(@Param("contactId") Long contactId);

    Optional<ContactAddress> findByContactIdAndAddressType(Long contactId, AddressType addressType);

    void deleteByContactIdAndAddressType(Long contactId, AddressType addressType);

    boolean existsByContactId(Long contactId);
}
