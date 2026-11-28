package com.factory.central.access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextActorResolver implements ActorResolver {

    @Override
    public ActorResolution resolve(HttpServletRequest request) {
        Object rejected = request.getAttribute(AuthRequestAttributes.REJECTED);
        if (Boolean.TRUE.equals(rejected)) {
            return new ActorResolution.Rejected();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new ActorResolution.Anonymous();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ActorContext actor) {
            return new ActorResolution.Resolved(actor);
        }
        if (principal instanceof EdgeSitePrincipal edge) {
            return new ActorResolution.EdgeSite(edge.credentialId(), edge.siteId());
        }
        return new ActorResolution.Anonymous();
    }
}