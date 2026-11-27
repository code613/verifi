package com.verifi.portal.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "screening_applicants")
class ScreeningApplicant {
    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

    @Column(name = "first_name", nullable = false, length = 100)
    String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    String lastName;

    @Column(nullable = false, length = 254)
    String email;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    protected ScreeningApplicant() {
    }

    ScreeningApplicant(UUID organizationId, UUID actorId, String firstName, String lastName, String email) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.createdBy = actorId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
