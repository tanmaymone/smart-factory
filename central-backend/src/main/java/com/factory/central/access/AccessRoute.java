package com.factory.central.access;

import java.util.Optional;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;

import jakarta.servlet.http.HttpServletRequest;

public final class AccessRoute {

    private AccessRoute() {}

    public static Optional<AccessAction> classify(HttpServletRequest request) {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        String path = normalize(pathWithinApplication(request));
        return classify(method, path);
    }

    public static Optional<AccessAction> classify(String method, String path) {
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        String normalizedPath = normalize(path);

        if (isRegistry(normalizedPath)) {
            if ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod)) {
                return Optional.of(AccessAction.OPEN);
            }
            return Optional.of(AccessAction.REGISTRY_WRITE);
        }
        if ("/api/commands/audit".equals(normalizedPath) && "GET".equals(normalizedMethod)) {
            return Optional.of(AccessAction.OPEN);
        }
        if (isCommandsRoot(normalizedPath) && "POST".equals(normalizedMethod)) {
            return Optional.of(AccessAction.COMMAND_WRITE);
        }
        if (isAccess(normalizedPath)) {
            return Optional.of(AccessAction.ACTOR_ADMIN);
        }
        if ("/api/onboarding/probe".equals(normalizedPath) && "POST".equals(normalizedMethod)) {
            return Optional.of(AccessAction.ONBOARDING_PROBE);
        }
        if (startsWith(normalizedPath, "/api/telemetry")
                || startsWith(normalizedPath, "/api/stream")
                || startsWith(normalizedPath, "/actuator")) {
            return Optional.of(AccessAction.OPEN);
        }
        return Optional.empty();
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

    private static boolean startsWith(String path, String prefix) {
        return path.startsWith(prefix);
    }
}
