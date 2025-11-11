package com.hibiscusmc.hmccosmetics.util;

import org.bukkit.attribute.Attribute;
import org.jetbrains.annotations.ApiStatus;

public class AttributeWrapper {
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    public static Attribute SCALE;

    static {
        try {
            SCALE = Attribute.SCALE;
        } catch (Exception e) {
            SCALE = Attribute.valueOf("GENERIC_SCALE");
        }
    }
}
