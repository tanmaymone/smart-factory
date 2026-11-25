package com.factory.edge.onboarding;

import com.factory.edge.opcua.NodeAddress;
import com.factory.edge.opcua.NodeIdParser;
import com.factory.edge.opcua.OpcUaProbeException;
import com.factory.edge.opcua.QualityGate;
import com.factory.edge.opcua.TagReading;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;

/** Everything after {@code connect()} has returned: namespace table, one value batch, DataType. */
final class OnboardingProbeReader {

    private OnboardingProbeReader() {}

    static OnboardingProbeResponse read(
            OpcUaClient client, OnboardingProbeRequest request, OnboardingProbeDeadline deadline) {
        List<String> nodeIds = request.nodeIds() == null ? List.of() : request.nodeIds();
        OnboardingProbeNamespaceTable table = OnboardingProbeNamespaceTable.read(client, deadline);

        List<OnboardingProbeRow> rows = new ArrayList<>(nodeIds.size());
        for (String text : nodeIds) {
            rows.add(resolveRow(text, table));
        }

        // Duplicate detection is raw submitted text: ns=0;i=2259 and its nsu= spelling read twice.
        Map<String, OnboardingProbeRow> distinct = new LinkedHashMap<>();
        for (OnboardingProbeRow row : rows) {
            if (row.resolved != null) {
                distinct.putIfAbsent(row.nodeId, row);
            }
        }
        if (!distinct.isEmpty()) {
            // Milo rejects an empty read with Bad_NothingToDo, so an all-unresolved list skips this.
            readValues(client, List.copyOf(distinct.values()), deadline);
        }
        for (OnboardingProbeRow row : rows) {
            OnboardingProbeRow leader = row.resolved == null ? null : distinct.get(row.nodeId);
            if (leader != null && leader != row) {
                row.copyFrom(leader);
            }
        }

        return new OnboardingProbeResponse(
                request.endpointUrl(),
                request.securityMode(),
                true,
                table.error(),
                rows.stream().map(OnboardingProbeRow::toDiagnostic).toList());
    }

    private static OnboardingProbeRow resolveRow(String text, OnboardingProbeNamespaceTable table) {
        OnboardingProbeRow row = new OnboardingProbeRow(text);
        NodeAddress address;
        try {
            address = NodeIdParser.parse(text);
        } catch (OpcUaProbeException e) {
            row.bad(e.getMessage());
            return row;
        }
        switch (address) {
            case NodeAddress.Resolved resolved -> row.resolved = resolved.nodeId();
            case NodeAddress.Unresolved unresolved -> resolveNsu(row, unresolved, table);
        }
        return row;
    }

    private static void resolveNsu(
            OnboardingProbeRow row,
            NodeAddress.Unresolved unresolved,
            OnboardingProbeNamespaceTable table) {
        if (table.failed()) {
            // Not the unknown-URI message: the table was never read.
            row.bad(table.error());
            return;
        }
        UShort index = table.indexByUri().get(unresolved.namespaceUri());
        if (index == null) {
            row.bad(
                    "Unknown namespace URI (not in Server_NamespaceArray): "
                            + unresolved.namespaceUri());
            return;
        }
        row.resolved = unresolved.identifier().toNodeId(index);
    }

    /** One batch that succeeds or times out together — there is no partial read subset. */
    private static void readValues(
            OpcUaClient client, List<OnboardingProbeRow> rows, OnboardingProbeDeadline deadline) {
        if (deadline.expired()) {
            rows.forEach(row -> row.unreachable(OnboardingProbeFailure.TIMEOUT));
            return;
        }
        List<DataValue> values;
        try {
            values =
                    client
                            .readValuesAsync(
                                    0.0,
                                    TimestampsToReturn.Both,
                                    rows.stream().map(row -> row.resolved).toList())
                            .get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            rows.forEach(row -> row.unreachable(OnboardingProbeFailure.TIMEOUT));
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OnboardingProbeFailure.interrupted();
        } catch (ExecutionException | RuntimeException e) {
            String message = message(e);
            rows.forEach(row -> row.unreachable(message));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            applyValue(rows.get(i), values == null || i >= values.size() ? null : values.get(i));
        }
        readDataTypes(client, rows, deadline);
    }

    private static void applyValue(OnboardingProbeRow row, DataValue value) {
        StatusCode status = value == null ? null : value.getStatusCode();
        if (status == null || !status.isGood()) {
            // Uncertain collapses to BAD and keeps its Uncertain_* name on statusName.
            row.bad(null);
            row.statusName = status == null ? null : TagReading.symbolicName(status);
            return;
        }
        Variant variant = value.getValue();
        Object raw = variant == null || variant.isNull() ? null : variant.getValue();
        row.quality = OnboardingProbeResponse.QUALITY_GOOD;
        row.value = OnboardingProbeValueMapper.toJsonValue(raw);
    }

    /** A miss here leaves {@code dataType} null and keeps the GOOD value row. */
    private static void readDataTypes(
            OpcUaClient client, List<OnboardingProbeRow> rows, OnboardingProbeDeadline deadline) {
        List<OnboardingProbeRow> good =
                rows.stream()
                        .filter(row -> OnboardingProbeResponse.QUALITY_GOOD.equals(row.quality))
                        .toList();
        if (good.isEmpty() || deadline.expired()) {
            return;
        }
        try {
            List<ReadValueId> ids =
                    good.stream()
                            .map(
                                    row ->
                                            new ReadValueId(
                                                    row.resolved,
                                                    AttributeId.DataType.uid(),
                                                    null,
                                                    QualifiedName.NULL_VALUE))
                            .toList();
            ReadResponse response =
                    client
                            .readAsync(0.0, TimestampsToReturn.Both, ids)
                            .get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
            DataValue[] results = response == null ? null : response.getResults();
            if (results == null) {
                return;
            }
            for (int i = 0; i < good.size() && i < results.length; i++) {
                good.get(i).dataType = dataTypeName(results[i]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OnboardingProbeFailure.interrupted();
        } catch (TimeoutException | ExecutionException | RuntimeException e) {
            // Deadline or read failure: dataType stays null, the value row stays GOOD.
        }
    }

    private static String dataTypeName(DataValue value) {
        if (!QualityGate.requireGood(value)) {
            return null;
        }
        Variant variant = value.getValue();
        if (variant == null || !(variant.getValue() instanceof NodeId nodeId)) {
            return null;
        }
        if (OpcUaDataType.isBuiltin(nodeId)) {
            return OpcUaDataType.fromNodeId(nodeId).name();
        }
        try {
            return nodeId.toParseableString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String message(Exception e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
