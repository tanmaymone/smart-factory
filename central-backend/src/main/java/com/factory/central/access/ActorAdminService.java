package com.factory.central.access;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActorAdminService {

    private final ActorRepository actorRepository;

    public ActorAdminService(ActorRepository actorRepository) {
        this.actorRepository = actorRepository;
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

        return actorRepository
                .findById(actorId)
                .map(existing -> {
                    existing.setDisplayName(displayName);
                    existing.setRole(role);
                    return new ActorUpsertResult(false, ActorResponse.from(actorRepository.save(existing)));
                })
                .orElseGet(() -> {
                    ActorEntity created = actorRepository.save(new ActorEntity(actorId, displayName, role));
                    return new ActorUpsertResult(true, ActorResponse.from(created));
                });
    }

    private static ActorRole parseAssignableRole(String roleRaw) {
        if (roleRaw.isEmpty()) {
            throw new AccessException(
                    AccessErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    HttpStatus.BAD_REQUEST,
                    List.of(new AccessErrorResponse.FieldErrorDetail("role", "must not be blank")));
        }
        String normalized = roleRaw.toUpperCase(Locale.ROOT);
        if ("VIEWER".equals(normalized)) {
            return ActorRole.VIEWER;
        }
        if ("OPERATOR".equals(normalized)) {
            return ActorRole.OPERATOR;
        }
        if ("SUPERVISOR".equals(normalized)) {
            return ActorRole.SUPERVISOR;
        }
        throw new AccessException(
                AccessErrorCode.ROLE_NOT_ASSIGNABLE,
                "role must be VIEWER, OPERATOR, or SUPERVISOR",
                HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new AccessErrorResponse.FieldErrorDetail(
                        "role", "role must be VIEWER, OPERATOR, or SUPERVISOR")));
    }

    public record ActorUpsertResult(boolean created, ActorResponse body) {}
}
