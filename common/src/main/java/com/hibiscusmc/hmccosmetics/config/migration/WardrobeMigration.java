package com.hibiscusmc.hmccosmetics.config.migration;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class WardrobeMigration {

    public static void migrate(@NotNull HMCCosmeticsPlugin instance) {
        File moveToFile = new File(instance.getDataFolder().getPath() + "/wardrobes/defaultwardrobe.yml");

        YamlConfiguration moveTo = YamlConfiguration.loadConfiguration(moveToFile);
        FileConfiguration moveFrom = instance.getConfig();
        ConfigurationSection wardrobes = moveFrom.getConfigurationSection("wardrobe.wardrobes");
        if (wardrobes == null) return;
        if (moveFrom.getKeys(false).isEmpty()) return;

        for (String key : wardrobes.getKeys(false)) {
            moveTo.set(key, wardrobes.getConfigurationSection(key));
        }

        moveFrom.set("wardrobe.wardrobes", null);
        try {
            instance.saveConfig();
            moveTo.save(moveToFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}