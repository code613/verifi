package com.verifi.portal.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "screening_orders")
class ScreeningOrder {
    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

    @Column(name = "request_id", nullable = false)
    UUID requestId;

    @Column(name = "applicant_id", nullable = false)
    UUID applicantId;

    @Column(name = "applicant_name", nullable = false, length = 201)
    String applicantName;

    @Column(name = "package_assignment_id", nullable = false)
    UUID packageAssignmentId;

    @Column(name = "package_name", nullable = false, length = 160)
    String packageName;

    @Column(name = "consent_id", nullable = false)
    UUID consentId;

    @Column(nullable = false, length = 20)
    String status;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    protected ScreeningOrder() {
    }

    ScreeningOrder(UUID organizationId, UUID actorId, UUID requestId, ScreeningApplicant applicant,
            PackageAssignment assignment, ScreeningPackage screeningPackage, ScreeningConsent consent) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.createdBy = actorId;
        this.requestId = requestId;
        this.applicantId = applicant.id;
        this.applicantName = applicant.firstName + " " + applicant.lastName;
        this.packageAssignmentId = assignment.id;
        this.packageName = screeningPackage.name;
        this.consentId = consent.id;
        this.status = "QUEUED";
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
