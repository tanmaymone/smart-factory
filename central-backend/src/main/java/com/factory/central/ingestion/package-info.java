@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
        allowedDependencies = {"registry", "mqtt", "cache", "quality"})
package com.factory.central.ingestion;
