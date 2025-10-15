package com.example.tecnimusic_recepcion;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error Crítico: No se encontró la clase del driver de MySQL. Verifique la dependencia 'mysql-connector-j' en pom.xml.", e);
        }
    }

    private static volatile DatabaseManager instance;
    private final Properties properties;

    private DatabaseManager() {
        this.properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IOException("No se encuentra el archivo 'config.properties' en la carpeta resources.");
            }
            this.properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error fatal: No se pudo cargar el archivo de configuración 'config.properties'.", e);
        }
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

    public Connection getConnection() throws SQLException {
        DriverManager.setLoginTimeout(10);

        String url = "jdbc:mysql://" +
                properties.getProperty("db.host") + ":" +
                properties.getProperty("db.port") + "/" +
                properties.getProperty("db.name") +
                properties.getProperty("db.params");

        return DriverManager.getConnection(url, properties.getProperty("db.user"), properties.getProperty("db.password"));
    }
}
