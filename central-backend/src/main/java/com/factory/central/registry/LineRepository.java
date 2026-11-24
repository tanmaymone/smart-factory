package com.factory.central.registry;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineRepository extends JpaRepository<Line, LineId> {

    List<Line> findBySiteId(String siteId);
}

