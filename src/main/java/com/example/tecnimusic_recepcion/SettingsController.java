package com.example.tecnimusic_recepcion;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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
    private TextArea pdfFooterField;

    private DatabaseConfig dbConfig;

    @FXML
    public void initialize() {
        dbConfig = new DatabaseConfig();
        loadSettings();
    }

    private void loadSettings() {
        hostField.setText(dbConfig.getHost());
        portField.setText(dbConfig.getPort());
        dbNameField.setText(dbConfig.getDbName());
        userField.setText(dbConfig.getUser());
        passwordField.setText(dbConfig.getPassword());
        pdfFooterField.setText(dbConfig.getPdfFooter());
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

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
