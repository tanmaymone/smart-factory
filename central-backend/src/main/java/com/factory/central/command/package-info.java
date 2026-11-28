@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {"registry", "cache", "mqtt", "stream", "access", "quality"})
package com.factory.central.command;
