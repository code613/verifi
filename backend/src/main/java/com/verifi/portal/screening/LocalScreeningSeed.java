package com.verifi.portal.screening;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@Profile("local")
@Order(20)
@ConditionalOnProperty(name = "VERIFI_DEMO_ENABLED", havingValue = "true")
public class LocalScreeningSeed implements ApplicationRunner {
    private final ScreeningPackageRepository packages;
    private final PackageAssignmentRepository assignments;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    public LocalScreeningSeed(ScreeningPackageRepository packages, PackageAssignmentRepository assignments, JdbcTemplate jdbc, Environment environment) {
        this.packages = packages; this.assignments = assignments; this.jdbc = jdbc; this.environment = environment;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var localPackages = List.of(
                new ScreeningPackage(UUID.fromString("20000000-0000-4000-8000-000000000001"),
                        "Employment screening (local demo)",
                        "Demonstration package. Orders remain queued locally; no provider is connected.", ScreeningPurpose.EMPLOYMENT),
                new ScreeningPackage(UUID.fromString("20000000-0000-4000-8000-000000000002"),
                        "Tenant screening (local demo)",
                        "Demonstration package. Orders remain queued locally; no provider is connected.", ScreeningPurpose.TENANT));
        for (var screeningPackage : localPackages) {
            if (!packages.existsById(screeningPackage.id)) packages.saveAndFlush(screeningPackage);
        }
        // Identity seed runs first (Order 10). Both seeds require explicit local demo opt-in.
        String demoEmail = environment.getRequiredProperty("VERIFI_DEMO_EMAIL").trim().toLowerCase(Locale.ROOT);
        var organizations = jdbc.query("select organization_id from portal_users where email = ?",
                (row, number) -> row.getObject("organization_id", UUID.class), demoEmail);
        if (organizations.isEmpty()) {
            throw new IllegalStateException("The configured local demo account must exist before assigning screening packages.");
        }
        for (var organizationId : organizations) {
            for (var screeningPackage : localPackages) {
                if (!assignments.existsByOrganizationIdAndPackageId(organizationId, screeningPackage.id))
                    assignments.save(new PackageAssignment(organizationId, screeningPackage.id));
            }
        }
    }
}

