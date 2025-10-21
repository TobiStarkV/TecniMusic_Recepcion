package com.example.tecnimusic_recepcion;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class tecniMusic extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(tecniMusic.class.getResource("loading-view.fxml"));
        Parent root = fxmlLoader.load();

        // Crea una escena con el contenido del FXML y la establece con un fondo transparente.
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Añadir la hoja de estilos para que la pantalla de carga y sus diálogos usen la paleta global
        URL cssUrl = tecniMusic.class.getResource("/com/example/tecnimusic_recepcion/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Advertencia: No se encontró styles.css en recursos.");
        }

        // Configura el Stage (la ventana) para que no tenga decoración (bordes, botones de cerrar, etc.)
        // y le asigna la escena transparente.
        stage.initStyle(StageStyle.TRANSPARENT);
        InputStream iconStream = tecniMusic.class.getResourceAsStream("/logo.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        } else {
            System.err.println("Advertencia: No se encontró /logo.png en recursos.");
        }
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
