package com.example.tecnimusic_recepcion;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.StringJoiner;
import com.example.tecnimusic_recepcion.Client;

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
    private Button closeButton;

    private final ObservableList<ServiceSheetSummary> serviceSheetList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        orderNumberColumn.setCellValueFactory(new PropertyValueFactory<>("orderNumber"));
        orderDateColumn.setCellValueFactory(new PropertyValueFactory<>("orderDate"));
        equipmentColumn.setCellValueFactory(new PropertyValueFactory<>("equipment"));
        serviceSheetsTable.setItems(serviceSheetList);
    }

    public void setClient(Client client) {
        clientNameLabel.setText("Hojas de Servicio para: " + client.getName());
        loadServiceSheets(client.getId());
    }

    private void loadServiceSheets(int clientId) {
        serviceSheetList.clear();
        String sql = "SELECT hs.numero_orden, hs.fecha_orden, hs.equipo_tipo, hs.equipo_marca, hs.equipo_modelo, hs.equipo_serie " +
                     "FROM x_hojas_servicio hs WHERE hs.cliente_id = ? ORDER BY hs.fecha_orden DESC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, clientId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                StringJoiner equipmentJoiner = new StringJoiner(" - ");
                addIfNotNull(equipmentJoiner, rs.getString("equipo_tipo"));
                addIfNotNull(equipmentJoiner, rs.getString("equipo_marca"));
                addIfNotNull(equipmentJoiner, rs.getString("equipo_modelo"));
                addIfNotNull(equipmentJoiner, rs.getString("equipo_serie"));

                serviceSheetList.add(new ServiceSheetSummary(
                        rs.getString("numero_orden"),
                        rs.getDate("fecha_orden").toLocalDate(),
                        equipmentJoiner.toString()
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // Aquí podrías mostrar una alerta al usuario
        }
    }

    private void addIfNotNull(StringJoiner joiner, String value) {
        if (value != null && !value.trim().isEmpty()) {
            joiner.add(value);
        }
    }

    @FXML
    private void handleCloseButton() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    // Clase interna para el modelo de datos de la hoja de servicio
    public static class ServiceSheetSummary {
        private final SimpleStringProperty orderNumber;
        private final SimpleStringProperty orderDate;
        private final SimpleStringProperty equipment;

        public ServiceSheetSummary(String orderNumber, LocalDate orderDate, String equipment) {
            this.orderNumber = new SimpleStringProperty(orderNumber);
            this.orderDate = new SimpleStringProperty(orderDate.toString()); // Simplificado para mostrar
            this.equipment = new SimpleStringProperty(equipment);
        }

        public String getOrderNumber() { return orderNumber.get(); }
        public String getOrderDate() { return orderDate.get(); }
        public String getEquipment() { return equipment.get(); }
    }
}
