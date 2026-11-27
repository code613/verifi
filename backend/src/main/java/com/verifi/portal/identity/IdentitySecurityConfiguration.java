package com.verifi.portal.identity;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class IdentitySecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(PortalAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        // Controller authentication must explicitly rotate the session and clear the old CSRF token.
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
                                            CsrfTokenRepository csrf) throws Exception {
        http
            .securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/session", "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/session/login").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, ex) ->
                            error(response, 401, "UNAUTHENTICATED", "Sign in to continue."))
                    .accessDeniedHandler((request, response, ex) -> {
                        if (ex instanceof CsrfException) {
                            error(response, 403, "CSRF_INVALID", "Refresh the session and try again.");
                        } else {
                            error(response, 403, "FORBIDDEN", "This action is not permitted.");
                        }
                    }))
            .logout(logout -> logout.logoutUrl("/api/session/logout")
                    .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // All arguments are fixed internal strings, never exception messages or user input.
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
