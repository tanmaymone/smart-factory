package com.factory.edge.opcua;

import static org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;

import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;

/** Minimal anonymous/None OPC UA server for EDGE-OPC-PROBE / EDGE-CMD protocol tests. */
public final class ProbeTestServer implements AutoCloseable {

    static final String NAMESPACE_URI = "urn:factory:edge:probe-test";

    private final OpcUaServer server;
    private final ProbeNamespace namespace;
    private final String endpointUrl;
    private final String counterNodeIdText;
    private final String doubleNodeIdText;
    private final String readOnlyNodeIdText;

    public ProbeTestServer() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        EndpointConfig endpoint =
                EndpointConfig.newBuilder()
                        .setBindAddress("127.0.0.1")
                        .setHostname("127.0.0.1")
                        .setBindPort(port)
                        .setPath("/probe")
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None)
                        .addTokenPolicy(USER_TOKEN_POLICY_ANONYMOUS)
                        .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                        .build();

        OpcUaServerConfig config =
                OpcUaServerConfig.builder()
                        .setApplicationUri("urn:factory:edge:probe-test-server")
                        .setApplicationName(LocalizedText.english("Plant Edge Probe Test Server"))
                        .setProductUri("urn:factory:edge:probe-test")
                        .setCertificateManager(
                                new DefaultCertificateManager(new MemoryCertificateQuarantine()))
                        .setEndpoints(Set.of(endpoint))
                        .build();

        server =
                new OpcUaServer(
                        config,
                        profile -> {
                            OpcTcpServerTransportConfig transportConfig =
                                    OpcTcpServerTransportConfig.newBuilder().build();
                            return new OpcTcpServerTransport(transportConfig);
                        });

        namespace = new ProbeNamespace(server);
        namespace.startup();
        server.startup().get(10, TimeUnit.SECONDS);

        endpointUrl = "opc.tcp://127.0.0.1:" + port + "/probe";
        counterNodeIdText = nsu(namespace.counterNodeId());
        doubleNodeIdText = nsu(namespace.doubleNodeId());
        readOnlyNodeIdText = nsu(namespace.readOnlyNodeId());
    }

    public String endpointUrl() {
        return endpointUrl;
    }

    public String namespaceUri() {
        return NAMESPACE_URI;
    }

    public String counterNodeIdText() {
        return counterNodeIdText;
    }

    public String doubleNodeIdText() {
        return doubleNodeIdText;
    }

    public String readOnlyNodeIdText() {
        return readOnlyNodeIdText;
    }

    private static String nsu(NodeId nodeId) {
        Object id = nodeId.getIdentifier();
        if (id instanceof Number n) {
            return "nsu=" + NAMESPACE_URI + ";i=" + n.intValue();
        }
        return "nsu=" + NAMESPACE_URI + ";s=" + id;
    }

    public void setCounter(int value) {
        namespace.setCounter(value);
    }

    @Override
    public void close() throws Exception {
        try {
            namespace.shutdown();
        } finally {
            try {
                server.shutdown().get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Probe test server shutdown failed", e);
            }
        }
    }

    private static final class ProbeNamespace extends ManagedNamespaceWithLifecycle {

        private final SubscriptionModel subscriptionModel;
        private final AtomicReference<UaVariableNode> counterNode = new AtomicReference<>();
        private final AtomicReference<UaVariableNode> doubleNode = new AtomicReference<>();
        private final AtomicReference<UaVariableNode> readOnlyNode = new AtomicReference<>();

        ProbeNamespace(OpcUaServer server) {
            super(server, NAMESPACE_URI);
            subscriptionModel = new SubscriptionModel(server, this);
            getLifecycleManager().addLifecycle(subscriptionModel);
            getLifecycleManager().addStartupTask(this::createNodes);
        }

        NodeId counterNodeId() {
            return counterNode.get().getNodeId();
        }

        NodeId doubleNodeId() {
            return doubleNode.get().getNodeId();
        }

        NodeId readOnlyNodeId() {
            return readOnlyNode.get().getNodeId();
        }

        void setCounter(int value) {
            UaVariableNode node = counterNode.get();
            node.setValue(
                    new DataValue(
                            new Variant(value),
                            org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode.GOOD,
                            DateTime.now(),
                            DateTime.now()));
        }

        private void createNodes() {
            UaFolderNode folder =
                    new UaFolderNode(
                            getNodeContext(),
                            newNodeId("Probe"),
                            newQualifiedName("Probe"),
                            LocalizedText.english("Probe"));
            getNodeManager().addNode(folder);
            folder.addReference(
                    new Reference(
                            folder.getNodeId(),
                            NodeIds.Organizes,
                            NodeIds.ObjectsFolder.expanded(),
                            false));

            UaVariableNode counter =
                    new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                            .setNodeId(newNodeId(1001))
                            .setBrowseName(newQualifiedName("Counter"))
                            .setDisplayName(LocalizedText.english("Counter"))
                            .setDataType(NodeIds.Int32)
                            .setTypeDefinition(NodeIds.BaseDataVariableType)
                            .setAccessLevel(AccessLevel.READ_WRITE)
                            .setUserAccessLevel(AccessLevel.READ_WRITE)
                            .setValue(new DataValue(new Variant(1)))
                            .build();
            getNodeManager().addNode(counter);
            folder.addOrganizes(counter);
            counterNode.set(counter);

            UaVariableNode dbl =
                    new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                            .setNodeId(newNodeId(1002))
                            .setBrowseName(newQualifiedName("Setpoint"))
                            .setDisplayName(LocalizedText.english("Setpoint"))
                            .setDataType(NodeIds.Double)
                            .setTypeDefinition(NodeIds.BaseDataVariableType)
                            .setAccessLevel(AccessLevel.READ_WRITE)
                            .setUserAccessLevel(AccessLevel.READ_WRITE)
                            .setValue(new DataValue(new Variant(0.0d)))
                            .build();
            getNodeManager().addNode(dbl);
            folder.addOrganizes(dbl);
            doubleNode.set(dbl);

            UaVariableNode readOnly =
                    new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                            .setNodeId(newNodeId(1003))
                            .setBrowseName(newQualifiedName("ReadOnly"))
                            .setDisplayName(LocalizedText.english("ReadOnly"))
                            .setDataType(NodeIds.Double)
                            .setTypeDefinition(NodeIds.BaseDataVariableType)
                            .setAccessLevel(AccessLevel.READ_ONLY)
                            .setUserAccessLevel(AccessLevel.READ_ONLY)
                            .setValue(new DataValue(new Variant(1.0d)))
                            .build();
            getNodeManager().addNode(readOnly);
            folder.addOrganizes(readOnly);
            readOnlyNode.set(readOnly);
        }

        @Override
        public void onDataItemsCreated(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsCreated(dataItems);
        }

        @Override
        public void onDataItemsModified(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsModified(dataItems);
        }

        @Override
        public void onDataItemsDeleted(List<DataItem> dataItems) {
            subscriptionModel.onDataItemsDeleted(dataItems);
        }

        @Override
        public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
            subscriptionModel.onMonitoringModeChanged(monitoredItems);
        }
    }
}
