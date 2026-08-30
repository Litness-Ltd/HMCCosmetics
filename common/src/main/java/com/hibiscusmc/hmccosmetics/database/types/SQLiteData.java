package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteData extends SQLData {

    // Volatile: written by whichever thread reconnects, read by every async get/save/clear task.
    private volatile Connection connection;

    @Override
    public void setup() {
        File dataFolder = new File(HMCCosmeticsPlugin.getInstance().getDataFolder(), "database.db");
        boolean exists = dataFolder.exists();

        if (!exists) {
            try {
                boolean created = dataFolder.createNewFile();
                if (!created) throw new IOException("File didn't exist but now does");
            } catch (IOException e) {
                MessagesUtil.sendDebugMessages("File write error. Database will not work properly", Level.SEVERE);
            }
        }

        try {
            openConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `COSMETICDATABASE` " +
                    "(UUID varchar(36) PRIMARY KEY, " +
                    "COSMETICS MEDIUMTEXT " +
                    ");")) {
                preparedStatement.execute();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("resource")
    public void clear(UUID uniqueId) {
        Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), () -> {
            try (PreparedStatement preparedSt = preparedStatement("DELETE FROM COSMETICDATABASE WHERE UUID=?;")){
                preparedSt.setString(1, uniqueId.toString());
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Synchronized so two async tasks that both find the connection stale don't open (and leak) two
    // replacements.
    private synchronized void openConnection() throws SQLException {
        if (isConnectionOpen(connection)) return;

        // Discard whatever stale handle we were holding before replacing it, so a connection that
        // failed validation is not simply leaked.
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                MessagesUtil.sendDebugMessages("Failed to close the previous SQLite connection: " + e.getMessage(), Level.WARNING);
            }
            connection = null;
            connectionChanged();
        }

        File dataFolder = new File(HMCCosmeticsPlugin.getInstance().getDataFolder(), "database.db");

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            connectionChanged();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PreparedStatement preparedStatement(String query) throws SQLException {
        if (!isConnectionOpen(connection)) {
            MessagesUtil.sendDebugMessages("Connection is not open");
            openConnection();
        }

        // Read the volatile field once: a concurrent reconnect could replace it between check and use.
        final Connection current = this.connection;
        if (current == null) throw new SQLException("No SQLite connection available after reconnect attempt");
        return current.prepareStatement(query);
    }
}
