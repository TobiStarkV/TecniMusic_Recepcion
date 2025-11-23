package com.example.tecnimusic_recepcion;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Alert;
import javafx.scene.control.TableRow;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.function.Consumer;

public class ClientServiceSheetsController {

    @FXML
    private Label clientNameLabel;
    @FXML
    private TableView<ServiceSheetSummary> serviceSheetsTable;
    @FXML
    private TableColumn<ServiceSheetSummary, String> orderNumberColumn;
    @FXML
    private TableColumn<ServiceSheetSummary, LocalDate> orderDateColumn;
    @FXML
    private TableColumn<ServiceSheetSummary, String> equipmentColumn;
    @FXML
    private TableColumn<ServiceSheetSummary, String> statusColumn; // Columna añadida
    @FXML
    private Button closeButton;

    private final ObservableList<ServiceSheetSummary> serviceSheetList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        orderNumberColumn.setCellValueFactory(new PropertyValueFactory<>("orderNumber"));
        orderDateColumn.setCellValueFactory(new PropertyValueFactory<>("orderDate"));
        equipmentColumn.setCellValueFactory(new PropertyValueFactory<>("equipment"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status")); // Vinculación de la nueva columna
        serviceSheetsTable.setItems(serviceSheetList);

        serviceSheetsTable.setRowFactory(tv -> {
            TableRow<ServiceSheetSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    onViewDetailsClicked();
                }
            });
            return row;
        });
    }

    public void setClient(Client client) {
        clientNameLabel.setText("Hojas de Servicio para: " + client.getName());
        loadServiceSheets(client.getId());
    }

    private void loadServiceSheets(int clientId) {
        serviceSheetList.clear();
        String sql = "SELECT hs.id, hs.numero_orden, hs.fecha_orden, hs.estado, " +
                     "GROUP_CONCAT(DISTINCT CONCAT_WS(' ', hse.equipo_tipo, hse.equipo_marca, hse.equipo_modelo) SEPARATOR '; ') as equipment_summary " +
                     "FROM x_hojas_servicio hs " +
                     "LEFT JOIN x_hojas_servicio_equipos hse ON hs.id = hse.hoja_id " +
                     "WHERE hs.cliente_id = ? " +
                     "GROUP BY hs.id, hs.numero_orden, hs.fecha_orden, hs.estado " +
                     "ORDER BY hs.id DESC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, clientId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String equipmentSummary = rs.getString("equipment_summary");
                if (equipmentSummary == null || equipmentSummary.trim().isEmpty()) {
                    equipmentSummary = "(No hay equipos detallados)";
                }

                serviceSheetList.add(new ServiceSheetSummary(
                        rs.getLong("id"),
                        rs.getString("numero_orden"),
                        rs.getDate("fecha_orden").toLocalDate(),
                        equipmentSummary.trim(),
                        rs.getString("estado")
                ));
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar las hojas de servicio del cliente.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCloseButton() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    @FXML
    protected void onViewDetailsClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para ver sus detalles.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getId(), (data) -> {
            if (data == null) {
                showAlert(Alert.AlertType.ERROR, "Error", "No se pudieron obtener los detalles completos para la hoja de servicio seleccionada.");
                return;
            }
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("tecniMusic-view.fxml"));
                Scene scene = new Scene(fxmlLoader.load());
                scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

                tecniMusicController controller = fxmlLoader.getController();
                controller.loadForViewing(data);

                Stage stage = new Stage();
                stage.setTitle("Detalles de Hoja de Servicio: " + data.getNumeroOrden());
                stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
                stage.setScene(scene);
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.showAndWait();
                
                // Refrescar la tabla por si el estado cambió
                loadServiceSheets(selected.getClientId());

            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudo abrir la ventana de detalles.");
                e.printStackTrace();
            }
        });
    }

    private void fetchAndProcessServiceSheet(long hojaId, Consumer<HojaServicioData> dataConsumer) {
        try {
            HojaServicioData data = DatabaseService.getInstance().getHojaServicioCompleta(hojaId);
            dataConsumer.accept(data);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "Ocurrió un error al consultar los datos: " + e.getMessage());
            e.printStackTrace();
            dataConsumer.accept(null);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
        alert.showAndWait();
    }

    public static class ServiceSheetSummary {
        private final long id;
        private final int clientId;
        private final SimpleStringProperty orderNumber;
        private final LocalDate orderDate;
        private final SimpleStringProperty equipment;
        private final SimpleStringProperty status;

        public ServiceSheetSummary(long id, String orderNumber, LocalDate orderDate, String equipment, String status) {
            this.id = id;
            this.orderNumber = new SimpleStringProperty(orderNumber);
            this.orderDate = orderDate;
            this.equipment = new SimpleStringProperty(equipment);
            this.status = new SimpleStringProperty(status);
            this.clientId = 0; // Este campo ya no es necesario aquí, pero se mantiene por compatibilidad
        }

        public long getId() { return id; }
        public int getClientId() { return clientId; }
        public String getOrderNumber() { return orderNumber.get(); }
        public LocalDate getOrderDate() { return orderDate; }
        public String getEquipment() { return equipment.get(); }
        public String getStatus() { return status.get(); }
    }
}
