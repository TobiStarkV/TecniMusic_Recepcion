package com.example.tecnimusic_recepcion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PopupControl;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
// import javafx.scene.input.MouseEvent; // Removed unused import
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.sql.SQLException;
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

    // Custom Autocomplete components
    private PopupControl suggestionPopup;
    private ListView<String> suggestionListView;

    @FXML
    private void initialize() {
        accesorios = FXCollections.observableArrayList();
        accesoriosListView.setItems(accesorios);
        setupSpellChecking();
        loadAccessorySuggestions();
        setupAutocomplete(); // Call the new autocomplete setup method

        // Replicar el método exacto de tecniMusicController para asegurar el estilo
        accesorioField.setStyle("-fx-background-color: #1E2A3A;");

        accesorioField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (suggestionPopup.isShowing() && suggestionListView.getSelectionModel().getSelectedItem() != null) {
                    // Handled by suggestionListView.setOnKeyPressed, so do nothing here
                } else {
                    onAddAccesorio();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN && suggestionPopup.isShowing()) {
                suggestionListView.requestFocus();
                suggestionListView.getSelectionModel().selectFirst();
                event.consume();
            } else if (event.getCode() == KeyCode.UP && suggestionPopup.isShowing()) {
                suggestionListView.requestFocus();
                suggestionListView.getSelectionModel().selectLast();
                event.consume();
            }
        });
    }

    private void setupAutocomplete() {
        suggestionPopup = new PopupControl();
        suggestionListView = new ListView<>();
        suggestionListView.setPrefHeight(150); // Set a reasonable height
        suggestionListView.prefWidthProperty().bind(accesorioField.widthProperty()); // Bind width to text area
        suggestionListView.setItems(accesorioSuggestions); // Bind to the existing suggestions list

        // Custom cell factory (optional, but good for UX)
        suggestionListView.setCellFactory(lv -> new ListCell<>() { // Use diamond operator
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
            }
        });

        suggestionPopup.getScene().setRoot(suggestionListView);
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);

        // Listener for text changes to filter and show/hide suggestions
        accesorioField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isEmpty()) {
                suggestionPopup.hide();
                return;
            }

            String currentWord = getCurrentWord(newText, accesorioField.getCaretPosition());
            if (currentWord.isEmpty()) {
                suggestionPopup.hide();
                return;
            }

            ObservableList<String> filteredSuggestions = FXCollections.observableArrayList();
            for (String suggestion : accesorioSuggestions) {
                if (suggestion.toLowerCase().startsWith(currentWord.toLowerCase())) {
                    filteredSuggestions.add(suggestion);
                }
            }

            if (filteredSuggestions.isEmpty()) {
                suggestionPopup.hide();
            } else {
                suggestionListView.setItems(filteredSuggestions);
                showSuggestionPopup();
            }
        });

        // Handle selection from the suggestion list
        suggestionListView.setOnMouseClicked(event -> {
            String selectedSuggestion = suggestionListView.getSelectionModel().getSelectedItem();
            if (selectedSuggestion != null) {
                replaceCurrentWord(selectedSuggestion);
                suggestionPopup.hide();
                accesorioField.requestFocus(); // Return focus to the text area
            }
        });

        // Handle keyboard navigation in the suggestion list
        suggestionListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String selectedSuggestion = suggestionListView.getSelectionModel().getSelectedItem();
                if (selectedSuggestion != null) {
                    replaceCurrentWord(selectedSuggestion);
                    suggestionPopup.hide();
                    accesorioField.requestFocus(); // Return focus to the text area
                    event.consume(); // Consume to prevent adding a new line in the textarea
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                suggestionPopup.hide();
                accesorioField.requestFocus(); // Return focus to the text area
                event.consume();
            }
        });

        // Hide popup if text area loses focus
        accesorioField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                suggestionPopup.hide();
            }
        });
    }

    private void showSuggestionPopup() {
        if (!suggestionPopup.isShowing()) {
            Bounds bounds = accesorioField.localToScreen(accesorioField.getBoundsInLocal());
            suggestionPopup.show(accesorioField, bounds.getMinX(), bounds.getMaxY());
        }
    }

    private String getCurrentWord(String text, int caretPosition) {
        int start = caretPosition - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }
        start++; // Move past the whitespace or to the beginning of the string

        int end = caretPosition;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }

        if (start >= 0 && start < end && end <= text.length()) {
            return text.substring(start, end);
        }
        return "";
    }

    private void replaceCurrentWord(String replacement) {
        int caretPosition = accesorioField.getCaretPosition();
        String text = accesorioField.getText();

        int start = caretPosition - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }
        start++;

        int end = caretPosition;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }

        accesorioField.replaceText(start, end, replacement);
    }

    private void loadAccessorySuggestions() {
        try {
            List<String> suggestions = DatabaseService.getInstance().getAllAccesorios();
            accesorioSuggestions.setAll(suggestions);
        } catch (SQLException e) {
            System.err.println("Error loading accessory suggestions: " + e.getMessage()); // Replaced printStackTrace
        }
    }

    private void setupSpellChecking() {
        PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
        accesorioField.textProperty().addListener((observable, oldValue, newValue) ->
            pause.setOnFinished(event -> Platform.runLater(() -> { // Converted to expression lambda
                try {
                    List<RuleMatch> matches = CorrectorOrtografico.verificar(newValue);
                    applyHighlighting(matches);
                } catch (IOException e) {
                    // ignore
                }
            }))
        );
        pause.playFromStart();


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
                            item.setOnAction(evt -> // Converted to expression lambda
                                accesorioField.replaceText(currentMatch.getFromPos(), currentMatch.getToPos(), suggestion)
                            );
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
                accesorioSuggestions.add(accesorio); // Add to suggestions list for immediate use
            }
        } catch (SQLException e) {
            System.err.println("Error saving accessory: " + e.getMessage()); // Replaced printStackTrace
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
