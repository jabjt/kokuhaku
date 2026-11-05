package com.kokuhaku;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static final SoundEvent CAR_ENGINE = register("car_engine");
    public static final SoundEvent CAR_HORN = register("car_horn");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(Kokuhaku.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Forces the class to load so the sounds register during mod init. */
    public static void init() {
    }
}