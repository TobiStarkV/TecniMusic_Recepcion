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

/**
 * Servicio de base de datos (Singleton) que encapsula toda la lógica de negocio y las interacciones con la base de datos.
 * <p>
 * Esta clase es responsable de manejar las transacciones complejas que involucran tanto la base de datos
 * de Snipe-IT como las tablas personalizadas de la aplicación (ej. {@code x_clientes}, {@code x_hojas_servicio}).
 * Su objetivo es garantizar la integridad y consistencia de los datos en todas las operaciones.
 *
 * <ul>
 *     <li><b>Gestión Transaccional:</b> Todas las operaciones de guardado se ejecutan dentro de una única transacción. Si un paso falla, todos los cambios se revierten (rollback).</li>
 *     <li><b>Abstracción de la Lógica:</b> Oculta la complejidad de las consultas SQL y la lógica de creación/actualización de entidades al controlador.</li>
 *     <li><b>Gestión de Entidades de Snipe-IT:</b> Contiene la lógica para buscar o crear entidades clave en Snipe-IT como Activos (Assets), Modelos, Compañías, Fabricantes y Categorías.</li>
 *     <li><b>Integración con Tablas Personalizadas:</b> Se encarga de crear los clientes y las hojas de servicio en las tablas locales.</li>
 * </ul>
 */
public class DatabaseService {

    private static DatabaseService instance;

    private DatabaseService() {
    }

    /**
     * Obtiene la instancia única del servicio de base de datos (patrón Singleton).
     * @return La instancia única de {@code DatabaseService}.
     */
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    /**
     * Orquesta el proceso completo de guardado de una hoja de servicio en una única transacción.
     * <p>
     * Este es el método principal del servicio. Realiza los siguientes pasos:
     * 1. Gestiona el cliente (lo crea si es nuevo o usa el existente).
     * 2. Gestiona el activo (Asset) en Snipe-IT (lo crea si es nuevo, lo actualiza si existe).
     * 3. Inserta el registro final en la tabla de hojas de servicio local ({@code x_hojas_servicio}).
     * 4. Genera y actualiza el número de orden final para la hoja de servicio.
     * <p>
     * Si cualquier paso falla, la transacción se revierte para no dejar datos inconsistentes.
     *
     * @param nombreCliente El nombre del cliente del formulario (puede contener el teléfono).
     * @param companiaEquipo El nombre de la compañía/marca del equipo.
     * @param modeloEquipo El nombre del modelo del equipo.
     * @param tipoEquipo El nombre del tipo/categoría del equipo.
     * @return El número de orden final generado para la hoja de servicio.
     * @throws SQLException Si ocurre un error durante la transacción en la base de datos.
     */
    public String guardarHojaServicioCompleta(
            Long idClienteSeleccionado, String nombreCliente, String telefonoCliente, String direccionCliente,
            Long idAssetSeleccionado, String serieEquipo, String companiaEquipo, String modeloEquipo, String tipoEquipo,
            LocalDate fechaOrden, String fallaReportada, String informeCostos, String totalCostos,
            LocalDate fechaEntrega, String firmaAclaracion, String aclaraciones) throws SQLException {

        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            long clienteId = gestionarCliente(conn, idClienteSeleccionado, nombreCliente, telefonoCliente, direccionCliente);
            Long assetId = gestionarAsset(conn, idAssetSeleccionado, serieEquipo, companiaEquipo, modeloEquipo, tipoEquipo, nombreCliente);
            long realHojaId = insertarHojaServicio(conn, clienteId, assetId, serieEquipo, tipoEquipo, companiaEquipo, modeloEquipo,
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
            throw e; // Re-lanzar la excepción para que el controlador la maneje.
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
     * @param idAssetSeleccionado El ID del activo si fue seleccionado por autocompletado.
     * @param serieEquipo El número de serie del equipo.
     * @param companiaEquipo El nombre de la compañía (usado como Marca y Compañía en Snipe-IT).
     * @param modeloEquipo El modelo del equipo.
     * @param tipoEquipo El tipo/categoría del equipo.
     * @param nombreCliente El nombre del cliente para vincularlo al activo.
     * @return El ID del activo (existente o nuevo).
     * @throws SQLException Si ocurre un error en la base de datos.
     */
    private Long gestionarAsset(Connection conn, Long idAssetSeleccionado, String serieEquipo, String companiaEquipo, String modeloEquipo, String tipoEquipo, String nombreCliente) throws SQLException {
        Long assetId = null;
        String nombreClienteSimple = nombreCliente.split("\\s*\\|\\s*")[0].trim();

        // 1. Determinar el ID del activo, ya sea por selección directa o por búsqueda de serie.
        if (idAssetSeleccionado != null) {
            assetId = idAssetSeleccionado;
        } else {
            String serieEquipoTrimmed = serieEquipo.trim();
            if (!serieEquipoTrimmed.isEmpty()) {
                String sqlSelect = "SELECT id FROM assets WHERE serial = ?";
                try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect)) {
                    pstmtSelect.setString(1, serieEquipoTrimmed);
                    ResultSet rs = pstmtSelect.executeQuery();
                    if (rs.next()) {
                        assetId = rs.getLong("id");
                    }
                }
            }
        }

        // 2. Si el activo no existe, crearlo y asignarle el nombre del cliente.
        if (assetId == null) {
            String serieEquipoTrimmed = serieEquipo.trim();
            if (serieEquipoTrimmed.isEmpty()) {
                return null; // No se puede crear un activo sin número de serie.
            }

            long modelId = gestionarModelo(conn, modeloEquipo, companiaEquipo, tipoEquipo);
            long statusId = obtenerIdStatusPendiente(conn);
            long companyId = gestionarEntidad(conn, "companies", companiaEquipo, "La Compañía no puede estar vacía.");

            String sqlInsert = "INSERT INTO assets (asset_tag, serial, model_id, status_id, name, company_id, _snipeit_cliente_2, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                String assetTag = "TEC-" + System.currentTimeMillis();
                String assetName = (companiaEquipo.trim() + " " + modeloEquipo.trim()).trim();
                if (assetName.isEmpty()) assetName = tipoEquipo.trim();
                if (assetName.isEmpty()) assetName = "Equipo (registrado desde app)";

                pstmtInsert.setString(1, assetTag);
                pstmtInsert.setString(2, serieEquipoTrimmed);
                pstmtInsert.setLong(3, modelId);
                pstmtInsert.setLong(4, statusId);
                pstmtInsert.setString(5, assetName);
                pstmtInsert.setLong(6, companyId);
                pstmtInsert.setString(7, nombreClienteSimple); // Asignar el NOMBRE del cliente

                pstmtInsert.executeUpdate();
                ResultSet rs = pstmtInsert.getGeneratedKeys();
                if (rs.next()) {
                    assetId = rs.getLong(1);
                }
            }
            if (assetId == null) {
                throw new SQLException("La creación del activo (asset) falló para el número de serie: " + serieEquipoTrimmed);
            }
        } else {
            // 3. Si el activo ya existe, actualizarlo con el NOMBRE del cliente actual.
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
     * Inserta el registro final en la tabla personalizada {@code x_hojas_servicio}.
     *
     * @param conn La conexión a la base de datos.
     * @param clienteId El ID del cliente de la tabla {@code x_clientes}.
     * @param assetId El ID del activo de la tabla {@code assets} de Snipe-IT.
     * @param equipoCompania El nombre de la compañía, que se guardará en la columna `equipo_marca` por retrocompatibilidad.
     * @return El ID de la nueva hoja de servicio creada.
     * @throws SQLException Si la inserción falla.
     */
    private long insertarHojaServicio(Connection conn, long clienteId, Long assetId, String equipoSerie, String equipoTipo, String equipoCompania, String equipoModelo,
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
            pstmt.setString(6, equipoCompania); // Se inserta la compañía en la columna `equipo_marca` por retrocompatibilidad.
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
        if (nombreModelo == null || nombreModelo.trim().isEmpty()) {
            throw new SQLException("El nombre del modelo no puede estar vacío.");
        }

        long manufacturerId = gestionarEntidad(conn, "manufacturers", nombreMarca, "La Marca (fabricante) no puede estar vacía para crear un modelo.");
        long categoryId = gestionarEntidad(conn, "categories", nombreCategoria, "El Tipo (categoría) no puede estar vacío para crear un modelo.");

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
