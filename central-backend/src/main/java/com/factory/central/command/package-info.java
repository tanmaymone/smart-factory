@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {"registry", "cache", "mqtt", "stream"})
package com.factory.central.command;
