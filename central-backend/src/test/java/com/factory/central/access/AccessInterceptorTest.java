package com.factory.central.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("SF-ACCESS")
@ExtendWith(MockitoExtension.class)
class AccessInterceptorTest {

    @Mock
    private ActorResolver actorResolver;

    private AccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AccessInterceptor(actorResolver, new ObjectMapper());
        ActorContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    @Test
    void resolverExceptionReturnsAccessCheckFailedAndClearsHolder() throws Exception {
        when(actorResolver.resolve(any())).thenThrow(new IllegalStateException("boom"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/registry/sites");
        request.setServletPath("/api/registry/sites");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("ACCESS_CHECK_FAILED");
        assertThat(ActorContextHolder.current().actorId()).isNull();
    }

    @Test
    void openStreamRejectedDoesNotSetHolder() throws Exception {
        when(actorResolver.resolve(any())).thenReturn(new ActorResolution.Rejected());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream/telemetry");
        request.setServletPath("/api/stream/telemetry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(ActorContextHolder.current().actorId()).isNull();
    }

    @Test
    void openStreamAnonymousAndResolvedDoNotSetHolder() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream/telemetry");
        request.setServletPath("/api/stream/telemetry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(actorResolver.resolve(any())).thenReturn(new ActorResolution.Anonymous());
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(ActorContextHolder.current().actorId()).isNull();

        when(actorResolver.resolve(any()))
                .thenReturn(new ActorResolution.Resolved(
                        new ActorContext("admin", "Admin", ActorRole.ONBOARDING_ADMIN)));
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(ActorContextHolder.current().actorId()).isNull();

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(ActorContextHolder.current().actorId()).isNull();
    }
}
