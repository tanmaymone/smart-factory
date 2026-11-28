package com.factory.central.access;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EdgeCredentialService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EdgeSiteCredentialRepository credentialRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public EdgeCredentialService(
            EdgeSiteCredentialRepository credentialRepository, JdbcTemplate jdbcTemplate, Clock clock) {
        this.credentialRepository = credentialRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EdgeCredentialSummaryResponse> list(String siteId) {
        String normalized = siteId == null ? "" : siteId.trim();
        if (normalized.isEmpty()) {
            throw new AccessException(
                    AccessErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    HttpStatus.BAD_REQUEST,
                    List.of(new AccessErrorResponse.FieldErrorDetail("siteId", "must not be blank")));
        }
        return credentialRepository.findBySiteIdOrderByCreatedAtDesc(normalized).stream()
                .map(EdgeCredentialSummaryResponse::from)
                .toList();
    }

    @Transactional
    public EdgeCredentialCreatedResponse create(EdgeCredentialCreateRequest request, String createdBy) {
        String siteId = request.siteId().trim();
        String label = request.label().trim();
        if (!siteExists(siteId)) {
            throw new AccessException(
                    AccessErrorCode.SITE_NOT_FOUND,
                    "Site not found: " + siteId,
                    HttpStatus.NOT_FOUND,
                    List.of());
        }
        String rawToken = newEdgeToken();
        String hash = TokenHashing.sha256Hex(rawToken);
        String prefix = rawToken.length() <= 16 ? rawToken : rawToken.substring(0, 16);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        credentialRepository.save(new EdgeSiteCredentialEntity(
                id, siteId, label, prefix, hash, createdBy, now));
        return new EdgeCredentialCreatedResponse(id, siteId, label, prefix, rawToken, now);
    }

    @Transactional
    public void revoke(UUID id) {
        EdgeSiteCredentialEntity entity = credentialRepository
                .findById(id)
                .orElseThrow(() -> new AccessException(
                        AccessErrorCode.CREDENTIAL_NOT_FOUND,
                        "Edge credential not found",
                        HttpStatus.NOT_FOUND,
                        List.of()));
        if (entity.getRevokedAt() == null) {
            entity.setRevokedAt(clock.instant());
            credentialRepository.save(entity);
        }
    }

    @Transactional
    public void seedBootstrapCredential(String siteId, String rawToken, String createdBy) {
        if (siteId == null
                || siteId.isBlank()
                || rawToken == null
                || rawToken.isBlank()
                || !siteExists(siteId)) {
            return;
        }
        String hash = TokenHashing.sha256Hex(rawToken);
        if (credentialRepository.findActiveByTokenHash(hash).isPresent()) {
            return;
        }
        String prefix = rawToken.length() <= 16 ? rawToken : rawToken.substring(0, 16);
        credentialRepository.save(new EdgeSiteCredentialEntity(
                UUID.randomUUID(),
                siteId.trim(),
                "bootstrap",
                prefix,
                hash,
                createdBy,
                clock.instant()));
    }

    private boolean siteExists(String siteId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sites WHERE id = ?", Integer.class, siteId);
        return count != null && count > 0;
    }

    private static String newEdgeToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sf_edge_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toLowerCase(Locale.ROOT);
    }
}