package com.factory.central.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.registry")
public class RegistryProperties {

    /**
     * OPC UA simulation endpoint used when seeding tag mappings.
     * Override with OPC_ENDPOINT_URL when Prosys advertises a hostname
     * (e.g. opc.tcp://Tanmay:53530/OPCUA/SimulationServer).
     */
    private String opcEndpointUrl = "opc.tcp://localhost:53530/OPCUA/SimulationServer";

    public String getOpcEndpointUrl() {
        return opcEndpointUrl;
    }

    public void setOpcEndpointUrl(String opcEndpointUrl) {
        this.opcEndpointUrl = opcEndpointUrl;
    }
}
