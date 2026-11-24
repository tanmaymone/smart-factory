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

    protected Site() {
    }

    public Site(String id, String name, String timezone, SiteStatus status) {
        this(id, name, timezone, status, RegistryLifecycle.ACTIVE);
    }

    public Site(String id, String name, String timezone, SiteStatus status, RegistryLifecycle lifecycle) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
        this.status = status;
        this.lifecycle = lifecycle == null ? RegistryLifecycle.ACTIVE : lifecycle;
    }

    public void apply(String name, String timezone, RegistryLifecycle lifecycle) {
        this.name = name;
        this.timezone = timezone;
        if (lifecycle != null) {
            this.lifecycle = lifecycle;
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
}
