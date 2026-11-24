package com.factory.central.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "actors")
public class ActorEntity {

    @Id
    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private ActorRole role;

    protected ActorEntity() {}

    public ActorEntity(String actorId, String displayName, ActorRole role) {
        this.actorId = actorId;
        this.displayName = displayName;
        this.role = role;
    }

    public String getActorId() {
        return actorId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ActorRole getRole() {
        return role;
    }

    public void setRole(ActorRole role) {
        this.role = role;
    }

    public ActorContext toContext() {
        return new ActorContext(actorId, displayName, role);
    }
}
