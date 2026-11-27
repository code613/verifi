package com.verifi.portal.screening;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ScreeningDtos {
    private ScreeningDtos() {}
    public record PackageView(UUID id, UUID packageId, String name, String description, ScreeningPurpose purpose) {}
    public record CreateApplicant(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email) {}
    public record ApplicantView(UUID id, String firstName, String lastName, String email, Instant createdAt) {}
    public record ConsentPolicyView(String version, String attestationText) {}
    public record CreateConsent(
            UUID requestId,
            @NotNull ScreeningPurpose purpose,
            @NotBlank @Size(max = 512) String authorizationReference,
            @NotNull @AssertTrue Boolean attested, String attestationVersion) {}
    public record ConsentView(UUID id, UUID requestId, UUID applicantId, ScreeningPurpose purpose,
            String authorizationReference, String attestationText, String attestationVersion, String evidenceType, String source,
            Instant recordedAt, UUID recordedBy) {}
    public record CreateOrder(@NotNull UUID requestId, @NotNull UUID applicantId,
            @NotNull UUID packageAssignmentId, @NotNull UUID consentId) {}
    public record OrderView(UUID id, UUID requestId, UUID applicantId, String applicantName,
            UUID packageAssignmentId, String packageName, UUID consentId, String status,
            Instant createdAt, UUID createdBy) {}
    public record WriteResult<T>(T value, boolean replayed) {}
    public record ApiError(String code, String message) {}
}

