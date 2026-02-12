/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.contact.repository;

import com.geastalt.contact.entity.StreetAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<StreetAddress, Long> {

    List<StreetAddress> findByLocalityAndAdministrativeAreaAndPostalCodeAndCountryCode(
            String locality,
            String administrativeArea,
            String postalCode,
            String countryCode
    );
}
