package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.central.access.ActorContext;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.InMemoryLatestTagCache;
import com.factory.central.cache.LatestTagState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@Tag("SF-REGISTRY")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryLivenessServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private LineRepository lineRepository;

    @Mock
    private TagMappingRepository tagMappingRepository;

    private InMemoryLatestTagCache cache;
    private RegistryLivenessService livenessService;
    private SiteRegistryService siteRegistryService;
    private LineRegistryService lineRegistryService;

    @BeforeEach
    void setUp() {
        cache = new InMemoryLatestTagCache();
        livenessService = new RegistryLivenessService(
                siteRepository, lineRepository, tagMappingRepository, cache);
        SiteConfigRevisionService revisionService = org.mockito.Mockito.mock(SiteConfigRevisionService.class);
        siteRegistryService = new SiteRegistryService(siteRepository, livenessService, revisionService);
        lineRegistryService =
                new LineRegistryService(lineRepository, siteRepository, livenessService, revisionService);
    }

    @Test
    void siteDemotionEvictsNestedTagsAcrossLinesWithoutPerTagLookups() {
        Site site = new Site("NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.ACTIVE);
        when(siteRepository.findById("NASHIK")).thenReturn(Optional.of(site));
        when(siteRepository.save(any(Site.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TagMapping tag1 = activeTag("LINE-01", "T1");
        TagMapping tag2 = activeTag("LINE-02", "T2");

        cache.put(sample("LINE-01", "T1"));
        cache.put(sample("LINE-02", "T2"));

        siteRegistryService.update(
                ActorContext.anonymous(),
                "NASHIK",
                new SiteUpdateRequest("Nashik", "Asia/Kolkata", RegistryLifecycle.DRAFT, null, null));

        assertThat(cache.get("NASHIK", "LINE-01", "T1")).isEmpty();
        assertThat(cache.get("NASHIK", "LINE-02", "T2")).isEmpty();
        verify(tagMappingRepository, never()).findBySiteIdAndLineIdAndTagCode(any(), any(), any());
        assertThat(tag1.getLifecycle()).isEqualTo(RegistryLifecycle.ACTIVE);
        assertThat(tag2.getLifecycle()).isEqualTo(RegistryLifecycle.ACTIVE);
    }

    @Test
    void lineDemotionEvictsOnlyThatLineNestedTags() {
        when(siteRepository.findById("NASHIK"))
                .thenReturn(Optional.of(new Site(
                        "NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.ACTIVE)));
        Line line01 = new Line("NASHIK", "LINE-01", "R1", RegistryLifecycle.ACTIVE);
        Line line02 = new Line("NASHIK", "LINE-02", "R2", RegistryLifecycle.ACTIVE);
        when(lineRepository.findById(new LineId("NASHIK", "LINE-01"))).thenReturn(Optional.of(line01));
        when(lineRepository.findById(new LineId("NASHIK", "LINE-02"))).thenReturn(Optional.of(line02));
        when(lineRepository.save(any(Line.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TagMapping tag1 = activeTag("LINE-01", "T1");
        TagMapping tag2 = activeTag("LINE-02", "T2");
        when(tagMappingRepository.findBySiteIdAndLineIdAndTagCode(eq("NASHIK"), eq("LINE-01"), eq("T1")))
                .thenReturn(Optional.of(tag1));
        when(tagMappingRepository.findBySiteIdAndLineIdAndTagCode(eq("NASHIK"), eq("LINE-02"), eq("T2")))
                .thenReturn(Optional.of(tag2));

        cache.put(sample("LINE-01", "T1"));
        cache.put(sample("LINE-02", "T2"));

        lineRegistryService.update(
                ActorContext.anonymous(),
                "NASHIK",
                "LINE-01",
                new LineUpdateRequest("R1", RegistryLifecycle.DRAFT));

        assertThat(cache.get("NASHIK", "LINE-01", "T1")).isEmpty();
        assertThat(cache.get("NASHIK", "LINE-02", "T2")).isPresent();
        assertThat(livenessService.isLive(tag2)).isTrue();
        assertThat(livenessService.isLive(tag1)).isFalse();
    }

    private static TagMapping activeTag(String lineId, String tagCode) {
        return new TagMapping(
                UUID.randomUUID(),
                "NASHIK",
                lineId,
                tagCode,
                "desc",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1",
                TagDataType.FLOAT,
                "C",
                false,
                null,
                null,
                0.1,
                null,
                null,
                null,
                null,
                null,
                RegistryLifecycle.ACTIVE);
    }

    private static LatestTagState sample(String lineId, String tagCode) {
        return LatestTagState.initial(
                "NASHIK",
                lineId,
                tagCode,
                1.0,
                "GOOD",
                Instant.parse("2026-11-24T12:00:00Z"),
                AlarmSeverity.NONE,
                null);
    }
}
