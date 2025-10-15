package com.example.tecnimusic_recepcion;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class tecniMusic extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(tecniMusic.class.getResource("loading-view.fxml"));
        Parent root = fxmlLoader.load();

        // Crea una escena con el contenido del FXML y la establece con un fondo transparente.
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Configura el Stage (la ventana) para que no tenga decoración (bordes, botones de cerrar, etc.)
        // y le asigna la escena transparente.
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
