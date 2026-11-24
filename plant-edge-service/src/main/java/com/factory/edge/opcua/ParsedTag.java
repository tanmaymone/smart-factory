package com.factory.edge.opcua;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

record ParsedTag(OpcUaProperties.TagConfig config, NodeId nodeId) {}
