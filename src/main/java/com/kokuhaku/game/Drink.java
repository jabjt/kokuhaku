package com.kokuhaku.game;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;

/** What the vending machines sell. Effects wear off after a minute. */
public enum Drink {
    GREEN_TEA("Green Tea", 130, "A clear head. You walk faster.", StatusEffects.SPEED),
    COFFEE("Canned Coffee", 150, "Wide awake. You see better in the dark.", StatusEffects.NIGHT_VISION),
    COLA("Cola", 160, "A sugar rush. You jump higher.", StatusEffects.JUMP_BOOST);

    public final String label;
    public final int price;
    public final String description;
    public final StatusEffect effect;

    Drink(String label, int price, String description, StatusEffect effect) {
        this.label = label;
        this.price = price;
        this.description = description;
        this.effect = effect;
    }
}
