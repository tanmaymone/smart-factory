package com.factory.central.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Line() {
    }

    public Line(String siteId, String lineId, String name) {
        this.siteId = siteId;
        this.lineId = lineId;
        this.name = name;
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
}
