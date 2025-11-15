package com.example.tecnimusic_recepcion;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.List;

public class AccesoriosDialogController {

    @FXML
    private TextField accesorioField;
    @FXML
    private ListView<String> accesoriosListView;

    private Stage dialogStage;
    private ObservableList<String> accesorios;
    private boolean aceptado = false;

    @FXML
    private void initialize() {
        accesorios = FXCollections.observableArrayList();
        accesoriosListView.setItems(accesorios);
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setAccesorios(List<String> accesorios) {
        this.accesorios.setAll(accesorios);
    }

    public List<String> getAccesorios() {
        return accesorios;
    }

    public boolean isAceptado() {
        return aceptado;
    }

    @FXML
    private void onAddAccesorio() {
        String accesorio = accesorioField.getText();
        if (accesorio != null && !accesorio.trim().isEmpty()) {
            accesorios.add(accesorio.trim());
            accesorioField.clear();
        }
    }

    @FXML
    private void onRemoveAccesorio() {
        String selectedItem = accesoriosListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            accesorios.remove(selectedItem);
        }
    }

    @FXML
    private void onAceptar() {
        aceptado = true;
        dialogStage.close();
    }

    @FXML
    private void onCancelar() {
        dialogStage.close();
    }
}
