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
import java.sql.Date; // Import java.sql.Date
import java.time.LocalDate;
import java.util.StringJoiner;
import java.util.function.Consumer; // Import Consumer

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
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar las hojas de servicio del cliente.");
            e.printStackTrace();
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

    @FXML
    protected void onViewDetailsClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para ver sus detalles.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getOrderNumber(), (data) -> {
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
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudo abrir la ventana de detalles.");
                e.printStackTrace();
            }
        });
    }

    private void fetchAndProcessServiceSheet(String orderNumber, Consumer<HojaServicioData> dataConsumer) {
        String sql = "SELECT hs.*, c.nombre as cliente_nombre, c.direccion as cliente_direccion, c.telefono as cliente_telefono " +
                     "FROM x_hojas_servicio hs JOIN x_clientes c ON hs.cliente_id = c.id WHERE hs.numero_orden = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, orderNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                HojaServicioData data = createHojaServicioDataFromResultSet(rs);
                dataConsumer.accept(data);
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "No se encontraron los detalles para la hoja de servicio seleccionada.");
            }

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "Ocurrió un error al consultar los datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private HojaServicioData createHojaServicioDataFromResultSet(ResultSet rs) throws SQLException {
        HojaServicioData data = new HojaServicioData();
        data.setNumeroOrden(rs.getString("numero_orden"));
        Date fechaOrden = rs.getDate("fecha_orden");
        if (fechaOrden != null) data.setFechaOrden(fechaOrden.toLocalDate());
        data.setClienteNombre(rs.getString("cliente_nombre"));
        data.setClienteDireccion(rs.getString("cliente_direccion"));
        data.setClienteTelefono(rs.getString("cliente_telefono"));
        data.setEquipoSerie(rs.getString("equipo_serie"));
        data.setEquipoTipo(rs.getString("equipo_tipo"));
        data.setEquipoMarca(rs.getString("equipo_marca"));
        data.setEquipoModelo(rs.getString("equipo_modelo"));
        data.setFallaReportada(rs.getString("falla_reportada"));
        data.setInformeCostos(rs.getString("informe_costos"));
        data.setTotalCostos(rs.getBigDecimal("total_costos"));
        Date fechaEntrega = rs.getDate("fecha_entrega");
        if (fechaEntrega != null) data.setFechaEntrega(fechaEntrega.toLocalDate());
        data.setFirmaAclaracion(rs.getString("firma_aclaracion"));
        data.setAclaraciones(rs.getString("aclaraciones"));
        return data;
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

    // Clase interna para el modelo de datos de la hoja de servicio
    public static class ServiceSheetSummary {
        private final SimpleStringProperty orderNumber;
        private final LocalDate orderDate; // Changed to LocalDate
        private final SimpleStringProperty equipment;

        public ServiceSheetSummary(String orderNumber, LocalDate orderDate, String equipment) {
            this.orderNumber = new SimpleStringProperty(orderNumber);
            this.orderDate = orderDate; // Assign LocalDate directly
            this.equipment = new SimpleStringProperty(equipment);
        }

        public String getOrderNumber() { return orderNumber.get(); }
        public LocalDate getOrderDate() { return orderDate; } // Getter for LocalDate
        public String getEquipment() { return equipment.get(); }
    }
}
