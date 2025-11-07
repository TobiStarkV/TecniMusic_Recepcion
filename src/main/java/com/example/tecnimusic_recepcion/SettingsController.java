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

    private DatabaseConfig dbConfig;

    @FXML
    public void initialize() {
        dbConfig = new DatabaseConfig();
        loadSettings();
        if (pdfFooterField != null) {
            setupSpellChecking(pdfFooterField);
        }
    }

    private void loadSettings() {
        hostField.setText(dbConfig.getHost());
        portField.setText(dbConfig.getPort());
        dbNameField.setText(dbConfig.getDbName());
        userField.setText(dbConfig.getUser());
        passwordField.setText(dbConfig.getPassword());
        pdfFooterField.replaceText(dbConfig.getPdfFooter());
    }

    @FXML
    private void handleSave() {
        dbConfig.setHost(hostField.getText());
        dbConfig.setPort(portField.getText());
        dbConfig.setDbName(dbNameField.getText());
        dbConfig.setUser(userField.getText());
        dbConfig.setPassword(passwordField.getText());
        dbConfig.setPdfFooter(pdfFooterField.getText());
        dbConfig.save();

        showAlert(Alert.AlertType.INFORMATION, "Configuración Guardada", "La configuración se ha guardado correctamente.");
        closeWindow();
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
