package com.factory.central.registry;

import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class RegistrySeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RegistrySeedRunner.class);

    private final SiteRepository siteRepository;
    private final LineRepository lineRepository;
    private final TagMappingRepository tagMappingRepository;
    private final RegistryProperties registryProperties;
    private final SiteConfigRevisionService revisionService;

    public RegistrySeedRunner(
            SiteRepository siteRepository,
            LineRepository lineRepository,
            TagMappingRepository tagMappingRepository,
            RegistryProperties registryProperties,
            SiteConfigRevisionService revisionService) {
        this.siteRepository = siteRepository;
        this.lineRepository = lineRepository;
        this.tagMappingRepository = tagMappingRepository;
        this.registryProperties = registryProperties;
        this.revisionService = revisionService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!registryProperties.isSeedEnabled()) {
            log.info("Registry seed skipped: app.registry.seed-enabled=false");
            return;
        }
        TreeSet<String> bumpedSites = new TreeSet<>();
        ensureSite(new Site("INDORE", "Indore Plant", "Asia/Kolkata", SiteStatus.ONLINE));
        ensureSite(new Site("PUNE", "Pune Plant", "Asia/Kolkata", SiteStatus.ONLINE));

        ensureLine(new Line("INDORE", "LINE-01", "Reactor Line 1"), bumpedSites);
        ensureLine(new Line("INDORE", "LINE-02", "Packaging Line"), bumpedSites);
        ensureLine(new Line("PUNE", "LINE-01", "Reactor Line 1"), bumpedSites);

        String endpoint = registryProperties.getOpcEndpointUrl();
        for (TagSeed seed : sampleTags()) {
            ensureTag(seed, endpoint, bumpedSites);
        }
        revisionService.bumpAll(bumpedSites);

        log.info(
                "Registry seed complete: sites={}, lines={}, tags={}",
                siteRepository.count(),
                lineRepository.count(),
                tagMappingRepository.count());
    }

    private void ensureSite(Site site) {
        if (siteRepository.findById(site.getId()).isEmpty()) {
            siteRepository.save(site);
            // Match SiteRegistryService.create: seed version 1 only; line/tag inserts bump.
            revisionService.seedIfAbsent(site.getId());
        }
    }

    private void ensureLine(Line line, TreeSet<String> bumpedSites) {
        LineId id = new LineId(line.getSiteId(), line.getLineId());
        if (lineRepository.findById(id).isEmpty()) {
            lineRepository.save(line);
            bumpedSites.add(line.getSiteId());
        }
    }

    private void ensureTag(TagSeed seed, String endpointUrl, TreeSet<String> bumpedSites) {
        tagMappingRepository
                .findBySiteIdAndLineIdAndTagCode(seed.siteId(), seed.lineId(), seed.tagCode())
                .ifPresentOrElse(
                        existing -> {
                            if (existing.reconcileSeed(
                                    endpointUrl,
                                    seed.warningLow(),
                                    seed.warningHigh(),
                                    seed.criticalLow(),
                                    seed.criticalHigh(),
                                    seed.externalAddress())) {
                                tagMappingRepository.save(existing);
                                bumpedSites.add(seed.siteId());
                            }
                        },
                        () -> {
                            tagMappingRepository.save(seed.toEntity(endpointUrl));
                            bumpedSites.add(seed.siteId());
                        });
    }

    private static List<TagSeed> sampleTags() {
        return List.of(
                TagSeed.valve(
                        "INDORE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "Inlet valve position",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007"),
                TagSeed.temp(
                        "INDORE",
                        "LINE-01",
                        "REACTOR_TEMP_C",
                        "Reactor temperature",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002"),
                TagSeed.setpoint(
                        "INDORE",
                        "LINE-01",
                        "REACTOR_TEMP_SP",
                        "Reactor temperature setpoint",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007"),
                TagSeed.valve(
                        "INDORE",
                        "LINE-02",
                        "VALVE_201_POS",
                        "Packaging valve position",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007"),
                TagSeed.valve(
                        "PUNE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "Inlet valve position",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007"),
                TagSeed.temp(
                        "PUNE",
                        "LINE-01",
                        "REACTOR_TEMP_C",
                        "Reactor temperature",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002"),
                TagSeed.setpoint(
                        "PUNE",
                        "LINE-01",
                        "REACTOR_TEMP_SP",
                        "Reactor temperature setpoint",
                        "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007"));
    }

    private record TagSeed(
            String siteId,
            String lineId,
            String tagCode,
            String description,
            String externalAddress,
            String unit,
            boolean writable,
            Double minValue,
            Double maxValue,
            double deadband,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh) {

        static TagSeed valve(String siteId, String lineId, String tagCode, String description, String address) {
            return new TagSeed(
                    siteId, lineId, tagCode, description, address, "%", true, 0.0, 100.0, 0.5, null, null, null, null);
        }

        static TagSeed temp(String siteId, String lineId, String tagCode, String description, String address) {
            return new TagSeed(
                    siteId, lineId, tagCode, description, address, "°C", false, null, null, 0.2, 30.0, 70.0, 20.0, 80.0);
        }

        static TagSeed setpoint(String siteId, String lineId, String tagCode, String description, String address) {
            return new TagSeed(
                    siteId, lineId, tagCode, description, address, "°C", true, 15.0, 90.0, 0.1, null, null, null, null);
        }

        TagMapping toEntity(String endpointUrl) {
            return new TagMapping(
                    UUID.randomUUID(),
                    siteId,
                    lineId,
                    tagCode,
                    description,
                    Protocol.OPC_UA,
                    endpointUrl,
                    externalAddress,
                    TagDataType.FLOAT,
                    unit,
                    writable,
                    minValue,
                    maxValue,
                    deadband,
                    warningLow,
                    warningHigh,
                    criticalLow,
                    criticalHigh,
                    null);
        }
    }
}
