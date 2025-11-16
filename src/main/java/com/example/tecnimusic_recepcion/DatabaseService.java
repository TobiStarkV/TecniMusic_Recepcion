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
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de base de datos (Singleton) que encapsula la lógica de acceso y operaciones.
 */
public class DatabaseService {

    private static DatabaseService instance;

    private DatabaseService() {}

    /**
     * Obtiene la instancia única del servicio de base de datos (patrón Singleton).
     * @return La instancia única de {@code DatabaseService}.
     */
    public static synchronized DatabaseService getInstance() {
        if (instance == null) instance = new DatabaseService();
        return instance;
    }
    
    public void checkAndUpgradeSchema() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            ensureAnticipoColumnExists(conn);
            ensureEquiposTableExists(conn);
            ensureCostoColumnExistsInEquiposTable(conn);
            ensureEstadoFisicoColumnExists(conn);
            ensureAccesoriosColumnExists(conn);
            ensureAccesoriosSugerenciasTableExists(conn); // Añadir la nueva verificación
        }
    }

    private void ensureAccesoriosSugerenciasTableExists(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS x_accesorios_sugerencias (" +
                     "id INT AUTO_INCREMENT PRIMARY KEY," +
                     "nombre VARCHAR(255) NOT NULL UNIQUE" +
                     ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public List<String> getAccesoriosSugerencias() throws SQLException {
        List<String> sugerencias = new ArrayList<>();
        String sql = "SELECT nombre FROM x_accesorios_sugerencias ORDER BY nombre ASC";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                sugerencias.add(rs.getString("nombre"));
            }
        }
        return sugerencias;
    }

    public void guardarSugerenciaAccesorio(String nombre) {
        String sql = "INSERT INTO x_accesorios_sugerencias (nombre) VALUES (?) ON DUPLICATE KEY UPDATE nombre = nombre";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            // Ignorar errores de duplicados, etc.
        }
    }

    private void ensureAccesoriosColumnExists(Connection conn) throws SQLException {
        String checkColumnSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'x_hojas_servicio_equipos' AND COLUMN_NAME = 'accesorios'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkColumnSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String addColumnSql = "ALTER TABLE x_hojas_servicio_equipos ADD COLUMN accesorios TEXT";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(addColumnSql);
                }
            }
        }
    }

    private void ensureEstadoFisicoColumnExists(Connection conn) throws SQLException {
        String checkColumnSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'x_hojas_servicio_equipos' AND COLUMN_NAME = 'estado_fisico'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkColumnSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String addColumnSql = "ALTER TABLE x_hojas_servicio_equipos ADD COLUMN estado_fisico TEXT";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(addColumnSql);
                }
            }
        }
    }

    // --------------------------------------------------
    // Métodos auxiliares (cliente, asset, modelos, etc.)
    // --------------------------------------------------

    /**
     * Gestiona un cliente en la tabla personalizada {@code x_clientes}.
     * <p>
     * Si se proporciona un ID de cliente, lo devuelve directamente.
     * Si no, busca un cliente por nombre y teléfono. Si lo encuentra, devuelve su ID.
     * Si no existe, crea un nuevo registro de cliente y devuelve el nuevo ID generado.
     *
     * @param conn La conexión a la base de datos.
     * @param idClienteSeleccionado El ID del cliente si fue seleccionado por autocompletado (puede ser null).
     * @param nombreCliente El nombre del cliente.
     * @param telefonoCliente El teléfono del cliente.
     * @param direccionCliente La dirección del cliente.
     * @return El ID del cliente (existente o nuevo).
     * @throws SQLException Si ocurre un error en la base de datos.
     */
    private long gestionarCliente(Connection conn, Long idClienteSeleccionado, String nombreCliente, String telefonoCliente, String direccionCliente) throws SQLException {
        if (idClienteSeleccionado != null) return idClienteSeleccionado;

        String nombreClienteSimple = nombreCliente.split("\\s*\\|\\s*")[0].trim();
        String telefonoClienteTrimmed = telefonoCliente == null ? "" : telefonoCliente.trim();
        String direccionClienteTrimmed = direccionCliente == null ? "" : direccionCliente.trim();

        String sqlSelect = "SELECT id FROM x_clientes WHERE nombre = ? AND telefono = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, nombreClienteSimple);
            pstmtSelect.setString(2, telefonoClienteTrimmed);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }

        String sqlInsert = "INSERT INTO x_clientes (nombre, direccion, telefono) VALUES (?, ?, ?)";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, nombreClienteSimple);
            pstmtInsert.setString(2, direccionClienteTrimmed);
            pstmtInsert.setString(3, telefonoClienteTrimmed);
            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        }
        throw new SQLException("No se pudo crear ni encontrar el cliente.");
    }

    /**
     * Gestiona un activo (Asset) en la base de datos de Snipe-IT.
     * <p>
     * Este método es central para la integración con Snipe-IT. Su lógica es la siguiente:
     * 1. Busca el activo por su número de serie.
     * 2. Si no lo encuentra, orquesta la creación de un nuevo activo. Esto implica:
     *    - Gestionar el modelo, la compañía, el fabricante y la categoría (creándolos si no existen).
     *    - Asignar un estado "Pendiente" por defecto.
     *    - Insertar el nuevo activo en la tabla {@code assets}.
     * 3. Una vez que el activo existe (ya sea porque se encontró o porque se creó), actualiza su campo personalizado
     *    {@code _snipeit_cliente_2} con el nombre del cliente actual. Esto asegura que el activo siempre esté
     *    vinculado al último cliente que lo trajo.
     *
     * @param conn La conexión a la base de datos.
     * @param serieEquipo El número de serie del equipo.
     * @param companiaEquipo El nombre de la compañía (usado como Marca y Compañía en Snipe-IT).
     * @param modeloEquipo El modelo del equipo.
     * @param tipoEquipo El tipo/categoría del equipo.
     * @param nombreCliente El nombre del cliente para vincularlo al activo.
     * @return El ID del activo (existente o nuevo).
     * @throws SQLException Si ocurre un error en la base de datos.
     */
    private Long gestionarAsset(Connection conn, String serieEquipo, String companiaEquipo, String modeloEquipo, String tipoEquipo, String nombreCliente) throws SQLException {
        Long assetId = null;
        String nombreClienteSimple = (nombreCliente == null) ? "" : nombreCliente.split("\\s*\\|\\s*")[0].trim();

        // 1. Buscar el activo por número de serie (si se proporcionó serie)
        String serieEquipoTrimmed = serieEquipo == null ? "" : serieEquipo.trim();
        if (!serieEquipoTrimmed.isEmpty()) {
            String sqlSelect = "SELECT id FROM assets WHERE serial = ?";
            try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
                pstmtSelect.setString(1, serieEquipoTrimmed);
                ResultSet rs = pstmtSelect.executeQuery();
                if (rs.next()) assetId = rs.getLong("id");
            }
        }

        // 2. Si el activo no existe, crearlo (si hay serie); si existe, actualizar el cliente asociado.
        if (assetId == null) {
            if (serieEquipoTrimmed.isEmpty()) return null; // no creamos asset sin serie

            long modelId = gestionarModelo(conn, modeloEquipo, companiaEquipo, tipoEquipo);
            long statusId = obtenerIdStatusPendiente(conn);
            long companyId = gestionarEntidad(conn, "companies", companiaEquipo, "La Compañía no puede estar vacía.");

            String sqlInsert = "INSERT INTO assets (asset_tag, serial, model_id, status_id, name, company_id, _snipeit_cliente_2, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                String assetTag = "TEC-" + System.currentTimeMillis();
                // los parámetros se normalizan por los llamadores ("" si no hay valor), así que podemos trimear directamente
                String marca = companiaEquipo.trim();
                String modelo = modeloEquipo.trim();
                String assetName = (marca + " " + modelo).trim();
                if (assetName.isEmpty()) assetName = tipoEquipo == null ? "" : tipoEquipo.trim();
                if (assetName.isEmpty()) assetName = "Equipo (registrado desde app)";

                pstmtInsert.setString(1, assetTag);
                pstmtInsert.setString(2, serieEquipoTrimmed);
                pstmtInsert.setLong(3, modelId);
                pstmtInsert.setLong(4, statusId);
                pstmtInsert.setString(5, assetName);
                pstmtInsert.setLong(6, companyId);
                pstmtInsert.setString(7, nombreClienteSimple);

                pstmtInsert.executeUpdate();
                ResultSet rs = pstmtInsert.getGeneratedKeys();
                if (rs.next()) assetId = rs.getLong(1);
            }
            if (assetId == null) throw new SQLException("La creación del activo (asset) falló para el número de serie: " + serieEquipoTrimmed);
        } else {
            String sqlUpdate = "UPDATE assets SET _snipeit_cliente_2 = ? WHERE id = ?";
            try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
                pstmtUpdate.setString(1, nombreClienteSimple);
                pstmtUpdate.setLong(2, assetId);
                pstmtUpdate.executeUpdate();
            }
        }

        return assetId;
    }


    /**
     * Actualiza la hoja de servicio recién creada con su número de orden final.
     * El número de orden se construye usando el ID de la hoja de servicio (ej. "TM-2024-123").
     *
     * @param conn La conexión a la base de datos.
     * @param hojaId El ID de la hoja de servicio a actualizar.
     * @param numeroOrden El número de orden final a asignar.
     * @throws SQLException Si la actualización falla.
     */
    private void actualizarNumeroDeOrden(Connection conn, long hojaId, String numeroOrden) throws SQLException {
        String sql = "UPDATE x_hojas_servicio SET numero_orden = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, numeroOrden);
            pstmt.setLong(2, hojaId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Gestiona un modelo de equipo en la tabla {@code models} de Snipe-IT.
     * <p>
     * Busca un modelo por su nombre, ID de fabricante y ID de categoría. Si no existe, lo crea.
     * Este método depende de la gestión previa del fabricante (marca) y la categoría (tipo).
     *
     * @param conn La conexión a la base de datos.
     * @param nombreModelo El nombre del modelo a gestionar.
     * @param nombreMarca El nombre de la marca/fabricante asociado al modelo.
     * @param nombreCategoria El nombre de la categoría asociada al modelo.
     * @return El ID del modelo (existente o nuevo).
     * @throws SQLException Si los datos son inválidos o la operación falla.
     */
    private long gestionarModelo(Connection conn, String nombreModelo, String nombreMarca, String nombreCategoria) throws SQLException {
        if (nombreModelo == null || nombreModelo.trim().isEmpty()) throw new SQLException("El nombre del modelo no puede estar vacío.");

        long manufacturerId = gestionarEntidad(conn, "manufacturers", nombreMarca, "La Marca (fabricante) no puede estar vacía para crear un modelo.");
        long categoryId = gestionarEntidad(conn, "categories", nombreCategoria, "El Tipo (categoría) no puede estar vacía para crear un modelo.");

        String sqlSelect = "SELECT id FROM models WHERE name = ? AND manufacturer_id = ? AND category_id = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, nombreModelo.trim());
            pstmtSelect.setLong(2, manufacturerId);
            pstmtSelect.setLong(3, categoryId);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }

        String sqlInsert = "INSERT INTO models (name, manufacturer_id, category_id, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, nombreModelo.trim());
            pstmtInsert.setLong(2, manufacturerId);
            pstmtInsert.setLong(3, categoryId);
            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        }
        throw new SQLException("No se pudo crear ni encontrar el modelo '" + nombreModelo.trim() + "'.");
    }

    /**
     * Método genérico para gestionar una entidad simple en una tabla (ej. companies, manufacturers, categories).
     * <p>
     * Busca una entidad por su nombre en la tabla especificada. Si no la encuentra, la crea.
     * Es un método de utilidad para evitar la repetición de código.
     *
     * @param conn La conexión a la base de datos.
     * @param tabla El nombre de la tabla (ej. "companies").
     * @param nombre El nombre de la entidad a buscar o crear.
     * @param mensajeError El mensaje de error a lanzar si el nombre está vacío.
     * @return El ID de la entidad (existente o nueva).
     * @throws SQLException Si el nombre es inválido o la operación falla.
     */
    private long gestionarEntidad(Connection conn, String tabla, String nombre, String mensajeError) throws SQLException {
        if (nombre == null || nombre.trim().isEmpty()) throw new SQLException(mensajeError);
        String nombreTrimmed = nombre.trim();
        String sqlSelect = "SELECT id FROM " + tabla + " WHERE name = ?";
        try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, nombreTrimmed);
            ResultSet rs = pstmtSelect.executeQuery();
            if (rs.next()) return rs.getLong("id");
        }

        String sqlInsert = "INSERT INTO " + tabla + " (name, created_at, updated_at) VALUES (?, NOW(), NOW())";
        try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setString(1, nombreTrimmed);
            pstmtInsert.executeUpdate();
            ResultSet rs = pstmtInsert.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        }
        throw new SQLException("No se pudo crear ni encontrar la entidad en la tabla '" + tabla + "' con nombre '" + nombreTrimmed + "'.");
    }

    /**
     * Obtiene el ID del estado por defecto para los equipos nuevos.
     * <p>
     * Busca en la tabla {@code status_labels} un estado que sea de tipo "pendiente".
     * Primero, intenta encontrarlo por la columna booleana {@code pending = 1}.
     * Si falla, como respaldo, busca un estado cuyo nombre sea exactamente "Pendiente".
     *
     * @param conn La conexión a la base de datos.
     * @return El ID del estado "pendiente".
     * @throws SQLException Si no se encuentra un estado apropiado, lo que impediría registrar nuevos equipos.
     */
    private long obtenerIdStatusPendiente(Connection conn) throws SQLException {
        String sql = "SELECT id FROM status_labels WHERE pending = 1 ORDER BY id LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getLong("id");
        }

        sql = "SELECT id FROM status_labels WHERE name = 'Pendiente' LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getLong("id");
        }

        throw new SQLException("No se encontró un StatusLabel apropiado (con la marca 'pending' activada o con el nombre 'Pendiente').\nPor favor, configure uno en Snipe-IT para registrar nuevos equipos desde la aplicación.");
    }

    private void ensureAnticipoColumnExists(Connection conn) throws SQLException {
        String checkColumnSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'x_hojas_servicio' AND COLUMN_NAME = 'anticipo'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkColumnSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String addColumnSql = "ALTER TABLE x_hojas_servicio ADD COLUMN anticipo DECIMAL(10, 2) DEFAULT 0.00";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(addColumnSql);
                }
            }
        }
    }
    
    private void ensureCostoColumnExistsInEquiposTable(Connection conn) throws SQLException {
        String checkColumnSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'x_hojas_servicio_equipos' AND COLUMN_NAME = 'costo'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkColumnSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String addColumnSql = "ALTER TABLE x_hojas_servicio_equipos ADD COLUMN costo DECIMAL(10, 2)";
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute(addColumnSql);
                }
            }
        }
    }

    // --------------------------------------------------
    // API pública: guardar hoja con múltiples equipos
    // --------------------------------------------------

    /**
     * Nueva sobrecarga: guarda una hoja de servicio que puede contener múltiples equipos.
     * Crea una entrada maestra en {@code x_hojas_servicio} y luego inserta las filas de equipos
     * en {@code x_hojas_servicio_equipos} enlazadas por el id de la hoja.
     */
    public String guardarHojaServicioCompleta(
            Long idClienteSeleccionado, String nombreCliente, String telefonoCliente, String direccionCliente,
            List<Equipo> equipos,
            LocalDate fechaOrden, String informeDiagnostico, BigDecimal subtotal, BigDecimal anticipo, // Changed parameters
            LocalDate fechaEntrega, String firmaAclaracion, String aclaraciones) throws SQLException {

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            long clienteId = gestionarCliente(conn, idClienteSeleccionado, nombreCliente, telefonoCliente, direccionCliente);

            // Crear la hoja maestra (sin datos de equipo específicos) y obtener su ID
            long hojaId = insertarHojaServicioMaestra(conn, clienteId, fechaOrden, informeDiagnostico, subtotal, anticipo, fechaEntrega, firmaAclaracion, aclaraciones);
            String realOrdenNumero = "TM-" + LocalDate.now().getYear() + "-" + hojaId;
            actualizarNumeroDeOrden(conn, hojaId, realOrdenNumero);

            if (equipos != null) {
                for (Equipo equipo : equipos) {
                    Long assetId = gestionarAsset(conn,
                            equipo.getSerie() == null ? "" : equipo.getSerie(),
                            equipo.getMarca() == null ? "" : equipo.getMarca(),
                            equipo.getModelo() == null ? "" : equipo.getModelo(),
                            equipo.getTipo() == null ? "" : equipo.getTipo(),
                            nombreCliente);

                    insertarEquipoEnHoja(conn, hojaId, assetId,
                            equipo.getSerie(), equipo.getTipo(), equipo.getMarca(), equipo.getModelo(), equipo.getFalla(), equipo.getCosto(), equipo.getEstadoFisico(), equipo.getAccesorios());
                }
            }

            conn.commit();
            return realOrdenNumero;

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { System.err.println("Error during rollback: " + ex.getMessage()); }
            throw e;
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { System.err.println("Error al cerrar la conexión: " + e.getMessage()); }
        }
    }

    private long insertarHojaServicioMaestra(Connection conn, long clienteId, LocalDate fechaOrden, String informeDiagnostico, BigDecimal subtotal, BigDecimal anticipo, LocalDate fechaEntrega, String firmaAclaracion, String aclaraciones) throws SQLException {
        String sql = "INSERT INTO x_hojas_servicio (fecha_orden, cliente_id, asset_id, equipo_serie, equipo_tipo, equipo_marca, equipo_modelo, falla_reportada, informe_costos, total_costos, anticipo, fecha_entrega, firma_aclaracion, aclaraciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setDate(1, fechaOrden != null ? Date.valueOf(fechaOrden) : null);
            pstmt.setLong(2, clienteId);
            pstmt.setNull(3, Types.INTEGER); // asset_id nulo en la hoja maestra
            pstmt.setNull(4, Types.VARCHAR);
            pstmt.setNull(5, Types.VARCHAR);
            pstmt.setNull(6, Types.VARCHAR);
            pstmt.setNull(7, Types.VARCHAR);
            pstmt.setNull(8, Types.VARCHAR);
            pstmt.setString(9, informeDiagnostico);
            if (subtotal != null) pstmt.setBigDecimal(10, subtotal); else pstmt.setNull(10, Types.DECIMAL);
            if (anticipo != null) pstmt.setBigDecimal(11, anticipo); else pstmt.setNull(11, Types.DECIMAL);
            pstmt.setDate(12, fechaEntrega != null ? Date.valueOf(fechaEntrega) : null);
            pstmt.setString(13, firmaAclaracion);
            pstmt.setString(14, aclaraciones);

            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        }
        throw new SQLException("No se pudo crear la hoja de servicio maestra.");
    }

    private void ensureEquiposTableExists(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS x_hojas_servicio_equipos (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "hoja_id BIGINT NOT NULL, " +
                "asset_id BIGINT, " +
                "equipo_serie VARCHAR(255), " +
                "equipo_tipo VARCHAR(255), " +
                "equipo_marca VARCHAR(255), " +
                "equipo_modelo VARCHAR(255), " +
                "falla_reportada TEXT, " +
                "costo DECIMAL(10, 2), " + // Añadida la columna costo
                "created_at DATETIME DEFAULT NOW(), " +
                "updated_at DATETIME DEFAULT NOW()" +
                ")";
        try (Statement stmt = conn.createStatement()) { stmt.execute(sql); }
    }

    private void insertarEquipoEnHoja(Connection conn, long hojaId, Long assetId, String serie, String tipo, String marca, String modelo, String falla, BigDecimal costo, String estadoFisico, String accesorios) throws SQLException {
        String sql = "INSERT INTO x_hojas_servicio_equipos (hoja_id, asset_id, equipo_serie, equipo_tipo, equipo_marca, equipo_modelo, falla_reportada, costo, estado_fisico, accesorios) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, hojaId);
            if (assetId != null) pstmt.setLong(2, assetId); else pstmt.setNull(2, Types.BIGINT);
            pstmt.setString(3, serie);
            pstmt.setString(4, tipo);
            pstmt.setString(5, marca);
            pstmt.setString(6, modelo);
            pstmt.setString(7, falla);
            if (costo != null) pstmt.setBigDecimal(8, costo); else pstmt.setNull(8, Types.DECIMAL);
            pstmt.setString(9, estadoFisico);
            pstmt.setString(10, accesorios);
            pstmt.executeUpdate();
        }
    }

    // --------------------------------------------------
    // API pública: leer hoja para PDF de prueba
    // --------------------------------------------------

    /**
     * Obtiene el ID de la última hoja de servicio creada.
     * @return El ID más alto de la tabla x_hojas_servicio, o -1 si no hay ninguna.
     * @throws SQLException Si ocurre un error de base de datos.
     */
    public long getLastHojaServicioId() throws SQLException {
        String sql = "SELECT MAX(id) FROM x_hojas_servicio";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return -1; // No records found
    }

    /**
     * Obtiene todos los datos de una hoja de servicio, incluyendo sus equipos, para reconstruirla.
     * @param hojaId El ID de la hoja de servicio a obtener.
     * @return Un objeto HojaServicioData completamente poblado, o null si no se encuentra.
     * @throws SQLException Si ocurre un error de base de datos.
     */
    public HojaServicioData getHojaServicioCompleta(long hojaId) throws SQLException {
        HojaServicioData data = null;
        String sqlHoja = "SELECT hs.*, c.nombre as cliente_nombre, c.direccion as cliente_direccion, c.telefono as cliente_telefono " +
                         "FROM x_hojas_servicio hs " +
                         "JOIN x_clientes c ON hs.cliente_id = c.id " +
                         "WHERE hs.id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmtHoja = conn.prepareStatement(sqlHoja)) {

            pstmtHoja.setLong(1, hojaId);
            ResultSet rsHoja = pstmtHoja.executeQuery();

            if (rsHoja.next()) {
                data = new HojaServicioData();
                data.setNumeroOrden(rsHoja.getString("numero_orden"));
                Date fechaOrden = rsHoja.getDate("fecha_orden");
                if (fechaOrden != null) data.setFechaOrden(fechaOrden.toLocalDate());

                data.setClienteNombre(rsHoja.getString("cliente_nombre"));
                data.setClienteDireccion(rsHoja.getString("cliente_direccion"));
                data.setClienteTelefono(rsHoja.getString("cliente_telefono"));

                data.setTotalCostos(rsHoja.getBigDecimal("total_costos"));
                data.setAnticipo(rsHoja.getBigDecimal("anticipo"));

                Date fechaEntrega = rsHoja.getDate("fecha_entrega");
                if (fechaEntrega != null) data.setFechaEntrega(fechaEntrega.toLocalDate());
                
                data.setAclaraciones(rsHoja.getString("aclaraciones"));
                data.setFirmaAclaracion(""); 
                data.setInformeCostos(rsHoja.getString("informe_costos"));

                // Ahora, cargar los equipos asociados
                String sqlEquipos = "SELECT * FROM x_hojas_servicio_equipos WHERE hoja_id = ?";
                try (PreparedStatement pstmtEquipos = conn.prepareStatement(sqlEquipos)) {
                    pstmtEquipos.setLong(1, hojaId);
                    ResultSet rsEquipos = pstmtEquipos.executeQuery();
                    List<Equipo> equipos = new ArrayList<>();
                    while (rsEquipos.next()) {
                        Equipo equipo = new Equipo(
                            rsEquipos.getString("equipo_tipo"),
                            rsEquipos.getString("equipo_marca"),
                            rsEquipos.getString("equipo_serie"),
                            rsEquipos.getString("equipo_modelo"),
                            rsEquipos.getString("falla_reportada"),
                            rsEquipos.getBigDecimal("costo"),
                            rsEquipos.getString("estado_fisico"),
                            rsEquipos.getString("accesorios")
                        );
                        equipos.add(equipo);
                    }
                    data.setEquipos(equipos);
                }
            }
        }
        return data;
    }
}
