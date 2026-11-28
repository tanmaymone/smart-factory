package com.factory.central.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public final class AuthTestSupport {

    public static final String ADMIN_ID = "admin@factory.com";
    public static final String ADMIN_PASSWORD = "it-admin-pass";
    public static final String SESSION_COOKIE = "SF_SESSION";
    public static final String LEGACY_SETUP_HEADER = "X-Setup-Token";
    public static final String LEGACY_ACTOR_HEADER = "X-Actor-Id";
    public static final String INDORE_EDGE_TOKEN = "sf_edge_indore_it_token";
    public static final String PUNE_EDGE_TOKEN = "sf_edge_pune_it_token";
    public static final String TEST_USER_PASSWORD = "it-user-pass";

    private AuthTestSupport() {}

    public static void registerBootstrapAdmin(DynamicPropertyRegistry registry) {
        registry.add("app.auth.bootstrap-admin.actor-id", () -> ADMIN_ID);
        registry.add("app.auth.bootstrap-admin.password", () -> ADMIN_PASSWORD);
        registry.add("app.auth.bootstrap-admin.display-name", () -> "Test Admin");
    }

    public static void registerBootstrapEdgeTokens(DynamicPropertyRegistry registry) {
        registry.add(
                "app.auth.bootstrap-edge-tokens",
                () -> "INDORE:" + INDORE_EDGE_TOKEN + ",PUNE:" + PUNE_EDGE_TOKEN);
    }

    public static Cookie login(MockMvc mockMvc, String actorId, String password) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"actorId":"%s","password":"%s"}
                                """
                                        .formatted(actorId, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        for (Cookie cookie : response.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie;
            }
        }
        throw new IllegalStateException("SF_SESSION cookie missing after login");
    }

    public static Cookie adminSession(MockMvc mockMvc) throws Exception {
        return login(mockMvc, ADMIN_ID, ADMIN_PASSWORD);
    }

    public static void seedActor(
            ActorRepository actorRepository, PasswordEncoder encoder, String id, String name, ActorRole role, String password) {
        ActorEntity entity = new ActorEntity(id, name, role);
        entity.setPasswordHash(encoder.encode(password));
        entity.setEnabled(true);
        actorRepository.save(entity);
    }

    public static MockMvc secureMvc(WebApplicationContext webApplicationContext) {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    public static MockMvc secureMvc(WebApplicationContext webApplicationContext, Cookie defaultSession) {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").cookie(defaultSession))
                .build();
    }
}