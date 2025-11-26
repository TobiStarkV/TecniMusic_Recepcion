package com.example.tecnimusic_recepcion;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

public class MainMenuController {

    @FXML
    private Button createServiceSheetButton;
    @FXML
    private Button manageServiceSheetsButton;
    @FXML
    private Button manageClientsButton;
    @FXML
    private Button settingsButton;

    @FXML
    public void initialize() {
        // Opcional: Configuración inicial de los botones o la ventana
    }

    private void openWindow(String fxmlFile, String title, Button ownerButton) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(fxmlFile));
            Scene scene = new Scene(fxmlLoader.load());
            scene.getStylesheets().add(getClass().getResource("/com/example/tecnimusic_recepcion/styles.css").toExternalForm());
            
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
            stage.setScene(scene);

            // **LA SOLUCIÓN DEFINITIVA: SIMULACIÓN DE MODALIDAD**
            Window parentWindow = ownerButton.getScene().getWindow();
            
            // 1. Deshabilitar la ventana padre.
            parentWindow.getScene().getRoot().setDisable(true);

            // 2. Añadir un listener que se ejecute cuando la ventana hija se cierre.
            stage.setOnHidden(e -> {
                // 3. Rehabilitar la ventana padre.
                parentWindow.getScene().getRoot().setDisable(false);
            });

            // 4. Mostrar la ventana de forma no bloqueante.
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudo abrir la ventana: " + title);
        }
    }

    @FXML
    protected void onCreateServiceSheetClicked() {
        openWindow("/com/example/tecnimusic_recepcion/tecniMusic-view.fxml", "TecniMusic - Nueva Hoja de Servicio", createServiceSheetButton);
    }

    @FXML
    protected void onManageServiceSheetsClicked() {
        openWindow("/com/example/tecnimusic_recepcion/manage-service-sheets-view.fxml", "TecniMusic - Consultar/Gestionar Hojas de Servicio", manageServiceSheetsButton);
    }

    @FXML
    protected void onManageClientsClicked() {
        openWindow("/com/example/tecnimusic_recepcion/manage-clients-view.fxml", "TecniMusic - Gestión de Clientes", manageClientsButton);
    }

    @FXML
    protected void onSettingsClicked() {
        openWindow("/com/example/tecnimusic_recepcion/settings-view.fxml", "TecniMusic - Configuración", settingsButton);
    }

    @FXML
    protected void onExitClicked() {
        Platform.exit();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
