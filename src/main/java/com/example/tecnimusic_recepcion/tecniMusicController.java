package com.example.tecnimusic_recepcion;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.TextFields;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Controlador principal para la interfaz de usuario de la hoja de servicio (tecniMusic-view.fxml).
 * <p>
 * Esta clase gestiona toda la lógica de la ventana de recepción de equipos. Sus responsabilidades incluyen:
 * <ul>
 *     <li>Inicializar y configurar los componentes de la interfaz (campos de texto, selectores de fecha, etc.).</li>
 *     <li>Cargar datos de configuración del local desde un archivo de propiedades.</li>
 *     <li>Implementar la funcionalidad de autocompletado para clientes y equipos, cargando sugerencias desde la base de datos.</li>
 *     <li>Validar los datos introducidos por el usuario antes de guardarlos.</li>
 *     <li>Orquestar el guardado de una nueva hoja de servicio, interactuando con {@link DatabaseService} para manejar la lógica de negocio y las transacciones de base de datos.</li>
 *     <li>Generar un recibo en formato PDF de la hoja de servicio utilizando la librería iTextPDF.</li>
 *     <li>Manejar la limpieza del formulario para crear una nueva hoja de servicio.</li>
 *     <li>Mostrar alertas y notificaciones al usuario.</li>
 * </ul>
 * La clase está diseñada para interactuar estrechamente con la base de datos de Snipe-IT, tanto para leer datos existentes (clientes, activos) como para crear nuevos registros.
 */
public class tecniMusicController {

    //region FXML Fields
    /**
     * Componentes de la interfaz de usuario (UI) inyectados desde el archivo FXML.
     * La anotación @FXML es utilizada por el FXMLLoader para vincular estos campos
     * con los elementos definidos en el archivo 'tecniMusic-view.fxml'.
     */
    @FXML private Label localNombreLabel, localDireccionLabel, localTelefonoLabel;
    @FXML private TextField ordenNumeroField, clienteNombreField, clienteDireccionField, clienteTelefonoField;
    @FXML private TextField equipoSerieField, equipoTipoField, equipoMarcaField, equipoModeloField, costosTotalField, entregaFirmaField;
    @FXML private DatePicker ordenFechaPicker, entregaFechaPicker;
    @FXML private TextArea equipoFallaArea, costosInformeArea, aclaracionesArea;
    //endregion

    //region State Variables
    /**
     * Variables para mantener el estado interno del formulario y la lógica de la aplicación.
     * Estas variables rastrean la información que no está directamente visible en un campo de la UI,
     * como los IDs de los registros seleccionados o el estado de las operaciones de autocompletado.
     */

    /** ID predicho para la próxima hoja de servicio, usado para mostrar un número de orden provisional. */
    private long predictedHojaId;
    /** Almacena el ID del cliente seleccionado desde la lista de autocompletado. Es null si el cliente es nuevo. */
    private Long idClienteSeleccionado = null;
    /** Almacena el nombre completo (con teléfono) del cliente seleccionado para validación. */
    private String nombreClienteSeleccionado = null;
    /** Almacena el ID del activo (equipo) seleccionado desde la lista de autocompletado. Es null si el equipo es nuevo. */
    private Long idAssetSeleccionado = null;
    /** Almacena el número de serie del equipo seleccionado para validación. */
    private String serieEquipoSeleccionado = null;
    /** Bandera para evitar que los 'listeners' de los campos de texto se disparen mientras se están llenando automáticamente. */
    private boolean isAutoCompleting = false;
    /** Locale para formatear los campos de moneda al formato de peso mexicano (MXN). */
    private static final Locale SPANISH_MEXICO_LOCALE = new Locale("es", "MX");
    //endregion

    //region Suggestion Lists
    /**
     * Listas observables para las sugerencias de autocompletado.
     * Estas listas se cargan desde la base de datos y se vinculan a los campos de texto
     * para proporcionar sugerencias al usuario mientras escribe.
     */
    private final ObservableList<String> clienteSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> serieGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> marcaGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> modeloGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> tipoGlobalSuggestions = FXCollections.observableArrayList();
    //endregion

    /**
     * Método de inicialización principal, llamado automáticamente por JavaFX después de que se carga el FXML.
     * <p>
     * Este método es el punto de partida para la configuración de la vista. Realiza las siguientes acciones:
     * 1. Hace que el campo de número de orden no sea editable.
     * 2. Carga la información del local (nombre, dirección, etc.) desde `config.properties`.
     * 3. Configura los listeners para los campos de texto de cliente y serie.
     * 4. Configura el campo de costo total para que se formatee como moneda.
     * 5. Carga todas las listas de sugerencias desde la base de datos.
     * 6. Vincula las listas de sugerencias a los campos de texto para el autocompletado.
     * 7. Limpia el formulario y predice el próximo número de orden.
     */
    @FXML
    public void initialize() {
        ordenNumeroField.setEditable(false);
        cargarDatosDelLocal();
        setupListeners();
        setupCurrencyField(); // Configura el campo de moneda
        cargarSugerenciasGlobales();
        setupAutocompleteFields();
        onLimpiarClicked();
    }

    /**
     * Configura los 'listeners' de cambio de texto para los campos de cliente y número de serie.
     * <p>
     * Estos listeners ayudan a gestionar el estado cuando un usuario interactúa con un campo
     * después de haber seleccionado un valor de autocompletado.
     * - Si el usuario modifica el campo después de una selección, el campo se limpia para forzar una nueva selección.
     * - Si el usuario borra el campo, los datos asociados (ID, otros campos) se resetean.
     */
    private void setupListeners() {
        // Listener para el nombre del cliente.
        clienteNombreField.textProperty().addListener((observable, oldValue, newV) -> {
            if (isAutoCompleting) return; // No hacer nada si el cambio es por autocompletado.
            // Si había un cliente seleccionado y el texto ya no coincide, limpiar el campo.
            if (nombreClienteSeleccionado != null && !newV.equals(nombreClienteSeleccionado)) {
                Platform.runLater(clienteNombreField::clear);
            }
            // Si el campo se vacía, resetear toda la información del cliente.
            if (idClienteSeleccionado != null && (newV == null || newV.trim().isEmpty())) {
                resetCamposCliente();
            }
        });

        // Listener para el número de serie del equipo.
        equipoSerieField.textProperty().addListener((observable, oldValue, newV) -> {
            if (isAutoCompleting) return; // No hacer nada si el cambio es por autocompletado.
            // Si había un equipo seleccionado y el texto ya no coincide, limpiar el campo.
            if (serieEquipoSeleccionado != null && !newV.equals(serieEquipoSeleccionado)) {
                Platform.runLater(equipoSerieField::clear);
            }
            // Si el campo se vacía, resetear toda la información del equipo.
            if (idAssetSeleccionado != null && (newV == null || newV.trim().isEmpty())) {
                resetCamposEquipo();
            }
        });
    }

    /**
     * Vincula los campos de texto de la UI con las listas de sugerencias para habilitar el autocompletado.
     * Utiliza la librería ControlsFX (TextFields.bindAutoCompletion).
     * <p>
     * - Para cliente y serie, también define una acción a ejecutar cuando se selecciona una sugerencia
     *   ({@link #cargarDatosDeClienteSeleccionado(String)} y {@link #cargarDatosDeAssetSeleccionado(String)}).
     * - Para tipo, marca y modelo, solo proporciona las sugerencias sin acciones adicionales.
     */
    private void setupAutocompleteFields() {
        TextFields.bindAutoCompletion(clienteNombreField, clienteSuggestions)
                .setOnAutoCompleted(e -> cargarDatosDeClienteSeleccionado(e.getCompletion()));

        TextFields.bindAutoCompletion(equipoSerieField, serieGlobalSuggestions)
                .setOnAutoCompleted(e -> cargarDatosDeAssetSeleccionado(e.getCompletion()));

        TextFields.bindAutoCompletion(equipoTipoField, tipoGlobalSuggestions);
        TextFields.bindAutoCompletion(equipoMarcaField, marcaGlobalSuggestions);
        TextFields.bindAutoCompletion(equipoModeloField, modeloGlobalSuggestions);
    }

    /**
     * Configura el campo de texto 'costosTotalField' para que se comporte como un campo de moneda.
     * <p>
     * - A medida que el usuario escribe, el listener elimina cualquier carácter que no sea un dígito.
     * - Convierte la cadena de dígitos a un valor {@link BigDecimal} (ej. "12345" -> 123.45).
     * - Formatea el valor como moneda mexicana (ej. "$1,234.50") y actualiza el campo.
     * - Mueve el cursor al final del texto formateado.
     * - Si el usuario deja el campo y el valor es "$0.00", lo limpia.
     */
    private void setupCurrencyField() {
        costosTotalField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(oldValue)) {
                return; // Evita bucles infinitos de actualización.
            }

            // 1. Limpiar la entrada para obtener solo dígitos.
            String digits = newValue.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                if (!newValue.isEmpty()) {
                    Platform.runLater(costosTotalField::clear); // Limpia si el usuario introduce texto no numérico.
                }
                return;
            }

            try {
                // 2. Convertir los dígitos a un número decimal (ej. "12345" -> 123.45)
                BigDecimal value = new BigDecimal(digits)
                        .setScale(2, RoundingMode.HALF_UP)
                        .divide(new BigDecimal(100), RoundingMode.HALF_UP);

                // 3. Formatear el número como moneda.
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
                String formatted = currencyFormat.format(value);

                // 4. Actualizar el campo de texto en el hilo de la UI.
                Platform.runLater(() -> {
                    costosTotalField.setText(formatted);
                    costosTotalField.positionCaret(formatted.length()); // Pone el cursor al final.
                });

            } catch (NumberFormatException e) {
                // Si algo sale mal, revierte al valor anterior.
                Platform.runLater(() -> costosTotalField.setText(oldValue));
            }
        });

        // Listener para cuando el campo pierde el foco.
        costosTotalField.focusedProperty().addListener((observable, wasFocused, isNowFocused) -> {
            if (!isNowFocused) { // Si perdió el foco...
                String text = costosTotalField.getText();
                if ("$0.00".equals(text)) { // ...y el valor es cero...
                    costosTotalField.clear(); // ...limpiar el campo.
                }
            }
        });
    }

    /**
     * Carga las listas de sugerencias para autocompletado desde la base de datos.
     * <p>
     * Realiza consultas SQL para obtener:
     * - Nombres y teléfonos de clientes.
     * - Números de serie de activos.
     * - Nombres de fabricantes (marcas).
     * - Nombres de modelos.
     * - Nombres de categorías (tipos).
     * <p>
     * Los resultados se cargan en las {@link ObservableList} correspondientes.
     * Muestra una alerta de error si falla la conexión o alguna de las consultas.
     */
    private void cargarSugerenciasGlobales() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Usar TRANSACTION_READ_COMMITTED para evitar lecturas sucias y mejorar el rendimiento.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // Cargar sugerencias de clientes (Nombre | Teléfono)
            clienteSuggestions.setAll(new ArrayList<>());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT nombre, telefono FROM x_clientes ORDER BY nombre ASC")) {
                while (rs.next()) clienteSuggestions.add(rs.getString("nombre") + " | " + rs.getString("telefono"));
            }
            // Cargar sugerencias de números de serie
            serieGlobalSuggestions.setAll(new ArrayList<>());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT serial FROM assets WHERE serial IS NOT NULL AND serial != '' ORDER BY serial ASC")) {
                while (rs.next()) serieGlobalSuggestions.add(rs.getString("serial"));
            }
            // Cargar sugerencias de marcas
            marcaGlobalSuggestions.setAll(new ArrayList<>());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT name FROM manufacturers WHERE name IS NOT NULL AND name != '' ORDER BY name ASC")) {
                while (rs.next()) marcaGlobalSuggestions.add(rs.getString("name"));
            }
            // Cargar sugerencias de modelos
            modeloGlobalSuggestions.setAll(new ArrayList<>());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT name FROM models WHERE name IS NOT NULL AND name != '' ORDER BY name ASC")) {
                while (rs.next()) modeloGlobalSuggestions.add(rs.getString("name"));
            }
            // Cargar sugerencias de tipos de equipo
            tipoGlobalSuggestions.setAll(new ArrayList<>());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT name FROM categories WHERE name IS NOT NULL AND name != '' ORDER BY name ASC")) {
                while (rs.next()) tipoGlobalSuggestions.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Carga", "No se pudieron cargar las listas de sugerencias desde la base de datos.");
        }
    }

    /**
     * Se ejecuta cuando el usuario selecciona un cliente de la lista de autocompletado.
     * <p>
     * Extrae el nombre y el teléfono de la sugerencia, busca el cliente en la base de datos
     * y rellena los campos de nombre, teléfono y dirección. También guarda el ID del cliente
     * y hace que los campos de teléfono y dirección no sean editables para evitar inconsistencias.
     *
     * @param suggestion La cadena de texto seleccionada, con el formato "Nombre | Teléfono".
     */
    private void cargarDatosDeClienteSeleccionado(String suggestion) {
        String[] parts = suggestion.split("\\s*\\|\\s*");
        if (parts.length < 2) return; // Ignorar si la sugerencia no tiene el formato esperado.

        String sql = "SELECT id, direccion FROM x_clientes WHERE nombre = ? AND telefono = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, parts[0]); // Nombre
            pstmt.setString(2, parts[1]); // Teléfono
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                isAutoCompleting = true; // Activar bandera para evitar que los listeners se disparen.
                this.idClienteSeleccionado = rs.getLong("id");
                this.nombreClienteSeleccionado = suggestion;
                clienteNombreField.setText(suggestion);
                clienteTelefonoField.setText(parts[1]);
                clienteDireccionField.setText(rs.getString("direccion"));
                // Bloquear campos para evitar edición accidental de un cliente existente.
                clienteDireccionField.setEditable(false);
                clienteTelefonoField.setEditable(false);
                isAutoCompleting = false; // Desactivar bandera.
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del cliente.");
        }
    }

    /**
     * Se ejecuta cuando el usuario selecciona un número de serie de la lista de autocompletado.
     * <p>
     * Busca el activo (equipo) en la base de datos usando el número de serie y rellena
     * automáticamente los campos de tipo, marca y modelo. También guarda el ID del activo
     * y hace que estos campos no sean editables.
     *
     * @param serial El número de serie seleccionado por el usuario.
     */
    private void cargarDatosDeAssetSeleccionado(String serial) {
        // Consulta SQL que une varias tablas para obtener la información completa del activo.
        String sql = "SELECT a.id, cat.name as tipo, man.name as marca, mdl.name as modelo " +
                     "FROM assets a " +
                     "LEFT JOIN models mdl ON a.model_id = mdl.id " +
                     "LEFT JOIN categories cat ON mdl.category_id = cat.id " +
                     "LEFT JOIN manufacturers man ON mdl.manufacturer_id = man.id " +
                     "WHERE a.serial = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, serial);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                isAutoCompleting = true; // Activar bandera.
                this.idAssetSeleccionado = rs.getLong("id");
                this.serieEquipoSeleccionado = serial;
                equipoTipoField.setText(rs.getString("tipo"));
                equipoMarcaField.setText(rs.getString("marca"));
                equipoModeloField.setText(rs.getString("modelo"));
                equipoSerieField.setText(serial);

                // Bloquear campos para evitar edición accidental de un activo existente.
                equipoTipoField.setEditable(false);
                equipoMarcaField.setEditable(false);
                equipoModeloField.setEditable(false);
                isAutoCompleting = false; // Desactivar bandera.
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del equipo.");
        }
    }

    /**
     * Manejador del evento de clic en el botón "Guardar".
     * <p>
     * Este método orquesta el proceso completo de guardado:
     * 1. Valida que los campos obligatorios no estén vacíos.
     * 2. Muestra un diálogo de confirmación con un resumen de los datos.
     * 3. Si el usuario confirma, llama a {@link DatabaseService#guardarHojaServicioCompleta(Long, String, String, String, Long, String, String, String, String, LocalDate, String, String, String, LocalDate, String, String)}
     *    para ejecutar la transacción de guardado en la base de datos.
     * 4. Si el guardado es exitoso, llama a {@link #generarPdfHojaServicio(String)} para crear el recibo.
     * 5. Muestra una notificación de éxito (o advertencia si el número de orden fue reasignado).
     * 6. Cierra la ventana de la aplicación.
     * 7. Muestra alertas de error si falla el guardado en la BD o la generación del PDF.
     */
    @FXML
    protected void onGuardarClicked() {
        // 1. Validar campos
        Optional<String> camposInvalidos = validarCamposObligatorios();
        if (camposInvalidos.isPresent()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Campos Requeridos", camposInvalidos.get());
            return;
        }

        // 2. Crear resumen para confirmación
        StringBuilder summary = new StringBuilder();
        String nombreCliente = clienteNombreField.getText().trim().split("\\s*\\|\\s*")[0];
        summary.append("CLIENTE:\n");
        summary.append("  Nombre: ").append(nombreCliente).append("\n");
        summary.append("  Teléfono: ").append(clienteTelefonoField.getText().trim()).append("\n\n");
        summary.append("EQUIPO:\n");
        summary.append("  Tipo: ").append(equipoTipoField.getText().trim()).append("\n");
        summary.append("  Marca: ").append(equipoMarcaField.getText().trim()).append("\n");
        summary.append("  Modelo: ").append(equipoModeloField.getText().trim()).append("\n");
        summary.append("  Serie: ").append(equipoSerieField.getText().trim()).append("\n");

        // 3. Mostrar diálogo de confirmación
        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirmar Guardado");
        confirmationAlert.setHeaderText("¿Está seguro de que desea guardar la hoja con los siguientes datos?");
        confirmationAlert.setContentText(summary.toString());

        Optional<ButtonType> result = confirmationAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return; // El usuario canceló la operación.
        }

        String predictedOrdenNumero = ordenNumeroField.getText();
        try {
            // 4. Llamar al servicio de base de datos para guardar todo en una transacción.
            String realOrdenNumero = DatabaseService.getInstance().guardarHojaServicioCompleta(
                    idClienteSeleccionado, clienteNombreField.getText(), clienteTelefonoField.getText(), clienteDireccionField.getText(),
                    idAssetSeleccionado, equipoSerieField.getText(), equipoMarcaField.getText(), equipoModeloField.getText(), equipoTipoField.getText(),
                    ordenFechaPicker.getValue(), equipoFallaArea.getText(), costosInformeArea.getText(), costosTotalField.getText(),
                    entregaFechaPicker.getValue(), entregaFirmaField.getText(), aclaracionesArea.getText()
            );

            // 5. Generar el PDF si el guardado fue exitoso.
            generarPdfHojaServicio(realOrdenNumero);

            // 6. Mostrar notificación de éxito.
            if (predictedOrdenNumero.equals("TM-" + LocalDate.now().getYear() + "-" + predictedHojaId)) {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Éxito", "Hoja de servicio " + realOrdenNumero + " guardada correctamente.");
            } else {
                // Esto puede ocurrir en un entorno multiusuario si otro usuario guardó una hoja mientras esta estaba abierta.
                mostrarAlerta(Alert.AlertType.WARNING, "Éxito con Reasignación",
                        "La hoja de servicio se guardó correctamente, pero el número de orden predicho (" + predictedOrdenNumero + ") ya estaba en uso.\nSe ha asignado el siguiente número disponible: " + realOrdenNumero);
            }

            // 7. Cerrar la ventana.
            Stage stage = (Stage) clienteNombreField.getScene().getWindow();
            stage.close();

        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo guardar la hoja de servicio. Error: " + e.getMessage());
        } catch (IOException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
        }
    }

    /**
     * Manejador del evento de clic en el botón "Generar Último PDF".
     * <p>
     * Este método busca la última hoja de servicio guardada en la base de datos,
     * carga sus datos en el formulario actual y luego invoca la generación del PDF
     * para esa hoja. Es útil si se necesita reimprimir un recibo sin tener que
     * buscarlo manualmente.
     */
    @FXML
    protected void onGenerarUltimoPdfClicked() {
        // Consulta para obtener la última hoja de servicio y los datos del cliente asociado.
        String sql = "SELECT hs.*, c.nombre as cliente_nombre, c.direccion as cliente_direccion, c.telefono as cliente_telefono " +
                     "FROM x_hojas_servicio hs " +
                     "JOIN x_clientes c ON hs.cliente_id = c.id " +
                     "ORDER BY hs.id DESC LIMIT 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                // Rellenar todos los campos del formulario con los datos de la hoja encontrada.
                ordenNumeroField.setText(rs.getString("numero_orden"));
                Date fechaOrden = rs.getDate("fecha_orden");
                if (fechaOrden != null) {
                    ordenFechaPicker.setValue(fechaOrden.toLocalDate());
                }

                clienteNombreField.setText(rs.getString("cliente_nombre"));
                clienteDireccionField.setText(rs.getString("cliente_direccion"));
                clienteTelefonoField.setText(rs.getString("cliente_telefono"));

                equipoSerieField.setText(rs.getString("equipo_serie"));
                equipoTipoField.setText(rs.getString("equipo_tipo"));
                equipoMarcaField.setText(rs.getString("equipo_marca"));
                equipoModeloField.setText(rs.getString("equipo_modelo"));
                equipoFallaArea.setText(rs.getString("falla_reportada"));

                costosInformeArea.setText(rs.getString("informe_costos"));
                BigDecimal totalCostos = rs.getBigDecimal("total_costos");
                if (totalCostos != null) {
                    NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
                    costosTotalField.setText(currencyFormat.format(totalCostos));
                } else {
                    costosTotalField.clear();
                }

                Date fechaEntrega = rs.getDate("fecha_entrega");
                if (fechaEntrega != null) {
                    entregaFechaPicker.setValue(fechaEntrega.toLocalDate());
                }
                entregaFirmaField.setText(rs.getString("firma_aclaracion"));
                aclaracionesArea.setText(rs.getString("aclaraciones"));

                // Generar el PDF con los datos cargados.
                generarPdfHojaServicio(rs.getString("numero_orden"));

            } else {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Información", "No se encontró ninguna hoja de servicio para generar el PDF.");
            }

        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos de la última hoja de servicio. Error: " + e.getMessage());
        } catch (IOException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
        }
    }

    /**
     * Genera un archivo PDF para una hoja de servicio específica usando iText.
     * <p>
     * El método construye el PDF sección por sección:
     * 1. Crea la ruta y el archivo de destino.
     * 2. Agrega el encabezado con el logo y la información del local.
     * 3. Agrega la información de la orden (número y fecha).
     * 4. Agrega las secciones de datos del cliente, equipo, falla, costos y entrega.
     * 5. Agrega una sección para la firma del cliente.
     * 6. Agrega una marca de agua con el logo en el fondo de cada página.
     * 7. Cierra el documento y muestra una alerta con la ubicación del archivo guardado.
     *
     * @param numeroOrden El número de orden, usado para nombrar el archivo PDF.
     * @throws IOException Si ocurre un error de E/S durante la creación del archivo o la lectura del logo.
     */
    private void generarPdfHojaServicio(String numeroOrden) throws IOException {
        String dest = crearRutaDestinoPdf(numeroOrden);
        if (dest == null) return; // No se pudo crear el directorio de destino.

        PdfWriter writer = new PdfWriter(dest);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        document.setMargins(20, 20, 20, 20); // Márgenes del documento.

        // Color para los encabezados de sección.
        com.itextpdf.kernel.colors.Color headerColor = new DeviceRgb(45, 65, 84);

        // Construcción del PDF.
        agregarEncabezado(document);
        agregarInformacionOrden(document, numeroOrden);
        document.add(new LineSeparator(new SolidLine(1)).setMarginTop(5).setMarginBottom(5));
        agregarSeccionesDeDatos(document, headerColor);
        agregarSeccionFirma(document);
        agregarMarcaDeAgua(pdf);

        document.close(); // Cierra el documento, guardando los cambios.
        mostrarAlerta(Alert.AlertType.INFORMATION, "PDF Generado", "El archivo PDF ha sido guardado en:\n" + dest);
    }

    /**
     * Crea la ruta de destino para el archivo PDF.
     * <p>
     * La ruta es: {@code [Carpeta de Usuario]/TecniMusic_Recepcion/HojasDeServicio/}.
     * Si los directorios no existen, intenta crearlos.
     *
     * @param numeroOrden El número de orden que se usará como nombre del archivo.
     * @return La ruta completa del archivo PDF, o null si no se pudo crear el directorio.
     */
    private String crearRutaDestinoPdf(String numeroOrden) {
        String destFolder = System.getProperty("user.home") + File.separator + "TecniMusic_Recepcion" + File.separator + "HojasDeServicio";
        File dir = new File(destFolder);
        if (!dir.exists() && !dir.mkdirs()) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Archivo", "No se pudo crear el directorio para guardar los PDFs.");
            return null;
        }
        return destFolder + File.separator + numeroOrden + ".pdf";
    }

    /**
     * Agrega el encabezado al documento PDF, incluyendo el logo y la información del local.
     * @param document El documento iText al que se agregará el encabezado.
     * @throws IOException Si no se puede encontrar o leer el archivo 'logo.png'.
     */
    private void agregarEncabezado(Document document) throws IOException {
        URL logoUrl = getClass().getClassLoader().getResource("logo.png");
        if (logoUrl == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Logo no encontrado", "El archivo logo.png no se encontró en los recursos.");
            return;
        }

        Image logo = new Image(ImageDataFactory.create(logoUrl));
        logo.setHeight(40);

        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 3})).useAllAvailableWidth();
        headerTable.addCell(new com.itextpdf.layout.element.Cell().add(logo).setBorder(Border.NO_BORDER));

        com.itextpdf.layout.element.Cell infoCell = new com.itextpdf.layout.element.Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        infoCell.add(new Paragraph(localNombreLabel.getText()).setBold().setFontSize(12));
        infoCell.add(new Paragraph(localDireccionLabel.getText()).setFontSize(8));
        infoCell.add(new Paragraph(localTelefonoLabel.getText()).setFontSize(8));
        headerTable.addCell(infoCell);

        document.add(headerTable);
    }

    /**
     * Agrega la información de la orden (número y fecha) al documento PDF.
     * @param document El documento iText.
     * @param numeroOrden El número de orden a mostrar.
     */
    private void agregarInformacionOrden(Document document, String numeroOrden) {
        document.add(new Paragraph("Hoja de Servicio de Recepción").setTextAlignment(TextAlignment.CENTER).setFontSize(16).setBold().setMarginTop(10));
        Table orderInfoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(orderInfoTable, "No. de Orden:", numeroOrden, true);
        LocalDate fechaOrden = ordenFechaPicker.getValue();
        addInfoRow(orderInfoTable, "Fecha de Recepción:", fechaOrden != null ? fechaOrden.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A", true);
        document.add(orderInfoTable);
    }

    /**
     * Agrega todas las secciones principales de datos (cliente, equipo, falla, etc.) al PDF.
     * @param document El documento iText.
     * @param headerColor El color de fondo para los títulos de sección.
     */
    private void agregarSeccionesDeDatos(Document document, com.itextpdf.kernel.colors.Color headerColor) {
        // Datos del Cliente
        document.add(createSectionHeader("Datos del Cliente", headerColor));
        Table clienteTable = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(clienteTable, "Nombre:", clienteNombreField.getText().split("\\s*\\|\\s*")[0], false);
        addInfoRow(clienteTable, "Teléfono:", clienteTelefonoField.getText(), false);
        addInfoRow(clienteTable, "Dirección:", clienteDireccionField.getText(), false);
        document.add(clienteTable);

        // Datos del Equipo
        document.add(createSectionHeader("Datos del Equipo", headerColor));
        Table equipoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1, 2})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(equipoTable, "Tipo:", equipoTipoField.getText(), false);
        addInfoRow(equipoTable, "Marca:", equipoMarcaField.getText(), false);
        addInfoRow(equipoTable, "Serie:", equipoSerieField.getText(), false);
        addInfoRow(equipoTable, "Modelo:", equipoModeloField.getText(), false);
        document.add(equipoTable);

        // Falla Reportada
        document.add(createSectionHeader("Falla Reportada por el Cliente", headerColor));
        document.add(new Paragraph(equipoFallaArea.getText()).setFontSize(9).setMarginTop(5).setMarginBottom(5));

        // Diagnóstico y Costos
        document.add(createSectionHeader("Diagnóstico y Desglose de Costos", headerColor));
        document.add(new Paragraph(costosInformeArea.getText()).setFontSize(9).setMarginTop(5));
        document.add(new Paragraph("Total: " + costosTotalField.getText()).setFontSize(12).setBold().setTextAlignment(TextAlignment.RIGHT).setMarginTop(5));

        // Entrega y Aclaraciones
        document.add(createSectionHeader("Entrega y Cierre", headerColor));
        LocalDate fechaEntrega = entregaFechaPicker.getValue();
        Table entregaTable = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(entregaTable, "Fecha de Entrega:", (fechaEntrega != null ? fechaEntrega.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Pendiente"), false);
        addInfoRow(entregaTable, "Aclaración de Entrega:", entregaFirmaField.getText(), false);
        document.add(entregaTable);

        document.add(new Paragraph("Aclaraciones Adicionales:").setBold().setFontSize(9).setMarginTop(5));
        document.add(new Paragraph(aclaracionesArea.getText()).setFontSize(9));
    }

    /**
     * Agrega la línea y el texto para la firma de conformidad del cliente al final del PDF.
     * @param document El documento iText.
     */
    private void agregarSeccionFirma(Document document) {
        document.add(new Paragraph("\n\n"));
        document.add(new LineSeparator(new SolidLine(1f)));
        String clienteNombre = clienteNombreField.getText().split("\\s*\\|\\s*")[0].trim();
        document.add(new Paragraph(clienteNombre)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(9));
        document.add(new Paragraph("Firma de Conformidad")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(8).setItalic());
    }

    /**
     * Agrega una imagen del logo como marca de agua semitransparente en el centro de cada página del PDF.
     * @param pdf El documento PDF de iText.
     * @throws IOException Si no se puede leer el archivo del logo.
     */
    private void agregarMarcaDeAgua(PdfDocument pdf) throws IOException {
        URL logoUrl = getClass().getClassLoader().getResource("logo.png");
        if (logoUrl == null) {
            return; // La alerta ya se mostró en agregarEncabezado.
        }

        com.itextpdf.io.image.ImageData imageData = ImageDataFactory.create(logoUrl);
        float originalImageWidth = imageData.getWidth();
        float originalImageHeight = imageData.getHeight();

        // Itera sobre cada página del documento.
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            PdfCanvas pdfCanvas = new PdfCanvas(pdf.getPage(i).newContentStreamBefore(), pdf.getPage(i).getResources(), pdf);
            Rectangle pageSize = pdf.getPage(i).getPageSize();

            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();

            // Define el tamaño máximo de la marca de agua (40% de la página).
            float maxWatermarkWidth = pageWidth * 0.4f;
            float maxWatermarkHeight = pageHeight * 0.4f;

            // Calcula el factor de escala para ajustar la imagen manteniendo la proporción.
            float widthRatio = maxWatermarkWidth / originalImageWidth;
            float heightRatio = maxWatermarkHeight / originalImageHeight;
            float scaleFactor = Math.min(widthRatio, heightRatio);

            float scaledWatermarkWidth = originalImageWidth * scaleFactor;
            float scaledWatermarkHeight = originalImageHeight * scaleFactor;

            // Crea la imagen, le aplica opacidad y la escala.
            Image watermarkImg = new Image(imageData);
            watermarkImg.setOpacity(0.2f);
            watermarkImg.scaleAbsolute(scaledWatermarkWidth, scaledWatermarkHeight);

            // Calcula la posición para centrar la imagen.
            float x = (pageWidth - scaledWatermarkWidth) / 2;
            float y = (pageHeight - scaledWatermarkHeight) / 2;

            // Dibuja la imagen en el canvas de la página.
            pdfCanvas.saveState()
                     .addXObjectAt(watermarkImg.getXObject(), x, y)
                     .restoreState();
        }
    }

    /**
     * Método de utilidad para crear un párrafo con estilo de encabezado de sección.
     * @param title El texto del encabezado.
     * @param bgColor El color de fondo del encabezado.
     * @return Un objeto {@link Paragraph} con el estilo aplicado.
     */
    private Paragraph createSectionHeader(String title, com.itextpdf.kernel.colors.Color bgColor) {
        Paragraph p = new Paragraph(title);
        p.setBackgroundColor(bgColor);
        p.setFontColor(ColorConstants.WHITE);
        p.setBold();
        p.setPadding(3);
        p.setMarginTop(8);
        p.setFontSize(10);
        p.setTextAlignment(TextAlignment.CENTER);
        return p;
    }

    /**
     * Método de utilidad para agregar una fila de "Etiqueta: Valor" a una tabla iText.
     * @param table La tabla a la que se agregará la fila.
     * @param label El texto de la etiqueta (se mostrará en negrita).
     * @param value El texto del valor.
     * @param isValueBold Si el valor también debe estar en negrita.
     */
    private void addInfoRow(Table table, String label, String value, boolean isValueBold) {
        com.itextpdf.layout.element.Cell labelCell = new com.itextpdf.layout.element.Cell().add(new Paragraph(label).setBold().setFontSize(9));
        labelCell.setBorder(Border.NO_BORDER).setPadding(1);
        table.addCell(labelCell);

        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell().add(new Paragraph(value).setFontSize(9));
        if (isValueBold) {
            valueCell.setBold();
        }
        valueCell.setBorder(Border.NO_BORDER).setPadding(1);
        table.addCell(valueCell);
    }


    /**
     * Valida que los campos de formulario considerados obligatorios no estén vacíos.
     * @return Un {@code Optional<String>} que contiene un mensaje de error listando los campos
     * faltantes, o un {@code Optional.empty()} si todos los campos obligatorios están llenos.
     */
    private Optional<String> validarCamposObligatorios() {
        List<String> camposFaltantes = new ArrayList<>();
        if (clienteNombreField.getText().trim().isEmpty()) camposFaltantes.add("• Nombre del Cliente");
        if (clienteTelefonoField.getText().trim().isEmpty()) camposFaltantes.add("• Teléfono del Cliente");
        if (equipoTipoField.getText().trim().isEmpty()) camposFaltantes.add("• Tipo de Equipo");
        if (equipoMarcaField.getText().trim().isEmpty()) camposFaltantes.add("• Marca");
        if (equipoModeloField.getText().trim().isEmpty()) camposFaltantes.add("• Modelo");
        if (equipoSerieField.getText().trim().isEmpty()) camposFaltantes.add("• Número de Serie (Activo)");

        if (!camposFaltantes.isEmpty()) {
            return Optional.of("Los siguientes campos son obligatorios y no pueden estar vacíos:\n\n" + String.join("\n", camposFaltantes));
        }
        return Optional.empty();
    }

    /**
     * Manejador del evento de clic en el botón "Limpiar".
     * <p>
     * Restablece el formulario a su estado inicial para permitir la creación de una nueva hoja de servicio.
     * Limpia todos los campos de entrada, resetea las variables de estado (como IDs seleccionados)
     * y establece la fecha de la orden al día actual. Finalmente, predice y asigna un nuevo número de orden provisional.
     */
    @FXML
    protected void onLimpiarClicked() {
        isAutoCompleting = true; // Evitar que los listeners se disparen durante la limpieza.
        resetCamposCliente();
        clienteNombreField.clear();
        equipoFallaArea.clear();
        costosInformeArea.clear();
        costosTotalField.clear();
        entregaFechaPicker.setValue(null);
        entregaFirmaField.clear();
        aclaracionesArea.clear();
        ordenFechaPicker.setValue(LocalDate.now());
        isAutoCompleting = false;

        predecirYAsignarNumeroDeOrden();
    }

    /**
     * Restablece los campos relacionados con el cliente a su estado inicial.
     * Limpia las variables de estado, los campos de texto de dirección y teléfono,
     * y los vuelve a hacer editables. También llama a {@link #resetCamposEquipo()}.
     */
    private void resetCamposCliente() {
        idClienteSeleccionado = null;
        nombreClienteSeleccionado = null;
        clienteDireccionField.clear();
        clienteTelefonoField.clear();
        clienteDireccionField.setEditable(true);
        clienteTelefonoField.setEditable(true);
        resetCamposEquipo(); // Limpiar los campos del equipo también.
    }

    /**
     * Restablece los campos relacionados con el equipo a su estado inicial.
     * Limpia las variables de estado y todos los campos de texto del equipo,
     * y los vuelve a hacer editables.
     */
    private void resetCamposEquipo() {
        idAssetSeleccionado = null;
        serieEquipoSeleccionado = null;
        equipoSerieField.clear();
        equipoTipoField.clear();
        equipoMarcaField.clear();
        equipoModeloField.clear();
        equipoTipoField.setEditable(true);
        equipoMarcaField.setEditable(true);
        equipoModeloField.setEditable(true);
        equipoSerieField.setEditable(true);
    }

    /**
     * Predice y asigna un número de orden provisional para la nueva hoja de servicio.
     * <p>
     * Obtiene el ID máximo actual de la tabla {@code x_hojas_servicio}, le suma 1 y construye
     * un número de orden con el formato "TM-[AÑO]-[ID_PREDICHO]". Este número es solo
     * una predicción y puede ser diferente del número final si otro usuario guarda una
     * hoja de servicio simultáneamente.
     */
    private void predecirYAsignarNumeroDeOrden() {
        long maxId = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MAX(id) FROM x_hojas_servicio");
            if (rs.next()) {
                maxId = rs.getLong(1);
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Error de Predicción", "No se pudo predecir el número de orden.");
        }
        this.predictedHojaId = maxId + 1;
        String provisionalOrden = "TM-" + LocalDate.now().getYear() + "-" + this.predictedHojaId;
        ordenNumeroField.setText(provisionalOrden);
    }

    /**
     * Carga los datos del local (nombre, dirección, teléfono) desde el archivo {@code config.properties}.
     * Estos datos se muestran en la parte superior de la interfaz y en el encabezado del PDF.
     * Muestra una alerta si el archivo de configuración no se encuentra o no se puede leer.
     */
    private void cargarDatosDelLocal() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error de Configuración", "No se encuentra el archivo 'config.properties'");
                return;
            }
            props.load(input);
            localNombreLabel.setText(props.getProperty("local.nombre", "(No configurado)"));
            localDireccionLabel.setText(props.getProperty("local.direccion", "(No configurado)"));
            localTelefonoLabel.setText(props.getProperty("local.telefono", "(No configurado)"));
        } catch (IOException ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Configuración", "No se pudo leer el archivo de propiedades.");
        }
    }

    /**
     * Muestra un cuadro de diálogo de alerta estándar de JavaFX.
     *
     * @param tipo      El tipo de alerta (p. ej., {@code Alert.AlertType.ERROR}, {@code Alert.AlertType.INFORMATION}).
     * @param titulo    El texto que se mostrará en la barra de título de la ventana de alerta.
     * @param contenido El mensaje principal que se mostrará dentro de la alerta.
     */
    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null); // No usar un encabezado separado.
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
