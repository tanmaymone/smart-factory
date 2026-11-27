package com.factory.central.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sites")
public class Site {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private SiteStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", length = 32, nullable = false)
    private RegistryLifecycle lifecycle;

    @Column(name = "site_label", length = 24, nullable = false)
    private String siteLabel = EntityDisplayLabel.DEFAULT_SITE;

    @Column(name = "line_label", length = 24, nullable = false)
    private String lineLabel = EntityDisplayLabel.DEFAULT_LINE;

    protected Site() {
    }

    public Site(String id, String name, String timezone, SiteStatus status) {
        this(id, name, timezone, status, RegistryLifecycle.ACTIVE);
    }

    public Site(String id, String name, String timezone, SiteStatus status, RegistryLifecycle lifecycle) {
        this(id, name, timezone, status, lifecycle, null, null);
    }

    public Site(
            String id,
            String name,
            String timezone,
            SiteStatus status,
            RegistryLifecycle lifecycle,
            String siteLabel,
            String lineLabel) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
        this.status = status;
        this.lifecycle = lifecycle == null ? RegistryLifecycle.ACTIVE : lifecycle;
        this.siteLabel = EntityDisplayLabel.normalizeSite(siteLabel);
        this.lineLabel = EntityDisplayLabel.normalizeLine(lineLabel);
    }

    public void apply(String name, String timezone, RegistryLifecycle lifecycle, String siteLabel, String lineLabel) {
        this.name = name;
        this.timezone = timezone;
        if (lifecycle != null) {
            this.lifecycle = lifecycle;
        }
        if (siteLabel != null) {
            this.siteLabel = siteLabel;
        }
        if (lineLabel != null) {
            this.lineLabel = lineLabel;
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public SiteStatus getStatus() {
        return status;
    }

    public RegistryLifecycle getLifecycle() {
        return lifecycle;
    }

    public String getSiteLabel() {
        return siteLabel;
    }

    public String getLineLabel() {
        return lineLabel;
    }
}
