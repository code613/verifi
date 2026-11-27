package com.verifi.portal.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "screening_packages")
class ScreeningPackage {
    @Id
    UUID id;

    @Column(nullable = false, length = 160)
    String name;

    @Column(nullable = false, length = 1000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ScreeningPurpose purpose;

    @Column(nullable = false)
    boolean active;

    protected ScreeningPackage() {
    }

    ScreeningPackage(UUID id, String name, String description, ScreeningPurpose purpose) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.purpose = purpose;
        this.active = true;
    }
}
