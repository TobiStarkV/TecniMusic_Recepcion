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

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.Optional;

// Importaciones para la impresión de PDF
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import java.awt.print.PrinterJob;
import java.awt.print.PrinterException;

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
                    File pdfFile = new File(pdfPath);

                    Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmationAlert.setTitle("Confirmar Impresión");
                    confirmationAlert.setHeaderText("¿Desea imprimir la hoja de servicio seleccionada?");
                    confirmationAlert.setContentText("Se enviará el documento a la impresora predeterminada.");

                    Optional<ButtonType> result = confirmationAlert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        try (PDDocument document = PDDocument.load(pdfFile)) {
                            PrinterJob job = PrinterJob.getPrinterJob();
                            job.setPageable(new PDFPageable(document));

                            if (job.printDialog()) {
                                job.print();
                                showAlert(Alert.AlertType.INFORMATION, "Impresión", "La hoja de servicio ha sido enviada a la impresora.");
                            } else {
                                showAlert(Alert.AlertType.INFORMATION, "Impresión Cancelada", "La impresión de la hoja de servicio fue cancelada por el usuario.");
                            }
                        } catch (PrinterException | IOException e) {
                            showAlert(Alert.AlertType.ERROR, "Error de Impresión", "No se pudo imprimir el documento: " + e.getMessage());
                            e.printStackTrace();
                        }
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
