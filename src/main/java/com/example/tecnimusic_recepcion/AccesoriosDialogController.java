package com.example.tecnimusic_recepcion;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PopupControl;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
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
import java.util.stream.Collectors;

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
                    // Apply the selected suggestion directly
                    String selectedSuggestion = suggestionListView.getSelectionModel().getSelectedItem();
                    replaceCurrentWord(selectedSuggestion);
                    suggestionPopup.hide();
                    accesorioField.requestFocus();
                } else {
                    // No suggestion selected or popup not showing, add the current text as a new accessory
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
        suggestionListView.setPrefHeight(150);
        suggestionListView.prefWidthProperty().bind(accesorioField.widthProperty());

        // Set a basic cell factory for displaying items
        suggestionListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });

        // --- START OF NEW CONTEXT MENU IMPLEMENTATION ---
        suggestionListView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.SECONDARY) { // Right-click
                ListCell<String> cell = findListCell((Node) event.getTarget(), suggestionListView); // Cast event.getTarget() to Node
                if (cell != null && !cell.isEmpty() && cell.getItem() != null) {
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem deleteItem = new MenuItem("Eliminar");
                    deleteItem.setOnAction(e -> deleteSuggestion(cell.getItem()));
                    contextMenu.getItems().add(deleteItem);
                    contextMenu.show(cell, event.getScreenX(), event.getScreenY());
                    event.consume(); // Consume the event to prevent other handlers
                }
            }
        });
        // --- END OF NEW CONTEXT MENU IMPLEMENTATION ---


        suggestionPopup.getScene().setRoot(suggestionListView);
        suggestionPopup.setAutoHide(true);
        suggestionPopup.setHideOnEscape(true);

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

            List<String> filtered = accesorioSuggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(currentWord.toLowerCase()))
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                suggestionPopup.hide();
            } else {
                suggestionListView.setItems(FXCollections.observableArrayList(filtered));
                showSuggestionPopup();
            }
        });

        suggestionListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) { // Only handle left-click for selection
                String selectedSuggestion = suggestionListView.getSelectionModel().getSelectedItem();
                if (selectedSuggestion != null) {
                    replaceCurrentWord(selectedSuggestion);
                    suggestionPopup.hide();
                    accesorioField.requestFocus();
                }
            }
        });

        suggestionListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String selectedSuggestion = suggestionListView.getSelectionModel().getSelectedItem();
                if (selectedSuggestion != null) {
                    replaceCurrentWord(selectedSuggestion);
                    suggestionPopup.hide();
                    accesorioField.requestFocus();
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                suggestionPopup.hide();
                accesorioField.requestFocus();
                event.consume();
            }
        });

        accesorioField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                suggestionPopup.hide();
            }
        });
    }

    // Helper method to find the ListCell from a mouse event target
    private ListCell<String> findListCell(Node node, ListView<String> listView) {
        if (node == listView) {
            return null; // Clicked on the ListView itself, not a cell
        }
        while (node != null && !(node instanceof ListCell)) {
            node = node.getParent();
        }
        return (ListCell<String>) node;
    }


    private void deleteSuggestion(String suggestion) {
        try {
            DatabaseService.getInstance().deleteAccesorio(suggestion);
            accesorioSuggestions.remove(suggestion); // Remove from the master list

            // Manually re-filter and update the suggestionListView
            String currentText = accesorioField.getText();
            String currentWord = getCurrentWord(currentText, accesorioField.getCaretPosition());

            if (!currentWord.isEmpty()) {
                ObservableList<String> filteredSuggestions = FXCollections.observableArrayList();
                for (String s : accesorioSuggestions) {
                    if (s.toLowerCase().startsWith(currentWord.toLowerCase())) {
                        filteredSuggestions.add(s);
                    }
                }
                suggestionListView.setItems(filteredSuggestions);
                if (filteredSuggestions.isEmpty()) {
                    suggestionPopup.hide();
                }
            } else {
                suggestionPopup.hide();
            }

        } catch (SQLException e) {
            System.err.println("Error deleting accessory suggestion: " + e.getMessage());
        }
    }


    private void showSuggestionPopup() {
        if (!suggestionPopup.isShowing()) {
            Bounds bounds = accesorioField.localToScreen(accesorioField.getBoundsInLocal());
            suggestionPopup.show(accesorioField, bounds.getMinX(), bounds.getMaxY());
        }
    }

    private String getCurrentWord(String text, int caretPosition) {
        if (text == null || text.isEmpty() || caretPosition < 0 || caretPosition > text.length()) {
            return "";
        }
        int start = caretPosition - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }
        start++;

        int end = caretPosition;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }

        return text.substring(start, end);
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

        if (start <= end) {
            accesorioField.replaceText(start, end, replacement);
        }
    }

    private void loadAccessorySuggestions() {
        try {
            List<String> suggestions = DatabaseService.getInstance().getAllAccesorios();
            accesorioSuggestions.setAll(suggestions);
        } catch (SQLException e) {
            System.err.println("Error loading accessory suggestions: " + e.getMessage());
        }
    }

    private void setupSpellChecking() {
        PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
        accesorioField.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> Platform.runLater(() -> {
                try {
                    List<RuleMatch> matches = CorrectorOrtografico.verificar(newValue);
                    applyHighlighting(matches);
                } catch (IOException e) {
                    // ignore
                }
            }));
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
                            item.setOnAction(evt ->
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
                accesorioSuggestions.add(accesorio);
            }
        } catch (SQLException e) {
            System.err.println("Error saving accessory: " + e.getMessage());
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