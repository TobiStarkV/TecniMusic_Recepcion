package com.example.tecnimusic_recepcion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
import javafx.scene.layout.HBox;
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
    @FXML private StyleClassedTextArea equipoFallaArea, aclaracionesArea, equipoEstadoFisicoArea;
    @FXML private HBox actionButtonsBox;
    @FXML private Button guardarButton, limpiarButton, salirButton, printButton, testPdfButton, accesoriosButton;

    // Nuevos campos para múltiples equipos
    @FXML private TableView<Equipo> equiposTable;
    @FXML private TableColumn<Equipo, String> colTipo, colMarca, colModelo, colSerie, colFalla, colCosto, colEstadoFisico, colAccesorios;
    @FXML private Button addEquipoButton, removeEquipoButton;

    private final ObservableList<Equipo> equiposObservable = FXCollections.observableArrayList();
    private final ObservableList<String> accesoriosList = FXCollections.observableArrayList();

    private long predictedHojaId;
    private Long idClienteSeleccionado = null;
    private String nombreClienteSeleccionado = null;
    private Long idAssetSeleccionado = null;
    private String serieEquipoSeleccionado = null;
    private boolean isAutoCompleting = false;
    private boolean isViewOnlyMode = false;
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
            if (colTipo != null) colTipo.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getTipo() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getTipo())));
            if (colMarca != null) colMarca.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getMarca() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getMarca())));
            if (colModelo != null) colModelo.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getModelo() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getModelo())));
            if (colSerie != null) colSerie.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getSerie() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getSerie())));
            if (colEstadoFisico != null) colEstadoFisico.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getEstadoFisico() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getEstadoFisico())));
            if (colAccesorios != null) colAccesorios.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getAccesorios() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getAccesorios())));
            if (colFalla != null) colFalla.setCellValueFactory(cell -> javafx.beans.property.SimpleStringProperty.stringExpression(cell.getValue().getFalla() == null ? new javafx.beans.property.SimpleStringProperty("") : new javafx.beans.property.SimpleStringProperty(cell.getValue().getFalla())));
            if (colCosto != null) colCosto.setCellValueFactory(cell -> {
                BigDecimal costo = cell.getValue().getCosto();
                String formattedCosto = "";
                if (costo != null) {
                    NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
                    formattedCosto = currencyFormat.format(costo);
                }
                return new javafx.beans.property.SimpleStringProperty(formattedCosto);
            });

            equiposTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    populateEquipoFields(newSelection);
                }
            });
        }

        equiposObservable.addListener((ListChangeListener.Change<? extends Equipo> c) -> {
            actualizarCostosTotales();
        });

        anticipoField.textProperty().addListener((observable, oldValue, newValue) -> actualizarCostosTotales());

        if (addEquipoButton != null) addEquipoButton.setOnAction(e -> onAddEquipo());
        if (removeEquipoButton != null) removeEquipoButton.setOnAction(e -> onRemoveEquipo());

        if (printButton != null) {
            printButton.setVisible(false);
            printButton.setManaged(false);
        }
        
        // Hide testPdfButton
        if (testPdfButton != null) {
            testPdfButton.setVisible(false);
            testPdfButton.setManaged(false);
        }

        // Corrector ortográfico
        if (equipoFallaArea != null) {
            setupSpellChecking(equipoFallaArea);
            equipoFallaArea.setStyle("-fx-background-color: #1E2A3A; -fx-text-fill: white;");
        }
        if (equipoEstadoFisicoArea != null) {
            setupSpellChecking(equipoEstadoFisicoArea);
            equipoEstadoFisicoArea.setStyle("-fx-background-color: #1E2A3A; -fx-text-fill: white;");
        }
        if (aclaracionesArea != null) {
            setupSpellChecking(aclaracionesArea);
            aclaracionesArea.setStyle("-fx-background-color: #1E2A3A; -fx-text-fill: white;");
        }
    }

    private void setupSpellChecking(StyleClassedTextArea textArea) {
        // Esta parte para resaltar el texto mientras se escribe es correcta y se mantiene.
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

        // --- INICIO DE LA CORRECCIÓN ---

        // 1. Crea el objeto ContextMenu.
        final ContextMenu contextMenu = new ContextMenu();

        // 2. Asigna el menú al área de texto.
        //    Esto es importante para que el framework sepa que existe un menú.
        textArea.setContextMenu(contextMenu);

        // 3. Usa setOnContextMenuRequested para controlar todo el proceso.
        textArea.setOnContextMenuRequested(event -> {
            if (isViewOnlyMode) {
                event.consume();
                return;
            }
            // Primero, esconde el menú si ya estaba visible por alguna razón.
            contextMenu.hide();

            // Mueve el cursor a la posición del clic.
            Point2D click = new Point2D(event.getX(), event.getY());
            int characterIndex = textArea.hit(click.getX(), click.getY()).getCharacterIndex().orElse(-1);
            if (characterIndex != -1) {
                textArea.moveTo(characterIndex);
            }

            // Ahora, pobla el menú (lógica que antes estaba en setOnShowing).
            contextMenu.getItems().clear();
            String text = textArea.getText();
            if (text == null || text.trim().isEmpty()) {
                event.consume(); // No hay texto, no hagas nada más.
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

            // Si hay items para mostrar, muestra el menú en la posición del evento.
            if (!contextMenu.getItems().isEmpty()) {
                contextMenu.show(textArea, event.getScreenX(), event.getScreenY());
            }
            
            // Consume el evento para prevenir que el sistema muestre otro menú.
            event.consume();
        });
        
        // Ya no necesitamos el manejador setOnShowing.
        
        // --- FIN DE LA CORRECCIÓN ---
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
        String costoStr = equipoCostoField.getText() == null ? "" : equipoCostoField.getText().trim();

        if (tipo.isEmpty() && marca.isEmpty() && modelo.isEmpty() && serie.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Equipo vacío", "Complete al menos un campo del equipo antes de añadir.");
            return;
        }

        BigDecimal costo = null;
        if (!costoStr.isEmpty()) {
            try {
                Number number = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).parse(costoStr);
                costo = BigDecimal.valueOf(number.doubleValue());
            } catch (ParseException e) {
                showAlert(Alert.AlertType.ERROR, "Formato de Costo Inválido", "El formato del costo de reparación no es válido.");
                return;
            }
        }

        Equipo equipo = new Equipo(tipo, marca, serie, modelo, falla, costo, estadoFisico, accesorios);
        equiposObservable.add(equipo);

        // Limpiar campos para añadir siguiente equipo
        equipoTipoField.clear();
        equipoCompaniaField.clear();
        equipoModeloField.clear();
        equipoSerieField.clear();
        equipoFallaArea.clear();
        equipoEstadoFisicoArea.clear();
        accesoriosList.clear();
        equipoCostoField.clear();
    }

    private void onRemoveEquipo() {
        Equipo seleccionado = equiposTable.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccionar equipo", "Seleccione un equipo en la tabla para quitarlo.");
            return;
        }
        equiposObservable.remove(seleccionado);
    }

    private void actualizarCostosTotales() {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Equipo equipo : equiposObservable) {
            if (equipo.getCosto() != null) {
                subtotal = subtotal.add(equipo.getCosto());
            }
        }
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
        if (subtotalLabel != null) {
            subtotalLabel.setText(currencyFormat.format(subtotal));
        }

        BigDecimal anticipo = BigDecimal.ZERO;
        String anticipoStr = anticipoField.getText().replaceAll("\\D", "");
        if (!anticipoStr.isEmpty()) {
            try {
                anticipo = new BigDecimal(anticipoStr).setScale(2, RoundingMode.HALF_UP).divide(new BigDecimal(100), RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                // Ignorar si el formato es inválido, se manejará en setupCurrencyField
            }
        }

        BigDecimal totalFinal = subtotal.subtract(anticipo);
        if (totalFinalLabel != null) {
            totalFinalLabel.setText(currencyFormat.format(totalFinal));
        }
    }

    public void loadForViewing(HojaServicioData data) {
        this.isViewOnlyMode = true;
        populateFormWithData(data);

        for (Node node : List.of(clienteNombreField, clienteDireccionField, clienteTelefonoField, equipoSerieField, equipoTipoField, equipoCompaniaField, equipoModeloField, equipoCostoField, anticipoField, ordenFechaPicker, entregaFechaPicker, equipoFallaArea, equipoEstadoFisicoArea, aclaracionesArea, accesoriosButton)) {
            if (node instanceof TextInputControl) {
                ((TextInputControl) node).setEditable(false);
            } else if (node instanceof StyleClassedTextArea) {
                ((StyleClassedTextArea) node).setEditable(false);
            } else if (node instanceof DatePicker) {
                ((DatePicker) node).setEditable(false);
                ((DatePicker) node).setDisable(true);
            } else if (node instanceof Button) {
                ((Button) node).setDisable(true);
            }
        }

        // Deshabilitar gestión de equipos en vista sólo lectura
        if (addEquipoButton != null) addEquipoButton.setDisable(true);
        if (removeEquipoButton != null) removeEquipoButton.setDisable(true);

        guardarButton.setVisible(false);
        limpiarButton.setVisible(false);
        guardarButton.setManaged(false);
        limpiarButton.setManaged(false);
        salirButton.setText("Cerrar Vista");

        if (printButton != null) {
            printButton.setVisible(true);
            printButton.setManaged(true);
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
                         "Equipos: " + (equiposObservable.isEmpty() ? "(sin equipos)" : equiposObservable.size() + " equipo(s)") + "\n\n" +
                         "Fallas resumidas:\n" + (equiposObservable.stream().map(Equipo::getFalla).reduce((a,b)->a+"; "+b).orElse("(sin fallas)"));

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirmar Guardado");
        confirmationAlert.setHeaderText("¿Está seguro de que desea guardar la hoja con los siguientes datos?");
        confirmationAlert.setContentText(summary);
        confirmationAlert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        ((Stage) confirmationAlert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));

        Optional<ButtonType> result = confirmationAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        String predictedOrdenNumero = ordenNumeroField.getText();
        try {
            BigDecimal subtotal = parseCurrency(subtotalLabel.getText());
            BigDecimal anticipo = parseCurrency(anticipoField.getText());

            String realOrdenNumero = DatabaseService.getInstance().guardarHojaServicioCompleta(
                    idClienteSeleccionado, clienteNombreField.getText(), clienteTelefonoField.getText(), clienteDireccionField.getText(),
                    new ArrayList<>(equiposObservable),
                    ordenFechaPicker.getValue(), "", subtotal, anticipo, // Updated parameters
                    entregaFechaPicker.getValue(), "", aclaracionesArea.getText()
            );

            HojaServicioData data = createHojaServicioDataFromForm(realOrdenNumero);
            String pdfPath = new PdfGenerator().generatePdf(data);

            // Añadir una pequeña pausa para asegurar que el archivo se ha escrito completamente
            try {
                Thread.sleep(200); // 200 milisegundos
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Alert printConfirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            printConfirmAlert.setTitle("Confirmar Impresión");
            printConfirmAlert.setHeaderText("Hoja de servicio guardada. ¿Desea imprimirla ahora?");
            printConfirmAlert.setContentText("Se abrirá el PDF en su visor predeterminado para que pueda imprimirlo.");
            printConfirmAlert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            ((Stage) printConfirmAlert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));

            Optional<ButtonType> printResult = printConfirmAlert.showAndWait();
            if (printResult.isPresent() && printResult.get() == ButtonType.OK) {
                performPrint(pdfPath);
            }

            mostrarExitoYSalir(predictedOrdenNumero, realOrdenNumero);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo guardar la hoja de servicio. Error: " + e.getMessage());
        } catch (IOException | ParseException e) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
        }
    }

    @FXML
    protected void onPrintClicked() {
        try {
            HojaServicioData data = createHojaServicioDataFromForm(ordenNumeroField.getText());
            String pdfPath = new PdfGenerator().generatePdf(data);

            if (pdfPath != null) {
                // Añadir una pequeña pausa para asegurar que el archivo se ha escrito completamente
                try {
                    Thread.sleep(200); // 200 milisegundos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                performPrint(pdfPath);
            } else {
                showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar la ruta del PDF.");
            }
        } catch (IOException | ParseException e) {
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
            if (isAutoCompleting) {
                return;
            }

            Equipo selectedEquipo = equiposTable.getSelectionModel().getSelectedItem();
            if (selectedEquipo != null) {
                equiposTable.getSelectionModel().clearSelection();
                resetOtherEquipoFields();
                return;
            }

            if (idAssetSeleccionado != null && !newValue.equals(serieEquipoSeleccionado)) {
                idAssetSeleccionado = null;
                serieEquipoSeleccionado = null;
                equipoTipoField.setEditable(true);
                equipoCompaniaField.setEditable(true);
                equipoModeloField.setEditable(true);
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

            String digits = newValue.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                if (!newValue.isEmpty()) Platform.runLater(textField::clear);
                return;
            }

            try {
                BigDecimal value = new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP).divide(new BigDecimal(100), RoundingMode.HALF_UP);
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
                clienteDireccionField.setEditable(false);
                clienteTelefonoField.setEditable(false);
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
                equipoTipoField.setEditable(false);
                equipoCompaniaField.setEditable(false);
                equipoModeloField.setEditable(false);
                isAutoCompleting = false;
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los datos del equipo.");
        }
    }

    private void resetFormulario() {
        isAutoCompleting = true;
        resetCamposCliente();
        clienteNombreField.clear();
        equipoFallaArea.clear();
        anticipoField.clear();
        subtotalLabel.setText("$0.00");
        totalFinalLabel.setText("$0.00");
        entregaFechaPicker.setValue(null);
        aclaracionesArea.clear();
        ordenFechaPicker.setValue(LocalDate.now());
        equiposObservable.clear();
        isAutoCompleting = false;
        predecirYAsignarNumeroDeOrden();
    }

    private boolean isFormDirty() {
        if (isViewOnlyMode) return false;
        return !clienteNombreField.getText().trim().isEmpty() ||
               !clienteDireccionField.getText().trim().isEmpty() ||
               !clienteTelefonoField.getText().trim().isEmpty() ||
               !equipoSerieField.getText().trim().isEmpty() ||
               !equipoTipoField.getText().trim().isEmpty() ||
               !equipoCompaniaField.getText().trim().isEmpty() ||
               !equipoModeloField.getText().trim().isEmpty() ||
               !equipoCostoField.getText().trim().isEmpty() ||
               !anticipoField.getText().trim().isEmpty() ||
               !subtotalLabel.getText().equals("$0.00") ||
               !totalFinalLabel.getText().equals("$0.00") ||
               !equiposObservable.isEmpty() ||
               !equipoFallaArea.getText().trim().isEmpty() ||
               !equipoEstadoFisicoArea.getText().trim().isEmpty() ||
               !accesoriosList.isEmpty() ||
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
        data.setEquipoTipo(equipoTipoField.getText());
        data.setEquipoMarca(equipoCompaniaField.getText());
        data.setEquipoSerie(equipoSerieField.getText());
        data.setEquipoModelo(equipoModeloField.getText());
        data.setFallaReportada(equipoFallaArea.getText());
        data.setEstadoFisico(equipoEstadoFisicoArea.getText());
        data.setFechaEntrega(entregaFechaPicker.getValue());
        data.setFirmaAclaracion("");
        data.setAclaraciones(aclaracionesArea.getText());

        // Subtotal de costos
        data.setTotalCostos(parseCurrency(subtotalLabel.getText())); // totalCostos en HojaServicioData ahora es el subtotal

        // Anticipo
        data.setAnticipo(parseCurrency(anticipoField.getText()));

        // Añadir lista de equipos (si existen), mantener compatibilidad con campos individuales usando el primero
        if (!equiposObservable.isEmpty()) {
            data.setEquipos(new ArrayList<>(equiposObservable));
        } else {
            // Si no hay equipos en la lista, crear uno con los campos individuales (retrocompat)
            BigDecimal costo = null;
            String costoStr = equipoCostoField.getText();
            if (costoStr != null && !costoStr.isEmpty()) {
                Number number = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE).parse(costoStr);
                costo = BigDecimal.valueOf(number.doubleValue());
            }
            Equipo single = new Equipo(equipoTipoField.getText(), equipoCompaniaField.getText(), equipoSerieField.getText(), equipoModeloField.getText(), equipoFallaArea.getText(), costo, equipoEstadoFisicoArea.getText(), String.join(", ", accesoriosList));
            data.getEquipos().add(single);
        }

        return data;
    }

    private void populateFormWithData(HojaServicioData data) {
        ordenNumeroField.setText(data.getNumeroOrden());
        ordenFechaPicker.setValue(data.getFechaOrden());
        clienteNombreField.setText(data.getClienteNombre());
        clienteDireccionField.setText(data.getClienteDireccion());
        clienteTelefonoField.setText(data.getClienteTelefono());
    
        // Limpiar campos de equipo individuales antes de popular
        equipoSerieField.clear();
        equipoTipoField.clear();
        equipoCompaniaField.clear();
        equipoModeloField.clear();
        equipoFallaArea.clear();
        equipoCostoField.clear();
        equipoEstadoFisicoArea.clear();
        accesoriosList.clear();
    
        // Rellenar tabla de equipos si la data contiene varios
        equiposObservable.clear();
        if (data.getEquipos() != null && !data.getEquipos().isEmpty()) {
            equiposObservable.addAll(data.getEquipos());
            // Seleccionar el primer equipo de la lista para mostrar sus detalles
            equiposTable.getSelectionModel().selectFirst();
        } else {
            // Para mantener compatibilidad con hojas de servicio viejas sin la lista de equipos
            Equipo equipoLegacy = new Equipo(
                data.getEquipoTipo(),
                data.getEquipoMarca(),
                data.getEquipoSerie(),
                data.getEquipoModelo(),
                data.getFallaReportada(),
                data.getTotalCostos(), // Asumiendo que el costo total era el costo del único equipo
                data.getEstadoFisico(),
                data.getAccesorios()
            );
            equiposObservable.add(equipoLegacy);
            equiposTable.getSelectionModel().selectFirst();
        }
    
        BigDecimal subtotal = data.getTotalCostos() != null ? data.getTotalCostos() : BigDecimal.ZERO;
        BigDecimal anticipo = data.getAnticipo() != null ? data.getAnticipo() : BigDecimal.ZERO;
        BigDecimal totalFinal = subtotal.subtract(anticipo);
    
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
    
        if (data.getTotalCostos() != null) {
            subtotalLabel.setText(currencyFormat.format(data.getTotalCostos()));
        } else {
            subtotalLabel.setText("$0.00");
        }
        if (data.getAnticipo() != null) {
            anticipoField.setText(currencyFormat.format(data.getAnticipo()));
        } else {
            anticipoField.clear();
        }
        if (totalFinalLabel != null) {
            totalFinalLabel.setText(currencyFormat.format(totalFinal));
        }
    
        entregaFechaPicker.setValue(data.getFechaEntrega());
        
        String aclaraciones = data.getAclaraciones() != null ? data.getAclaraciones() : "";
        String informeCostos = data.getInformeCostos() != null ? data.getInformeCostos() : "";
        StringBuilder combinedText = new StringBuilder();
        if (!informeCostos.isEmpty()) {
            combinedText.append("--- INFORME DE COSTOS ---\n");
            combinedText.append(informeCostos);
        }
        if (!aclaraciones.isEmpty()) {
            if (combinedText.length() > 0) {
                combinedText.append("\n\n--- ACLARACIONES ---\n");
            }
            combinedText.append(aclaraciones);
        }
        aclaracionesArea.replaceText(combinedText.toString());
    }

    private void populateEquipoFields(Equipo equipo) {
        isAutoCompleting = true;
        equipoTipoField.setText(equipo.getTipo());
        equipoCompaniaField.setText(equipo.getMarca());
        equipoModeloField.setText(equipo.getModelo());
        equipoSerieField.setText(equipo.getSerie());
        equipoFallaArea.replaceText(equipo.getFalla() != null ? equipo.getFalla() : "");
        equipoEstadoFisicoArea.replaceText(equipo.getEstadoFisico() != null ? equipo.getEstadoFisico() : "");

        accesoriosList.clear();
        String acc = equipo.getAccesorios();
        if (acc != null && !acc.trim().isEmpty()) {
            accesoriosList.setAll(Arrays.asList(acc.split("\\s*,\\s*")));
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
        // Opcional: validar campos del primer equipo
        if (!equiposObservable.isEmpty()) {
            Equipo primero = equiposObservable.get(0);
            if (primero.getTipo() == null || primero.getTipo().trim().isEmpty()) camposFaltantes.add("• Tipo de Equipo (primer equipo)");
            if (primero.getMarca() == null || primero.getMarca().trim().isEmpty()) camposFaltantes.add("• • Marca (primer equipo)");
        }

        if (!camposFaltantes.isEmpty()) {
            return Optional.of("Los siguientes campos son obligatorios y no pueden estar vacíos:\n\n" + String.join("\n", camposFaltantes));
        }
        return Optional.empty();
    }

    private void resetCamposCliente() {
        idClienteSeleccionado = null;
        nombreClienteSeleccionado = null;
        clienteNombreField.clear();
        clienteDireccionField.clear();
        clienteTelefonoField.clear();
        clienteDireccionField.setEditable(true);
        clienteTelefonoField.setEditable(true);
        resetCamposEquipo();
    }

    private void resetOtherEquipoFields() {
        equipoTipoField.clear();
        equipoCompaniaField.clear();
        equipoModeloField.clear();
        equipoFallaArea.clear();
        equipoEstadoFisicoArea.clear();
        accesoriosList.clear();
        equipoCostoField.clear();
        equipoTipoField.setEditable(true);
        equipoCompaniaField.setEditable(true);
        equipoModeloField.setEditable(true);
    }

    private void resetCamposEquipo() {
        idAssetSeleccionado = null;
        serieEquipoSeleccionado = null;
        equipoSerieField.clear();
        resetOtherEquipoFields();
    }

    private void predecirYAsignarNumeroDeOrden() {
        long maxId = 0;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT MAX(id) FROM x_hojas_servicio");
                if (rs.next()) maxId = rs.getLong(1);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.WARNING, "Error de Predicción", "No se pudo predecir el número de orden.");
        }
        this.predictedHojaId = maxId + 1;
        String provisionalOrden = "TM-" + LocalDate.now().getYear() + "-" + this.predictedHojaId;
        ordenNumeroField.setText(provisionalOrden);
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
}
