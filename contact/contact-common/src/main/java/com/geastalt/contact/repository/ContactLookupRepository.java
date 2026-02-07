package com.geastalt.contact.repository;

import com.geastalt.contact.entity.ContactLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactLookupRepository extends JpaRepository<ContactLookup, Long> {
}
