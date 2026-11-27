package com.verifi.portal.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organization_screening_packages")
class PackageAssignment {
    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

    @Column(name = "package_id", nullable = false)
    UUID packageId;

    @Column(nullable = false)
    boolean active;

    protected PackageAssignment() {
    }

    PackageAssignment(UUID organizationId, UUID packageId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.packageId = packageId;
        this.active = true;
    }
}
