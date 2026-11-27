package com.verifi.portal.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "screening_consents")
class ScreeningConsent {
    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

    @Column(name = "request_id", nullable = false)
    UUID requestId;

    @Column(name = "applicant_id", nullable = false)
    UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ScreeningPurpose purpose;

    @Column(name = "authorization_reference", nullable = false, length = 512)
    String authorizationReference;

    @Column(name = "attestation_text", nullable = false, length = 1000)
    String attestationText;

    @Column(name = "evidence_type", nullable = false, length = 30)
    String evidenceType;

    @Column(nullable = false, length = 30)
    String source;

    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    @Column(name = "recorded_by", nullable = false)
    UUID recordedBy;

    protected ScreeningConsent() {
    }

    ScreeningConsent(UUID organizationId, UUID requestId, UUID applicantId, ScreeningPurpose purpose,
            String reference, String attestation, UUID actorId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.requestId = requestId;
        this.applicantId = applicantId;
        this.purpose = purpose;
        this.authorizationReference = reference;
        this.attestationText = attestation;
        this.recordedBy = actorId;
        this.recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.evidenceType = "REFERENCE_ONLY";
        this.source = "OPERATOR_ATTESTATION";
    }
}
