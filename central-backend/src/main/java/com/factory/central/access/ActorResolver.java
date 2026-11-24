package com.factory.central.access;

import jakarta.servlet.http.HttpServletRequest;

public interface ActorResolver {

    ActorResolution resolve(HttpServletRequest request);
}
