package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@Tag("SF-ACCESS")
class SecurityContextActorResolverTest {

    private final SecurityContextActorResolver resolver = new SecurityContextActorResolver();

    @AfterEach
    void tearDown() {
        SecurityContextSupport.clear();
    }

    @Test
    void mapsRejectedAnonymousHumanAndEdge() {
        HttpServletRequest rejected = new MockHttpServletRequest();
        rejected.setAttribute(AuthRequestAttributes.REJECTED, Boolean.TRUE);
        assertThat(resolver.resolve(rejected)).isInstanceOf(ActorResolution.Rejected.class);

        assertThat(resolver.resolve(new MockHttpServletRequest())).isInstanceOf(ActorResolution.Anonymous.class);

        ActorContext actor = new ActorContext("op", "Op", ActorRole.OPERATOR);
        SecurityContextSupport.setAuthentication(new ActorAuthenticationToken(actor));
        assertThat(resolver.resolve(new MockHttpServletRequest()))
                .isEqualTo(new ActorResolution.Resolved(actor));

        EdgeSitePrincipal edge = new EdgeSitePrincipal(UUID.randomUUID(), "INDORE");
        SecurityContextSupport.clear();
        SecurityContextSupport.setAuthentication(new EdgeSiteAuthenticationToken(edge));
        assertThat(resolver.resolve(new MockHttpServletRequest()))
                .isEqualTo(new ActorResolution.EdgeSite(edge.credentialId(), edge.siteId()));
    }
}