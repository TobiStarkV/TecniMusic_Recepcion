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
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoadingController {

    @FXML
    private ImageView loadingImageView;

    // Constantes para la animación
    private static final int TOTAL_FRAMES = 271;
    private static final String IMAGE_PREFIX = "/com/example/tecnimusic_recepcion/images/TecniMusic Intro_";
    private static final String IMAGE_SUFFIX = ".png";

    private final List<Image> animationFrames = new ArrayList<>(TOTAL_FRAMES);
    private int currentFrameIndex = 0;
    private Timeline timeline;

    private volatile boolean isDatabaseReady = false;
    private volatile boolean isSpellCheckerReady = false; // Nuevo estado
    private volatile boolean isAnimationCycleComplete = false;
    private volatile boolean isTransitioning = false;

    public void initialize() {
        Platform.runLater(() -> {
            if (loadingImageView.getScene() != null && loadingImageView.getScene().getWindow() != null) {
                loadingImageView.getScene().getWindow().centerOnScreen();
            }
        });

        preloadAnimationAndStart();
        startDatabaseLoadTask();
        startSpellCheckerLoadTask(); // Iniciar la carga del corrector
    }

    /**
     * Carga todas las imágenes de la animación en segundo plano.
     * Una vez cargadas, inicia el Timeline de JavaFX para reproducirlas.
     */
    private void preloadAnimationAndStart() {
        Task<Void> imageLoaderTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i < TOTAL_FRAMES; i++) {
                    String frameNumber = String.format("%05d", i);
                    String imagePath = IMAGE_PREFIX + frameNumber + IMAGE_SUFFIX;
                    // Cargar la imagen con un tamaño reducido para ahorrar memoria y mejorar rendimiento
                    Image frame = new Image(getClass().getResourceAsStream(imagePath), 500, 500, true, true);
                    animationFrames.add(frame);
                }
                return null;
            }
        };

        imageLoaderTask.setOnSucceeded(event -> {
            // Una vez que todas las imágenes están en memoria, iniciamos la animación.
            timeline = new Timeline();
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.millis(33), e -> {
                        if (animationFrames.isEmpty()) return;
                        loadingImageView.setImage(animationFrames.get(currentFrameIndex));
                        currentFrameIndex++;
                        if (currentFrameIndex >= TOTAL_FRAMES) {
                            currentFrameIndex = 0; // Reiniciar para el bucle
                            if (!isAnimationCycleComplete) {
                                isAnimationCycleComplete = true;
                                trySwitchToMainView();
                            }
                        }
                    })
            );
            timeline.play();
        });

        imageLoaderTask.setOnFailed(event -> {
            // Manejar el error si las imágenes no se pueden cargar
            System.err.println("Error crítico: No se pudieron cargar los frames de la animación.");
            imageLoaderTask.getException().printStackTrace();
            // Podrías mostrar una alerta y cerrar la app aquí.
        });

        new Thread(imageLoaderTask).start();
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
                    if (timeline != null) timeline.stop();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error de Base de Datos");
                    alert.setHeaderText("No se pudo conectar a la base de datos de Snipe-IT.");
                    alert.setContentText("Por favor, verifique la configuración de la conexión.\nError: " + databaseTask.getMessage());

                    ButtonType retryButton = new ButtonType("Reintentar");
                    ButtonType exitButton = new ButtonType("Salir");

                    alert.getButtonTypes().setAll(retryButton, exitButton);

                    alert.getDialogPane().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
                    ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));

                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == retryButton) {
                        showSettingsAndRetry();
                    } else {
                        Platform.exit();
                    }
                });
            }
        });

        new Thread(databaseTask).start();
    }

    private void startSpellCheckerLoadTask() {
        Task<Void> spellCheckerTask = new Task<>() {
            @Override
            protected Void call() {
                System.out.println("Iniciando corrector ortográfico...");
                CorrectorOrtografico.inicializar();
                System.out.println("Corrector ortográfico: Listo");
                return null;
            }
        };

        spellCheckerTask.setOnSucceeded(event -> {
            isSpellCheckerReady = true;
            trySwitchToMainView();
        });

        spellCheckerTask.setOnFailed(event -> {
            System.err.println("Error crítico: No se pudo inicializar el corrector ortográfico.");
            spellCheckerTask.getException().printStackTrace();
            // Opcional: Mostrar una alerta, aunque la app podría seguir funcionando sin el corrector.
        });

        new Thread(spellCheckerTask).start();
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

            DatabaseManager.resetInstance();
            
            // Reiniciar el estado para una nueva verificación
            isDatabaseReady = false;
            isAnimationCycleComplete = false;
            // La animación ya está cargada, solo necesitamos reiniciar el timeline si se detuvo.
            if (timeline != null) timeline.play();
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

    private synchronized void trySwitchToMainView() {
        if (isDatabaseReady && isSpellCheckerReady && isAnimationCycleComplete && !isTransitioning) {
            isTransitioning = true;
            Platform.runLater(this::switchToMainView);
        }
    }

    private void switchToMainView() {
        if (timeline != null) timeline.stop();

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
