package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.section.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLData extends SQLData {

    // Connection Information
    private String host;
    private String user;
    private String database;
    private String password;
    private int port;

    // Volatile: written by whichever thread reconnects, read by every async get/save/clear task.
    @Nullable
    private volatile Connection connection;

    @Override
    public void setup() {
        host = DatabaseSettings.getHost();
        user = DatabaseSettings.getUsername();
        database = DatabaseSettings.getDatabase();
        password = DatabaseSettings.getPassword();
        port = DatabaseSettings.getPort();

        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        try {
            openConnection();
            if (connection == null) throw new IllegalStateException("Connection is null");
            try (PreparedStatement preparedStatement =  connection.prepareStatement("CREATE TABLE IF NOT EXISTS `COSMETICDATABASE` " +
                    "(UUID varchar(36) PRIMARY KEY, " +
                    "COSMETICS MEDIUMTEXT " +
                    ");")) {
                preparedStatement.execute();
            }
        } catch (SQLException | IllegalStateException e) {
            plugin.getLogger().severe("");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("MySQL DATABASE CAN NOT BE REACHED.");
            plugin.getLogger().severe("CHECK CONFIG FOR ERRORS");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("SAFETY SHUTTING DOWN SERVER");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("");
            Bukkit.shutdown();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear(UUID uniqueId) {
        Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), () -> {
            try (PreparedStatement preparedSt = preparedStatement("DELETE FROM COSMETICDATABASE WHERE UUID=?;")) {
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

        close(); // Discard whatever stale handle we were holding before replacing it.

        try {
            // Legacy driver class, kept for old Connector/J jars. JDBC 4 auto-registers drivers from
            // the classpath, so failing to find it must not stop us from connecting.
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Not fatal - see above.
        }

        // Let a failure propagate: setup() turns it into the "database can not be reached" shutdown,
        // and preparedStatement() logs it. Swallowing it here only produced a null connection and an
        // NPE further along, hiding why the connection never came back.
        connection = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database, setupProperties());
        connectionChanged();
    }

    public void close() {
        // Detach the handle before closing it. close() runs off-thread, so reading the field inside
        // the task would race openConnection()'s reassignment and shut the *fresh* connection down -
        // leaving the reconnect path permanently closing whatever it had just opened.
        final Connection stale = this.connection;
        this.connection = null;
        connectionChanged();
        if (stale == null) return;

        Runnable close = () -> {
            try {
                stale.close();
            } catch (SQLException e) {
                MessagesUtil.sendDebugMessages("Failed to close the previous MySQL connection: " + e.getMessage(), Level.WARNING);
            }
        };
        // On shutdown the scheduler no longer runs tasks, so close inline instead of dropping it.
        if (HMCCosmeticsPlugin.getInstance().isDisabled()) close.run();
        else Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), close);
    }

    @NotNull
    private Properties setupProperties() {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        return props;
    }

    @Override
    public PreparedStatement preparedStatement(String query) {
        PreparedStatement ps = null;

        if (!isConnectionOpen(connection)) {
            MessagesUtil.sendDebugMessages("The MySQL database connection is not open (Could the database been idle for to long?). Reconnecting...", Level.WARNING);
            try {
                openConnection();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        try {
            if (connection == null) throw new IllegalStateException("Connection is null");
            ps = connection.prepareStatement(query);
        } catch (SQLException | IllegalStateException e) {
            e.printStackTrace();
        }

        return ps;
    }
}