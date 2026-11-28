package com.factory.central.access;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class EdgeSiteAuthenticationToken extends AbstractAuthenticationToken {

    private final EdgeSitePrincipal principal;

    public EdgeSiteAuthenticationToken(EdgeSitePrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_EDGE_SITE")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public EdgeSitePrincipal getPrincipal() {
        return principal;
    }
}