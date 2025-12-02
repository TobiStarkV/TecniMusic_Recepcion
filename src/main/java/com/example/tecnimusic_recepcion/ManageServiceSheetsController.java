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
import javafx.stage.Stage;
import javafx.stage.Window;

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
    @FXML
    private Button editSheetButton;
    @FXML
    private CheckBox showAnuladasCheckBox; // Nuevo: CheckBox para mostrar/ocultar anuladas

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

        // Listener para habilitar/deshabilitar el botón de editar
        editSheetButton.setDisable(true); // Keep disabled by default
        serviceSheetsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                // Enable edit button for 'ABIERTA' or 'CERRADA' sheets
                editSheetButton.setDisable(!("ABIERTA".equals(newSelection.getStatus()) || "CERRADA".equals(newSelection.getStatus())));
            } else {
                editSheetButton.setDisable(true);
            }
        });

        serviceSheetsTable.setRowFactory(tv -> {
            TableRow<ServiceSheetSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    onViewDetailsClicked();
                }
            });
            return row;
        });

        // Asegurarse de que el CheckBox esté desmarcado al inicio
        showAnuladasCheckBox.setSelected(false); 
        // Cargar inicialmente las hojas de servicio (sin anuladas por defecto)
        searchAndLoadServiceSheets(null);
        // Listener para el CheckBox
        showAnuladasCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> onShowAnuladasChanged());
    }

    private void searchAndLoadServiceSheets(String searchTerm) {
        serviceSheets.clear();

        String baseSql = "SELECT hs.id, hs.numero_orden, hs.fecha_orden, c.nombre as cliente_nombre, hs.estado, " +
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

        StringBuilder whereClause = new StringBuilder();
        List<String> params = new ArrayList<>();

        // Filtrar por estado "ANULADA" si el checkbox no está marcado
        if (!showAnuladasCheckBox.isSelected()) {
            whereClause.append(" WHERE hs.estado != ? ");
            params.add("ANULADA");
        }

        boolean hasSearchTerm = searchTerm != null && !searchTerm.trim().isEmpty();
        if (hasSearchTerm) {
            String searchPattern = "%" + searchTerm.trim() + "%";
            String searchCondition = " (hs.numero_orden LIKE ? OR c.nombre LIKE ? OR hse.equipo_serie LIKE ? OR hse.equipo_marca LIKE ? OR hse.equipo_modelo LIKE ?) ";
            
            if (whereClause.length() == 0) {
                whereClause.append(" WHERE ").append(searchCondition);
            } else {
                whereClause.append(" AND ").append(searchCondition);
            }
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
        }

        String sql = baseSql + whereClause.toString() +
                     "GROUP BY hs.id, hs.numero_orden, hs.fecha_orden, c.nombre, hs.estado " +
                     "ORDER BY hs.id DESC";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setString(i + 1, params.get(i));
            }

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                serviceSheets.add(new ServiceSheetSummary(
                        rs.getLong("id"),
                        rs.getString("numero_orden"),
                        rs.getDate("fecha_orden").toLocalDate(),
                        rs.getString("cliente_nombre"),
                        rs.getString("equipment_summary") != null ? rs.getString("equipment_summary") : "",
                        rs.getString("estado")
                ));
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
    protected void onShowAnuladasChanged() {
        searchAndLoadServiceSheets(searchField.getText());
    }

    @FXML
    protected void onPrintReceptionClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para imprimir.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getId(), (data) -> {
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

        fetchAndProcessServiceSheet(selected.getId(), (data) -> {
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
    protected void onEditSheetClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Inválida", "Por favor, seleccione una hoja de servicio para editar.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getId(), (data) -> {
            if ("ABIERTA".equals(selected.getStatus())) {
                // Para hojas ABIERTAS, abrir en modo de edición de recepción
                openTecniMusicView(data, true, null); 
            } else if ("CERRADA".equals(selected.getStatus())) {
                // Para hojas CERRADAS, abrir en modo de vista, que permite editar detalles de cierre
                openTecniMusicView(data, false, null);
            } else {
                showAlert(Alert.AlertType.WARNING, "Estado Inválido", "No se puede editar una hoja con estado '" + selected.getStatus() + "'.");
            }
        });
    }

    @FXML
    protected void onViewDetailsClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para ver sus detalles.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getId(), (data) -> {
            openTecniMusicView(data, false, null); // false para modo vista, sin motivo
        });
    }

    private void openTecniMusicView(HojaServicioData data, boolean isEditMode, String motivoEdicion) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("tecniMusic-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

            tecniMusicController controller = fxmlLoader.getController();
            if (isEditMode) {
                controller.loadForEditing(data, motivoEdicion);
            } else {
                controller.loadForViewing(data);
            }

            Stage stage = new Stage();
            stage.setTitle(isEditMode ? "Editando Hoja de Servicio: " + data.getNumeroOrden() : "Detalles de Hoja de Servicio: " + data.getNumeroOrden());
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
            stage.setScene(scene);
            
            Window parentWindow = serviceSheetsTable.getScene().getWindow();
            parentWindow.getScene().getRoot().setDisable(true);
            
            stage.setOnHidden(e -> {
                parentWindow.getScene().getRoot().setDisable(false);
                searchAndLoadServiceSheets(searchField.getText());
            });
            
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudo abrir la ventana de detalles/edición.");
            e.printStackTrace();
        }
    }

    @FXML
    protected void onSalirClicked() {
        Stage stage = (Stage) salirButton.getScene().getWindow();
        stage.close();
    }

    private void fetchAndProcessServiceSheet(long hojaId, java.util.function.Consumer<HojaServicioData> dataConsumer) {
        try {
            HojaServicioData data = DatabaseService.getInstance().getHojaServicioCompleta(hojaId);
            if (data != null) {
                dataConsumer.accept(data);
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "No se pudieron obtener los detalles completos para la hoja de servicio seleccionada.");
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
        alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
        alert.showAndWait();
    }

    public static class ServiceSheetSummary {
        private final long id;
        private final SimpleStringProperty orderNumber;
        private final LocalDate date;
        private final SimpleStringProperty clientName;
        private final SimpleStringProperty equipment;
        private final SimpleStringProperty status;

        public ServiceSheetSummary(long id, String orderNumber, LocalDate date, String clientName, String equipment, String status) {
            this.id = id;
            this.orderNumber = new SimpleStringProperty(orderNumber);
            this.date = date;
            this.clientName = new SimpleStringProperty(clientName);
            this.equipment = new SimpleStringProperty(equipment);
            this.status = new SimpleStringProperty(status);
        }

        public long getId() { return id; }
        public String getOrderNumber() { return orderNumber.get(); }
        public LocalDate getDate() { return date; }
        public String getClientName() { return clientName.get(); }
        public String getEquipment() { return equipment.get(); }
        public String getStatus() { return status.get(); }
    }
}
