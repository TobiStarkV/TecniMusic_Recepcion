package com.example.tecnimusic_recepcion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;

public class DatabaseService {

    private static DatabaseService instance;

    private DatabaseService() {
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    public String guardarHojaServicioCompleta(
            Long idClienteSeleccionado, String nombreCliente, String telefonoCliente, String direccionCliente,
            Long idAssetSeleccionado, String serieEquipo, String marcaEquipo, String modeloEquipo, String tipoEquipo,
            LocalDate fechaOrden, String fallaReportada, String informeCostos, String totalCostos,
            LocalDate fechaEntrega, String firmaAclaracion, String aclaraciones) throws SQLException {

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            long clienteId = gestionarCliente(conn, idClienteSeleccionado, nombreCliente, telefonoCliente, direccionCliente);
            Long assetId = gestionarAsset(conn, idAssetSeleccionado, serieEquipo, marcaEquipo, modeloEquipo);
            long realHojaId = insertarHojaServicio(conn, clienteId, assetId, serieEquipo, tipoEquipo, marcaEquipo, modeloEquipo,
                    fechaOrden, fallaReportada, informeCostos, totalCostos, fechaEntrega, firmaAclaracion, aclaraciones);
            String realOrdenNumero = "TM-" + LocalDate.now().getYear() + "-" + realHojaId;
            actualizarNumeroDeOrden(conn, realHojaId, realOrdenNumero);

            conn.commit();
            return realOrdenNumero;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Log rollback error
                    System.err.println("Error during rollback: " + ex.getMessage());
                }
            }
            throw e; // Re-throw the exception to be handled by the controller
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Log closing error
                     System.err.println("Error al cerrar la conexión: " + e.getMessage());
                }
            }
        }
    }

    private long gestionarCliente(Connection conn, Long idClienteSeleccionado, String nombreCliente, String telefonoCliente, String direccionCliente) throws SQLException {
        if (idClienteSeleccionado != null) {
            return idClienteSeleccionado;
        }

        String nombreClienteSimple = nombreCliente.split("\\s*\\|\\s*")[0].trim();
        String telefonoClienteTrimmed = telefonoCliente.trim();
        String direccionClienteTrimmed = direccionCliente.trim();

        String sqlSelect = "SELECT id FROM x_clientes WHERE nombre = ? AND telefono = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, nombreClienteSimple);
            pstmtSelect.setString(2, telefonoClienteTrimmed);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String sqlInsert = "INSERT INTO x_clientes (nombre, direccion, telefono) VALUES (?, ?, ?)";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, nombreClienteSimple);
            pstmtInsert.setString(2, direccionClienteTrimmed);
            pstmtInsert.setString(3, telefonoClienteTrimmed);
            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo crear ni encontrar el cliente.");
    }

    private Long gestionarAsset(Connection conn, Long idAssetSeleccionado, String serieEquipo, String marcaEquipo, String modeloEquipo) throws SQLException {
        if (idAssetSeleccionado != null) {
            return idAssetSeleccionado;
        }

        String serieEquipoTrimmed = serieEquipo.trim();
        if (serieEquipoTrimmed.isEmpty()) {
            return null; // No asset to manage if there is no serial
        }

        String sqlSelect = "SELECT id FROM assets WHERE serial = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, serieEquipoTrimmed);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String sqlInsert = "INSERT INTO assets (asset_tag, serial, model_id, status_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            String assetTag = "TEC-" + System.currentTimeMillis();
            String assetName = (marcaEquipo + " " + modeloEquipo).trim();
            if (assetName.isEmpty()) {
                assetName = "Equipo (registrado desde app)";
            }

            long modelId = 1; // Placeholder
            long statusId = 1; // Placeholder

            pstmtInsert.setString(1, assetTag);
            pstmtInsert.setString(2, serieEquipoTrimmed);
            pstmtInsert.setLong(3, modelId);
            pstmtInsert.setLong(4, statusId);
            pstmtInsert.setString(5, assetName);

            if (pstmtInsert.executeUpdate() > 0) {
                ResultSet rs = pstmtInsert.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("La creación del activo falló.");
    }

    private long insertarHojaServicio(Connection conn, long clienteId, Long assetId, String equipoSerie, String equipoTipo, String equipoMarca, String equipoModelo,
                                      LocalDate fechaOrden, String fallaReportada, String informeCostos, String totalCostos,
                                      LocalDate fechaEntrega, String firmaAclaracion, String aclaraciones) throws SQLException {
        String sql = "INSERT INTO x_hojas_servicio (fecha_orden, cliente_id, asset_id, equipo_serie, equipo_tipo, equipo_marca, equipo_modelo, falla_reportada, informe_costos, total_costos, fecha_entrega, firma_aclaracion, aclaraciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setDate(1, fechaOrden != null ? Date.valueOf(fechaOrden) : null);
            pstmt.setLong(2, clienteId);
            if (assetId != null) {
                pstmt.setLong(3, assetId);
            } else {
                pstmt.setNull(3, Types.INTEGER);
            }
            pstmt.setString(4, equipoSerie);
            pstmt.setString(5, equipoTipo);
            pstmt.setString(6, equipoMarca);
            pstmt.setString(7, equipoModelo);
            pstmt.setString(8, fallaReportada);
            pstmt.setString(9, informeCostos);
            try {
                String totalText = totalCostos.trim().replaceAll("[^\\d.]", "");
                if (totalText.isEmpty()) {
                    pstmt.setNull(10, Types.DECIMAL);
                } else {
                    pstmt.setBigDecimal(10, new BigDecimal(totalText));
                }
            } catch (NumberFormatException e) {
                pstmt.setNull(10, Types.DECIMAL);
            }
            pstmt.setDate(11, fechaEntrega != null ? Date.valueOf(fechaEntrega) : null);
            pstmt.setString(12, firmaAclaracion);
            pstmt.setString(13, aclaraciones);
            
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo crear la hoja de servicio.");
    }

    private void actualizarNumeroDeOrden(Connection conn, long hojaId, String numeroOrden) throws SQLException {
        String sql = "UPDATE x_hojas_servicio SET numero_orden = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, numeroOrden);
            pstmt.setLong(2, hojaId);
            pstmt.executeUpdate();
        }
    }
}
