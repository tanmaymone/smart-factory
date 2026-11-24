package com.factory.central.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tag_mappings")
public class TagMapping {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "site_id", length = 64, nullable = false)
    private String siteId;

    @Column(name = "line_id", length = 64, nullable = false)
    private String lineId;

    @Column(name = "tag_code", length = 128, nullable = false)
    private String tagCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", length = 32, nullable = false)
    private Protocol protocol;

    @Column(name = "endpoint_url", length = 512, nullable = false)
    private String endpointUrl;

    @Column(name = "external_address", nullable = false)
    private String externalAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", length = 32, nullable = false)
    private TagDataType dataType;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "writable", nullable = false)
    private boolean writable;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "deadband", nullable = false)
    private double deadband;

    @Column(name = "warning_low")
    private Double warningLow;

    @Column(name = "warning_high")
    private Double warningHigh;

    @Column(name = "critical_low")
    private Double criticalLow;

    @Column(name = "critical_high")
    private Double criticalHigh;

    @Column(name = "interlock_rule_key", length = 128)
    private String interlockRuleKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", length = 32, nullable = false)
    private RegistryLifecycle lifecycle;

    protected TagMapping() {
    }

    public TagMapping(
            UUID id,
            String siteId,
            String lineId,
            String tagCode,
            String description,
            Protocol protocol,
            String endpointUrl,
            String externalAddress,
            TagDataType dataType,
            String unit,
            boolean writable,
            Double minValue,
            Double maxValue,
            double deadband,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh,
            String interlockRuleKey) {
        this(
                id,
                siteId,
                lineId,
                tagCode,
                description,
                protocol,
                endpointUrl,
                externalAddress,
                dataType,
                unit,
                writable,
                minValue,
                maxValue,
                deadband,
                warningLow,
                warningHigh,
                criticalLow,
                criticalHigh,
                interlockRuleKey,
                RegistryLifecycle.ACTIVE);
    }

    public TagMapping(
            UUID id,
            String siteId,
            String lineId,
            String tagCode,
            String description,
            Protocol protocol,
            String endpointUrl,
            String externalAddress,
            TagDataType dataType,
            String unit,
            boolean writable,
            Double minValue,
            Double maxValue,
            double deadband,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh,
            String interlockRuleKey,
            RegistryLifecycle lifecycle) {
        this.id = id;
        this.siteId = siteId;
        this.lineId = lineId;
        this.tagCode = tagCode;
        this.description = description;
        this.protocol = protocol;
        this.endpointUrl = endpointUrl;
        this.externalAddress = externalAddress;
        this.dataType = dataType;
        this.unit = unit;
        this.writable = writable;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.deadband = deadband;
        this.warningLow = warningLow;
        this.warningHigh = warningHigh;
        this.criticalLow = criticalLow;
        this.criticalHigh = criticalHigh;
        this.interlockRuleKey = interlockRuleKey;
        this.lifecycle = lifecycle == null ? RegistryLifecycle.ACTIVE : lifecycle;
    }

    public static TagMapping create(UUID id, TagMappingRequest request) {
        RegistryLifecycle lifecycle =
                request.lifecycle() == null ? RegistryLifecycle.ACTIVE : request.lifecycle();
        return new TagMapping(
                id,
                request.siteId(),
                request.lineId(),
                request.tagCode(),
                request.description(),
                request.protocol(),
                request.endpointUrl(),
                request.externalAddress(),
                request.dataType(),
                request.unit(),
                request.writable(),
                request.minValue(),
                request.maxValue(),
                request.deadband(),
                request.warningLow(),
                request.warningHigh(),
                request.criticalLow(),
                request.criticalHigh(),
                request.interlockRuleKey(),
                lifecycle);
    }

    public void apply(TagMappingRequest request) {
        this.tagCode = request.tagCode();
        this.description = request.description();
        this.protocol = request.protocol();
        this.endpointUrl = request.endpointUrl();
        this.externalAddress = request.externalAddress();
        this.dataType = request.dataType();
        this.unit = request.unit();
        this.writable = request.writable();
        this.minValue = request.minValue();
        this.maxValue = request.maxValue();
        this.deadband = request.deadband();
        this.warningLow = request.warningLow();
        this.warningHigh = request.warningHigh();
        this.criticalLow = request.criticalLow();
        this.criticalHigh = request.criticalHigh();
        this.interlockRuleKey = request.interlockRuleKey();
        if (request.lifecycle() != null) {
            this.lifecycle = request.lifecycle();
        }
    }

    public void applyBatch(
            String description,
            Protocol protocol,
            String endpointUrl,
            String externalAddress,
            TagDataType dataType,
            String unit,
            boolean writable,
            Double minValue,
            Double maxValue,
            double deadband,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh,
            String interlockRuleKey,
            RegistryLifecycle lifecycle) {
        this.description = description;
        this.protocol = protocol;
        this.endpointUrl = endpointUrl;
        this.externalAddress = externalAddress;
        this.dataType = dataType;
        this.unit = unit;
        this.writable = writable;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.deadband = deadband;
        this.warningLow = warningLow;
        this.warningHigh = warningHigh;
        this.criticalLow = criticalLow;
        this.criticalHigh = criticalHigh;
        this.interlockRuleKey = interlockRuleKey;
        if (lifecycle != null) {
            this.lifecycle = lifecycle;
        }
    }

    public boolean reconcileSeed(
            String endpointUrl,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh,
            String externalAddress) {
        boolean changed = false;
        if (!Objects.equals(this.endpointUrl, endpointUrl)) {
            this.endpointUrl = endpointUrl;
            changed = true;
        }
        if (!Objects.equals(this.warningLow, warningLow)) {
            this.warningLow = warningLow;
            changed = true;
        }
        if (!Objects.equals(this.warningHigh, warningHigh)) {
            this.warningHigh = warningHigh;
            changed = true;
        }
        if (!Objects.equals(this.criticalLow, criticalLow)) {
            this.criticalLow = criticalLow;
            changed = true;
        }
        if (!Objects.equals(this.criticalHigh, criticalHigh)) {
            this.criticalHigh = criticalHigh;
            changed = true;
        }
        if (retargetLegacyNode(externalAddress)) {
            changed = true;
        }
        return changed;
    }

    /**
     * Move a tag off a known Prosys seed {@code ns=3;i=…} address onto the seeded {@code nsu=} form.
     * Hand-edited addresses (including {@code ns=3;s=…}) are left alone and fail at the edge.
     */
    private boolean retargetLegacyNode(String seeded) {
        if (seeded == null || seeded.isBlank() || seeded.equals(externalAddress)) {
            return false;
        }
        if (!isLegacySeedAddress(tagCode, externalAddress)) {
            return false;
        }
        externalAddress = seeded;
        return true;
    }

    static boolean isLegacySeedAddress(String tagCode, String address) {
        if (address == null) {
            return false;
        }
        return switch (tagCode) {
            case "VALVE_101_POS" -> "ns=3;i=1001".equals(address) || "ns=3;i=1007".equals(address);
            case "REACTOR_TEMP_SP" -> "ns=3;i=1003".equals(address) || "ns=3;i=1007".equals(address);
            case "VALVE_201_POS" -> "ns=3;i=1004".equals(address) || "ns=3;i=1007".equals(address);
            case "REACTOR_TEMP_C" -> "ns=3;i=1002".equals(address);
            default -> false;
        };
    }

    public UUID getId() {
        return id;
    }

    public String getSiteId() {
        return siteId;
    }

    public String getLineId() {
        return lineId;
    }

    public String getTagCode() {
        return tagCode;
    }

    public String getDescription() {
        return description;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getExternalAddress() {
        return externalAddress;
    }

    public TagDataType getDataType() {
        return dataType;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isWritable() {
        return writable;
    }

    public Double getMinValue() {
        return minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public double getDeadband() {
        return deadband;
    }

    public Double getWarningLow() {
        return warningLow;
    }

    public Double getWarningHigh() {
        return warningHigh;
    }

    public Double getCriticalLow() {
        return criticalLow;
    }

    public Double getCriticalHigh() {
        return criticalHigh;
    }

    public String getInterlockRuleKey() {
        return interlockRuleKey;
    }

    public RegistryLifecycle getLifecycle() {
        return lifecycle;
    }
}
