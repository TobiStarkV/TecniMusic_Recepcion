package com.example.tecnimusic_recepcion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.util.Duration;
// import org.controlsfx.control.textfield.TextFields; // Removed this import as it's not used after removing the bindAutoCompletion line
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AccesoriosDialogController {

    @FXML
    private StyleClassedTextArea accesorioField;
    @FXML
    private ListView<String> accesoriosListView;

    private Stage dialogStage;
    private ObservableList<String> accesorios;
    private boolean aceptado = false;
    private final ObservableList<String> accesorioSuggestions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        accesorios = FXCollections.observableArrayList();
        accesoriosListView.setItems(accesorios);
        setupSpellChecking();
        loadAccessorySuggestions();
        // TextFields.bindAutoCompletion(accesorioField, accesorioSuggestions); // This line caused the error and has been removed

        // Replicar el método exacto de tecniMusicController para asegurar el estilo
        accesorioField.setStyle("-fx-background-color: #1E2A3A;");

        accesorioField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onAddAccesorio();
                event.consume();
            }
        });
    }

    private void loadAccessorySuggestions() {
        try {
            List<String> suggestions = DatabaseService.getInstance().getAllAccesorios();
            accesorioSuggestions.setAll(suggestions);
        } catch (SQLException e) {
            e.printStackTrace();
            // Manejar el error, por ejemplo, mostrando una alerta
        }
    }

    private void setupSpellChecking() {
        PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
        accesorioField.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                Platform.runLater(() -> {
                    try {
                        List<RuleMatch> matches = CorrectorOrtografico.verificar(newValue);
                        applyHighlighting(matches);
                    } catch (IOException e) {
                        // ignore
                    }
                });
            });
            pause.playFromStart();
        });

        final ContextMenu contextMenu = new ContextMenu();
        accesorioField.setContextMenu(contextMenu);

        accesorioField.setOnContextMenuRequested(event -> {
            contextMenu.hide();

            Point2D click = new Point2D(event.getX(), event.getY());
            int characterIndex = accesorioField.hit(click.getX(), click.getY()).getCharacterIndex().orElse(-1);
            if (characterIndex != -1) {
                accesorioField.moveTo(characterIndex);
            }

            contextMenu.getItems().clear();
            String text = accesorioField.getText();
            if (text == null || text.trim().isEmpty()) {
                event.consume();
                return;
            }

            int caretPosition = accesorioField.getCaretPosition();

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
                                accesorioField.replaceText(currentMatch.getFromPos(), currentMatch.getToPos(), suggestion);
                            });
                            contextMenu.getItems().add(item);
                        }
                        contextMenu.getItems().add(new SeparatorMenuItem());
                    }
                }

            } catch (IOException ex) {
                // Ignorar error de corrector
            }

            if (!contextMenu.getItems().isEmpty()) {
                contextMenu.show(accesorioField, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
    }

    private void applyHighlighting(List<RuleMatch> matches) {
        accesorioField.setStyleSpans(0, computeHighlighting(accesorioField.getText(), matches));
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text, List<RuleMatch> matches) {
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        for (RuleMatch match : matches) {
            spansBuilder.add(Collections.emptyList(), match.getFromPos() - lastKwEnd);
            spansBuilder.add(Collections.singleton("spell-error"), match.getToPos() - match.getFromPos());
            lastKwEnd = match.getToPos();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setAccesorios(List<String> accesorios) {
        this.accesorios.setAll(accesorios);
    }

    public List<String> getAccesorios() {
        return accesorios;
    }

    public boolean isAceptado() {
        return aceptado;
    }

    public void setStylesheet(String stylesheet) {
        dialogStage.getScene().getStylesheets().add(stylesheet);
    }

    @FXML
    private void onAddAccesorio() {
        String accesorio = accesorioField.getText();
        if (accesorio != null && !accesorio.trim().isEmpty()) {
            String trimmedAccesorio = accesorio.trim();
            accesorios.add(trimmedAccesorio);
            saveAccesorioIfNotExists(trimmedAccesorio);
            accesorioField.clear();
        }
    }

    private void saveAccesorioIfNotExists(String accesorio) {
        try {
            if (!DatabaseService.getInstance().accesorioExists(accesorio)) {
                DatabaseService.getInstance().addAccesorio(accesorio);
                accesorioSuggestions.add(accesorio);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // Manejar el error
        }
    }

    @FXML
    private void onRemoveAccesorio() {
        String selectedItem = accesoriosListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            accesorios.remove(selectedItem);
        }
    }

    @FXML
    private void onAceptar() {
        aceptado = true;
        dialogStage.close();
    }

    @FXML
    private void onCancelar() {
        dialogStage.close();
    }
}
