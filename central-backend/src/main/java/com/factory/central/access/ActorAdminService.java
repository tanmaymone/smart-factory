package com.factory.central.access;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActorAdminService {

    private final ActorRepository actorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final Clock clock;

    public ActorAdminService(
            ActorRepository actorRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            Clock clock) {
        this.actorRepository = actorRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActorResponse> list() {
        return actorRepository.findAll().stream().map(ActorResponse::from).toList();
    }

    @Transactional
    public ActorUpsertResult upsert(ActorRequest request) {
        String actorId = request.actorId() == null ? "" : request.actorId().trim();
        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        String roleRaw = request.role() == null ? "" : request.role().trim();
        String password = request.password();

        java.util.ArrayList<AccessErrorResponse.FieldErrorDetail> blanks = new java.util.ArrayList<>();
        if (actorId.isEmpty()) {
            blanks.add(new AccessErrorResponse.FieldErrorDetail("actorId", "must not be blank"));
        }
        if (displayName.isEmpty()) {
            blanks.add(new AccessErrorResponse.FieldErrorDetail("displayName", "must not be blank"));
        }
        if (roleRaw.isEmpty()) {
            blanks.add(new AccessErrorResponse.FieldErrorDetail("role", "must not be blank"));
        }
        if (!blanks.isEmpty()) {
            throw new AccessException(
                    AccessErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    HttpStatus.BAD_REQUEST,
                    List.copyOf(blanks));
        }

        ActorRole role = parseAssignableRole(roleRaw);
        boolean enabled = request.enabled() == null || request.enabled();

        return actorRepository
                .findById(actorId)
                .map(existing -> updateExisting(existing, displayName, role, password, enabled))
                .orElseGet(() -> createNew(actorId, displayName, role, password, enabled));
    }

    private ActorUpsertResult createNew(
            String actorId, String displayName, ActorRole role, String password, boolean enabled) {
        if (password == null || password.isBlank()) {
            throw new AccessException(
                    AccessErrorCode.PASSWORD_REQUIRED,
                    "password is required for new actors",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(new AccessErrorResponse.FieldErrorDetail(
                            "password", "password is required for new actors")));
        }
        if (password.length() < 8) {
            throw new AccessException(
                    AccessErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    HttpStatus.BAD_REQUEST,
                    List.of(new AccessErrorResponse.FieldErrorDetail("password", "size must be between 8 and 255")));
        }
        ActorEntity created = new ActorEntity(actorId, displayName, role);
        created.setPasswordHash(passwordEncoder.encode(password));
        created.setEnabled(enabled);
        return new ActorUpsertResult(true, ActorResponse.from(actorRepository.save(created)));
    }

    private ActorUpsertResult updateExisting(
            ActorEntity existing, String displayName, ActorRole role, String password, boolean enabled) {
        boolean wasEnabled = existing.isEnabled();
        existing.setDisplayName(displayName);
        existing.setRole(role);
        existing.setEnabled(enabled);
        existing.touchUpdatedAt(clock.instant());
        if (password != null && !password.isBlank()) {
            if (password.length() < 8) {
                throw new AccessException(
                        AccessErrorCode.VALIDATION_FAILED,
                        "Request validation failed",
                        HttpStatus.BAD_REQUEST,
                        List.of(new AccessErrorResponse.FieldErrorDetail("password", "size must be between 8 and 255")));
            }
            existing.setPasswordHash(passwordEncoder.encode(password));
            authService.revokeSessionsForActor(existing.getActorId());
        }
        if (wasEnabled && !enabled) {
            authService.revokeSessionsForActor(existing.getActorId());
        }
        return new ActorUpsertResult(false, ActorResponse.from(actorRepository.save(existing)));
    }

    private static ActorRole parseAssignableRole(String roleRaw) {
        String normalized = roleRaw.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VIEWER" -> ActorRole.VIEWER;
            case "OPERATOR" -> ActorRole.OPERATOR;
            case "SUPERVISOR" -> ActorRole.SUPERVISOR;
            case "ONBOARDING_ADMIN" -> ActorRole.ONBOARDING_ADMIN;
            default ->
                    throw new AccessException(
                            AccessErrorCode.ROLE_NOT_ASSIGNABLE,
                            "role must be VIEWER, OPERATOR, SUPERVISOR, or ONBOARDING_ADMIN",
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            List.of(new AccessErrorResponse.FieldErrorDetail(
                                    "role",
                                    "role must be VIEWER, OPERATOR, SUPERVISOR, or ONBOARDING_ADMIN")));
        };
    }

    public record ActorUpsertResult(boolean created, ActorResponse body) {}
}