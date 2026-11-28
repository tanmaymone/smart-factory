package com.factory.central.access;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionCookieAuthenticationFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;
    private final AuthSessionRepository sessionRepository;
    private final ActorRepository actorRepository;
    private final Clock clock;

    public SessionCookieAuthenticationFilter(
            AuthProperties authProperties,
            AuthSessionRepository sessionRepository,
            ActorRepository actorRepository,
            Clock clock) {
        this.authProperties = authProperties;
        this.sessionRepository = sessionRepository;
        this.actorRepository = actorRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isAuthExempt(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String cookieValue = readCookie(request, authProperties.getSessionCookieName());
        if (cookieValue == null || cookieValue.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String hash = TokenHashing.sha256Hex(cookieValue);
        Instant now = clock.instant();
        Optional<AuthSessionEntity> session = sessionRepository.findActiveByTokenHash(hash, now);
        if (session.isEmpty()) {
            request.setAttribute(AuthRequestAttributes.REJECTED, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }
        Optional<ActorEntity> actor = actorRepository.findById(session.get().getActorId());
        if (actor.isEmpty() || !actor.get().isEnabled() || actor.get().getPasswordHash() == null) {
            request.setAttribute(AuthRequestAttributes.REJECTED, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }
        ActorContext context = actor.get().toContext();
        SecurityContextSupport.setAuthentication(new ActorAuthenticationToken(context));
        filterChain.doFilter(request, response);
    }

    static boolean isAuthExempt(HttpServletRequest request) {
        if (!org.springframework.web.util.ServletRequestPathUtils.hasParsedRequestPath(request)) {
            org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
        }
        String path = AccessRoute.normalize(
                org.springframework.web.util.ServletRequestPathUtils.getParsedRequestPath(request)
                        .pathWithinApplication()
                        .value());
        return "/api/auth/login".equals(path) || "/api/auth/logout".equals(path);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}