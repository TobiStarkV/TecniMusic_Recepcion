package com.example.tecnimusic_recepcion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SettingsController {

    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private TextField dbNameField;
    @FXML
    private TextField userField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private StyleClassedTextArea pdfFooterField;
    @FXML
    private Spinner<Integer> pdfFooterSizeSpinner;
    @FXML
    private TextField localNombreField;
    @FXML
    private TextField localDireccionField;
    @FXML
    private TextField localTelefonoField;

    @FXML
    public void initialize() {
        // Configurar el Spinner
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(4, 12, 6);
        pdfFooterSizeSpinner.setValueFactory(valueFactory);

        loadSettings();

        Platform.runLater(() -> {
            if (hostField.getScene() != null) {
                String css = this.getClass().getResource("styles.css").toExternalForm();
                hostField.getScene().getStylesheets().add(css);
            }
        });

        if (pdfFooterField != null) {
            setupSpellChecking(pdfFooterField);
            pdfFooterField.setStyle("-fx-background-color: #1E2A3A; -fx-text-fill: white;");
        }
    }

    private void loadSettings() {
        // Cargar configuración de la DB (del archivo local)
        DatabaseConfig dbConfig = DatabaseManager.getInstance().getDbConfig();
        hostField.setText(dbConfig.getHost());
        portField.setText(dbConfig.getPort());
        dbNameField.setText(dbConfig.getDbName());
        userField.setText(dbConfig.getUser());
        passwordField.setText(dbConfig.getPassword());

        // Intentar cargar configuración de PDF y Local (de la base de datos)
        try {
            DatabaseService dbService = DatabaseService.getInstance();
            pdfFooterField.replaceText(dbService.getSetting("pdf.footer", ""));
            int footerSize = Integer.parseInt(dbService.getSetting("pdf.footer.fontsize", "6"));
            pdfFooterSizeSpinner.getValueFactory().setValue(footerSize);
            
            localNombreField.setText(dbService.getSetting("local.nombre", "TecniMusic"));
            localDireccionField.setText(dbService.getSetting("local.direccion", "Dirección no configurada"));
            localTelefonoField.setText(dbService.getSetting("local.telefono", "Teléfono no configurado"));
            // Habilitar campos si la carga fue exitosa
            setLocalPdfFieldsDisabled(false);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.WARNING, "Sin Conexión", "No se pudo conectar a la base de datos para cargar la configuración del local y PDF. Estos campos están deshabilitados.");
            // Deshabilitar campos si la carga falló
            setLocalPdfFieldsDisabled(true);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Configuración Corrupta", "El tamaño de la fuente del pie de página no es un número válido. Se usará el valor por defecto.");
            pdfFooterSizeSpinner.getValueFactory().setValue(6);
        }
    }

    @FXML
    private void handleSave() {
        // 1. Guardar configuración de la DB en el archivo local
        DatabaseConfig dbConfig = DatabaseManager.getInstance().getDbConfig();
        dbConfig.setHost(hostField.getText());
        dbConfig.setPort(portField.getText());
        dbConfig.setDbName(dbNameField.getText());
        dbConfig.setUser(userField.getText());
        dbConfig.setPassword(passwordField.getText());
        dbConfig.save();

        boolean wereFieldsDisabled = localNombreField.isDisabled();

        // 2. Resetear y probar la nueva conexión
        DatabaseManager.resetInstance();
        try (Connection testConnection = DatabaseManager.getInstance().getConnection()) {
            // Si la conexión es exitosa
            if (wereFieldsDisabled) {
                // Los campos estaban deshabilitados, lo que significa que acabamos de arreglar la conexión.
                // Recargamos los datos desde la DB en lugar de guardar los campos vacíos.
                loadSettings(); // Esto recargará y habilitará los campos.
                showAlert(Alert.AlertType.INFORMATION, "Conexión Exitosa", "Se ha conectado a la base de datos. La configuración del local y PDF ha sido cargada. Verifique los datos y guarde de nuevo si es necesario.");
            } else {
                // La conexión ya era buena, así que procedemos a guardar los cambios.
                DatabaseService dbService = DatabaseService.getInstance();
                dbService.saveSetting("pdf.footer", pdfFooterField.getText());
                dbService.saveSetting("pdf.footer.fontsize", pdfFooterSizeSpinner.getValue().toString());
                dbService.saveSetting("local.nombre", localNombreField.getText());
                dbService.saveSetting("local.direccion", localDireccionField.getText());
                dbService.saveSetting("local.telefono", localTelefonoField.getText());

                showAlert(Alert.AlertType.INFORMATION, "Configuración Guardada", "Toda la configuración se ha guardado correctamente.");
                closeWindow();
            }

        } catch (SQLException e) {
            // Si la conexión falla con los nuevos datos
            showAlert(Alert.AlertType.ERROR, "Error de Conexión", "No se pudo conectar a la base de datos con la nueva configuración. \n\nLos datos de conexión se guardaron, pero la configuración del local y PDF no pudo ser actualizada. Por favor, revise los datos de conexión.");
            setLocalPdfFieldsDisabled(true); // Mantener los campos deshabilitados
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error al guardar la configuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setLocalPdfFieldsDisabled(boolean disabled) {
        pdfFooterField.setDisable(disabled);
        pdfFooterSizeSpinner.setDisable(disabled);
        localNombreField.setDisable(disabled);
        localDireccionField.setDisable(disabled);
        localTelefonoField.setDisable(disabled);
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) hostField.getScene().getWindow();
        stage.close();
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

    private void setupSpellChecking(StyleClassedTextArea textArea) {
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
}
