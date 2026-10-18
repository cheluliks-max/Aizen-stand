package com.aizenstand.client;

import com.aizenstand.net.StateSyncPayload;

/** Latest values received from the server; used only for the HUD. */
public final class ClientState {
    public static boolean hasStand;
    public static boolean summoned;
    public static int reiatsu;
    public static int cooldown90;
    public static int cooldown99;

    private ClientState() {
    }

    public static void update(StateSyncPayload p) {
        hasStand = p.hasStand();
        summoned = p.summoned();
        reiatsu = p.reiatsu();
        cooldown90 = p.cooldown90();
        cooldown99 = p.cooldown99();
    }

    public static void reset() {
        hasStand = false;
        summoned = false;
        reiatsu = 0;
        cooldown90 = 0;
        cooldown99 = 0;
    }
}
