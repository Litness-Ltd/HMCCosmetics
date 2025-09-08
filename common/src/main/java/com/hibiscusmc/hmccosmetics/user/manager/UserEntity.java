package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class UserEntity {

    @Getter
    private UUID owner;
    @Getter
    private List<Player> viewers = new ArrayList<>();
    @Getter @Setter
    private Long viewerLastUpdate = 0L;
    @Getter @Setter
    private Long lastPositionUpdate = 0L;
    @Getter @Setter
    private List<Integer> ids = new ArrayList<>();
    @Getter
    private Location location;

    public UserEntity(UUID owner) {
        this.owner = owner;
    }

    public List<Player> refreshViewers() {
        return refreshViewers(location);
    }

    public List<Player> refreshViewers(Location location) {
        if (System.currentTimeMillis() - viewerLastUpdate <= 1000) return List.of(); //Prevents mass refreshes

        Entity ownerPlayer = Bukkit.getEntity(owner);
        if (ownerPlayer == null) {
            MessagesUtil.sendDebugMessages("Owner is null (refreshViewers), returning empty list");
            return List.of();
        }

        final HashSet<Player> players = new HashSet<>(HMCCPacketManager.getViewers(location));
        final ArrayList<Player> newPlayers = new ArrayList<>();
        final ArrayList<Player> removePlayers = new ArrayList<>();

        // Go through all nearby players, check if they are new to the viewers list.
        for (Player player : players) {
            CosmeticUser user = CosmeticUsers.getUser(player);
            if (user != null && owner != user.getUniqueId() && user.isInWardrobe() && !player.canSee(ownerPlayer)) { // Fixes issue where players in wardrobe would see other players cosmetics if they were not in wardrobe
                removePlayers.add(player);
                continue;
            }
            if (!viewers.contains(player)) {
                viewers.add(player);
                newPlayers.add(player);
            }
        }
        // Basically, if they are not nearby, they are still in the viewers and we need to kick em to the curb
        for (Player viewerPlayer : viewers) {
            if (!players.contains(viewerPlayer)) {
                removePlayers.add(viewerPlayer);
            }
        }

        // If there are players for removal, send the packets to them
        if (!removePlayers.isEmpty()) {
            HMCCPacketManager.sendEntityDestroyPacket(ids, removePlayers);
            viewers.removeAll(removePlayers);
        }
        setViewerLastUpdate(System.currentTimeMillis());
        return newPlayers;
    }

    public void teleport(Location location) {
        if (this.getLocation() != null && this.getLocation().getWorld() == location.getWorld()) {
            // Was thinking about using schedulers to just send the packet later... but that would be a lot of tasks and
            // would probably cause more lag. Furthermore, the server "ticks" the cosmetics every second by defualt. So it's fine like this.
            //if (System.currentTimeMillis() - getLastPositionUpdate() <= Settings.getPacketEntityTeleportCooldown()) return;
        }
        this.location = location;
        for (Integer entity : ids) {
            HMCCPacketManager.sendTeleportPacket(entity, location, false, getViewers());
        }
        setLastPositionUpdate(System.currentTimeMillis());
    }

    public void setRotation(int yaw) {
        setRotation(yaw, false);
    }

    public void setRotation(int yaw, boolean additonalPacket) {
        location.setYaw(yaw);
        for (Integer entity : ids) {
            // First person backpacks need both packets to rotate properly, otherwise they look off
            // Regular backpacks just need the look packet
            if (additonalPacket) HMCCPacketManager.sendRotationPacket(entity, yaw, false, getViewers());
            HMCCPacketManager.sendRotateHeadPacket(entity, location, getViewers());
        }
    }
}
