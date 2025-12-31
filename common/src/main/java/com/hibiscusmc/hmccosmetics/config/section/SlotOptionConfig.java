package com.hibiscusmc.hmccosmetics.config.section;

import lombok.Getter;
import org.bukkit.inventory.EquipmentSlot;

public class SlotOptionConfig {

    @Getter
    private final EquipmentSlot slot;
    @Getter
    private final boolean addEnchantments;
    @Getter
    private final boolean requireEmpty;
    @Getter
    private final boolean addElytraComponent;

    public SlotOptionConfig(EquipmentSlot slot, boolean addEnchantments, boolean requireEmpty, boolean addElytraComponent) {
        this.slot = slot;
        this.addEnchantments = addEnchantments;
        this.requireEmpty = requireEmpty;
        this.addElytraComponent = addElytraComponent;
    }
}
