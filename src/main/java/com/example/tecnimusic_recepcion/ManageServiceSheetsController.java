package com.example.tecnimusic_recepcion;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ManageServiceSheetsController {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<ServiceSheetSummary> serviceSheetsTable;
    @FXML
    private TableColumn<ServiceSheetSummary, String> orderNumberCol;
    @FXML
    private TableColumn<ServiceSheetSummary, LocalDate> dateCol;
    @FXML
    private TableColumn<ServiceSheetSummary, String> clientNameCol;
    @FXML
    private TableColumn<ServiceSheetSummary, String> equipmentCol;
    @FXML
    private TableColumn<ServiceSheetSummary, String> statusCol;
    @FXML
    private Button salirButton;

    private final ObservableList<ServiceSheetSummary> serviceSheets = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        try {
            DatabaseService.getInstance().checkAndUpgradeSchema();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo verificar o actualizar la estructura de la base de datos.");
        }

        orderNumberCol.setCellValueFactory(new PropertyValueFactory<>("orderNumber"));
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        clientNameCol.setCellValueFactory(new PropertyValueFactory<>("clientName"));
        equipmentCol.setCellValueFactory(new PropertyValueFactory<>("equipment"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));


        serviceSheetsTable.setItems(serviceSheets);

        serviceSheetsTable.setRowFactory(tv -> {
            TableRow<ServiceSheetSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    onViewDetailsClicked();
                }
            });
            return row;
        });

        searchAndLoadServiceSheets(null);
    }

    private void searchAndLoadServiceSheets(String searchTerm) {
        serviceSheets.clear();

        String baseSql = "SELECT hs.numero_orden, hs.fecha_orden, c.nombre as cliente_nombre, hs.estado, " +
                         "GROUP_CONCAT( " +
                         "    TRIM(CONCAT_WS(' ', " +
                         "        hse.equipo_tipo, " +
                         "        hse.equipo_marca, " +
                         "        hse.equipo_modelo, " +
                         "        CASE WHEN hse.equipo_serie IS NOT NULL AND hse.equipo_serie != '' THEN CONCAT('(Serie: ', hse.equipo_serie, ')') ELSE NULL END " +
                         "    )) " +
                         "    SEPARATOR '; ' " +
                         ") AS equipment_summary " +
                         "FROM x_hojas_servicio hs " +
                         "JOIN x_clientes c ON hs.cliente_id = c.id " +
                         "LEFT JOIN x_hojas_servicio_equipos hse ON hs.id = hse.hoja_id ";

        String sql;
        boolean hasSearchTerm = searchTerm != null && !searchTerm.trim().isEmpty();

        if (hasSearchTerm) {
            sql = baseSql + "WHERE hs.numero_orden LIKE ? OR c.nombre LIKE ? OR " +
                          "hse.equipo_serie LIKE ? OR hse.equipo_marca LIKE ? OR hse.equipo_modelo LIKE ? " +
                          "GROUP BY hs.id, hs.numero_orden, hs.fecha_orden, c.nombre, hs.estado " +
                          "ORDER BY hs.id DESC";
        } else {
            sql = baseSql + "GROUP BY hs.id, hs.numero_orden, hs.fecha_orden, c.nombre, hs.estado " +
                          "ORDER BY hs.id DESC";
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (hasSearchTerm) {
                String searchPattern = "%" + searchTerm.trim() + "%";
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, searchPattern);
                pstmt.setString(5, searchPattern);
            }

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String orderNumber = rs.getString("numero_orden");
                LocalDate date = rs.getDate("fecha_orden").toLocalDate();
                String clientName = rs.getString("cliente_nombre");
                String equipmentSummary = rs.getString("equipment_summary");
                String status = rs.getString("estado");

                serviceSheets.add(new ServiceSheetSummary(orderNumber, date, clientName, equipmentSummary != null ? equipmentSummary : "", status));
            }

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar las hojas de servicio.");
            e.printStackTrace();
        }
    }

    @FXML
    protected void onSearchAction() {
        searchAndLoadServiceSheets(searchField.getText());
    }

    @FXML
    protected void onPrintReceptionClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para imprimir.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getOrderNumber(), (data) -> {
            try {
                // Forzar la generación del PDF de recepción
                data.setEstado("ABIERTA"); 
                String pdfPath = new PdfGenerator().generatePdf(data);
                performPrint(pdfPath);
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @FXML
    protected void onPrintClosureClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para imprimir.");
            return;
        }

        if (!"CERRADA".equals(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Hoja no Cerrada", "La hoja de servicio debe estar cerrada para poder imprimir el informe de cierre.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getOrderNumber(), (data) -> {
            try {
                String pdfPath = new PdfGenerator().generatePdf(data);
                performPrint(pdfPath);
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void performPrint(String pdfPath) {
        if (pdfPath == null) {
            showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo encontrar la ruta del PDF para imprimir.");
            return;
        }
        
        File pdfFile = new File(pdfPath);
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(pdfFile);
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error al Abrir", "No se pudo abrir el archivo PDF con el visor predeterminado.");
                e.printStackTrace();
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Función no Soportada", "La apertura automática de archivos no es soportada en este sistema.");
        }
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
                
                // Después de cerrar la ventana de detalles, refrescar la tabla
                searchAndLoadServiceSheets(searchField.getText());

            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudo abrir la ventana de detalles.");
                e.printStackTrace();
            }
        });
    }

    @FXML
    protected void onSalirClicked() {
        Stage stage = (Stage) salirButton.getScene().getWindow();
        stage.close();
    }

    private void fetchAndProcessServiceSheet(String orderNumber, java.util.function.Consumer<HojaServicioData> dataConsumer) {
        String sql = "SELECT hs.id FROM x_hojas_servicio hs WHERE hs.numero_orden = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, orderNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                long hojaId = rs.getLong("id");
                HojaServicioData data = DatabaseService.getInstance().getHojaServicioCompleta(hojaId);
                if (data != null) {
                    dataConsumer.accept(data);
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "No se pudieron obtener los detalles completos para la hoja de servicio seleccionada.");
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "No se encontraron los detalles para la hoja de servicio seleccionada.");
            }

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "Ocurrió un error al consultar los datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        // Añadir estilos y icono a todos los diálogos
        alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
        alert.showAndWait();
    }

    public static class ServiceSheetSummary {
        private final SimpleStringProperty orderNumber;
        private final LocalDate date;
        private final SimpleStringProperty clientName;
        private final SimpleStringProperty equipment;
        private final SimpleStringProperty status;

        public ServiceSheetSummary(String orderNumber, LocalDate date, String clientName, String equipment, String status) {
            this.orderNumber = new SimpleStringProperty(orderNumber);
            this.date = date;
            this.clientName = new SimpleStringProperty(clientName);
            this.equipment = new SimpleStringProperty(equipment);
            this.status = new SimpleStringProperty(status);
        }

        public String getOrderNumber() { return orderNumber.get(); }
        public LocalDate getDate() { return date; }
        public String getClientName() { return clientName.get(); }
        public String getEquipment() { return equipment.get(); }
        public String getStatus() { return status.get(); }
    }
}
