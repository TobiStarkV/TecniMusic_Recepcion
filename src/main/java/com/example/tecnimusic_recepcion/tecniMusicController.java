package com.example.tecnimusic_recepcion;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.TextFields;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public class tecniMusicController {

    @FXML private Label localNombreLabel, localDireccionLabel, localTelefonoLabel;
    @FXML private TextField ordenNumeroField, clienteNombreField, clienteDireccionField, clienteTelefonoField;
    @FXML private TextField equipoSerieField, equipoTipoField, equipoCompaniaField, equipoModeloField, costosTotalField, entregaFirmaField;
    @FXML private DatePicker ordenFechaPicker, entregaFechaPicker;
    @FXML private TextArea equipoFallaArea, costosInformeArea, aclaracionesArea;
    @FXML private HBox actionButtonsBox;
    @FXML private Button guardarButton, limpiarButton, generarUltimoPdfButton;

    private long predictedHojaId;
    private Long idClienteSeleccionado = null;
    private String nombreClienteSeleccionado = null;
    private Long idAssetSeleccionado = null;
    private String serieEquipoSeleccionado = null;
    private boolean isAutoCompleting = false;
    private static final Locale SPANISH_MEXICO_LOCALE = new Locale("es", "MX");

    private final ObservableList<String> clienteSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> serieGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> companiaGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> modeloGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> tipoGlobalSuggestions = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        ordenNumeroField.setEditable(false);
        cargarDatosDelLocal();
        setupListeners();
        setupCurrencyField();
        cargarSugerenciasGlobales();
        setupAutocompleteFields();
        onLimpiarClicked();
    }

    public void loadForViewing(HojaServicioData data) {
        populateFormWithData(data);

        // Deshabilitar todos los campos de entrada
        for (Node node : List.of(clienteNombreField, clienteDireccionField, clienteTelefonoField, equipoSerieField, equipoTipoField, equipoCompaniaField, equipoModeloField, costosTotalField, entregaFirmaField, ordenFechaPicker, entregaFechaPicker, equipoFallaArea, costosInformeArea, aclaracionesArea)) {
            if (node instanceof TextField) {
                ((TextField) node).setEditable(false);
            } else if (node instanceof TextArea) {
                ((TextArea) node).setEditable(false);
            } else if (node instanceof DatePicker) {
                ((DatePicker) node).setEditable(false);
                ((DatePicker) node).setDisable(true);
            }
        }

        // Ocultar los botones de acción
        actionButtonsBox.setVisible(false);
        actionButtonsBox.setManaged(false);
    }

    private void setupListeners() {
        clienteNombreField.textProperty().addListener((observable, oldValue, newV) -> {
            if (isAutoCompleting) return;
            if (nombreClienteSeleccionado != null && !newV.equals(nombreClienteSeleccionado)) {
                Platform.runLater(clienteNombreField::clear);
            }
            if (idClienteSeleccionado != null && (newV == null || newV.trim().isEmpty())) {
                resetCamposCliente();
            }
        });

        equipoSerieField.textProperty().addListener((observable, oldValue, newV) -> {
            if (isAutoCompleting) return;
            if (serieEquipoSeleccionado != null && !newV.equals(serieEquipoSeleccionado)) {
                Platform.runLater(equipoSerieField::clear);
            }
            if (idAssetSeleccionado != null && (newV == null || newV.trim().isEmpty())) {
                resetCamposEquipo();
            }
        });
    }

    private void setupAutocompleteFields() {
        TextFields.bindAutoCompletion(clienteNombreField, clienteSuggestions)
                .setOnAutoCompleted(e -> cargarDatosDeClienteSeleccionado(e.getCompletion()));

        TextFields.bindAutoCompletion(equipoSerieField, serieGlobalSuggestions)
                .setOnAutoCompleted(e -> cargarDatosDeAssetSeleccionado(e.getCompletion()));

        TextFields.bindAutoCompletion(equipoTipoField, tipoGlobalSuggestions);
        TextFields.bindAutoCompletion(equipoCompaniaField, companiaGlobalSuggestions);
        TextFields.bindAutoCompletion(equipoModeloField, modeloGlobalSuggestions);
    }

    private void setupCurrencyField() {
        costosTotalField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(oldValue)) return;

            String digits = newValue.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                if (!newValue.isEmpty()) Platform.runLater(costosTotalField::clear);
                return;
            }

            try {
                BigDecimal value = new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP).divide(new BigDecimal(100), RoundingMode.HALF_UP);
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
                String formatted = currencyFormat.format(value);

                Platform.runLater(() -> {
                    costosTotalField.setText(formatted);
                    costosTotalField.positionCaret(formatted.length());
                });

            } catch (NumberFormatException e) {
                Platform.runLater(() -> costosTotalField.setText(oldValue));
            }
        });

        costosTotalField.focusedProperty().addListener((observable, wasFocused, isNowFocused) -> {
            if (!isNowFocused && "$0.00".equals(costosTotalField.getText())) {
                costosTotalField.clear();
            }
        });
    }

    private void cargarSugerenciasGlobales() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            clienteSuggestions.setAll(fetchSuggestions(conn, "SELECT nombre, telefono FROM x_clientes ORDER BY nombre ASC", " | "));
            serieGlobalSuggestions.setAll(fetchSuggestions(conn, "SELECT serial FROM assets WHERE serial IS NOT NULL AND serial != '' ORDER BY serial ASC", null));
            companiaGlobalSuggestions.setAll(fetchSuggestions(conn, "SELECT name FROM companies WHERE name IS NOT NULL AND name != '' ORDER BY name ASC", null));
            modeloGlobalSuggestions.setAll(fetchSuggestions(conn, "SELECT name FROM models WHERE name IS NOT NULL AND name != '' ORDER BY name ASC", null));
            tipoGlobalSuggestions.setAll(fetchSuggestions(conn, "SELECT name FROM categories WHERE name IS NOT NULL AND name != '' ORDER BY name ASC", null));
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Carga", "No se pudieron cargar las listas de sugerencias desde la base de datos.");
        }
    }

    private List<String> fetchSuggestions(Connection conn, String query, String separator) throws SQLException {
        List<String> suggestions = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int columnCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                if (separator != null && columnCount > 1) {
                    suggestions.add(rs.getString(1) + separator + rs.getString(2));
                } else {
                    suggestions.add(rs.getString(1));
                }
            }
        }
        return suggestions;
    }

    private void cargarDatosDeClienteSeleccionado(String suggestion) {
        String[] parts = suggestion.split("\\s*\\|\\s*");
        if (parts.length < 2) return;

        String sql = "SELECT id, direccion FROM x_clientes WHERE nombre = ? AND telefono = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, parts[0]);
            pstmt.setString(2, parts[1]);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                isAutoCompleting = true;
                this.idClienteSeleccionado = rs.getLong("id");
                this.nombreClienteSeleccionado = suggestion;
                clienteNombreField.setText(suggestion);
                clienteTelefonoField.setText(parts[1]);
                clienteDireccionField.setText(rs.getString("direccion"));
                clienteDireccionField.setEditable(false);
                clienteTelefonoField.setEditable(false);
                isAutoCompleting = false;
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del cliente.");
        }
    }

    private void cargarDatosDeAssetSeleccionado(String serial) {
        String sql = "SELECT a.id, cat.name as tipo, cmp.name as compania, mdl.name as modelo " +
                     "FROM assets a " +
                     "LEFT JOIN models mdl ON a.model_id = mdl.id " +
                     "LEFT JOIN categories cat ON mdl.category_id = cat.id " +
                     "LEFT JOIN companies cmp ON a.company_id = cmp.id " +
                     "WHERE a.serial = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, serial);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                isAutoCompleting = true;
                this.idAssetSeleccionado = rs.getLong("id");
                this.serieEquipoSeleccionado = serial;
                equipoTipoField.setText(rs.getString("tipo"));
                equipoCompaniaField.setText(rs.getString("compania"));
                equipoModeloField.setText(rs.getString("modelo"));
                equipoSerieField.setText(serial);
                equipoTipoField.setEditable(false);
                equipoCompaniaField.setEditable(false);
                equipoModeloField.setEditable(false);
                isAutoCompleting = false;
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del equipo.");
        }
    }

    @FXML
    protected void onGuardarClicked() {
        Optional<String> camposInvalidos = validarCamposObligatorios();
        if (camposInvalidos.isPresent()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Campos Requeridos", camposInvalidos.get());
            return;
        }

        if (!mostrarConfirmacion()) return;

        String predictedOrdenNumero = ordenNumeroField.getText();
        try {
            String realOrdenNumero = DatabaseService.getInstance().guardarHojaServicioCompleta(
                    idClienteSeleccionado, clienteNombreField.getText(), clienteTelefonoField.getText(), clienteDireccionField.getText(),
                    idAssetSeleccionado, equipoSerieField.getText(), equipoCompaniaField.getText(), equipoModeloField.getText(), equipoTipoField.getText(),
                    ordenFechaPicker.getValue(), equipoFallaArea.getText(), costosInformeArea.getText(), costosTotalField.getText(),
                    entregaFechaPicker.getValue(), entregaFirmaField.getText(), aclaracionesArea.getText()
            );

            HojaServicioData data = createHojaServicioDataFromForm(realOrdenNumero);
            new PdfGenerator().generatePdf(data);

            mostrarExitoYSalir(predictedOrdenNumero, realOrdenNumero);

        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo guardar la hoja de servicio. Error: " + e.getMessage());
        } catch (IOException | ParseException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
        }
    }

    @FXML
    protected void onGenerarUltimoPdfClicked() {
        String sql = "SELECT hs.*, c.nombre as cliente_nombre, c.direccion as cliente_direccion, c.telefono as cliente_telefono " +
                     "FROM x_hojas_servicio hs JOIN x_clientes c ON hs.cliente_id = c.id ORDER BY hs.id DESC LIMIT 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                HojaServicioData data = createHojaServicioDataFromResultSet(rs);
                new PdfGenerator().generatePdf(data);
                Platform.runLater(() -> populateFormWithData(data)); // Opcional: Rellenar el formulario con los datos del PDF generado
            } else {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Información", "No se encontró ninguna hoja de servicio para generar el PDF.");
            }
        } catch (SQLException | IOException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error General", "Ocurrió un error al generar el último PDF: " + e.getMessage());
        }
    }

    private HojaServicioData createHojaServicioDataFromForm(String numeroOrden) throws ParseException {
        HojaServicioData data = new HojaServicioData();
        data.setNumeroOrden(numeroOrden);
        data.setFechaOrden(ordenFechaPicker.getValue());
        data.setClienteNombre(clienteNombreField.getText().split("\\s*\\|\\s*")[0].trim());
        data.setClienteTelefono(clienteTelefonoField.getText());
        data.setClienteDireccion(clienteDireccionField.getText());
        data.setEquipoTipo(equipoTipoField.getText());
        data.setEquipoMarca(equipoCompaniaField.getText());
        data.setEquipoSerie(equipoSerieField.getText());
        data.setEquipoModelo(equipoModeloField.getText());
        data.setFallaReportada(equipoFallaArea.getText());
        data.setInformeCostos(costosInformeArea.getText());
        data.setFechaEntrega(entregaFechaPicker.getValue());
        data.setFirmaAclaracion(entregaFirmaField.getText());
        data.setAclaraciones(aclaracionesArea.getText());

        String totalCostosStr = costosTotalField.getText();
        if (totalCostosStr != null && !totalCostosStr.isEmpty()) {
            Number number = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).parse(totalCostosStr);
            data.setTotalCostos(BigDecimal.valueOf(number.doubleValue()));
        }
        return data;
    }

    private HojaServicioData createHojaServicioDataFromResultSet(ResultSet rs) throws SQLException {
        HojaServicioData data = new HojaServicioData();
        data.setNumeroOrden(rs.getString("numero_orden"));
        Date fechaOrden = rs.getDate("fecha_orden");
        if (fechaOrden != null) data.setFechaOrden(fechaOrden.toLocalDate());
        data.setClienteNombre(rs.getString("cliente_nombre"));
        data.setClienteDireccion(rs.getString("cliente_direccion"));
        data.setClienteTelefono(rs.getString("cliente_telefono"));
        data.setEquipoSerie(rs.getString("equipo_serie"));
        data.setEquipoTipo(rs.getString("equipo_tipo"));
        data.setEquipoMarca(rs.getString("equipo_marca"));
        data.setEquipoModelo(rs.getString("equipo_modelo"));
        data.setFallaReportada(rs.getString("falla_reportada"));
        data.setInformeCostos(rs.getString("informe_costos"));
        data.setTotalCostos(rs.getBigDecimal("total_costos"));
        Date fechaEntrega = rs.getDate("fecha_entrega");
        if (fechaEntrega != null) data.setFechaEntrega(fechaEntrega.toLocalDate());
        data.setFirmaAclaracion(rs.getString("firma_aclaracion"));
        data.setAclaraciones(rs.getString("aclaraciones"));
        return data;
    }

    private void populateFormWithData(HojaServicioData data) {
        ordenNumeroField.setText(data.getNumeroOrden());
        ordenFechaPicker.setValue(data.getFechaOrden());
        clienteNombreField.setText(data.getClienteNombre());
        clienteDireccionField.setText(data.getClienteDireccion());
        clienteTelefonoField.setText(data.getClienteTelefono());
        equipoSerieField.setText(data.getEquipoSerie());
        equipoTipoField.setText(data.getEquipoTipo());
        equipoCompaniaField.setText(data.getEquipoMarca());
        equipoModeloField.setText(data.getEquipoModelo());
        equipoFallaArea.setText(data.getFallaReportada());
        costosInformeArea.setText(data.getInformeCostos());
        if (data.getTotalCostos() != null) {
            costosTotalField.setText(NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).format(data.getTotalCostos()));
        } else {
            costosTotalField.clear();
        }
        entregaFechaPicker.setValue(data.getFechaEntrega());
        entregaFirmaField.setText(data.getFirmaAclaracion());
        aclaracionesArea.setText(data.getAclaraciones());
    }

    private boolean mostrarConfirmacion() {
        StringBuilder summary = new StringBuilder();
        summary.append("CLIENTE:\n");
        summary.append("  Nombre: ").append(clienteNombreField.getText().trim().split("\\s*\\|\\s*")[0]).append("\n");
        summary.append("  Teléfono: ").append(clienteTelefonoField.getText().trim()).append("\n\n");
        summary.append("EQUIPO:\n");
        summary.append("  Tipo: ").append(equipoTipoField.getText().trim()).append("\n");
        summary.append("  Marca: ").append(equipoCompaniaField.getText().trim()).append("\n");
        summary.append("  Modelo: ").append(equipoModeloField.getText().trim()).append("\n");
        summary.append("  Serie: ").append(equipoSerieField.getText().trim()).append("\n");

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirmar Guardado");
        confirmationAlert.setHeaderText("¿Está seguro de que desea guardar la hoja con los siguientes datos?");
        confirmationAlert.setContentText(summary.toString());

        Optional<ButtonType> result = confirmationAlert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void mostrarExitoYSalir(String predictedOrdenNumero, String realOrdenNumero) {
        if (predictedOrdenNumero.equals("TM-" + LocalDate.now().getYear() + "-" + predictedHojaId)) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Éxito", "Hoja de servicio " + realOrdenNumero + " guardada correctamente.");
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Éxito con Reasignación",
                    "La hoja de servicio se guardó correctamente, pero el número de orden predicho (" + predictedOrdenNumero + ") ya estaba en uso.\nSe ha asignado el siguiente número disponible: " + realOrdenNumero);
        }
        Stage stage = (Stage) clienteNombreField.getScene().getWindow();
        stage.close();
    }

    private Optional<String> validarCamposObligatorios() {
        List<String> camposFaltantes = new ArrayList<>();
        if (clienteNombreField.getText().trim().isEmpty()) camposFaltantes.add("• Nombre del Cliente");
        if (clienteTelefonoField.getText().trim().isEmpty()) camposFaltantes.add("• Teléfono del Cliente");
        if (equipoTipoField.getText().trim().isEmpty()) camposFaltantes.add("• Tipo de Equipo");
        if (equipoCompaniaField.getText().trim().isEmpty()) camposFaltantes.add("• Marca");
        if (equipoModeloField.getText().trim().isEmpty()) camposFaltantes.add("• Modelo");
        if (equipoSerieField.getText().trim().isEmpty()) camposFaltantes.add("• Número de Serie (Activo)");

        if (!camposFaltantes.isEmpty()) {
            return Optional.of("Los siguientes campos son obligatorios y no pueden estar vacíos:\n\n" + String.join("\n", camposFaltantes));
        }
        return Optional.empty();
    }

    @FXML
    protected void onLimpiarClicked() {
        isAutoCompleting = true;
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

    private void resetCamposCliente() {
        idClienteSeleccionado = null;
        nombreClienteSeleccionado = null;
        clienteDireccionField.clear();
        clienteTelefonoField.clear();
        clienteDireccionField.setEditable(true);
        clienteTelefonoField.setEditable(true);
        resetCamposEquipo();
    }

    private void resetCamposEquipo() {
        idAssetSeleccionado = null;
        serieEquipoSeleccionado = null;
        equipoSerieField.clear();
        equipoTipoField.clear();
        equipoCompaniaField.clear();
        equipoModeloField.clear();
        equipoTipoField.setEditable(true);
        equipoCompaniaField.setEditable(true);
        equipoModeloField.setEditable(true);
        equipoSerieField.setEditable(true);
    }

    private void predecirYAsignarNumeroDeOrden() {
        long maxId = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MAX(id) FROM x_hojas_servicio");
            if (rs.next()) maxId = rs.getLong(1);
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Error de Predicción", "No se pudo predecir el número de orden.");
        }
        this.predictedHojaId = maxId + 1;
        String provisionalOrden = "TM-" + LocalDate.now().getYear() + "-" + this.predictedHojaId;
        ordenNumeroField.setText(provisionalOrden);
    }

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

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
