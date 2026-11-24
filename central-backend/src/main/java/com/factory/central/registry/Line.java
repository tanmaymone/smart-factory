package com.factory.central.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "lines")
@IdClass(LineId.class)
public class Line {

    @Id
    @Column(name = "site_id", length = 64, nullable = false)
    private String siteId;

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String lineId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle", length = 32, nullable = false)
    private RegistryLifecycle lifecycle;

    protected Line() {
    }

    public Line(String siteId, String lineId, String name) {
        this(siteId, lineId, name, RegistryLifecycle.ACTIVE);
    }

    public Line(String siteId, String lineId, String name, RegistryLifecycle lifecycle) {
        this.siteId = siteId;
        this.lineId = lineId;
        this.name = name;
        this.lifecycle = lifecycle == null ? RegistryLifecycle.ACTIVE : lifecycle;
    }

    public void apply(String name, RegistryLifecycle lifecycle) {
        this.name = name;
        if (lifecycle != null) {
            this.lifecycle = lifecycle;
        }
    }

    public String getSiteId() {
        return siteId;
    }

    public String getLineId() {
        return lineId;
    }

    public String getName() {
        return name;
    }

    public RegistryLifecycle getLifecycle() {
        return lifecycle;
    }
}
