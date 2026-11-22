package com.factory.central.registry;

import java.io.Serializable;
import java.util.Objects;

public class LineId implements Serializable {

    private String siteId;
    private String lineId;

    public LineId() {
    }

    public LineId(String siteId, String lineId) {
        this.siteId = siteId;
        this.lineId = lineId;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LineId lineId1)) {
            return false;
        }
        return Objects.equals(siteId, lineId1.siteId) && Objects.equals(lineId, lineId1.lineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteId, lineId);
    }
}
