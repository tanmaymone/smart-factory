package com.factory.central.access;

public final class ActorContextHolder {

    private static final ThreadLocal<ActorContext> CURRENT = new ThreadLocal<>();

    private ActorContextHolder() {}

    public static void set(ActorContext actor) {
        CURRENT.set(actor);
    }

    public static ActorContext current() {
        ActorContext actor = CURRENT.get();
        return actor == null ? ActorContext.anonymous() : actor;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
