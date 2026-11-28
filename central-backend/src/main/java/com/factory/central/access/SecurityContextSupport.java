package com.factory.central.access;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextSupport {

    private SecurityContextSupport() {}

    public static void setAuthentication(org.springframework.security.core.Authentication authentication) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    public static boolean hasHumanAuthentication() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof ActorContext;
    }
}