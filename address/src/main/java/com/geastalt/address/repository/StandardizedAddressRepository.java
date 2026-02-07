package com.geastalt.address.repository;

import com.geastalt.address.entity.StandardizedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StandardizedAddressRepository extends JpaRepository<StandardizedAddress, Long> {

    Optional<StandardizedAddress> findByStreetAddressAndSecondaryAddressAndCityAndStateAndZipCodeAndZipPlus4(
            String streetAddress,
            String secondaryAddress,
            String city,
            String state,
            String zipCode,
            String zipPlus4
    );
}
