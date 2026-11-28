package com.factory.central.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(200)
public class AuthBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapRunner.class);

    private final AuthProperties authProperties;
    private final ActorRepository actorRepository;
    private final PasswordEncoder passwordEncoder;
    private final EdgeCredentialService edgeCredentialService;

    public AuthBootstrapRunner(
            AuthProperties authProperties,
            ActorRepository actorRepository,
            PasswordEncoder passwordEncoder,
            EdgeCredentialService edgeCredentialService) {
        this.authProperties = authProperties;
        this.actorRepository = actorRepository;
        this.passwordEncoder = passwordEncoder;
        this.edgeCredentialService = edgeCredentialService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedEdgeTokens();
    }

    private void seedAdmin() {
        AuthProperties.BootstrapAdmin admin = authProperties.getBootstrapAdmin();
        if (admin.getActorId().isEmpty() || admin.getPassword().isEmpty()) {
            return;
        }
        if (actorRepository.countByRole(ActorRole.ONBOARDING_ADMIN) > 0) {
            return;
        }
        if (actorRepository.findById(admin.getActorId()).isPresent()) {
            return;
        }
        ActorEntity entity = new ActorEntity(admin.getActorId(), admin.getDisplayName(), ActorRole.ONBOARDING_ADMIN);
        entity.setPasswordHash(passwordEncoder.encode(admin.getPassword()));
        entity.setEnabled(true);
        actorRepository.save(entity);
        log.info("Seeded bootstrap ONBOARDING_ADMIN actor {}", admin.getActorId());
    }

    private void seedEdgeTokens() {
        String raw = authProperties.getBootstrapEdgeTokens();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0 || colon >= trimmed.length() - 1) {
                continue;
            }
            String siteId = trimmed.substring(0, colon).trim();
            String token = trimmed.substring(colon + 1).trim();
            edgeCredentialService.seedBootstrapCredential(siteId, token, "bootstrap");
        }
    }
}