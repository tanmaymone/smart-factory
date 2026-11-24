package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.central.cache.AlarmDirection;
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

@Tag("SF-M1.3")
@ExtendWith(MockitoExtension.class)
class TagRegistryServiceDeleteTest {

    @Mock
    private TagMappingRepository tagMappingRepository;

    @Mock
    private LineRepository lineRepository;

    private InMemoryLatestTagCache latestTagCache;
    private TagRegistryService service;

    @BeforeEach
    void setUp() {
        latestTagCache = new InMemoryLatestTagCache();
        service = new TagRegistryService(tagMappingRepository, lineRepository, latestTagCache);
    }

    @Test
    void deleteRemovesMappingAndLatestAlarm() {
        UUID id = UUID.randomUUID();
        TagMapping tag = tag(id, "DELETE_ME_TAG");
        latestTagCache.put(LatestTagState.initial(
                "INDORE",
                "LINE-01",
                "DELETE_ME_TAG",
                12.5,
                "GOOD",
                Instant.parse("2026-11-21T00:00:00Z"),
                AlarmSeverity.WARNING,
                AlarmDirection.HIGH));
        when(tagMappingRepository.findById(id)).thenReturn(Optional.of(tag));

        service.delete(id);

        verify(tagMappingRepository).delete(tag);
        assertThat(latestTagCache.get("INDORE", "LINE-01", "DELETE_ME_TAG")).isEmpty();
        assertThat(latestTagCache.findAlarms("INDORE", "LINE-01")).isEmpty();
    }

    @Test
    void deleteMissingIdThrowsTagNotFoundAndLeavesCache() {
        UUID id = UUID.randomUUID();
        latestTagCache.put(LatestTagState.initial(
                "INDORE",
                "LINE-01",
                "KEEP_ME",
                1.0,
                "GOOD",
                Instant.parse("2026-11-21T00:00:00Z"),
                AlarmSeverity.NONE,
                null));
        when(tagMappingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(RegistryException.class)
                .extracting(ex -> ((RegistryException) ex).getCode())
                .isEqualTo(RegistryErrorCode.TAG_NOT_FOUND);

        verify(tagMappingRepository, never()).delete(any());
        assertThat(latestTagCache.get("INDORE", "LINE-01", "KEEP_ME")).isPresent();
    }

    private static TagMapping tag(UUID id, String tagCode) {
        return new TagMapping(
                id,
                "INDORE",
                "LINE-01",
                tagCode,
                "delete me",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=9001",
                TagDataType.FLOAT,
                "Â\u00B0C",
                false,
                null,
                null,
                0.2,
                null,
                null,
                null,
                null,
                null);
    }
}
