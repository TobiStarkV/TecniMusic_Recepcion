package com.example.tecnimusic_recepcion;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class ManageClientsController {

    @FXML
    private TableView<Client> clientsTable;
    @FXML
    private TableColumn<Client, Integer> idColumn;
    @FXML
    private TableColumn<Client, String> nameColumn;
    @FXML
    private TableColumn<Client, String> phoneColumn;
    @FXML
    private TableColumn<Client, String> addressColumn;

    @FXML
    private TextField searchField;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button exitButton;

    @FXML
    private VBox formVBox;
    @FXML
    private TextField nameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private final ObservableList<Client> clientList = FXCollections.observableArrayList();
    private Client selectedClient = null;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));

        loadClientsFromDatabase();

        FilteredList<Client> filteredData = new FilteredList<>(clientList, b -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(client -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();

                if (client.getName() != null && client.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Filter matches name.
                } else if (client.getPhone() != null && client.getPhone().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Filter matches phone.
                } else if (client.getAddress() != null && client.getAddress().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Filter matches address.
                }
                return false; // Does not match.
            });
        });

        SortedList<Client> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(clientsTable.comparatorProperty());
        clientsTable.setItems(sortedData);

        formVBox.setVisible(false);
        formVBox.setManaged(false);
    }

    private void loadClientsFromDatabase() {
        clientList.clear();
        String sql = "SELECT id, nombre, telefono, direccion FROM x_clientes ORDER BY nombre";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                clientList.add(new Client(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getString("telefono"),
                        rs.getString("direccion")
                ));
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudieron cargar los clientes.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddButton() {
        selectedClient = null;
        clearForm();
        showForm();
    }

    @FXML
    private void handleEditButton() {
        selectedClient = clientsTable.getSelectionModel().getSelectedItem();
        if (selectedClient == null) {
            showAlert(Alert.AlertType.WARNING, "Ningún Cliente Seleccionado", "Por favor, seleccione un cliente de la tabla para editar.");
            return;
        }
        fillForm(selectedClient);
        showForm();
    }

    @FXML
    private void handleDeleteButton() {
        Client clientToDelete = clientsTable.getSelectionModel().getSelectedItem();
        if (clientToDelete == null) {
            showAlert(Alert.AlertType.WARNING, "Ningún Cliente Seleccionado", "Por favor, seleccione un cliente de la tabla para eliminar.");
            return;
        }

        Optional<ButtonType> result = showConfirmationDialog("Confirmar Eliminación", "Está a punto de eliminar al cliente: " + clientToDelete.getName() + ". ¿Está seguro?");
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteClientFromDatabase(clientToDelete);
        }
    }

    @FXML
    private void handleSaveButton() {
        String name = nameField.getText();
        String phone = phoneField.getText();
        String address = addressField.getText();

        if (name == null || name.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Campo Requerido", "El nombre del cliente no puede estar vacío.");
            return;
        }

        if (selectedClient == null) { // Adding new client
            String sql = "INSERT INTO x_clientes (nombre, telefono, direccion) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, phone);
                pstmt.setString(3, address);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo guardar el nuevo cliente.");
                e.printStackTrace();
            }
        } else { // Editing existing client
            String sql = "UPDATE x_clientes SET nombre = ?, telefono = ?, direccion = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, phone);
                pstmt.setString(3, address);
                pstmt.setInt(4, selectedClient.getId());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo actualizar el cliente.");
                e.printStackTrace();
            }
        }

        loadClientsFromDatabase();
        hideForm();
        clearForm();
    }

    @FXML
    private void handleCancelButton() {
        hideForm();
        clearForm();
    }

    @FXML
    private void handleExitButton() {
        Stage stage = (Stage) exitButton.getScene().getWindow();
        stage.close();
    }

    private void deleteClientFromDatabase(Client client) {
        String sql = "DELETE FROM x_clientes WHERE id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, client.getId());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                loadClientsFromDatabase(); // Refresh table
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Base de Datos", "No se pudo eliminar el cliente.");
            e.printStackTrace();
        }
    }

    private void showForm() {
        formVBox.setVisible(true);
        formVBox.setManaged(true);
    }

    private void hideForm() {
        formVBox.setVisible(false);
        formVBox.setManaged(false);
    }

    private void clearForm() {
        nameField.clear();
        phoneField.clear();
        addressField.clear();
    }

    private void fillForm(Client client) {
        nameField.setText(client.getName());
        phoneField.setText(client.getPhone());
        addressField.setText(client.getAddress());
    }

    private void showAlert(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }

    private Optional<ButtonType> showConfirmationDialog(String title, String header) {
        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle(title);
        confirmationAlert.setHeaderText(header);
        confirmationAlert.setContentText("Esta acción no se puede deshacer.");
        return confirmationAlert.showAndWait();
    }

    // Inner class for the client data model
    public static class Client {
        private final SimpleIntegerProperty id;
        private final SimpleStringProperty name;
        private final SimpleStringProperty phone;
        private final SimpleStringProperty address;

        public Client(int id, String name, String phone, String address) {
            this.id = new SimpleIntegerProperty(id);
            this.name = new SimpleStringProperty(name);
            this.phone = new SimpleStringProperty(phone);
            this.address = new SimpleStringProperty(address);
        }

        public int getId() {
            return id.get();
        }

        public SimpleIntegerProperty idProperty() {
            return id;
        }

        public String getName() {
            return name.get();
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getPhone() {
            return phone.get();
        }

        public SimpleStringProperty phoneProperty() {
            return phone;
        }

        public String getAddress() {
            return address.get();
        }

        public SimpleStringProperty addressProperty() {
            return address;
        }
    }
}
