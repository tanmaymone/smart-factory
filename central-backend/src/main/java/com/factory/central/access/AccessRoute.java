package com.factory.central.access;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;

public final class AccessRoute {

    private AccessRoute() {}

    public static Optional<AccessRouteMatch> match(HttpServletRequest request) {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        String path = normalize(pathWithinApplication(request));
        return match(method, path);
    }

    public static Optional<AccessAction> classify(HttpServletRequest request) {
        return match(request).map(AccessRouteMatch::action);
    }

    public static Optional<AccessAction> classify(String method, String path) {
        return match(method, path).map(AccessRouteMatch::action);
    }

    public static Optional<AccessRouteMatch> match(String method, String path) {
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        String normalizedPath = normalize(path);

        if ("/api/auth/login".equals(normalizedPath) && "POST".equals(normalizedMethod)) {
            return Optional.of(new AccessRouteMatch(AccessAction.OPEN, Optional.empty()));
        }
        if ("/api/auth/logout".equals(normalizedPath) && "POST".equals(normalizedMethod)) {
            return Optional.of(new AccessRouteMatch(AccessAction.OPEN, Optional.empty()));
        }
        if ("/api/auth/me".equals(normalizedPath) && "GET".equals(normalizedMethod)) {
            return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
        }

        Optional<String> edgeSiteId = extractEdgeConfigSiteId(normalizedPath);
        if (edgeSiteId.isPresent()
                && ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod))) {
            return Optional.of(new AccessRouteMatch(AccessAction.EDGE_CONFIG_READ, edgeSiteId));
        }

        if (isRegistry(normalizedPath)) {
            if ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod)) {
                return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
            }
            return Optional.of(new AccessRouteMatch(AccessAction.REGISTRY_WRITE, Optional.empty()));
        }
        if ("/api/commands/audit".equals(normalizedPath) && "GET".equals(normalizedMethod)) {
            return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
        }
        if (isCommandsRoot(normalizedPath) && "POST".equals(normalizedMethod)) {
            return Optional.of(new AccessRouteMatch(AccessAction.COMMAND_WRITE, Optional.empty()));
        }
        if (isAccess(normalizedPath)) {
            return Optional.of(new AccessRouteMatch(AccessAction.ACTOR_ADMIN, Optional.empty()));
        }
        if ("/api/onboarding/probe".equals(normalizedPath) && "POST".equals(normalizedMethod)) {
            return Optional.of(new AccessRouteMatch(AccessAction.ONBOARDING_PROBE, Optional.empty()));
        }
        if (isOnboardingTemplates(normalizedPath)
                && ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod))) {
            return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
        }
        if ("/api/onboarding/checklist".equals(normalizedPath)
                && ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod))) {
            return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
        }
        if (startsWith(normalizedPath, "/api/telemetry")) {
            if ("/api/telemetry/ingest".equals(normalizedPath) && "POST".equals(normalizedMethod)) {
                return Optional.of(new AccessRouteMatch(AccessAction.TELEMETRY_INGEST, Optional.empty()));
            }
            if ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod)) {
                return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
            }
        }
        if (startsWith(normalizedPath, "/api/stream")
                && ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod))) {
            return Optional.of(new AccessRouteMatch(AccessAction.AUTHENTICATED_READ, Optional.empty()));
        }
        if (startsWith(normalizedPath, "/actuator")) {
            return Optional.of(new AccessRouteMatch(AccessAction.OPEN, Optional.empty()));
        }
        return Optional.empty();
    }

    static Optional<String> extractEdgeConfigSiteId(String normalizedPath) {
        if (!startsWith(normalizedPath, "/api/edge/")) {
            return Optional.empty();
        }
        PathContainer parsed = PathContainer.parsePath(normalizedPath);
        List<String> segments = new ArrayList<>();
        for (PathContainer.Element element : parsed.elements()) {
            if (element instanceof PathContainer.PathSegment segment) {
                segments.add(segment.valueToMatch());
            }
        }
        if (segments.size() != 4) {
            return Optional.empty();
        }
        if (!"api".equals(segments.get(0))
                || !"edge".equals(segments.get(1))
                || !"config".equals(segments.get(3))) {
            return Optional.empty();
        }
        String siteId = segments.get(2);
        return siteId.isBlank() ? Optional.empty() : Optional.of(siteId);
    }

    static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        StringBuilder collapsed = new StringBuilder(path.length());
        boolean previousSlash = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/') {
                if (!previousSlash) {
                    collapsed.append(c);
                }
                previousSlash = true;
            } else {
                collapsed.append(c);
                previousSlash = false;
            }
        }
        String value = collapsed.toString();
        if (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? "/" : value;
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        if (!ServletRequestPathUtils.hasParsedRequestPath(request)) {
            ServletRequestPathUtils.parseAndCache(request);
        }
        RequestPath requestPath = ServletRequestPathUtils.getParsedRequestPath(request);
        return requestPath.pathWithinApplication().value();
    }

    private static boolean isRegistry(String path) {
        return "/api/registry".equals(path) || startsWith(path, "/api/registry/");
    }

    private static boolean isAccess(String path) {
        return "/api/access".equals(path) || startsWith(path, "/api/access/");
    }

    private static boolean isCommandsRoot(String path) {
        return "/api/commands".equals(path);
    }

    private static boolean isOnboardingTemplates(String path) {
        if ("/api/onboarding/templates".equals(path)) {
            return true;
        }
        if (!startsWith(path, "/api/onboarding/templates/")) {
            return false;
        }
        String rest = path.substring("/api/onboarding/templates/".length());
        return rest.indexOf('/') < 0 && !rest.isEmpty();
    }

    private static boolean startsWith(String path, String prefix) {
        return path.startsWith(prefix);
    }
}