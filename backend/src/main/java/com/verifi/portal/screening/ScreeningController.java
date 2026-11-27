package com.verifi.portal.screening;

import com.verifi.portal.identity.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static com.verifi.portal.screening.ScreeningDtos.*;

@RestController
@RequestMapping("/api")
public class ScreeningController {
    private final ScreeningService service;
    public ScreeningController(ScreeningService service) { this.service = service; }

    @GetMapping("/consent-policy")
    public ConsentPolicyView consentPolicy() { return service.consentPolicy(); }
    @GetMapping("/packages")
    public List<PackageView> packages(@AuthenticationPrincipal CurrentActor actor) { return service.packages(actor); }

    @GetMapping("/applicants")
    public List<ApplicantView> applicants(@AuthenticationPrincipal CurrentActor actor) { return service.applicants(actor); }

    @PostMapping("/applicants")
    public ResponseEntity<ApplicantView> createApplicant(@AuthenticationPrincipal CurrentActor actor,
            @Valid @RequestBody CreateApplicant request) {
        return ResponseEntity.status(201).body(service.createApplicant(actor, request));
    }

    @GetMapping("/applicants/{id}/consents")
    public List<ConsentView> consents(@AuthenticationPrincipal CurrentActor actor, @PathVariable UUID id) {
        return service.consents(actor, id);
    }

    @PostMapping("/applicants/{id}/consents")
    public ResponseEntity<ConsentView> recordConsent(@AuthenticationPrincipal CurrentActor actor,
            @PathVariable UUID id, @Valid @RequestBody CreateConsent request) {
        var result = service.recordConsent(actor, id, request);
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(result.value());
    }

    @GetMapping("/orders")
    public List<OrderView> orders(@AuthenticationPrincipal CurrentActor actor) { return service.orders(actor); }

    @GetMapping("/orders/{id}")
    public OrderView order(@AuthenticationPrincipal CurrentActor actor, @PathVariable UUID id) {
        return service.order(actor, id);
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderView> createOrder(@AuthenticationPrincipal CurrentActor actor,
            @Valid @RequestBody CreateOrder request) {
        var result = service.createOrder(actor, request);
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(result.value());
    }
}

