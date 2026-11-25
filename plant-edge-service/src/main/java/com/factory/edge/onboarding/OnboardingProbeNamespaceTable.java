package com.factory.edge.onboarding;

import com.factory.edge.opcua.QualityGate;
import com.factory.edge.opcua.TagReading;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

/**
 * One {@code Server_NamespaceArray} read, no retry, after {@code connect()} has returned. A failure
 * here keeps {@code reachable} true and sets the envelope {@code error}; it never goes through the
 * pre-session helper. On the empty-{@code nodeIds} M3.5 Step 2 call, {@code error} null is the
 * signal that the table was read.
 */
record OnboardingProbeNamespaceTable(Map<String, UShort> indexByUri, String error) {

    static OnboardingProbeNamespaceTable read(OpcUaClient client, OnboardingProbeDeadline deadline) {
        if (deadline.expired()) {
            return failed(OnboardingProbeFailure.TIMEOUT);
        }
        DataValue value;
        try {
            List<DataValue> results =
                    client
                            .readValuesAsync(
                                    0.0,
                                    TimestampsToReturn.Both,
                                    List.of(NodeIds.Server_NamespaceArray))
                            .get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
            value = results == null || results.isEmpty() ? null : results.get(0);
        } catch (TimeoutException e) {
            return failed(OnboardingProbeFailure.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OnboardingProbeFailure.interrupted();
        } catch (ExecutionException | RuntimeException e) {
            return failed("Failed to read Server_NamespaceArray: " + message(e));
        }

        if (!QualityGate.requireGood(value)) {
            StatusCode status = value == null ? null : value.getStatusCode();
            return failed(
                    "Server_NamespaceArray quality is not GOOD status="
                            + (status == null ? "null" : TagReading.symbolicName(status)));
        }
        Variant variant = value.getValue();
        if (variant == null || variant.isNull() || !(variant.getValue() instanceof String[] uris)) {
            return failed("Server_NamespaceArray value is missing or not String[]");
        }
        Map<String, UShort> map = new HashMap<>();
        for (int i = 0; i < uris.length; i++) {
            if (uris[i] != null && !uris[i].isBlank()) {
                map.put(uris[i], UShort.valueOf(i));
            }
        }
        if (map.isEmpty()) {
            return failed("Server_NamespaceArray is empty");
        }
        return new OnboardingProbeNamespaceTable(Map.copyOf(map), null);
    }

    boolean failed() {
        return error != null;
    }

    private static OnboardingProbeNamespaceTable failed(String error) {
        return new OnboardingProbeNamespaceTable(Map.of(), error);
    }

    private static String message(Exception e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
