package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@Tag("SF-ACCESS")
@ExtendWith(MockitoExtension.class)
class BootstrapActorResolverTest {

    @Mock
    private ActorRepository actorRepository;

    private BootstrapProperties properties;
    private BootstrapActorResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new BootstrapProperties();
        properties.setToken("secret-token");
        properties.setActorId("bootstrap-admin");
        properties.setDisplayName("Bootstrap Admin");
        resolver = new BootstrapActorResolver(properties, actorRepository);
    }

    @Test
    void validTokenWinsOverViewerActorHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.SETUP_TOKEN_HEADER, "secret-token");
        request.addHeader(BootstrapActorResolver.ACTOR_ID_HEADER, "viewer@factory.com");

        ActorResolution resolution = resolver.resolve(request);

        assertThat(resolution).isInstanceOf(ActorResolution.Resolved.class);
        ActorContext actor = ((ActorResolution.Resolved) resolution).actor();
        assertThat(actor.role()).isEqualTo(ActorRole.ONBOARDING_ADMIN);
        assertThat(actor.actorId()).isEqualTo("bootstrap-admin");
    }

    @Test
    void wrongTokenRejectsEvenWithValidActor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.SETUP_TOKEN_HEADER, "wrong");
        request.addHeader(BootstrapActorResolver.ACTOR_ID_HEADER, "op@plant");

        assertThat(resolver.resolve(request)).isInstanceOf(ActorResolution.Rejected.class);
    }

    @Test
    void spacedTokenIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.SETUP_TOKEN_HEADER, " secret-token");

        assertThat(resolver.resolve(request)).isInstanceOf(ActorResolution.Rejected.class);
    }

    @Test
    void emptyTokenHeaderFallsThroughToActorLookup() {
        when(actorRepository.findById("op@plant")).thenReturn(Optional.of(operator()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.SETUP_TOKEN_HEADER, "");
        request.addHeader(BootstrapActorResolver.ACTOR_ID_HEADER, "op@plant");

        ActorResolution resolution = resolver.resolve(request);
        assertThat(resolution).isInstanceOf(ActorResolution.Resolved.class);
        assertThat(((ActorResolution.Resolved) resolution).actor().actorId()).isEqualTo("op@plant");
    }

    @Test
    void whitespaceActorHeaderIsAnonymous() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.ACTOR_ID_HEADER, "   ");

        assertThat(resolver.resolve(request)).isInstanceOf(ActorResolution.Anonymous.class);
    }

    @Test
    void trimsActorHeaderAndMatchesExactly() {
        when(actorRepository.findById("op@plant")).thenReturn(Optional.of(operator()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.ACTOR_ID_HEADER, " op@plant ");

        ActorResolution resolution = resolver.resolve(request);
        assertThat(resolution).isInstanceOf(ActorResolution.Resolved.class);
        assertThat(((ActorResolution.Resolved) resolution).actor().actorId()).isEqualTo("op@plant");
    }

    @Test
    void caseMismatchIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BootstrapActorResolver.ACTOR_ID_HEADER, "OP@plant");

        assertThat(resolver.resolve(request)).isInstanceOf(ActorResolution.Rejected.class);
    }

    private static ActorEntity operator() {
        return new ActorEntity("op@plant", "Operator", ActorRole.OPERATOR);
    }
}
