package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.world.town.Town;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.Random;

/**
 * Near the end of the month, the power goes across the whole town at once. Out
 * on the street she stops pretending and comes for you. At home you've got as
 * long as it takes someone to climb the stairs, and one place to choose.
 */
final class Blackout {
    private static final int DAY = 11, AT = Schedule.EVENING + 60, DARK_TICKS = 20 * 90;
    private static final Random RANDOM = new Random();
    private static final String[] PLACES = {"Under the bed.", "In the wardrobe.", "Behind the door."};

    private Blackout() {
    }

    private static boolean due(RunState s) {
        return s.day == DAY && s.minute == AT && !s.devoted && !Living.active(s) && !Kept.on(s);
    }

    static void minute(RunState s, List<ServerPlayerEntity> living, Town town) {
        if (!due(s)) return;
        Kokuhaku.LOG.info("Blackout");
        Shadow.saw(s, "The whole town went dark at seven. She'd been in the substation since lunch.");
        for (ServerPlayerEntity p : living) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, DARK_TICKS, 0, false, false));
            p.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1f, 0.6f);
            Radio.silence(p);
            if (RunManager.isHomeNow(p, s)) hide(p, s);
            else {
                RunManager.tell(p, "The power's gone.", "Every light in town, all at once. Get home.");
                Stalker.comeFor(p, s, town);
            }
        }
    }

    private static void hide(ServerPlayerEntity p, RunState s) {
        int wrong = RANDOM.nextInt(PLACES.length);
        Talk.Option[] options = new Talk.Option[PLACES.length];
        for (int i = 0; i < PLACES.length; i++) {
            boolean found = i == wrong;
            options[i] = Talk.option(PLACES[i], q -> {
                if (found) {
                    s.fondness[s.freeYandere()] = Math.min(5, s.fondness[s.freeYandere()] + 1);
                    s.notes.add("The night the power went, someone found you in the dark. They didn't do anything. They said goodnight.");
                    s.markDirty();
                    Talk.say(q, "In the dark", "The steps stop right beside you. Someone crouches down. You can hear them smiling. \"Goodnight.\" Then they're gone, and the lights don't come back until morning.", Talk.leave("..."));
                } else {
                    Talk.say(q, "In the dark", "The steps go past. The door of your room opens, stays open a long time, and closes. You don't move until it's light.", Talk.leave("..."));
                }
            });
        }
        Talk.say(p, "The power's gone.", "Every light on the street, all at once. Then, downstairs, your front door opens. You locked that. There's time to do one thing.", options);
    }
}
