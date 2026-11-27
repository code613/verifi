package com.verifi.portal.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;

    public SessionController(AuthenticationManager authenticationManager,
                             SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.sessions = sessions;
    }

    @GetMapping
    public SessionResponse session(@AuthenticationPrincipal CurrentActor actor, CsrfToken token) {
        SessionUser user = actor == null ? null : new SessionUser(
                actor.id(), actor.email(), actor.displayName(), actor.organizationId(), actor.role());
        return new SessionResponse(user, token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest login, HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            Authentication authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            login.email() == null ? "" : login.email(), login.password()));
            sessions.onAuthentication(authenticated, request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return ResponseEntity.noContent().build();
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(
                    new ErrorResponse("INVALID_CREDENTIALS", "Invalid email or password."));
        }
    }

    public record LoginRequest(String email, String password) {}
    public record SessionUser(UUID id, String email, String displayName, UUID organizationId, PortalRole role) {}
    public record SessionResponse(SessionUser user, String csrfToken) {}
    public record ErrorResponse(String code, String message) {}
}
