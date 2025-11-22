package com.example.tecnimusic_recepcion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.textfield.TextFields;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.languagetool.rules.RuleMatch;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

// Importaciones para la impresión de PDF
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

import java.awt.print.PrinterJob;
import java.awt.print.PrinterException;

public class tecniMusicController {

    @FXML private Label localNombreLabel, localDireccionLabel, localTelefonoLabel, subtotalLabel, totalFinalLabel;
    @FXML private TextField ordenNumeroField, clienteNombreField, clienteDireccionField, clienteTelefonoField;
    @FXML private TextField equipoSerieField, equipoTipoField, equipoCompaniaField, equipoModeloField, equipoCostoField, anticipoField;
    @FXML private DatePicker ordenFechaPicker, entregaFechaPicker;
    @FXML private StyleClassedTextArea equipoFallaArea, aclaracionesArea, equipoEstadoFisicoArea, equipoInformeTecnicoArea;
    @FXML private HBox actionButtonsBox;
    @FXML private Button guardarButton, limpiarButton, salirButton, printReceptionButton, printClosureButton, testPdfButton, accesoriosButton;

    // Nuevos campos para múltiples equipos
    @FXML private TableView<Equipo> equiposTable;
    @FXML private TableColumn<Equipo, String> colTipo, colMarca, colModelo, colSerie, colFalla, colCosto, colEstadoFisico, colAccesorios, colInforme;
    @FXML private Button addEquipoButton, removeEquipoButton, updateEquipoButton;
    @FXML private HBox equipoActionBox, subtotalBox, totalBox;
    @FXML private VBox cierreBox;
    @FXML private GridPane costoGridPane;

    // Componentes para el cierre de la hoja
    @FXML private Button cierreButton;


    private final ObservableList<Equipo> equiposObservable = FXCollections.observableArrayList();
    private final ObservableList<String> accesoriosList = FXCollections.observableArrayList();

    private long predictedHojaId;
    private Long idClienteSeleccionado = null;
    private String nombreClienteSeleccionado = null;
    private Long idAssetSeleccionado = null;
    private String serieEquipoSeleccionado = null;
    private boolean isAutoCompleting = false;
    private boolean isViewOnlyMode = false;
    private HojaServicioData currentHojaServicioData; // Campo para almacenar la HojaServicioData
    private static final Locale SPANISH_MEXICO_LOCALE = new Locale("es", "MX");

    private final ObservableList<String> clienteSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> serieGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> companiaGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> modeloGlobalSuggestions = FXCollections.observableArrayList();
    private final ObservableList<String> tipoGlobalSuggestions = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        try {
            DatabaseService.getInstance().checkAndUpgradeSchema();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo verificar o actualizar la estructura de la base de datos.");
        }

        ordenNumeroField.setEditable(false);
        cargarDatosDelLocal();
        setupListeners();
        setupCurrencyField(equipoCostoField);
        setupCurrencyField(anticipoField);
        cargarSugerenciasGlobales();
        setupAutocompleteFields();
        resetFormulario();

        // Inicializar tabla de equipos
        if (equiposTable != null) {
            equiposTable.setItems(equiposObservable);
            // Configurar las columnas para mostrar propiedades de Equipo usando lambdas simples
            colTipo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTipo()));
            colMarca.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMarca()));
            colModelo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getModelo()));
            colSerie.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSerie()));
            colEstadoFisico.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEstadoFisico()));
            colAccesorios.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAccesorios()));
            colFalla.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFalla()));
            colInforme.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getInformeTecnico()));
            colCosto.setCellValueFactory(cell -> {
                BigDecimal costo = cell.getValue().getCosto();
                String formattedCosto = (costo != null) ? NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).format(costo) : "";
                return new SimpleStringProperty(formattedCosto);
            });

            equiposTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    populateEquipoFields(newSelection);
                }
            });
        }

        equiposObservable.addListener((ListChangeListener.Change<? extends Equipo> c) -> actualizarCostosTotales());
        anticipoField.textProperty().addListener((observable, oldValue, newValue) -> actualizarCostosTotales());

        if (addEquipoButton != null) addEquipoButton.setOnAction(e -> onAddEquipo());
        if (removeEquipoButton != null) removeEquipoButton.setOnAction(e -> onRemoveEquipo());
        if (updateEquipoButton != null) updateEquipoButton.setOnAction(e -> onUpdateEquipo());


        if (printReceptionButton != null) {
            printReceptionButton.setVisible(false);
            printReceptionButton.setManaged(false);
        }
        if (printClosureButton != null) {
            printClosureButton.setVisible(false);
            printClosureButton.setManaged(false);
        }
        
        if (testPdfButton != null) {
            testPdfButton.setVisible(false);
            testPdfButton.setManaged(false);
        }

        // Corrector ortográfico
        setupSpellChecking(equipoFallaArea);
        setupSpellChecking(equipoEstadoFisicoArea);
        setupSpellChecking(aclaracionesArea);
        setupSpellChecking(equipoInformeTecnicoArea);
    }

    private void setupSpellChecking(StyleClassedTextArea textArea) {
        if (textArea == null) return;
        textArea.setStyle("-fx-background-color: #1E2A3A; -fx-text-fill: white;");
        PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                Platform.runLater(() -> {
                    try {
                        List<RuleMatch> matches = CorrectorOrtografico.verificar(newValue);
                        applyHighlighting(textArea, matches);
                    } catch (IOException e) {
                        // ignore
                    }
                });
            });
            pause.playFromStart();
        });

        final ContextMenu contextMenu = new ContextMenu();
        textArea.setContextMenu(contextMenu);

        textArea.setOnContextMenuRequested(event -> {
            if (isViewOnlyMode && !"CERRADA".equals(aclaracionesArea.getText())) { // Allow editing unless closed
                event.consume();
                return;
            }
            contextMenu.hide();
            Point2D click = new Point2D(event.getX(), event.getY());
            int characterIndex = textArea.hit(click.getX(), click.getY()).getCharacterIndex().orElse(-1);
            if (characterIndex != -1) {
                textArea.moveTo(characterIndex);
            }

            contextMenu.getItems().clear();
            String text = textArea.getText();
            if (text == null || text.trim().isEmpty()) {
                event.consume();
                return;
            }

            int caretPosition = textArea.getCaretPosition();

            try {
                List<RuleMatch> allMatches = CorrectorOrtografico.verificar(text);
                Optional<RuleMatch> matchAtCaret = allMatches.stream()
                        .filter(m -> caretPosition >= m.getFromPos() && caretPosition <= m.getToPos())
                        .findFirst();

                if (matchAtCaret.isPresent()) {
                    RuleMatch currentMatch = matchAtCaret.get();
                    List<String> suggestions = currentMatch.getSuggestedReplacements();

                    if (!suggestions.isEmpty()) {
                        for (String suggestion : suggestions) {
                            MenuItem item = new MenuItem(suggestion);
                            item.setOnAction(evt -> {
                                textArea.replaceText(currentMatch.getFromPos(), currentMatch.getToPos(), suggestion);
                            });
                            contextMenu.getItems().add(item);
                        }
                        contextMenu.getItems().add(new SeparatorMenuItem());
                    }
                }

                MenuItem fullCheck = new MenuItem("Verificar Ortografía (Informe completo)");
                fullCheck.setOnAction(evt -> verificarOrtografiaCompleta(textArea));
                contextMenu.getItems().add(fullCheck);

            } catch (IOException ex) {
                // ignore
            }

            if (!contextMenu.getItems().isEmpty()) {
                contextMenu.show(textArea, event.getScreenX(), event.getScreenY());
            }
            
            event.consume();
        });
    }

    private void applyHighlighting(StyleClassedTextArea textArea, List<RuleMatch> matches) {
        textArea.setStyleSpans(0, computeHighlighting(textArea.getText(), matches));
    }

    private static org.fxmisc.richtext.model.StyleSpans<Collection<String>> computeHighlighting(String text, List<RuleMatch> matches) {
        int lastKwEnd = 0;
        org.fxmisc.richtext.model.StyleSpansBuilder<Collection<String>> spansBuilder = new org.fxmisc.richtext.model.StyleSpansBuilder<>();
        for (RuleMatch match : matches) {
            spansBuilder.add(Collections.emptyList(), match.getFromPos() - lastKwEnd);
            spansBuilder.add(Collections.singleton("spell-error"), match.getToPos() - match.getFromPos());
            lastKwEnd = match.getToPos();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void verificarOrtografiaCompleta(StyleClassedTextArea textArea) {
        if (textArea.getText() == null || textArea.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Texto Vacío", "No hay texto para verificar.");
            return;
        }
        try {
            List<RuleMatch> matches = CorrectorOrtografico.verificar(textArea.getText());
            if (matches.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "Ortografía Correcta", "No se encontraron errores de ortografía o gramática.");
            } else {
                StringBuilder errors = new StringBuilder("Se encontraron los siguientes errores:\n\n");
                for (RuleMatch match : matches) {
                    errors.append("Error: '").append(textArea.getText(), match.getFromPos(), match.getToPos()).append("'\n");
                    errors.append("Mensaje: ").append(match.getMessage()).append("\n");
                    errors.append("Sugerencias: ").append(match.getSuggestedReplacements()).append("\n\n");
                }

                Alert errorAlert = new Alert(Alert.AlertType.WARNING);
                errorAlert.setTitle("Revisión de Ortografía");
                errorAlert.setHeaderText("Se encontraron posibles errores en el texto.");

                TextArea errorTextArea = new TextArea(errors.toString());
                errorTextArea.setEditable(false);
                errorTextArea.setWrapText(true);
                errorTextArea.setPrefHeight(300);

                errorAlert.getDialogPane().setContent(errorTextArea);
                errorAlert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
                ((Stage) errorAlert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
                errorAlert.setResizable(true);

                errorAlert.showAndWait();
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error del Corrector", "No se pudo inicializar el corrector ortográfico.");
            e.printStackTrace();
        }
    }

    @FXML
    private void onAccesoriosClicked() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("AccesoriosDialog.fxml"));
            Scene scene = new Scene(loader.load());
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Gestionar Accesorios");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(accesoriosButton.getScene().getWindow());
            dialogStage.setScene(scene);

            AccesoriosDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setAccesorios(accesoriosList);
            controller.setStylesheet(getClass().getResource("styles.css").toExternalForm());

            dialogStage.showAndWait();

            if (controller.isAceptado()) {
                accesoriosList.setAll(controller.getAccesorios());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onAddEquipo() {
        String tipo = equipoTipoField.getText() == null ? "" : equipoTipoField.getText().trim();
        String marca = equipoCompaniaField.getText() == null ? "" : equipoCompaniaField.getText().trim();
        String modelo = equipoModeloField.getText() == null ? "" : equipoModeloField.getText().trim();
        String serie = equipoSerieField.getText() == null ? "" : equipoSerieField.getText().trim();
        String falla = equipoFallaArea.getText() == null ? "" : equipoFallaArea.getText().trim();
        String estadoFisico = equipoEstadoFisicoArea.getText() == null ? "" : equipoEstadoFisicoArea.getText().trim();
        String accesorios = String.join(", ", accesoriosList);
        
        if (tipo.isEmpty() && marca.isEmpty() && modelo.isEmpty() && serie.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Equipo vacío", "Complete al menos un campo del equipo antes de añadir.");
            return;
        }

        Equipo equipo = new Equipo(tipo, marca, serie, modelo, falla, null, estadoFisico, accesorios);
        equiposObservable.add(equipo);

        clearEquipoInputFields();
    }

    private void onRemoveEquipo() {
        Equipo seleccionado = equiposTable.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccionar equipo", "Seleccione un equipo en la tabla para quitarlo.");
            return;
        }
        equiposObservable.remove(seleccionado);
    }

    private void onUpdateEquipo() {
        Equipo selectedEquipo = equiposTable.getSelectionModel().getSelectedItem();
        if (selectedEquipo == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccionar Equipo", "Por favor, seleccione un equipo de la tabla para actualizar.");
            return;
        }

        String costoStr = equipoCostoField.getText();
        BigDecimal nuevoCosto = null;
        if (costoStr != null && !costoStr.isEmpty()) {
            try {
                Number number = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).parse(costoStr);
                nuevoCosto = BigDecimal.valueOf(number.doubleValue());
            } catch (ParseException e) {
                showAlert(Alert.AlertType.ERROR, "Formato de Costo Inválido", "El formato del costo de reparación no es válido.");
                return;
            }
        }

        selectedEquipo.setCosto(nuevoCosto);
        selectedEquipo.setInformeTecnico(equipoInformeTecnicoArea.getText());
        
        equiposTable.refresh();
        actualizarCostosTotales();
    }


    private void actualizarCostosTotales() {
        BigDecimal subtotal = equiposObservable.stream()
            .map(Equipo::getCosto)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
        subtotalLabel.setText(currencyFormat.format(subtotal));

        BigDecimal anticipo = BigDecimal.ZERO;
        String anticipoStr = anticipoField.getText().replaceAll("[^\\d]", "");
        if (!anticipoStr.isEmpty()) {
            try {
                anticipo = new BigDecimal(anticipoStr).movePointLeft(2);
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        BigDecimal totalFinal = subtotal.subtract(anticipo);
        totalFinalLabel.setText(currencyFormat.format(totalFinal));
    }

    public void loadForViewing(HojaServicioData data) {
        this.isViewOnlyMode = true;
        this.currentHojaServicioData = data; // Guardar la HojaServicioData original
        populateFormWithData(data);

        setNodesDisabled(true, clienteNombreField, clienteDireccionField, clienteTelefonoField, equipoSerieField, equipoTipoField, equipoCompaniaField, equipoModeloField, anticipoField, ordenFechaPicker, entregaFechaPicker, equipoFallaArea, equipoEstadoFisicoArea, aclaracionesArea, accesoriosButton);

        guardarButton.setVisible(false);
        guardarButton.setManaged(false);
        limpiarButton.setVisible(false);
        limpiarButton.setManaged(false);
        salirButton.setText("Cerrar Vista");

        printReceptionButton.setVisible(true);
        printReceptionButton.setManaged(true);
        printClosureButton.setVisible(true);
        printClosureButton.setManaged(true);


        // Configurar modo cierre
        cierreBox.setVisible(true);
        cierreBox.setManaged(true);
        subtotalBox.setVisible(true);
        subtotalBox.setManaged(true);
        totalBox.setVisible(true);
        totalBox.setManaged(true);
        colCosto.setVisible(true);
        colInforme.setVisible(true);
        addEquipoButton.setVisible(false);
        addEquipoButton.setManaged(false);
        removeEquipoButton.setVisible(false);
        removeEquipoButton.setManaged(false);
        updateEquipoButton.setVisible(true);
        updateEquipoButton.setManaged(true);
        cierreButton.setVisible(true);
        cierreButton.setManaged(true);

        if ("CERRADA".equals(data.getEstado())) {
            setNodesDisabled(true, cierreButton, updateEquipoButton, equipoCostoField, equipoInformeTecnicoArea);
            cierreButton.setText("Hoja Cerrada");
            printClosureButton.setDisable(false);
        } else {
            setNodesDisabled(false, cierreButton, updateEquipoButton, equipoCostoField, equipoInformeTecnicoArea);
            printClosureButton.setDisable(true);
        }
    }

    @FXML
    protected void onGuardarClicked() {
        Optional<String> camposInvalidos = validarCamposObligatorios();
        if (camposInvalidos.isPresent()) {
            showAlert(Alert.AlertType.WARNING, "Campos Requeridos", camposInvalidos.get());
            return;
        }

        String summary = "Cliente: " + clienteNombreField.getText().split("\\s*\\|\\s*")[0].trim() + "\n" +
                         "Equipos: " + equiposObservable.size() + " equipo(s)";

        if (!showConfirmationDialog("Confirmar Guardado", "Se guardará la hoja con los siguientes datos:\n\n" + summary)) {
            return;
        }

        try {
            BigDecimal anticipo = parseCurrency(anticipoField.getText());

            String realOrdenNumero = DatabaseService.getInstance().guardarHojaServicioCompleta(
                    idClienteSeleccionado, clienteNombreField.getText(), clienteTelefonoField.getText(), clienteDireccionField.getText(),
                    new ArrayList<>(equiposObservable),
                    ordenFechaPicker.getValue(), "", BigDecimal.ZERO, anticipo,
                    entregaFechaPicker.getValue(), "", aclaracionesArea.getText()
            );

            HojaServicioData data = createHojaServicioDataFromForm(realOrdenNumero);
            String pdfPath = new PdfGenerator().generatePdf(data);

            if (showConfirmationDialog("Imprimir Hoja", "Hoja de servicio guardada. ¿Desea imprimirla ahora?")) {
                performPrint(pdfPath);
            }

            mostrarExitoYSalir(ordenNumeroField.getText(), realOrdenNumero);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo guardar la hoja de servicio. Error: " + e.getMessage());
        } catch (IOException | ParseException e) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
        }
    }

    @FXML
    protected void onCierreClicked() {
        if (!showConfirmationDialog("Confirmar Cierre", "¿Está seguro de que desea cerrar esta hoja de servicio? Una vez cerrada, no podrá ser modificada.")) {
            return;
        }

        try {
            long hojaId = Long.parseLong(ordenNumeroField.getText().split("-")[2]);
            BigDecimal totalCostos = parseCurrency(subtotalLabel.getText());
            
            DatabaseService.getInstance().cerrarHojaServicio(hojaId, "", new ArrayList<>(equiposObservable), totalCostos);

            // Actualizar el estado en currentHojaServicioData después de cerrar
            currentHojaServicioData.setEstado("CERRADA");
            currentHojaServicioData.setEquipos(new ArrayList<>(equiposObservable)); // Asegurarse de que los equipos actualizados estén en el objeto
            currentHojaServicioData.setTotalCostos(totalCostos); // Actualizar el total de costos

            String pdfPath = new PdfGenerator().generatePdf(currentHojaServicioData, false); // Usar el objeto actualizado

            showAlert(Alert.AlertType.INFORMATION, "Hoja Cerrada", "La hoja de servicio ha sido cerrada y el PDF de cierre ha sido generado.");

            performPrint(pdfPath);
            
            Stage stage = (Stage) cierreButton.getScene().getWindow();
            stage.close();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error al Cerrar Hoja", "Ocurrió un error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @FXML
    protected void onPrintReceptionClicked() {
        try {
            // Usar currentHojaServicioData para la impresión de recepción
            String pdfPath = new PdfGenerator().generatePdf(currentHojaServicioData, true); // Forzar recepción
            performPrint(pdfPath);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    protected void onPrintClosureClicked() {
        try {
            if (!"CERRADA".equals(currentHojaServicioData.getEstado())) {
                showAlert(Alert.AlertType.WARNING, "Hoja no Cerrada", "La hoja de servicio debe estar cerrada para poder imprimir el informe de cierre.");
                return;
            }
            // Usar currentHojaServicioData para la impresión de cierre
            String pdfPath = new PdfGenerator().generatePdf(currentHojaServicioData, false); // No forzar recepción, usar estado real
            performPrint(pdfPath);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @FXML
    protected void onTestPdfClicked() {
        try {
            long lastId = DatabaseService.getInstance().getLastHojaServicioId();
            if (lastId == -1) {
                showAlert(Alert.AlertType.INFORMATION, "Sin Hojas de Servicio", "No hay hojas de servicio guardadas para generar un PDF de prueba.");
                return;
            }

            HojaServicioData data = DatabaseService.getInstance().getHojaServicioCompleta(lastId);
            if (data == null) {
                showAlert(Alert.AlertType.ERROR, "Error de Datos", "No se pudieron recuperar los datos de la última hoja de servicio.");
                return;
            }

            String pdfPath = new PdfGenerator().generatePdf(data);
            showAlert(Alert.AlertType.INFORMATION, "PDF de Prueba Generado", "El PDF de prueba para la hoja de servicio " + data.getNumeroOrden() + " ha sido generado en: " + pdfPath);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo acceder a la base de datos para generar el PDF de prueba. Error: " + e.getMessage());
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF de prueba. Error: " + e.getMessage());
        }
    }

    private void performPrint(String pdfPath) {
        if (pdfPath == null) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo encontrar la ruta del PDF para imprimir.");
            return;
        }
        
        File pdfFile = new File(pdfPath);
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(pdfFile);
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error al Abrir", "No se pudo abrir el archivo PDF con el visor predeterminado.");
                e.printStackTrace();
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Función no Soportada", "La apertura automática de archivos no es soportada en este sistema.");
        }
    }

    @FXML
    protected void onSalirClicked() {
        if (!isViewOnlyMode && isFormDirty()) {
            if (!showConfirmationDialog("Confirmar Salida", "Hay cambios sin guardar. ¿Está seguro de que desea salir?")) {
                return;
            }
        }
        Stage stage = (Stage) salirButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    protected void onLimpiarClicked() {
        if (isFormDirty()) {
            if (!showConfirmationDialog("Confirmar Limpieza", "¿Está seguro de que desea limpiar el formulario? Se borrarán todos los datos introducidos.")) {
                return;
            }
        }
        resetFormulario();
    }
    
    private void setupListeners() {
        clienteNombreField.textProperty().addListener((observable, oldValue, newV) -> {
            if (isAutoCompleting) return;
            if (nombreClienteSeleccionado != null && !newV.equals(nombreClienteSeleccionado)) {
                Platform.runLater(this::resetCamposCliente);
            }
        });

        equipoSerieField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isAutoCompleting) return;
            if (equiposTable.getSelectionModel().getSelectedItem() != null) {
                equiposTable.getSelectionModel().clearSelection();
                clearEquipoInputFields();
            }
            if (idAssetSeleccionado != null && !newValue.equals(serieEquipoSeleccionado)) {
                idAssetSeleccionado = null;
                serieEquipoSeleccionado = null;
                setNodesEditable(true, equipoTipoField, equipoCompaniaField, equipoModeloField);
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

    private BigDecimal parseCurrency(String text) throws ParseException {
        if (text == null || text.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
        return BigDecimal.valueOf(currencyFormat.parse(text).doubleValue());
    }

    private void setupCurrencyField(TextField textField) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(oldValue)) return;

            String digits = newValue.replaceAll("[^\\d]", "");
            if (digits.isEmpty()) {
                if (!newValue.isEmpty()) Platform.runLater(textField::clear);
                return;
            }

            try {
                BigDecimal value = new BigDecimal(digits).movePointLeft(2);
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
                String formatted = currencyFormat.format(value);

                Platform.runLater(() -> {
                    textField.setText(formatted);
                    textField.positionCaret(formatted.length());
                });

            } catch (NumberFormatException e) {
                Platform.runLater(() -> textField.setText(oldValue));
            }
        });

        textField.focusedProperty().addListener((observable, wasFocused, isNowFocused) -> {
            if (!isNowFocused && "$0.00".equals(textField.getText())) {
                textField.clear();
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
            showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudieron cargar las listas de sugerencias desde la base de datos.");
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
                setNodesEditable(false, clienteDireccionField, clienteTelefonoField);
                isAutoCompleting = false;
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del cliente.");
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
                setNodesEditable(false, equipoTipoField, equipoCompaniaField, equipoModeloField);
                isAutoCompleting = false;
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del equipo.");
        }
    }

    private void resetFormulario() {
        isAutoCompleting = true;
        isViewOnlyMode = false;

        setNodesVisible(false, cierreBox, subtotalBox, totalBox, updateEquipoButton, cierreButton, printReceptionButton, printClosureButton);
        setNodesVisible(true, addEquipoButton, removeEquipoButton, guardarButton, limpiarButton);
        colCosto.setVisible(false);
        colInforme.setVisible(false);

        clearAllInputFields();
        isAutoCompleting = false;
        predecirYAsignarNumeroDeOrden();
    }

    private boolean isFormDirty() {
        if (isViewOnlyMode) return false;
        return !clienteNombreField.getText().trim().isEmpty() ||
               !clienteDireccionField.getText().trim().isEmpty() ||
               !clienteTelefonoField.getText().trim().isEmpty() ||
               !equiposObservable.isEmpty() ||
               !anticipoField.getText().trim().isEmpty() ||
               entregaFechaPicker.getValue() != null ||
               !aclaracionesArea.getText().trim().isEmpty();
    }

    private HojaServicioData createHojaServicioDataFromForm(String numeroOrden) throws ParseException {
        HojaServicioData data = new HojaServicioData();
        data.setNumeroOrden(numeroOrden);
        data.setFechaOrden(ordenFechaPicker.getValue());
        data.setClienteNombre(clienteNombreField.getText().split("\\s*\\|\\s*")[0].trim());
        data.setClienteTelefono(clienteTelefonoField.getText());
        data.setClienteDireccion(clienteDireccionField.getText());
        data.setFechaEntrega(entregaFechaPicker.getValue());
        data.setAclaraciones(aclaracionesArea.getText());
        data.setTotalCostos(parseCurrency(subtotalLabel.getText()));
        data.setAnticipo(parseCurrency(anticipoField.getText()));
        data.setEquipos(new ArrayList<>(equiposObservable));
        return data;
    }

    private void populateFormWithData(HojaServicioData data) {
        ordenNumeroField.setText(data.getNumeroOrden());
        ordenFechaPicker.setValue(data.getFechaOrden());
        clienteNombreField.setText(data.getClienteNombre());
        clienteDireccionField.setText(data.getClienteDireccion());
        clienteTelefonoField.setText(data.getClienteTelefono());
    
        equiposObservable.clear();
        if (data.getEquipos() != null) {
            equiposObservable.addAll(data.getEquipos());
            if (!equiposObservable.isEmpty()) {
                equiposTable.getSelectionModel().selectFirst();
            }
        }
    
        actualizarCostosTotales();
        if (data.getAnticipo() != null) {
            anticipoField.setText(NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).format(data.getAnticipo()));
        } else {
            anticipoField.clear();
        }
    
        entregaFechaPicker.setValue(data.getFechaEntrega());
        aclaracionesArea.replaceText(data.getAclaraciones() != null ? data.getAclaraciones() : "");
    }

    private void populateEquipoFields(Equipo equipo) {
        isAutoCompleting = true;
        equipoTipoField.setText(equipo.getTipo());
        equipoCompaniaField.setText(equipo.getMarca());
        equipoModeloField.setText(equipo.getModelo());
        equipoSerieField.setText(equipo.getSerie());
        equipoFallaArea.replaceText(equipo.getFalla() != null ? equipo.getFalla() : "");
        equipoEstadoFisicoArea.replaceText(equipo.getEstadoFisico() != null ? equipo.getEstadoFisico() : "");
        equipoInformeTecnicoArea.replaceText(equipo.getInformeTecnico() != null ? equipo.getInformeTecnico() : "");

        accesoriosList.clear();
        String acc = equipo.getAccesorios();
        if (acc != null && !acc.trim().isEmpty()) {
            accesoriosList.setAll(Arrays.asList(acc.split("\\s*,\\s*")));
        } else {
            // No clear equipoAccesoriosArea here, it's not a user input field anymore
        }

        if (equipo.getCosto() != null) {
            equipoCostoField.setText(NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).format(equipo.getCosto()));
        } else {
            equipoCostoField.clear();
        }
        isAutoCompleting = false;
    }

    private boolean showConfirmationDialog(String title, String header) {
        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle(title);
        confirmationAlert.setHeaderText(header);
        confirmationAlert.setContentText("Esta acción no se puede deshacer.");
        confirmationAlert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        ((Stage) confirmationAlert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));

        Optional<ButtonType> result = confirmationAlert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void mostrarExitoYSalir(String predictedOrdenNumero, String realOrdenNumero) {
        if (predictedOrdenNumero.equals("TM-" + LocalDate.now().getYear() + "-" + predictedHojaId)) {
            showAlert(Alert.AlertType.INFORMATION, "Éxito", "Hoja de servicio " + realOrdenNumero + " guardada correctamente.");
        } else {
            showAlert(Alert.AlertType.WARNING, "Éxito con Reasignación",
                    "La hoja de servicio se guardó correctamente, pero el número de orden predicho (" + predictedOrdenNumero + ") ya estaba en uso.\nSe ha asignado el siguiente número disponible: " + realOrdenNumero);
        }
        Stage stage = (Stage) clienteNombreField.getScene().getWindow();
        stage.close();
    }

    private Optional<String> validarCamposObligatorios() {
        List<String> camposFaltantes = new ArrayList<>();
        if (clienteNombreField.getText().trim().isEmpty()) camposFaltantes.add("• Nombre del Cliente");
        if (clienteTelefonoField.getText().trim().isEmpty()) camposFaltantes.add("• Teléfono del Cliente");
        if (equiposObservable.isEmpty()) camposFaltantes.add("• Al menos un Equipo");
        return camposFaltantes.isEmpty() ? Optional.empty() : Optional.of("Los siguientes campos son obligatorios:\n\n" + String.join("\n", camposFaltantes));
    }

    private void clearAllInputFields() {
        resetCamposCliente();
        anticipoField.clear();
        subtotalLabel.setText("$0.00");
        totalFinalLabel.setText("$0.00");
        entregaFechaPicker.setValue(null);
        aclaracionesArea.clear();
        ordenFechaPicker.setValue(LocalDate.now());
        equiposObservable.clear();
    }

    private void clearEquipoInputFields() {
        equipoTipoField.clear();
        equipoCompaniaField.clear();
        equipoModeloField.clear();
        equipoSerieField.clear();
        equipoFallaArea.clear();
        equipoEstadoFisicoArea.clear();
        accesoriosList.clear();
        equipoCostoField.clear();
        equipoInformeTecnicoArea.clear();
    }

    private void resetCamposCliente() {
        idClienteSeleccionado = null;
        nombreClienteSeleccionado = null;
        clienteNombreField.clear();
        clienteDireccionField.clear();
        clienteTelefonoField.clear();
        setNodesEditable(true, clienteDireccionField, clienteTelefonoField);
        clearEquipoInputFields();
    }

    private void predecirYAsignarNumeroDeOrden() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT MAX(id) FROM x_hojas_servicio");
                long maxId = rs.next() ? rs.getLong(1) : 0;
                this.predictedHojaId = maxId + 1;
                ordenNumeroField.setText("TM-" + LocalDate.now().getYear() + "-" + this.predictedHojaId);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.WARNING, "Error de Predicción", "No se pudo predecir el número de orden.");
        }
    }

    private void cargarDatosDelLocal() {
        DatabaseConfig dbConfig = new DatabaseConfig();
        localNombreLabel.setText(dbConfig.getLocalNombre());
        localDireccionLabel.setText(dbConfig.getLocalDireccion());
        localTelefonoLabel.setText(dbConfig.getLocalTelefono());
    }

    private void showAlert(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
        alert.showAndWait();
    }

    private void setNodesVisible(boolean visible, Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void setNodesDisabled(boolean disabled, Node... nodes) {
        for (Node node : nodes) {
            node.setDisable(disabled);
        }
    }

    private void setNodesEditable(boolean editable, Control... controls) {
        for (Control control : controls) {
            if (control instanceof TextInputControl) {
                ((TextInputControl) control).setEditable(editable);
            }
        }
    }
}
