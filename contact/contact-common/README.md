# contact-common

Shared library module containing entities, repositories, DTOs, and common services used across all Contact Service applications.

## Overview

This module is a non-executable library that provides the foundational data layer and shared business logic for the Contact Service ecosystem.

## Package Structure

```
com.geastalt.contact/
├── entity/              # JPA entities
├── repository/          # Spring Data JPA repositories
├── dto/                 # Data Transfer Objects
├── service/             # Shared services
├── config/              # Shared configuration
└── crypto/              # Encryption utilities
```

## Entities

```mermaid
erDiagram
    Contact ||--o{ ContactAlternateId : has
    Contact ||--o{ ContactPendingAction : has
    Contact ||--o{ ContactAddress : has
    Contact ||--o{ ContactEmail : has
    Contact ||--o{ ContactPhone : has
    Contact ||--o{ ContactContract : has
    ContactContract }o--|| Contract : references
    ContactAddress }o--|| StreetAddress : references
    StreetAddress ||--o{ AddressLine : has

    Contact {
        Long id PK
        String firstName
        String lastName
    }

    ContactAlternateId {
        Long id PK
        Long contactId FK
        String idType
        String alternateId
        Instant createdAt
    }

    ContactPendingAction {
        Long id PK
        Long contactId FK
        PendingActionType actionType
        Instant createdAt
    }

    ContactAddress {
        Long id PK
        Long contactId FK
        Long addressId FK
        AddressKind addressType
    }

    Contract {
        UUID id PK
        String contractName
        UUID companyId
        String companyName
    }

    ContactContract {
        Long id PK
        Long contactId FK
        UUID contractId FK
        OffsetDateTime effectiveDate
        OffsetDateTime expirationDate
    }

    StreetAddress {
        Long id PK
        String locality
        String administrativeArea
        String postalCode
        String countryCode
        String subLocality
        String sortingCode
        Boolean validated
    }

    AddressLine {
        Long id PK
        Long addressId FK
        Integer lineOrder
        String lineValue
    }
```

## Enums and Types

### Alternate ID Types (free-form string)
Alternate ID types are free-form strings validated as Java identifiers. Types starting with `_` or `$` are reserved for internal use. Well-known types include:

| Value | Description |
|-------|-------------|
| `NEW_NATIONS` | Current FPE-generated identifier (NH + 15 digits) — default type |
| `OLD_NATIONS` | Legacy Nations identifier |
| `PAN_HASH` | PAN hash identifier |
| `CONTACT_TUPLE` | Contact tuple identifier |

Custom types can be added without code changes (e.g., `SSN_HASH`, `PARTNER_ID`). See `AlternateIdTypeValidator` for validation rules.

### PendingActionType
| Value | Description |
|-------|-------------|
| `GENERATE_EXTERNAL_IDENTIFIERS` | Generate external identifiers for the contact |

### AddressKind
| Value | Description |
|-------|-------------|
| `HOME` | Home address |
| `BUSINESS` | Business address |
| `MAILING` | Mailing address |

## Services

### ExternalIdService
Generates format-preserving encrypted external identifiers for contacts.

### PendingActionEventPublisher
Publishes pending action events to Kafka topics.

## Repositories

| Repository | Entity | Key Methods |
|------------|--------|-------------|
| `ContactRepository` | Contact | `searchByName`, `searchByNamePrefix` |
| `ContactAlternateIdRepository` | ContactAlternateId | `findByIdTypeAndAlternateId`, `existsByIdTypeAndAlternateId` |
| `ContactPendingActionRepository` | ContactPendingAction | `existsByContactIdAndActionType`, `findByContactIdAndActionType` |
| `ContactAddressRepository` | ContactAddress | `findByContactId` |
| `ContactEmailRepository` | ContactEmail | `findByContactId` |
| `ContactPhoneRepository` | ContactPhone | `findByContactId` |
| `ContractRepository` | Contract | `findByCompanyId` |
| `ContactContractRepository` | ContactContract | `findByContactIdWithContract`, `findCurrentContract`, `findOverlappingContracts` |
| `AddressRepository` | StreetAddress | `findByLocalityAndAdministrativeAreaAndPostalCodeAndCountryCode` |
| `AddressLineRepository` | AddressLine | JPA default methods |
| `ContactSearchJdbcRepository` | - | JDBC-based search operations |

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

## Usage

Add as a dependency in other modules:

```xml
<dependency>
    <groupId>com.geastalt</groupId>
    <artifactId>contact-common</artifactId>
    <version>${project.version}</version>
</dependency>
```
