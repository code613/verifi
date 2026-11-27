package com.verifi.portal.screening;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ScreeningPackageRepository extends JpaRepository<ScreeningPackage, UUID> {}
interface PackageAssignmentRepository extends JpaRepository<PackageAssignment, UUID> {
    List<PackageAssignment> findAllByOrganizationIdAndActiveTrueOrderById(UUID organizationId);
    Optional<PackageAssignment> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndPackageId(UUID organizationId, UUID packageId);
}
interface ScreeningApplicantRepository extends JpaRepository<ScreeningApplicant, UUID> {
    List<ScreeningApplicant> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId);
    Optional<ScreeningApplicant> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
interface ScreeningConsentRepository extends JpaRepository<ScreeningConsent, UUID> {
    Optional<ScreeningConsent> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<ScreeningConsent> findByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);
    List<ScreeningConsent> findAllByOrganizationIdAndApplicantIdOrderByRecordedAtDescIdDesc(UUID organizationId, UUID applicantId);
}
interface ScreeningOrderRepository extends JpaRepository<ScreeningOrder, UUID> {
    Optional<ScreeningOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<ScreeningOrder> findByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);
    List<ScreeningOrder> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId);
    long countByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);
}

