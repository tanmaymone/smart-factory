package com.factory.central;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithStructureTest {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(CentralBackendApplication.class).verify();
    }
}
