package com.example.tecnimusic_recepcion;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LoadingController {

    @FXML
    private ImageView loadingImageView;

    private static final int TOTAL_FRAMES = 271;
    private static final String IMAGE_PREFIX = "/com/example/tecnimusic_recepcion/images/TecniMusic Intro_";
    private static final String IMAGE_SUFFIX = ".png";

    private List<Image> animationFrames;
    private int currentFrame = 0;
    private Timeline timeline;

    private volatile boolean isDatabaseReady = false;
    private volatile boolean isAnimationCycleComplete = false;
    private volatile boolean isTransitioning = false;

    public void initialize() {
        Platform.runLater(() -> {
            if (loadingImageView.getScene() != null && loadingImageView.getScene().getWindow() != null) {
                loadingImageView.getScene().getWindow().centerOnScreen();
            }
        });

        loadAnimationFrames();

        timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(33), event -> {
                    if (!animationFrames.isEmpty()) {
                        loadingImageView.setImage(animationFrames.get(currentFrame));
                        currentFrame++;
                        if (currentFrame >= animationFrames.size()) {
                            currentFrame = 0;
                            if (!isAnimationCycleComplete) {
                                isAnimationCycleComplete = true;
                                trySwitchToMainView();
                            }
                        }
                    }
                })
        );
        timeline.play();

        startDatabaseLoadTask();
    }

    private void startDatabaseLoadTask() {
        Task<Boolean> databaseTask = new Task<>() {
            @Override
            protected Boolean call() {
                System.out.println("Iniciando conexión a la base de datos...");
                try (var ignored = DatabaseManager.getInstance().getConnection()) {
                    System.out.println("Conexión a la base de datos: Exitosa");
                    return true;
                } catch (SQLException e) {
                    System.err.println("Conexión a la base de datos: Fallida");
                    e.printStackTrace();
                    updateMessage(e.getMessage());
                    return false;
                }
            }
        };

        databaseTask.setOnSucceeded(event -> {
            boolean success = databaseTask.getValue();
            if (success) {
                isDatabaseReady = true;
                trySwitchToMainView();
            } else {
                Platform.runLater(() -> {
                    timeline.stop();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error de Base de Datos");
                    alert.setHeaderText("No se pudo conectar a la base de datos de Snipe-IT.");
                    alert.setContentText("Por favor, verifique la configuración de la conexión.\nError: " + databaseTask.getMessage());
                    alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
                    ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
                    alert.showAndWait();
                    showSettingsAndRetry();
                });
            }
        });

        new Thread(databaseTask).start();
    }

    private void showSettingsAndRetry() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/tecnimusic_recepcion/settings-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("TecniMusic - Configuración");
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            timeline.play();
            startDatabaseLoadTask();

        } catch (IOException e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error Crítico");
                alert.setHeaderText("No se pudo abrir la ventana de configuración.");
                alert.setContentText("La aplicación no puede continuar y se cerrará.");
                alert.showAndWait();
                Platform.exit();
            });
        }
    }

    private void loadAnimationFrames() {
        animationFrames = new ArrayList<>();
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            String frameNumber = String.format("%05d", i);
            String imagePath = IMAGE_PREFIX + frameNumber + IMAGE_SUFFIX;
            try {
                Image frame = new Image(getClass().getResourceAsStream(imagePath));
                animationFrames.add(frame);
            } catch (Exception e) {
                System.err.println("No se pudo cargar el frame de animación: " + imagePath);
            }
        }
    }

    private synchronized void trySwitchToMainView() {
        if (isDatabaseReady && isAnimationCycleComplete && !isTransitioning) {
            isTransitioning = true;
            Platform.runLater(this::switchToMainView);
        }
    }

    private void switchToMainView() {
        try {
            Parent mainView = FXMLLoader.load(getClass().getResource("main-menu-view.fxml"));
            Scene mainScene = new Scene(mainView);
            mainScene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

            Stage currentStage = (Stage) loadingImageView.getScene().getWindow();

            Stage mainStage = new Stage();
            mainStage.setTitle("TecniMusic - Menú Principal");
            mainStage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
            mainStage.setScene(mainScene);
            mainStage.centerOnScreen();
            mainStage.show();

            currentStage.close();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error Crítico");
            alert.setHeaderText("No se pudo cargar la interfaz principal de la aplicación.");
            alert.setContentText("Error: " + e.getMessage());
            alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
            alert.showAndWait();
            Platform.exit();
        }
    }
}
