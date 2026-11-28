package com.factory.central.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final ActorRepository actorRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            ActorRepository actorRepository,
            AuthSessionRepository sessionRepository,
            AuthProperties authProperties,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.actorRepository = actorRepository;
        this.sessionRepository = sessionRepository;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Transactional
    public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String actorId = request.actorId().trim();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(actorId, request.password()));
        } catch (DisabledException ex) {
            throw new AccessException(
                    AccessErrorCode.ACCOUNT_DISABLED,
                    "Account is disabled",
                    HttpStatus.FORBIDDEN,
                    java.util.List.of());
        } catch (BadCredentialsException ex) {
            throw new AccessException(
                    AccessErrorCode.INVALID_CREDENTIALS,
                    "Invalid username or password",
                    HttpStatus.UNAUTHORIZED,
                    java.util.List.of());
        }
        ActorEntity actor = actorRepository.findById(actorId).orElseThrow();
        String rawToken = newSessionToken();
        Instant now = clock.instant();
        Instant expires = now.plus(Duration.ofSeconds(authProperties.getSessionTtlSeconds()));
        sessionRepository.save(new AuthSessionEntity(TokenHashing.sha256Hex(rawToken), actorId, now, expires));
        sessionRepository.deleteExpiredOrRevoked(now.minus(Duration.ofDays(7)));
        writeSessionCookie(httpRequest, response, rawToken, authProperties.getSessionTtlSeconds());
        return AuthUserResponse.from(actor.toContext());
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = readCookie(request, authProperties.getSessionCookieName());
        if (cookieValue != null && !cookieValue.isBlank()) {
            sessionRepository.revokeByTokenHash(TokenHashing.sha256Hex(cookieValue), clock.instant());
        }
        clearSessionCookie(request, response);
    }

    public AuthUserResponse currentUser() {
        ActorContext actor = ActorContextHolder.current();
        if (actor.actorId() == null || actor.role() == null) {
            throw new AccessException(
                    AccessErrorCode.UNAUTHENTICATED,
                    AccessPolicy.UNAUTHENTICATED_MESSAGE,
                    HttpStatus.UNAUTHORIZED,
                    java.util.List.of());
        }
        return AuthUserResponse.from(actor);
    }

    @Transactional
    public void revokeSessionsForActor(String actorId) {
        sessionRepository.revokeAllForActor(actorId, clock.instant());
    }

    private void writeSessionCookie(
            HttpServletRequest request, HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.getSessionCookieName(), token)
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.getSessionCookieName(), "")
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private static String newSessionToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}