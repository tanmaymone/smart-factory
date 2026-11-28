package com.factory.central.access;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActorRepository extends JpaRepository<ActorEntity, String> {

    long countByRole(ActorRole role);
}
