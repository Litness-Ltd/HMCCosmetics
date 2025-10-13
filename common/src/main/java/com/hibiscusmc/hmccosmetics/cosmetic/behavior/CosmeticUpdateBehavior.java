package com.hibiscusmc.hmccosmetics.cosmetic.behavior;

import com.hibiscusmc.hmccosmetics.user.CosmeticUser;

/**
 * Generic updates that happen every tick or when manually requested to be dispatched.
 */
public interface CosmeticUpdateBehavior {
    void dispatchUpdate(final CosmeticUser user);
}