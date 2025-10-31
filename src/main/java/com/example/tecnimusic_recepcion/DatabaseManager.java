package com.example.tecnimusic_recepcion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error Crítico: No se encontró la clase del driver de MySQL. Verifique la dependencia 'mysql-connector-j' en pom.xml.", e);
        }
    }

    private static volatile DatabaseManager instance;
    private final DatabaseConfig dbConfig;

    private DatabaseManager() {
        this.dbConfig = new DatabaseConfig();
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    public static void resetInstance() {
        synchronized (DatabaseManager.class) {
            instance = null;
        }
    }

    public Connection getConnection() throws SQLException {
        DriverManager.setLoginTimeout(10);

        String url = "jdbc:mysql://" +
                dbConfig.getHost() + ":" +
                dbConfig.getPort() + "/" +
                dbConfig.getDbName();

        return DriverManager.getConnection(url, dbConfig.getUser(), dbConfig.getPassword());
    }
}
