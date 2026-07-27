package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class SQLData extends Data {

    /** Seconds the driver may spend on the {@link Connection#isValid(int)} round trip. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    /**
     * How long a successful validation is trusted before the connection is probed again.
     * {@link Connection#isValid(int)} is a network round trip, and a save/load burst would otherwise
     * pay one per statement.
     */
    private static final long VALIDATION_CACHE_MILLIS = 30_000L;

    /** Written from whichever async task last validated; read by all of them. */
    private volatile long lastValidatedAt;
    @Override
    @SuppressWarnings({"resource"}) // Duplicate is from deprecated InternalData
    public CompletableFuture<UserData> get(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            UserData data = new UserData(uniqueId);

            try (PreparedStatement preparedStatement = preparedStatement("SELECT * FROM COSMETICDATABASE WHERE UUID = ?;")){
                preparedStatement.setString(1, uniqueId.toString());
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        String rawData = rs.getString("COSMETICS");
                        HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics = deserializeData(rawData);
                        data.setCosmetics(cosmetics);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return data;
        });
    }

    @Override
    @SuppressWarnings("resource")
    public void save(CosmeticUser user) {
        Runnable run = () -> {
            try (PreparedStatement preparedSt = preparedStatement("REPLACE INTO COSMETICDATABASE(UUID,COSMETICS) VALUES(?,?);")) {
                preparedSt.setString(1, user.getUniqueId().toString());
                preparedSt.setString(2, serializeData(user));
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };
        if (!HMCCosmeticsPlugin.getInstance().isDisabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), run);
        } else {
            run.run();
        }
    }

    /**
     * @return true if {@code connection} is present and can still be used to run a statement.
     *
     * <p>{@link Connection#isClosed()} alone is not enough: it only reports a {@code close()} we made
     * ourselves, so a connection dropped server-side — a MySQL {@code wait_timeout} on an idle
     * server, a restarted database, a killed session — keeps reporting "open" and only reveals itself
     * when a statement fails. {@link Connection#isValid(int)} round-trips to the server and catches
     * that, which is the case the callers' "could the database have been idle for too long?"
     * reconnect exists for.</p>
     *
     * <p>A connection we cannot interrogate is reported as unusable rather than thrown from: every
     * caller answers false by reconnecting, so throwing would replace a recoverable stale connection
     * with a crash.</p>
     */
    protected boolean isConnectionOpen(@Nullable Connection connection) {
        if (connection == null) return false;
        try {
            if (connection.isClosed()) return false;

            long now = System.currentTimeMillis();
            if (now - this.lastValidatedAt < VALIDATION_CACHE_MILLIS) return true;

            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) return false;
            this.lastValidatedAt = now;
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Drops the cached validation so the next {@link #isConnectionOpen(Connection)} probes for real.
     * Call whenever the connection field is replaced or closed.
     */
    protected void connectionChanged() {
        this.lastValidatedAt = 0L;
    }

    public abstract PreparedStatement preparedStatement(String query);
}
