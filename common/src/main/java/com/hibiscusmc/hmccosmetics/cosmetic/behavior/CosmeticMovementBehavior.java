package com.hibiscusmc.hmccosmetics.cosmetic.behavior;

import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.bukkit.Location;

/**
 * Updates cosmetics whenever a player moves.
 */
public interface CosmeticMovementBehavior {
    void dispatchMove(
        final CosmeticUser user,
        final Location from,
        final Location to
    );
}
