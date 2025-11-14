package com.example.tecnimusic_recepcion;

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

        String baseSql = "SELECT hs.numero_orden, hs.fecha_orden, c.nombre as cliente_nombre, hs.equipo_tipo, hs.equipo_marca, hs.equipo_modelo, hs.equipo_serie " +
                         "FROM x_hojas_servicio hs " +
                         "JOIN x_clientes c ON hs.cliente_id = c.id ";

        String sql;
        boolean hasSearchTerm = searchTerm != null && !searchTerm.trim().isEmpty();

        if (hasSearchTerm) {
            sql = baseSql + "WHERE hs.numero_orden LIKE ? OR c.nombre LIKE ? OR hs.equipo_serie LIKE ? OR hs.equipo_marca LIKE ? OR hs.equipo_modelo LIKE ? " +
                          "ORDER BY hs.id DESC";
        } else {
            sql = baseSql + "ORDER BY hs.id DESC";
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

                String equipment = String.join(" ",
                        rs.getString("equipo_tipo") != null ? rs.getString("equipo_tipo") : "",
                        rs.getString("equipo_marca") != null ? rs.getString("equipo_marca") : "",
                        rs.getString("equipo_modelo") != null ? rs.getString("equipo_modelo") : ""
                ).trim().replaceAll(" +", " ");

                String serie = rs.getString("equipo_serie");
                if (serie != null && !serie.isEmpty()) {
                    equipment += " (Serie: " + serie + ")";
                }

                serviceSheets.add(new ServiceSheetSummary(orderNumber, date, clientName, equipment));
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
    protected void onPrintSelectedClicked() {
        ServiceSheetSummary selected = serviceSheetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Por favor, seleccione una hoja de servicio de la tabla para imprimir.");
            return;
        }

        fetchAndProcessServiceSheet(selected.getOrderNumber(), (data) -> {
            try {
                String pdfPath = new PdfGenerator().generatePdf(data);
                if (pdfPath != null) {
                    // Añadir una pequeña pausa para asegurar que el archivo se ha escrito completamente
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    File pdfFile = new File(pdfPath);
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfFile);
                    } else {
                        showAlert(Alert.AlertType.WARNING, "Función no Soportada", "La apertura automática de archivos no es soportada en este sistema.");
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar la ruta del PDF.");
                }
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error de PDF", "No se pudo generar el PDF. Error: " + e.getMessage());
                e.printStackTrace();
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

    @FXML
    protected void onSalirClicked() {
        Stage stage = (Stage) salirButton.getScene().getWindow();
        stage.close();
    }

    private void fetchAndProcessServiceSheet(String orderNumber, java.util.function.Consumer<HojaServicioData> dataConsumer) {
        String sql = "SELECT hs.*, c.nombre as cliente_nombre, c.direccion as cliente_direccion, c.telefono as cliente_telefono " +
                     "FROM x_hojas_servicio hs JOIN x_clientes c ON hs.cliente_id = c.id WHERE hs.numero_orden = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, orderNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                HojaServicioData data = createHojaServicioDataFromResultSet(rs, conn);
                dataConsumer.accept(data);
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "No se encontraron los detalles para la hoja de servicio seleccionada.");
            }

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "Ocurrió un error al consultar los datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private HojaServicioData createHojaServicioDataFromResultSet(ResultSet rs, Connection conn) throws SQLException {
        HojaServicioData data = new HojaServicioData();
        long hojaServicioId = rs.getLong("id");
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
        data.setEstadoFisico(rs.getString("estado_fisico"));
        data.setInformeCostos(rs.getString("informe_costos"));
        data.setTotalCostos(rs.getBigDecimal("total_costos"));
        data.setAnticipo(rs.getBigDecimal("anticipo"));
        Date fechaEntrega = rs.getDate("fecha_entrega");
        if (fechaEntrega != null) data.setFechaEntrega(fechaEntrega.toLocalDate());
        data.setFirmaAclaracion(rs.getString("firma_aclaracion"));
        data.setAclaraciones(rs.getString("aclaraciones"));

        // Cargar equipos asociados
        data.setEquipos(loadEquiposForHojaServicio(hojaServicioId, conn));

        return data;
    }

    private List<Equipo> loadEquiposForHojaServicio(long hojaServicioId, Connection conn) throws SQLException {
        List<Equipo> equipos = new ArrayList<>();
        String sql = "SELECT * FROM x_hojas_servicio_equipos WHERE hoja_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, hojaServicioId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String tipo = rs.getString("equipo_tipo");
                String marca = rs.getString("equipo_marca");
                String modelo = rs.getString("equipo_modelo");
                String serie = rs.getString("equipo_serie");
                String falla = rs.getString("falla_reportada");
                BigDecimal costo = rs.getBigDecimal("costo");
                String estadoFisico = rs.getString("estado_fisico");
                equipos.add(new Equipo(tipo, marca, serie, modelo, falla, costo, estadoFisico));
            }
        }
        return equipos;
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
        private final String orderNumber;
        private final LocalDate date;
        private final String clientName;
        private final String equipment;

        public ServiceSheetSummary(String orderNumber, LocalDate date, String clientName, String equipment) {
            this.orderNumber = orderNumber;
            this.date = date;
            this.clientName = clientName;
            this.equipment = equipment;
        }

        public String getOrderNumber() { return orderNumber; }
        public LocalDate getDate() { return date; }
        public String getClientName() { return clientName; }
        public String getEquipment() { return equipment; }
    }
}
