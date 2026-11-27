package com.verifi.portal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "portal_users")
public class PortalUser {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PortalRole role;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PortalUser() {}

    public PortalUser(UUID id, String email, String displayName, UUID organizationId,
                      PortalRole role, String passwordHash) {
        this.id = id;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.organizationId = organizationId;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    String passwordHash() { return passwordHash; }
    boolean enabled() { return enabled; }

    CurrentActor actor() {
        return new CurrentActor(id, email, displayName, organizationId, role);
    }
}
