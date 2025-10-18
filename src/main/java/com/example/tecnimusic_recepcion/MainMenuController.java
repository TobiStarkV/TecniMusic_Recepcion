package com.example.tecnimusic_recepcion;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class MainMenuController {

    @FXML
    private Button createServiceSheetButton;
    @FXML
    private Button manageServiceSheetsButton;
    @FXML
    private Button manageClientsButton;

    @FXML
    public void initialize() {
        // Opcional: Configuración inicial de los botones o la ventana
    }

    @FXML
    protected void onCreateServiceSheetClicked() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/tecnimusic_recepcion/tecniMusic-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("TecniMusic - Nueva Hoja de Servicio");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL); // Bloquea la ventana principal hasta que esta se cierre
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo abrir la ventana de Nueva Hoja de Servicio.");
        }
    }

    @FXML
    protected void onManageServiceSheetsClicked() {
        try {
            // TODO: Crear el FXML y el Controller para la gestión de hojas de servicio
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/tecnimusic_recepcion/manage-service-sheets-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("TecniMusic - Consultar/Gestionar Hojas de Servicio");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo abrir la ventana de Gestión de Hojas de Servicio.");
        }
    }

    @FXML
    protected void onManageClientsClicked() {
        try {
            // TODO: Crear el FXML y el Controller para la gestión de clientes
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/tecnimusic_recepcion/manage-clients-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("TecniMusic - Gestión de Clientes");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo abrir la ventana de Gestión de Clientes.");
        }
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
