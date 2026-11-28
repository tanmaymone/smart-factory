package com.factory.central.access;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ActorAuthenticationToken extends AbstractAuthenticationToken {

    private final ActorContext actor;

    public ActorAuthenticationToken(ActorContext actor) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name())));
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public ActorContext getPrincipal() {
        return actor;
    }
}