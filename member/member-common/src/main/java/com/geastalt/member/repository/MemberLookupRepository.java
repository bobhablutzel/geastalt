package com.geastalt.member.repository;

import com.geastalt.member.entity.MemberLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberLookupRepository extends JpaRepository<MemberLookup, Long> {
}
