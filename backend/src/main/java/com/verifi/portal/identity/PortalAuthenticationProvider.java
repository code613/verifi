package com.verifi.portal.identity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PortalAuthenticationProvider implements AuthenticationProvider {
    private final PortalUserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public PortalAuthenticationProvider(PortalUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public Authentication authenticate(Authentication input) {
        String email = input.getName() == null ? "" : input.getName().trim().toLowerCase(Locale.ROOT);
        String password = input.getCredentials() instanceof String value ? value : "";
        if (email.length() > 254 || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        PortalUser user = users.findByEmail(email).orElse(null);
        // Perform an expensive password comparison for unknown accounts too.
        boolean matched = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (!matched || user == null || !user.enabled()) {
            throw invalidCredentials();
        }
        CurrentActor actor = user.actor();
        return UsernamePasswordAuthenticationToken.authenticated(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name())));
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid email or password.");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
