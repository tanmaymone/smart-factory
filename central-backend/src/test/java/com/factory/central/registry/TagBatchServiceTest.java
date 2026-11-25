package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.central.access.ActorContext;
import com.factory.central.cache.InMemoryLatestTagCache;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@Tag("SF-REGISTRY")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagBatchServiceTest {

    @Mock
    private TagMappingRepository tagMappingRepository;

    @Mock
    private LineRepository lineRepository;

    @Mock
    private SiteRepository siteRepository;

    private InMemoryLatestTagCache cache;
    private RegistryLivenessService livenessService;
    private TagBatchService service;
    private final Map<String, TagMapping> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        cache = new InMemoryLatestTagCache();
        livenessService = new RegistryLivenessService(
                siteRepository, lineRepository, tagMappingRepository, cache);
        service = new TagBatchService(
                tagMappingRepository,
                lineRepository,
                livenessService,
                org.mockito.Mockito.mock(SiteConfigRevisionService.class));
        when(lineRepository.findById(any())).thenReturn(Optional.of(new Line("NASHIK", "LINE-01", "Reactor")));
        when(siteRepository.findById("NASHIK"))
                .thenReturn(Optional.of(new Site(
                        "NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.DRAFT)));
        when(tagMappingRepository.findBySiteIdAndLineIdAndTagCode(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String key = key(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2));
                    return Optional.ofNullable(store.get(key));
                });
        when(tagMappingRepository.save(any(TagMapping.class))).thenAnswer((Answer<TagMapping>) invocation -> {
            TagMapping tag = invocation.getArgument(0);
            store.put(key(tag.getSiteId(), tag.getLineId(), tag.getTagCode()), tag);
            return tag;
        });
    }

    @Test
    void duplicateValidRowsCreateThenUpdate() {
        TagBatchResponse response = service.importBatch(
                ActorContext.anonymous(), List.of(validRow("T1", null), validRow("T1", null)));
        assertThat(response.received()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(0);
        assertThat(store).hasSize(1);
        assertThat(store.values().iterator().next().getLifecycle()).isEqualTo(RegistryLifecycle.DRAFT);
    }

    @Test
    void firstFailSecondCreate() {
        TagBatchResponse response = service.importBatch(
                ActorContext.anonymous(),
                List.of(validRow("T1", Map.of("deadband", "-1")), validRow("T1", null)));
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(0);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(store).hasSize(1);
    }

    @Test
    void draftThenActiveOnSameKeyUsesSharedLivenessAndSkipsCacheWrite() {
        TagBatchResponse response = service.importBatch(
                ActorContext.anonymous(),
                List.of(
                        validRow("REACTOR_TEMP_C", null),
                        validRow("REACTOR_TEMP_C", Map.of("lifecycle", "ACTIVE"))));
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
        TagMapping saved = store.get(key("NASHIK", "LINE-01", "REACTOR_TEMP_C"));
        assertThat(saved.getLifecycle()).isEqualTo(RegistryLifecycle.ACTIVE);
        assertThat(cache.get("NASHIK", "LINE-01", "REACTOR_TEMP_C")).isEmpty();
        verify(tagMappingRepository, never()).delete(any());
        // Parent site stays DRAFT in this fixture, so hierarchy is still not live.
        assertThat(livenessService.isLive(saved)).isFalse();
        verify(siteRepository, atLeastOnce()).findById(eq("NASHIK"));
        verify(lineRepository, atLeastOnce()).findById(any(LineId.class));
    }

    @Test
    void explicitActiveWithActiveParentsIsLiveButLeavesCacheForIngest() {
        when(siteRepository.findById("NASHIK"))
                .thenReturn(Optional.of(new Site(
                        "NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.ACTIVE)));
        when(lineRepository.findById(any()))
                .thenReturn(Optional.of(new Line("NASHIK", "LINE-01", "Reactor", RegistryLifecycle.ACTIVE)));

        TagBatchResponse response = service.importBatch(
                ActorContext.anonymous(),
                List.of(validRow("REACTOR_TEMP_C", Map.of("lifecycle", "ACTIVE"))));

        assertThat(response.created()).isEqualTo(1);
        TagMapping saved = store.get(key("NASHIK", "LINE-01", "REACTOR_TEMP_C"));
        assertThat(saved.getLifecycle()).isEqualTo(RegistryLifecycle.ACTIVE);
        assertThat(livenessService.isLive(saved)).isTrue();
        // Batch does not seed latest-state; ingest owns the first live put.
        assertThat(cache.get("NASHIK", "LINE-01", "REACTOR_TEMP_C")).isEmpty();

        cache.put(com.factory.central.cache.LatestTagState.initial(
                "NASHIK",
                "LINE-01",
                "REACTOR_TEMP_C",
                42.0,
                "GOOD",
                java.time.Instant.parse("2026-11-24T12:00:00Z"),
                com.factory.central.cache.AlarmSeverity.NONE,
                null));
        assertThat(livenessService.filterLive(cache.find("NASHIK", "LINE-01", "REACTOR_TEMP_C")))
                .hasSize(1);
    }

    @Test
    void explicitDemotionOfLiveTagEvictsViaSharedLiveness() {
        when(siteRepository.findById("NASHIK"))
                .thenReturn(Optional.of(new Site(
                        "NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.ACTIVE)));
        when(lineRepository.findById(any()))
                .thenReturn(Optional.of(new Line("NASHIK", "LINE-01", "Reactor", RegistryLifecycle.ACTIVE)));
        TagMapping live = new TagMapping(
                java.util.UUID.randomUUID(),
                "NASHIK",
                "LINE-01",
                "REACTOR_TEMP_C",
                "temp",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1002",
                TagDataType.FLOAT,
                "C",
                false,
                null,
                null,
                0.2,
                null,
                null,
                null,
                null,
                null,
                RegistryLifecycle.ACTIVE);
        store.put(key("NASHIK", "LINE-01", "REACTOR_TEMP_C"), live);
        cache.put(com.factory.central.cache.LatestTagState.initial(
                "NASHIK",
                "LINE-01",
                "REACTOR_TEMP_C",
                40.0,
                "GOOD",
                java.time.Instant.parse("2026-11-24T00:00:00Z"),
                com.factory.central.cache.AlarmSeverity.NONE,
                null));

        TagBatchResponse response = service.importBatch(
                ActorContext.anonymous(),
                List.of(validRow("REACTOR_TEMP_C", Map.of("lifecycle", "DRAFT"))));

        assertThat(response.updated()).isEqualTo(1);
        assertThat(cache.get("NASHIK", "LINE-01", "REACTOR_TEMP_C")).isEmpty();
        assertThat(livenessService.isLive("NASHIK", "LINE-01", "REACTOR_TEMP_C")).isFalse();
        verify(siteRepository, atLeastOnce()).findById(anyString());
    }

    private static TagBatchRowInput validRow(String tagCode, Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("siteId", "NASHIK");
        fields.put("lineId", "LINE-01");
        fields.put("tagCode", tagCode);
        fields.put("description", "desc");
        fields.put("protocol", "OPC_UA");
        fields.put("endpointUrl", "opc.tcp://localhost");
        fields.put("externalAddress", "ns=3;i=1002");
        fields.put("dataType", "FLOAT");
        fields.put("writable", "false");
        fields.put("deadband", "0.2");
        if (overrides != null) {
            fields.putAll(overrides);
        }
        return new TagBatchRowInput(fields, null, false);
    }

    private static String key(String siteId, String lineId, String tagCode) {
        return siteId + '|' + lineId + '|' + tagCode;
    }
}
