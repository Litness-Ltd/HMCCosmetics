package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.util.Octree;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class UserSearchManager implements Listener {
    private final Map<UUID, Octree<Player>> worldOctrees = new HashMap<>();
    private final Map<UUID, Octree.Point3D> playerPositions = new HashMap<>();

    private static final double WORLD_HALF_SIZE = 30_000_000;

    private Octree<Player> getOrCreateOctree(World world) {
        return worldOctrees.computeIfAbsent(world.getUID(), $ -> {
            Octree.BoundingBox worldBoundary = new Octree.BoundingBox(
                new Octree.Point3D(0, 160, 0), WORLD_HALF_SIZE
            );
            return new Octree<>(worldBoundary);
        });
    }

    private Octree.Point3D toPoint3D(Location location) {
        return new Octree.Point3D(location.getX(), location.getY(), location.getZ());
    }

    public boolean addPlayer(Player player) {
        Octree<Player> octree = getOrCreateOctree(player.getWorld());
        Octree.Point3D point = toPoint3D(player.getLocation());

        if(octree.insert(point, player)) {
            playerPositions.put(player.getUniqueId(), point);
            return true;
        }
        return false;
    }

    public boolean removePlayer(Player player) {
        Octree<Player> octree = worldOctrees.get(player.getWorld().getUID());
        if (octree == null) return false;

        Octree.Point3D point = playerPositions.remove(player.getUniqueId());
        if (point != null) {
            return octree.remove(point, player);
        }
        return false;
    }

    public void updatePlayerPosition(Player player) {
        removePlayer(player);
        addPlayer(player);
    }

    public List<Player> getPlayersInRange(Location location, double range) {
        Octree<Player> octree = worldOctrees.get(location.getWorld().getUID());
        if (octree == null) {
            return Collections.emptyList();
        }

        Octree.Point3D point = toPoint3D(location);
        Octree.BoundingBox searchArea = new Octree.BoundingBox(point, range);

        return octree.queryRange(searchArea)
            .stream()
            .filter(Objects::nonNull)
            .toList();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.hasChangedBlock()) {
            updatePlayerPosition(player);
        }
    }

    public void clear() {
        worldOctrees.clear();
        playerPositions.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        addPlayer(event.getPlayer());
    }
}