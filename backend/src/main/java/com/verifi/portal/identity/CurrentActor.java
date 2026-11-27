package com.verifi.portal.identity;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** Immutable, credential-free identity stored in the authenticated session. */
public record CurrentActor(UUID id, String email, String displayName,
                           UUID organizationId, PortalRole role) implements Principal, Serializable {
    @Override
    public String getName() {
        return id.toString();
    }
}
