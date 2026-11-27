package com.verifi.portal.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PortalUserRepository extends JpaRepository<PortalUser, UUID> {
    Optional<PortalUser> findByEmail(String email);
}
