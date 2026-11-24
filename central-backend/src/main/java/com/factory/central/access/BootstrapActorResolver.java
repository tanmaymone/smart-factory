package com.factory.central.access;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BootstrapActorResolver implements ActorResolver {

    public static final String SETUP_TOKEN_HEADER = "X-Setup-Token";
    public static final String ACTOR_ID_HEADER = "X-Actor-Id";

    private final BootstrapProperties properties;
    private final ActorRepository actorRepository;

    public BootstrapActorResolver(BootstrapProperties properties, ActorRepository actorRepository) {
        this.properties = properties;
        this.actorRepository = actorRepository;
    }

    @Override
    public ActorResolution resolve(HttpServletRequest request) {
        String setupToken = request.getHeader(SETUP_TOKEN_HEADER);
        if (setupToken != null && !setupToken.isEmpty()) {
            if (tokenMatches(setupToken)) {
                return new ActorResolution.Resolved(new ActorContext(
                        properties.getActorId(), properties.getDisplayName(), ActorRole.ONBOARDING_ADMIN));
            }
            return new ActorResolution.Rejected();
        }

        String actorHeader = request.getHeader(ACTOR_ID_HEADER);
        String actorId = actorHeader == null ? "" : actorHeader.trim();
        if (actorId.isEmpty()) {
            return new ActorResolution.Anonymous();
        }

        Optional<ActorEntity> actor = actorRepository.findById(actorId);
        if (actor.isEmpty()) {
            return new ActorResolution.Rejected();
        }
        ActorEntity entity = actor.get();
        ActorRole role = entity.getRole();
        if (role == null || role == ActorRole.ONBOARDING_ADMIN) {
            return new ActorResolution.Rejected();
        }
        return new ActorResolution.Resolved(entity.toContext());
    }

    private boolean tokenMatches(String presented) {
        String configured = properties.getToken();
        if (configured == null || configured.isEmpty()) {
            return false;
        }
        byte[] expected = configured.getBytes(StandardCharsets.UTF_8);
        byte[] actual = presented.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
