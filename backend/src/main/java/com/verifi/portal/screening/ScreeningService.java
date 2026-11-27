package com.verifi.portal.screening;

import com.verifi.portal.identity.CurrentActor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static com.verifi.portal.screening.ScreeningDtos.*;

@Service
@Transactional(readOnly = true)
public class ScreeningService {
    public static final String ATTESTATION =
            "I attest that I have obtained authorization from this applicant for the selected screening purpose.";
    private static final ConsentPolicyView CONSENT_POLICY = new ConsentPolicyView(attestationVersion(ATTESTATION), ATTESTATION);
    private final ScreeningPackageRepository packages;
    private final PackageAssignmentRepository assignments;
    private final ScreeningApplicantRepository applicants;
    private final ScreeningConsentRepository consents;
    private final ScreeningOrderRepository orders;
    private final EntityManager entityManager;

    public ScreeningService(ScreeningPackageRepository packages, PackageAssignmentRepository assignments,
            ScreeningApplicantRepository applicants, ScreeningConsentRepository consents,
            ScreeningOrderRepository orders, EntityManager entityManager) {
        this.packages = packages; this.assignments = assignments; this.applicants = applicants;
        this.consents = consents; this.orders = orders; this.entityManager = entityManager;
    }

    public ConsentPolicyView consentPolicy() {
        return CONSENT_POLICY;
    }

    public List<PackageView> packages(CurrentActor actor) {
        return assignments.findAllByOrganizationIdAndActiveTrueOrderById(actor.organizationId()).stream()
                .flatMap(assignment -> packages.findById(assignment.packageId)
                        .filter(screeningPackage -> screeningPackage.active)
                        .map(screeningPackage -> new PackageView(assignment.id, screeningPackage.id,
                                screeningPackage.name, screeningPackage.description, screeningPackage.purpose)).stream())
                .sorted(Comparator.comparing(PackageView::name).thenComparing(PackageView::id)).toList();
    }

    public List<ApplicantView> applicants(CurrentActor actor) {
        return applicants.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(actor.organizationId())
                .stream().map(ScreeningService::applicantView).toList();
    }

    @Transactional
    public ApplicantView createApplicant(CurrentActor actor, CreateApplicant request) {
        var applicant = new ScreeningApplicant(actor.organizationId(), actor.id(),
                request.firstName().trim(), request.lastName().trim(),
                request.email().trim().toLowerCase(Locale.ROOT));
        return applicantView(applicants.saveAndFlush(applicant));
    }

    public List<ConsentView> consents(CurrentActor actor, UUID applicantId) {
        requireApplicant(actor.organizationId(), applicantId);
        return consents.findAllByOrganizationIdAndApplicantIdOrderByRecordedAtDescIdDesc(actor.organizationId(), applicantId)
                .stream().map(ScreeningService::consentView).toList();
    }

    @Transactional
    public WriteResult<ConsentView> recordConsent(CurrentActor actor, UUID applicantId, CreateConsent request) {
        if (!Boolean.TRUE.equals(request.attested())) {
            throw new ScreeningException(422, "ATTESTATION_REQUIRED", "Explicit authorization attestation is required.");
        }
        if (request.purpose() == null || request.authorizationReference() == null
                || request.authorizationReference().isBlank() || request.authorizationReference().length() > 512) {
            throw new ScreeningException(422, "INVALID_CONSENT", "Select a screening purpose and provide an authorization reference.");
        }
        if (request.attestationVersion() == null || request.attestationVersion().isBlank()) throw attestationChanged();
        lockOrganization(actor.organizationId());
        requireApplicant(actor.organizationId(), applicantId);
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        String reference = request.authorizationReference().trim();
        var existing = consents.findByOrganizationIdAndRequestId(actor.organizationId(), requestId);
        if (existing.isPresent()) {
            var consent = existing.get();
            if (!consent.applicantId.equals(applicantId) || consent.purpose != request.purpose()
                    || !consent.authorizationReference.equals(reference)
                    || !attestationVersion(consent.attestationText).equals(request.attestationVersion())) throw ScreeningException.conflict();
            return new WriteResult<>(consentView(consent), true);
        }
        // Validate the displayed revision only for new writes. A saved retry must keep its historical statement.
        if (!CONSENT_POLICY.version().equals(request.attestationVersion())) throw attestationChanged();
        var consent = new ScreeningConsent(actor.organizationId(), requestId, applicantId,
                request.purpose(), reference, CONSENT_POLICY.attestationText(), actor.id());
        return new WriteResult<>(consentView(consents.saveAndFlush(consent)), false);
    }

    public List<OrderView> orders(CurrentActor actor) {
        return orders.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(actor.organizationId())
                .stream().map(ScreeningService::orderView).toList();
    }

    public OrderView order(CurrentActor actor, UUID orderId) {
        return orderView(orders.findByIdAndOrganizationId(orderId, actor.organizationId())
                .orElseThrow(ScreeningException::missing));
    }

    @Transactional
    public WriteResult<OrderView> createOrder(CurrentActor actor, CreateOrder request) {
        // A company-row lock serializes this small slice's writes. The unique company/request
        // constraint is a second guard. Check the saved request before revalidating mutable
        // package availability, so a response lost after commit can always be recovered.
        lockOrganization(actor.organizationId());
        var existing = orders.findByOrganizationIdAndRequestId(actor.organizationId(), request.requestId());
        if (existing.isPresent()) {
            var order = existing.get();
            if (!order.applicantId.equals(request.applicantId())
                    || !order.packageAssignmentId.equals(request.packageAssignmentId())
                    || !order.consentId.equals(request.consentId())) throw ScreeningException.conflict();
            return new WriteResult<>(orderView(order), true);
        }
        var applicant = requireApplicant(actor.organizationId(), request.applicantId());
        var assignment = assignments.findByIdAndOrganizationId(request.packageAssignmentId(), actor.organizationId())
                .orElseThrow(ScreeningException::missing);
        var screeningPackage = packages.findById(assignment.packageId).orElseThrow(ScreeningException::missing);
        if (!assignment.active || !screeningPackage.active) {
            throw new ScreeningException(422, "PACKAGE_UNAVAILABLE", "This screening package is no longer available to your organization.");
        }
        var consent = consents.findByIdAndOrganizationId(request.consentId(), actor.organizationId())
                .orElseThrow(ScreeningException::missing);
        if (!consent.applicantId.equals(applicant.id) || consent.purpose != screeningPackage.purpose
                || !"REFERENCE_ONLY".equals(consent.evidenceType) || !"OPERATOR_ATTESTATION".equals(consent.source)) {
            throw new ScreeningException(422, "CONSENT_MISMATCH", "Record authorization for this applicant and the selected screening purpose.");
        }
        var order = new ScreeningOrder(actor.organizationId(), actor.id(), request.requestId(),
                applicant, assignment, screeningPackage, consent);
        // QUEUED records local intent only. This slice has no provider dispatch or success assertion.
        return new WriteResult<>(orderView(orders.saveAndFlush(order)), false);
    }

    private static ScreeningException attestationChanged() {
        return new ScreeningException(422, "ATTESTATION_CHANGED",
                "The authorization statement has changed or was not loaded. Reload it and acknowledge it before continuing.");
    }

    private static String attestationVersion(String text) {
        try {
            // The stored text itself is the historical revision; existing consent records need no migration.
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable.", impossible);
        }
    }
    private ScreeningApplicant requireApplicant(UUID organizationId, UUID applicantId) {
        return applicants.findByIdAndOrganizationId(applicantId, organizationId)
                .orElseThrow(ScreeningException::missing);
    }

    private void lockOrganization(UUID organizationId) {
        var rows = entityManager.createNativeQuery("select id from organizations where id = :organizationId for update")
                .setParameter("organizationId", organizationId).getResultList();
        if (rows.isEmpty()) throw ScreeningException.missing();
    }

    private static ApplicantView applicantView(ScreeningApplicant applicant) {
        return new ApplicantView(applicant.id, applicant.firstName, applicant.lastName, applicant.email, applicant.createdAt);
    }
    private static ConsentView consentView(ScreeningConsent consent) {
        return new ConsentView(consent.id, consent.requestId, consent.applicantId, consent.purpose,
                consent.authorizationReference, consent.attestationText, attestationVersion(consent.attestationText), consent.evidenceType,
                consent.source, consent.recordedAt, consent.recordedBy);
    }
    private static OrderView orderView(ScreeningOrder order) {
        return new OrderView(order.id, order.requestId, order.applicantId, order.applicantName,
                order.packageAssignmentId, order.packageName, order.consentId, order.status, order.createdAt, order.createdBy);
    }
}

