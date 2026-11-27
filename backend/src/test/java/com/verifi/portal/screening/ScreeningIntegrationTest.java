package com.verifi.portal.screening;

import com.verifi.portal.identity.CurrentActor;
import com.verifi.portal.identity.PortalRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static com.verifi.portal.screening.ScreeningDtos.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"VERIFI_DEMO_ENABLED=false"})
@Testcontainers
class ScreeningIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired ScreeningService service;
    @Autowired ScreeningPackageRepository packages;
    @Autowired PackageAssignmentRepository assignments;
    @Autowired ScreeningOrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    CurrentActor actor;
    CurrentActor otherActor;
    PackageAssignment employment;
    PackageAssignment tenant;
    PackageAssignment otherEmployment;
    ApplicantView applicant;

    @BeforeEach
    void isolatedFixtures() {
        actor = identity("First Company");
        otherActor = identity("Other Company");
        var employmentPackage = packages.saveAndFlush(new ScreeningPackage(UUID.randomUUID(),
                "Synthetic Employment Package", "Test fixture; no provider", ScreeningPurpose.EMPLOYMENT));
        var tenantPackage = packages.saveAndFlush(new ScreeningPackage(UUID.randomUUID(),
                "Synthetic Tenant Package", "Test fixture; no provider", ScreeningPurpose.TENANT));
        employment = assignments.saveAndFlush(new PackageAssignment(actor.organizationId(), employmentPackage.id));
        tenant = assignments.saveAndFlush(new PackageAssignment(actor.organizationId(), tenantPackage.id));
        otherEmployment = assignments.saveAndFlush(new PackageAssignment(otherActor.organizationId(), employmentPackage.id));
        applicant = service.createApplicant(actor, new CreateApplicant("Synthetic", "Applicant", "person@example.invalid"));
    }

    @Test
    void createsOnlyLocallyQueuedOrdersWithExplicitApplicantBoundProvenance() {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var result = service.createOrder(actor, request(applicant.id(), employment.id, consent.id()));
        assertThat(result.replayed()).isFalse();
        assertThat(result.value().status()).isEqualTo("QUEUED");
        assertThat(result.value().consentId()).isEqualTo(consent.id());
        assertThat(result.value().createdBy()).isEqualTo(actor.id());
        assertThat(consent.applicantId()).isEqualTo(applicant.id());
        assertThat(consent.recordedBy()).isEqualTo(actor.id());
        assertThat(consent.recordedAt()).isNotNull();
        assertThat(consent.attestationText()).isEqualTo(service.consentPolicy().attestationText());
        assertThat(consent.attestationVersion()).isEqualTo(service.consentPolicy().version());
        assertThat(consent.attestationVersion()).hasSize(64);
        assertThat(consent.source()).isEqualTo("OPERATOR_ATTESTATION");
        assertThat(consent.evidenceType()).isEqualTo("REFERENCE_ONLY");
        assertThat(service.order(actor, result.value().id())).isEqualTo(result.value());
        assertThat(service.orders(actor)).extracting(OrderView::id).containsExactly(result.value().id());
        assertThat(service.packages(actor)).extracting(PackageView::id).containsExactlyInAnyOrder(employment.id, tenant.id);
    }

    @Test
    void rejectsConsentForAnotherApplicantWithinTheSameCompany() {
        var second = service.createApplicant(actor, new CreateApplicant("Another", "Person", "second@example.invalid"));
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        expectFailure(422, "CONSENT_MISMATCH",
                () -> service.createOrder(actor, request(second.id(), employment.id, consent.id())));
        assertThat(service.orders(actor)).isEmpty();
    }

    @Test
    void rejectsWrongPurposeAndUnavailablePackages() {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.TENANT);
        expectFailure(422, "CONSENT_MISMATCH",
                () -> service.createOrder(actor, request(applicant.id(), employment.id, consent.id())));
        tenant.active = false;
        assignments.saveAndFlush(tenant);
        expectFailure(422, "PACKAGE_UNAVAILABLE",
                () -> service.createOrder(actor, request(applicant.id(), tenant.id, consent.id())));
        assertThat(service.packages(actor)).extracting(PackageView::id).doesNotContain(tenant.id);
        assertThat(service.orders(actor)).isEmpty();
    }

    @Test
    void requiresExplicitAttestationAndNonemptyReference() {
        expectFailure(422, "ATTESTATION_REQUIRED", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "reference", false, service.consentPolicy().version())));
        expectFailure(422, "INVALID_CONSENT", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "  ", true, service.consentPolicy().version())));
        assertThat(service.consents(actor, applicant.id())).isEmpty();
    }

    @Test
    void concealsForeignApplicantAssignmentConsentAndOrders() {
        var otherApplicant = service.createApplicant(otherActor,
                new CreateApplicant("Other", "Applicant", "other@example.invalid"));
        var ownConsent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var otherConsent = consent(otherActor, otherApplicant.id(), ScreeningPurpose.EMPLOYMENT);
        expectFailure(404, "RESOURCE_NOT_FOUND",
                () -> service.createOrder(actor, request(otherApplicant.id(), employment.id, ownConsent.id())));
        expectFailure(404, "RESOURCE_NOT_FOUND",
                () -> service.createOrder(actor, request(applicant.id(), otherEmployment.id, ownConsent.id())));
        expectFailure(404, "RESOURCE_NOT_FOUND",
                () -> service.createOrder(actor, request(applicant.id(), employment.id, otherConsent.id())));
        expectFailure(404, "RESOURCE_NOT_FOUND", () -> service.recordConsent(actor, otherApplicant.id(),
                new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "reference", true, service.consentPolicy().version())));
        expectFailure(404, "RESOURCE_NOT_FOUND", () -> service.consents(actor, otherApplicant.id()));
        var order = service.createOrder(otherActor, request(otherApplicant.id(), otherEmployment.id, otherConsent.id())).value();
        expectFailure(404, "RESOURCE_NOT_FOUND", () -> service.order(actor, order.id()));
        expectFailure(404, "RESOURCE_NOT_FOUND", () -> service.order(actor, UUID.randomUUID()));
        assertThat(service.orders(actor)).isEmpty();
        assertThat(service.applicants(actor)).extracting(ApplicantView::id).containsExactly(applicant.id());
        assertThat(service.packages(actor)).extracting(PackageView::id).doesNotContain(otherEmployment.id);
    }

    @Test
    void exactOrderRetriesReturnOriginalEvenAfterAssignmentIsDisabled() {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var request = request(applicant.id(), employment.id, consent.id());
        var first = service.createOrder(actor, request);
        employment.active = false;
        assignments.saveAndFlush(employment);
        var retry = service.createOrder(actor, request);
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.value()).isEqualTo(first.value());
        assertThat(orders.countByOrganizationIdAndRequestId(actor.organizationId(), request.requestId())).isOne();
    }

    @Test
    void changedInputsCannotReuseAnOrderRequestId() {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var request = request(applicant.id(), employment.id, consent.id());
        service.createOrder(actor, request);
        var anotherConsent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        expectFailure(409, "REQUEST_CONFLICT", () -> service.createOrder(actor,
                new CreateOrder(request.requestId(), applicant.id(), employment.id, anotherConsent.id())));
        assertThat(orders.countByOrganizationIdAndRequestId(actor.organizationId(), request.requestId())).isOne();
    }

    @Test
    void concurrentOrderRetriesPersistExactlyOneOrder() throws Exception {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var request = request(applicant.id(), employment.id, consent.id());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> { start.await(); return service.createOrder(actor, request); });
            var two = executor.submit(() -> { start.await(); return service.createOrder(actor, request); });
            start.countDown();
            var first = one.get(15, TimeUnit.SECONDS);
            var second = two.get(15, TimeUnit.SECONDS);
            assertThat(first.value().id()).isEqualTo(second.value().id());
            assertThat(first.replayed()).isNotEqualTo(second.replayed());
        }
        assertThat(orders.countByOrganizationIdAndRequestId(actor.organizationId(), request.requestId())).isOne();
    }

    @Test
    void requestIdsAreScopedToCompany() {
        var sharedRequestId = UUID.randomUUID();
        var ownConsent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var otherApplicant = service.createApplicant(otherActor, new CreateApplicant("Other", "Person", "other@example.invalid"));
        var otherConsent = consent(otherActor, otherApplicant.id(), ScreeningPurpose.EMPLOYMENT);
        var one = service.createOrder(actor, new CreateOrder(sharedRequestId, applicant.id(), employment.id, ownConsent.id()));
        var two = service.createOrder(otherActor, new CreateOrder(sharedRequestId, otherApplicant.id(), otherEmployment.id, otherConsent.id()));
        assertThat(one.value().id()).isNotEqualTo(two.value().id());
        assertThat(service.orders(actor)).hasSize(1);
        assertThat(service.orders(otherActor)).hasSize(1);
    }

    @Test
    void consentRetriesPreserveOriginalActorAndEvidence() {
        var request = new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "synthetic-reference", true, service.consentPolicy().version());
        var first = service.recordConsent(actor, applicant.id(), request);
        var retry = service.recordConsent(actor, applicant.id(), request);
        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.value()).isEqualTo(first.value());
        expectFailure(409, "REQUEST_CONFLICT", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(request.requestId(), ScreeningPurpose.EMPLOYMENT, "different-reference", true, service.consentPolicy().version())));
        assertThat(service.consents(actor, applicant.id())).hasSize(1);
    }

    @Test
    void databaseAlsoRejectsConsentForAnotherApplicant() {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var second = service.createApplicant(actor, new CreateApplicant("Second", "Applicant", "second@example.invalid"));
        assertThatThrownBy(() -> jdbc.update("""
                insert into screening_orders(id,organization_id,request_id,applicant_id,applicant_name,
                  package_assignment_id,package_name,consent_id,status,created_at,created_by)
                values (?,?,?,?,?,?,?,?,'QUEUED',now(),?)
                """, UUID.randomUUID(), actor.organizationId(), UUID.randomUUID(), second.id(), "Second Applicant",
                employment.id, "Synthetic Package", consent.id(), actor.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.orders(actor)).isEmpty();
    }

    @Test
    void localDemoAssignmentsOnlyReachConfiguredAccountCompanyAndAreRepeatable() {
        var environment = new MockEnvironment().withProperty("VERIFI_DEMO_EMAIL", "  " + actor.email().toUpperCase(java.util.Locale.ROOT) + "  ");
        var seed = new LocalScreeningSeed(packages, assignments, jdbc, environment);
        var args = new DefaultApplicationArguments();
        seed.run(args);
        seed.run(args);
        var demoEmploymentId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        var demoTenantId = UUID.fromString("20000000-0000-4000-8000-000000000002");
        assertThat(service.packages(actor)).extracting(PackageView::packageId)
                .contains(demoEmploymentId, demoTenantId).hasSize(4);
        assertThat(service.packages(otherActor)).extracting(PackageView::packageId)
                .doesNotContain(demoEmploymentId, demoTenantId).hasSize(1);
    }
    @Test
    void databaseRejectsAttributingScreeningRecordsToAnotherCompanyUser() {
        var consent = consent(actor, applicant.id(), ScreeningPurpose.EMPLOYMENT);
        var order = service.createOrder(actor, request(applicant.id(), employment.id, consent.id())).value();
        assertThatThrownBy(() -> jdbc.update("update screening_applicants set created_by = ? where id = ?",
                otherActor.id(), applicant.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update screening_consents set recorded_by = ? where id = ?",
                otherActor.id(), consent.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update screening_orders set created_by = ? where id = ?",
                otherActor.id(), order.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select created_by from screening_applicants where id = ?",
                UUID.class, applicant.id())).isEqualTo(actor.id());
        assertThat(jdbc.queryForObject("select recorded_by from screening_consents where id = ?",
                UUID.class, consent.id())).isEqualTo(actor.id());
        assertThat(jdbc.queryForObject("select created_by from screening_orders where id = ?",
                UUID.class, order.id())).isEqualTo(actor.id());
    }
    @Test
    void rejectsNewConsentWithoutTheDisplayedCurrentPolicyRevision() {
        expectFailure(422, "ATTESTATION_CHANGED", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "reference", true, null)));
        expectFailure(422, "ATTESTATION_CHANGED", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "reference", true, "stale-policy")));
        assertThat(service.consents(actor, applicant.id())).isEmpty();
    }

    @Test
    void retriesHistoricalConsentAgainstSavedWordingButRejectsNewWritesWithOldRevision() {
        var requestId = UUID.randomUUID();
        var currentPolicy = service.consentPolicy();
        var first = service.recordConsent(actor, applicant.id(), new CreateConsent(requestId,
                ScreeningPurpose.EMPLOYMENT, "reference", true, currentPolicy.version())).value();
        // Fixture represents a consent saved before a future deployment changed the active statement.
        jdbc.update("update screening_consents set attestation_text = ? where id = ?",
                "Synthetic earlier authorization statement.", first.id());
        var historical = service.consents(actor, applicant.id()).getFirst();
        assertThat(historical.attestationVersion()).isNotEqualTo(currentPolicy.version());
        var retry = service.recordConsent(actor, applicant.id(), new CreateConsent(requestId,
                ScreeningPurpose.EMPLOYMENT, "reference", true, historical.attestationVersion()));
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.value()).isEqualTo(historical);
        expectFailure(422, "ATTESTATION_CHANGED", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(UUID.randomUUID(), ScreeningPurpose.EMPLOYMENT, "reference", true,
                        historical.attestationVersion())));
        expectFailure(409, "REQUEST_CONFLICT", () -> service.recordConsent(actor, applicant.id(),
                new CreateConsent(requestId, ScreeningPurpose.EMPLOYMENT, "reference", true, currentPolicy.version())));
        assertThat(service.consents(actor, applicant.id())).hasSize(1);
    }
    private CurrentActor identity(String name) {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = userId + "@example.invalid";
        jdbc.update("insert into organizations(id,name) values (?,?)", organizationId, name);
        jdbc.update("""
                insert into portal_users(id,organization_id,email,display_name,role,password_hash)
                values (?,?,?,?,'CLIENT_ADMIN','unused-test-hash')
                """, userId, organizationId, email, "Synthetic Operator");
        return new CurrentActor(userId, email, "Synthetic Operator", organizationId, PortalRole.CLIENT_ADMIN);
    }

    private ConsentView consent(CurrentActor operator, UUID applicantId, ScreeningPurpose purpose) {
        return service.recordConsent(operator, applicantId,
                new CreateConsent(UUID.randomUUID(), purpose, "synthetic-reference", true, service.consentPolicy().version())).value();
    }

    private CreateOrder request(UUID applicantId, UUID assignmentId, UUID consentId) {
        return new CreateOrder(UUID.randomUUID(), applicantId, assignmentId, consentId);
    }

    private void expectFailure(int status, String code, ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ScreeningException.class, failure -> {
            assertThat(failure.status).isEqualTo(status);
            assertThat(failure.code).isEqualTo(code);
        });
    }
}

