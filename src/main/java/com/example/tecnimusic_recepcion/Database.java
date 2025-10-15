package com.example.tecnimusic_recepcion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String URL = "jdbc:sqlite:tecnimusic.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS Instrumentos (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    nombre_cliente TEXT NOT NULL,\n"
                + "    cedula TEXT NOT NULL,\n"
                + "    direccion TEXT,\n"
                + "    telefono TEXT,\n"
                + "    marca TEXT NOT NULL,\n"
                + "    modelo TEXT NOT NULL,\n"
                + "    serie TEXT NOT NULL UNIQUE,\n"
                + "    fecha_ingreso TEXT NOT NULL,\n"
                + "    observaciones TEXT\n"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}
