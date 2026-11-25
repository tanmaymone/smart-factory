package com.factory.edge.config;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Owner of the apply-side status snapshot. Written only from the reconcile thread; read by the
 * diagnostics API.
 */
@Component
public class EdgeApplyState {

    private final AtomicReference<EdgeApplyView> view = new AtomicReference<>(EdgeApplyView.empty());

    public EdgeApplyView get() {
        return view.get();
    }

    public void set(EdgeApplyView next) {
        view.set(next == null ? EdgeApplyView.empty() : next);
    }
}
