package com.hibiscusmc.hmccosmetics.util.search;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.util.Octree;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BukkitPlayerSearchEngine extends PlayerSearchEngine {

    public BukkitPlayerSearchEngine(@NotNull HMCCosmeticsPlugin plugin) {
        super(plugin);
    }

    @Override
    public List<Player> getPlayersInRange(Location location, double range) {
        return location.getNearbyPlayers(range).stream().toList();
    }
}
