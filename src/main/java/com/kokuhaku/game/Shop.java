package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import net.minecraft.server.network.ServerPlayerEntity;

/** What your shift money is for: a shop that sells a little peace of mind, and something for a girl you like. */
final class Shop {
    private static final int CHAIN = 1500, SPRAY = 1200, SWEETS = 600, CHAIN_NIGHTS = 3;
    private static final String NAME = "Shop";

    private Shop() {
    }

    private static boolean pay(ServerPlayerEntity p, RunState s, int price) {
        if (s.yen < price) {
            Talk.say(p, NAME, "That's ¥" + price + ", and you've got ¥" + Math.max(0, s.yen) + ". The till by the station's hiring.", Talk.leave("Okay."));
            return false;
        }
        s.yen -= price;
        s.markDirty();
        return true;
    }

    static void open(ServerPlayerEntity p, RunState s) {
        Talk.say(p, NAME, "\"Getting a lot of students in lately. Nobody says why.\"",
                Talk.option("Door chain, ¥" + CHAIN + " (safe for " + CHAIN_NIGHTS + " nights).", q -> {
                    if (!pay(q, s, CHAIN)) return;
                    s.chains += CHAIN_NIGHTS;
                    s.markDirty();
                    Kokuhaku.LOG.info("Bought a door chain");
                    RunManager.tell(q, "", "You fit it as soon as you get in. Nothing's coming through that door for " + s.chains + " nights.");
                }),
                Talk.option("Pepper spray, ¥" + SPRAY + " (breaks any grip, once).", q -> {
                    if (!pay(q, s, SPRAY)) return;
                    s.sprays++;
                    s.markDirty();
                    RunManager.tell(q, "", "It's in your pocket. You've got " + s.sprays + ".");
                }),
                Talk.option("Box of sweets, ¥" + SWEETS + " (a gift for someone).", q -> {
                    if (!pay(q, s, SWEETS)) return;
                    s.gifts++;
                    s.markDirty();
                    RunManager.tell(q, "", "You've got " + s.gifts + (s.gifts == 1 ? " box" : " boxes") + " to give away. Talk to a girl.");
                }),
                Talk.leave("Just looking."));
    }
}
