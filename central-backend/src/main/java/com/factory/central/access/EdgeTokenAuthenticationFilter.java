package com.factory.central.access;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

public class EdgeTokenAuthenticationFilter extends OncePerRequestFilter {

    private final EdgeSiteCredentialRepository credentialRepository;

    public EdgeTokenAuthenticationFilter(EdgeSiteCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SessionCookieAuthenticationFilter.isAuthExempt(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextSupport.hasHumanAuthentication()) {
            filterChain.doFilter(request, response);
            return;
        }
        String bearer = readBearer(request);
        if (bearer == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String hash = TokenHashing.sha256Hex(bearer);
        Optional<EdgeSiteCredentialEntity> credential = credentialRepository.findActiveByTokenHash(hash);
        if (credential.isEmpty()) {
            request.setAttribute(AuthRequestAttributes.REJECTED, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }
        EdgeSiteCredentialEntity row = credential.get();
        SecurityContextSupport.setAuthentication(
                new EdgeSiteAuthenticationToken(new EdgeSitePrincipal(row.getId(), row.getSiteId())));
        filterChain.doFilter(request, response);
    }

    private static String readBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            request.setAttribute(AuthRequestAttributes.REJECTED, Boolean.TRUE);
            return null;
        }
        String token = header.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }
}