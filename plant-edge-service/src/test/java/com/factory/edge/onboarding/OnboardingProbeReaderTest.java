package com.factory.edge.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.edge.onboarding.OnboardingProbeResponse.TagDiagnostic;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Post-session read outcomes: the session is up, so {@code reachable} stays true throughout. */
@Tag("EDGE-PROBE")
class OnboardingProbeReaderTest {

    private static final String PROSYS_NS = "http://www.prosysopc.com/OPCUA/SimulationNodes/";
    private static final String COUNTER = "nsu=" + PROSYS_NS + ";i=1001";
    private static final String[] TABLE = {"http://opcfoundation.org/UA/", PROSYS_NS};

    private final OpcUaClient client = mock(OpcUaClient.class);

    @Test
    void emptyNodeIdsReportsOnlyThatTheNamespaceTableWasRead() {
        stubReads();

        OnboardingProbeResponse response = read(List.of());

        assertThat(response.reachable()).isTrue();
        assertThat(response.error()).isNull();
        assertThat(response.tags()).isEmpty();
    }

    @Test
    void goodValueKeepsItsTypeAndDataTypeName() {
        stubReads(new DataValue(new Variant(12), StatusCode.GOOD, null));
        stubDataTypes(new DataValue(new Variant(NodeIds.Int32), StatusCode.GOOD, null));

        TagDiagnostic tag = read(List.of(COUNTER)).tags().get(0);

        assertThat(tag.reachability()).isEqualTo("REACHABLE");
        assertThat(tag.quality()).isEqualTo("GOOD");
        assertThat(tag.value()).isEqualTo(12);
        assertThat(tag.dataType()).isEqualTo("Int32");
        assertThat(tag.statusName()).isNull();
    }

    @Test
    void goodNullAndNonFiniteScalarsStayGood() {
        stubReads(
                new DataValue(Variant.NULL_VALUE, StatusCode.GOOD, null),
                new DataValue(new Variant(Double.NaN), StatusCode.GOOD, null),
                new DataValue(new Variant(ULong.MAX), StatusCode.GOOD, null));
        stubDataTypes(
                new DataValue(new Variant(NodeIds.Double), StatusCode.GOOD, null),
                new DataValue(new Variant(NodeIds.Double), StatusCode.GOOD, null),
                new DataValue(new Variant(NodeIds.UInt64), StatusCode.GOOD, null));

        List<TagDiagnostic> tags = read(List.of("ns=0;i=1", "ns=0;i=2", "ns=0;i=3")).tags();

        assertThat(tags).extracting(TagDiagnostic::quality).containsOnly("GOOD");
        assertThat(tags.get(0).value()).isNull();
        assertThat(tags.get(1).value()).isNull();
        assertThat(tags.get(1).dataType()).isEqualTo("Double");
        assertThat(tags.get(2).value()).isEqualTo(new BigInteger("18446744073709551615"));
    }

    @Test
    void uncertainStatusIsBadAndKeepsTheUncertainName() {
        stubReads(
                new DataValue(
                        Variant.NULL_VALUE,
                        new StatusCode(StatusCodes.Uncertain_LastUsableValue),
                        null));

        TagDiagnostic tag = read(List.of(COUNTER)).tags().get(0);

        assertThat(tag.quality()).isEqualTo("BAD");
        assertThat(tag.reachability()).isEqualTo("REACHABLE");
        assertThat(tag.statusName()).isEqualTo("Uncertain_LastUsableValue");
        assertThat(tag.value()).isNull();
    }

    @Test
    void missingNodeIsBadNodeIdUnknown() {
        stubReads(
                new DataValue(
                        Variant.NULL_VALUE, new StatusCode(StatusCodes.Bad_NodeIdUnknown), null));

        TagDiagnostic tag = read(List.of("nsu=" + PROSYS_NS + ";i=999999")).tags().get(0);

        assertThat(tag.quality()).isEqualTo("BAD");
        assertThat(tag.statusName()).isEqualTo("Bad_NodeIdUnknown");
    }

    @Test
    void nullBlankBadSyntaxAndUnknownUriStayBadAndReachable() {
        stubReads();

        List<TagDiagnostic> tags =
                read(Arrays.asList(null, " ", "not-a-node-id", "nsu=urn:absent;i=5")).tags();

        assertThat(tags).extracting(TagDiagnostic::quality).containsOnly("BAD");
        assertThat(tags).extracting(TagDiagnostic::reachability).containsOnly("REACHABLE");
        assertThat(tags.get(0).error()).isEqualTo("NodeId text is blank");
        assertThat(tags.get(3).error())
                .isEqualTo("Unknown namespace URI (not in Server_NamespaceArray): urn:absent");
        // Milo rejects an empty read with Bad_NothingToDo, so no value batch runs.
        verify(client, times(1)).readValuesAsync(anyDouble(), any(), anyList());
    }

    @Test
    void duplicateRawTextIsReadOnceAndCopiedOntoEachIndex() {
        stubReads(new DataValue(new Variant(7), StatusCode.GOOD, null));
        stubDataTypes(new DataValue(new Variant(NodeIds.Int32), StatusCode.GOOD, null));

        List<TagDiagnostic> tags = read(List.of(COUNTER, COUNTER)).tags();

        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).value()).isEqualTo(7);
        assertThat(tags.get(1).value()).isEqualTo(7);
        assertThat(tags.get(1).dataType()).isEqualTo("Int32");
        // One namespace read plus one value batch for the single distinct string.
        verify(client, times(2)).readValuesAsync(anyDouble(), any(), anyList());
    }

    @Test
    void namespaceFailureKeepsReachableTrueAndMarksNsuRowsBad() {
        DataValue badTable =
                new DataValue(
                        Variant.NULL_VALUE, new StatusCode(StatusCodes.Bad_NodeIdUnknown), null);
        when(client.readValuesAsync(anyDouble(), any(), anyList()))
                .thenAnswer(
                        invocation ->
                                CompletableFuture.completedFuture(
                                        isNamespaceRead(invocation.getArgument(2))
                                                ? List.of(badTable)
                                                : List.of(
                                                        new DataValue(
                                                                new Variant(5),
                                                                StatusCode.GOOD,
                                                                null))));
        stubDataTypes(new DataValue(new Variant(NodeIds.Int32), StatusCode.GOOD, null));

        OnboardingProbeResponse response = read(List.of(COUNTER, "ns=0;i=2259"));

        assertThat(response.reachable()).isTrue();
        assertThat(response.error())
                .isEqualTo("Server_NamespaceArray quality is not GOOD status=Bad_NodeIdUnknown");
        TagDiagnostic nsu = response.tags().get(0);
        assertThat(nsu.quality()).isEqualTo("BAD");
        assertThat(nsu.reachability()).isEqualTo("REACHABLE");
        assertThat(nsu.statusName()).isNull();
        assertThat(nsu.error()).isEqualTo(response.error());
        // ns=0 does not need the table and is still value-read while time remains.
        assertThat(response.tags().get(1).quality()).isEqualTo("GOOD");
        assertThat(response.tags().get(1).value()).isEqualTo(5);
    }

    private OnboardingProbeResponse read(List<String> nodeIds) {
        return OnboardingProbeReader.read(
                client,
                new OnboardingProbeRequest(
                        "opc.tcp://localhost:53530/OPCUA/SimulationServer", "None", nodeIds),
                OnboardingProbeDeadline.start(8_000L));
    }

    /** Answers the namespace read with a good table and the value batch with {@code values}. */
    private void stubReads(DataValue... values) {
        when(client.readValuesAsync(anyDouble(), any(), anyList()))
                .thenAnswer(
                        invocation ->
                                CompletableFuture.completedFuture(
                                        isNamespaceRead(invocation.getArgument(2))
                                                ? List.of(
                                                        new DataValue(
                                                                new Variant(TABLE),
                                                                StatusCode.GOOD,
                                                                null))
                                                : List.of(values)));
    }

    private void stubDataTypes(DataValue... results) {
        ReadResponse response = mock(ReadResponse.class);
        when(response.getResults()).thenReturn(results);
        when(client.readAsync(anyDouble(), any(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(response));
    }

    private static boolean isNamespaceRead(List<NodeId> nodeIds) {
        return List.of(NodeIds.Server_NamespaceArray).equals(nodeIds);
    }
}
