package com.kokuhaku.item;

import com.kokuhaku.Kokuhaku;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    /** Right-click your front door with it. An unlocked door at night is a risk. */
    public static final Item ROOM_KEY = Registry.register(Registries.ITEM, new Identifier(Kokuhaku.MOD_ID, "room_key"),
            new Item(new Item.Settings().maxCount(1)));

    /** What she carries. Only ever seen once. */
    public static final Item BAT = Registry.register(Registries.ITEM, new Identifier(Kokuhaku.MOD_ID, "bat"),
            new Item(new Item.Settings().maxCount(1)));

    /** What the teacher's got in their hand. */
    public static final Item CHALK = Registry.register(Registries.ITEM, new Identifier(Kokuhaku.MOD_ID, "chalk"),
            new Item(new Item.Settings().maxCount(1)));

    private ModItems() {
    }

    /** Forces the class to load so the items register during mod init. */
    public static void init() {
    }
}
