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
            Long assetId = gestionarAsset(conn, idAssetSeleccionado, serieEquipo, marcaEquipo, modeloEquipo, tipoEquipo);
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
                    System.err.println("Error during rollback: " + ex.getMessage());
                }
            }
            throw e; // Re-throw the exception to be handled by the controller
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
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

    private Long gestionarAsset(Connection conn, Long idAssetSeleccionado, String serieEquipo, String marcaEquipo, String modeloEquipo, String tipoEquipo) throws SQLException {
        if (idAssetSeleccionado != null) {
            return idAssetSeleccionado;
        }

        String serieEquipoTrimmed = serieEquipo.trim();
        if (serieEquipoTrimmed.isEmpty()) {
            return null; // No hay activo que gestionar si no hay número de serie.
        }

        String sqlSelect = "SELECT id FROM assets WHERE serial = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, serieEquipoTrimmed);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        // Si el activo no existe, lo creamos de forma segura.
        long modelId = gestionarModelo(conn, modeloEquipo, marcaEquipo, tipoEquipo);
        long statusId = obtenerIdStatusPendiente(conn);
        long companyId = gestionarEntidad(conn, "companies", marcaEquipo, "La marca (compañía) no puede estar vacía.");

        String sqlInsert = "INSERT INTO assets (asset_tag, serial, model_id, status_id, name, company_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            String assetTag = "TEC-" + System.currentTimeMillis(); // Genera un asset tag único.
            String assetName = (marcaEquipo.trim() + " " + modeloEquipo.trim()).trim();
            if (assetName.isEmpty()) {
                assetName = tipoEquipo.trim(); // Si no hay marca/modelo, usar el tipo.
            }
            if (assetName.isEmpty()){
                assetName = "Equipo (registrado desde app)"; // Último recurso.
            }

            pstmtInsert.setString(1, assetTag);
            pstmtInsert.setString(2, serieEquipoTrimmed);
            pstmtInsert.setLong(3, modelId);
            pstmtInsert.setLong(4, statusId);
            pstmtInsert.setString(5, assetName);
            pstmtInsert.setLong(6, companyId);

            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("La creación del activo (asset) falló para el número de serie: " + serieEquipoTrimmed);
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

    private long gestionarModelo(Connection conn, String nombreModelo, String nombreMarca, String nombreCategoria) throws SQLException {
        if (nombreModelo == null || nombreModelo.trim().isEmpty()) {
            throw new SQLException("El nombre del modelo no puede estar vacío.");
        }

        long manufacturerId = gestionarEntidad(conn, "manufacturers", nombreMarca, "La marca no puede estar vacía.");
        long categoryId = gestionarEntidad(conn, "categories", nombreCategoria, "El tipo de equipo no puede estar vacío.");

        String sqlSelect = "SELECT id FROM models WHERE name = ? AND manufacturer_id = ? AND category_id = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, nombreModelo.trim());
            pstmtSelect.setLong(2, manufacturerId);
            pstmtSelect.setLong(3, categoryId);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String sqlInsert = "INSERT INTO models (name, manufacturer_id, category_id, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, nombreModelo.trim());
            pstmtInsert.setLong(2, manufacturerId);
            pstmtInsert.setLong(3, categoryId);
            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo crear ni encontrar el modelo '" + nombreModelo.trim() + "'.");
    }

    private long gestionarEntidad(Connection conn, String tabla, String nombre, String mensajeError) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new SQLException(mensajeError);
        }
        String nombreTrimmed = nombre.trim();
        String sqlSelect = "SELECT id FROM " + tabla + " WHERE name = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, nombreTrimmed);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        String sqlInsert = "INSERT INTO " + tabla + " (name, created_at, updated_at) VALUES (?, NOW(), NOW())";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, nombreTrimmed);
            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo crear ni encontrar la entidad en la tabla '" + tabla + "' con nombre '" + nombreTrimmed + "'.");
    }

    private long obtenerIdStatusPendiente(Connection conn) throws SQLException {
        String sql = "SELECT id FROM status_labels WHERE pending = 1 ORDER BY id LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        sql = "SELECT id FROM status_labels WHERE name = 'Pendiente' LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong("id");
            }
        }

        throw new SQLException("No se encontró un StatusLabel apropiado (con la marca 'pending' activada o con el nombre 'Pendiente').\nPor favor, configure uno en Snipe-IT para registrar nuevos equipos desde la aplicación.");
    }
}
