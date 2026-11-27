package com.verifi.portal.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in local data only. Never installs a default password or resets an existing account. */
@Component
@Profile("local")
@Order(10)
@ConditionalOnProperty(name = "VERIFI_DEMO_ENABLED", havingValue = "true")
public class LocalDemoIdentityProvisioner implements ApplicationRunner {
    private final OrganizationRepository organizations;
    private final PortalUserRepository users;
    private final PasswordEncoder passwords;
    private final Environment environment;

    public LocalDemoIdentityProvisioner(OrganizationRepository organizations, PortalUserRepository users,
                                        PasswordEncoder passwords, Environment environment) {
        this.organizations = organizations;
        this.users = users;
        this.passwords = passwords;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = required("VERIFI_DEMO_EMAIL").trim().toLowerCase(Locale.ROOT);
        String password = required("VERIFI_DEMO_PASSWORD");
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || passwordBytes < 12 || passwordBytes > 72) {
            throw new IllegalStateException("Local demo requires a valid email and a password of 12 to 72 UTF-8 bytes.");
        }
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        String organizationName = environment.getProperty("VERIFI_DEMO_ORGANIZATION_NAME", "Local Demo Company").trim();
        String displayName = environment.getProperty("VERIFI_DEMO_DISPLAY_NAME", "Local Demo Admin").trim();
        if (organizationName.isEmpty() || organizationName.length() > 160
                || displayName.isEmpty() || displayName.length() > 120) {
            throw new IllegalStateException("Local demo organization or display name has an invalid length.");
        }
        UUID organizationId = UUID.randomUUID();
        organizations.saveAndFlush(new Organization(organizationId, organizationName));
        users.save(new PortalUser(UUID.randomUUID(), email, displayName, organizationId,
                PortalRole.CLIENT_ADMIN, passwords.encode(password)));
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Explicit local demo configuration is missing: " + key);
        }
        return value;
    }
}
