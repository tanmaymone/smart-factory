package com.factory.central.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
public class AccessInterceptor implements AsyncHandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessInterceptor.class);

    private final ActorResolver actorResolver;
    private final ObjectMapper objectMapper;

    public AccessInterceptor(ActorResolver actorResolver, ObjectMapper objectMapper) {
        this.actorResolver = actorResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC
                || request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }
        ActorContextHolder.clear();
        boolean proceed = false;
        try {
            Optional<AccessRouteMatch> route = AccessRoute.match(request);
            if (route.isEmpty()) {
                proceed = true;
                return true;
            }

            AccessRouteMatch match = route.get();
            ActorResolution resolution = actorResolver.resolve(request);
            if (resolution instanceof ActorResolution.Rejected) {
                writeError(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        AccessErrorCode.UNAUTHENTICATED,
                        AccessPolicy.UNAUTHENTICATED_MESSAGE);
                return false;
            }

            AccessAction accessAction = match.action();
            if (accessAction == AccessAction.OPEN) {
                proceed = true;
                return true;
            }

            AccessPolicy.Decision decision =
                    accessAction == AccessAction.EDGE_CONFIG_READ
                            ? AccessPolicy.evaluateEdgeConfig(
                                    accessAction, resolution, match.targetSiteId().orElse(null))
                            : AccessPolicy.evaluate(accessAction, resolution);
            if (decision instanceof AccessPolicy.Deny deny) {
                writeError(response, deny.status(), deny.code(), deny.message());
                return false;
            }

            AccessPolicy.Allow allow = (AccessPolicy.Allow) decision;
            if (allow.actor().actorId() != null) {
                ActorContextHolder.set(allow.actor());
            }
            proceed = true;
            return true;
        } catch (Exception ex) {
            log.warn("Access check failed: {}", ex.getClass().getName());
            try {
                if (!response.isCommitted()) {
                    writeError(
                            response,
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            AccessErrorCode.ACCESS_CHECK_FAILED,
                            "Access check failed");
                }
            } catch (IOException writeEx) {
                log.debug("Failed to write access check error response: {}", writeEx.getClass().getName());
            }
            return false;
        } finally {
            if (!proceed) {
                ActorContextHolder.clear();
            }
        }
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        ActorContextHolder.clear();
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ActorContextHolder.clear();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), AccessErrorResponse.of(code, message));
    }
}