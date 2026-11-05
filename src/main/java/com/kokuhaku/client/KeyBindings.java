package com.kokuhaku.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    /** T opens the timetable. (Chat doesn't exist, so T is free.) */
    public static final KeyBinding TIMETABLE = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.kokuhaku.timetable", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_T, "category.kokuhaku"));

    /** V mutes your mic. */
    public static final KeyBinding MUTE = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.kokuhaku.mute", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.kokuhaku"));

    /** G gets your phone out. */
    public static final KeyBinding PHONE = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.kokuhaku.phone", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.kokuhaku"));

    private KeyBindings() {
    }

    /** Forces the class to load so the binding registers during client init. */
    public static void init() {
    }
}
