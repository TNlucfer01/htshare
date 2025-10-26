package com.htshare.ui;

import com.htshare.server.FileServer;
import com.htshare.util.NetworkUtils;
import com.htshare.util.QRCodeGenerator;
import java.io.File;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {
  private static final Logger logger = LoggerFactory.getLogger(MainController.class);
  private static final int DEFAULT_PORT = 8080;

  @FXML private VBox rootContainer;
  @FXML private Label folderPathLabel;
  @FXML private Button selectFolderButton;
  @FXML private Button startServerButton;
  @FXML private Button stopServerButton;
  @FXML private ImageView qrCodeImageView;
  @FXML private Label serverUrlLabel;
  @FXML private Label statusLabel;
  @FXML private ToggleButton themeToggle;
  @FXML private ProgressIndicator serverProgress;

  private Stage stage;
  private File selectedFolder;
  private FileServer fileServer;
  private boolean isDarkTheme = false;

  @FXML
  public void initialize() {
    stopServerButton.setDisable(true);
    serverProgress.setVisible(false);
    qrCodeImageView.setVisible(false);
    serverUrlLabel.setVisible(false);

    updateStatus("Ready. Select a folder to share.", "info");
  }

  public void setStage(Stage stage) {
    this.stage = stage;
  }

  @FXML
  private void handleSelectFolder() {
    DirectoryChooser directoryChooser = new DirectoryChooser();
    directoryChooser.setTitle("Select Folder to Share");

    if (selectedFolder != null && selectedFolder.exists()) {
      directoryChooser.setInitialDirectory(selectedFolder.getParentFile());
    }

    File folder = directoryChooser.showDialog(stage);

    if (folder != null) {
      selectedFolder = folder;
      folderPathLabel.setText(folder.getAbsolutePath());
      startServerButton.setDisable(false);
      updateStatus("Folder selected: " + folder.getName(), "success");
      logger.info("Selected folder: {}", folder.getAbsolutePath());
    }
  }

  @FXML
  private void handleStartServer() {
    if (selectedFolder == null) {
      showError("Please select a folder first");
      return;
    }

    try {
      // Disable buttons during startup
      startServerButton.setDisable(true);
      selectFolderButton.setDisable(true);
      serverProgress.setVisible(true);

      // Start server in background thread
      new Thread(
              () -> {
                try {
                  fileServer = new FileServer(DEFAULT_PORT, selectedFolder);
                  fileServer.start();

                  String localIP = NetworkUtils.getLocalIPAddress();
                  String serverUrl = "http://" + localIP + ":" + DEFAULT_PORT;

                  Platform.runLater(
                      () -> {
                        try {
                          // Generate QR Code
                          Image qrImage = QRCodeGenerator.generateQRCode(serverUrl, 300, 300);
                          qrCodeImageView.setImage(qrImage);
                          qrCodeImageView.setVisible(true);

                          serverUrlLabel.setText(serverUrl);
                          serverUrlLabel.setVisible(true);

                          stopServerButton.setDisable(false);
                          serverProgress.setVisible(false);

                          updateStatus("Server running on " + serverUrl, "success");
                          logger.info("Server started successfully on {}", serverUrl);

                        } catch (Exception e) {
                          handleServerError(e);
                        }
                      });

                } catch (IOException e) {
                  Platform.runLater(() -> handleServerError(e));
                }
              })
          .start();

    } catch (Exception e) {
      handleServerError(e);
    }
  }

  @FXML
  private void handleStopServer() {
    if (fileServer != null) {
      fileServer.stop();
      fileServer = null;

      qrCodeImageView.setVisible(false);
      serverUrlLabel.setVisible(false);
      stopServerButton.setDisable(true);
      startServerButton.setDisable(false);
      selectFolderButton.setDisable(false);

      updateStatus("Server stopped", "info");
      logger.info("Server stopped");
    }
  }

  @FXML
  private void handleThemeToggle() {
    isDarkTheme = !isDarkTheme;
    switchTheme();
  }

  private void switchTheme() {
    Scene scene = rootContainer.getScene();
    scene.getStylesheets().clear();

    if (isDarkTheme) {
      scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
      themeToggle.setText("☀");
    } else {
      scene.getStylesheets().add(getClass().getResource("/css/light-theme.css").toExternalForm());
      themeToggle.setText("🌙");
    }

    logger.info("Switched to {} theme", isDarkTheme ? "dark" : "light");
  }

  private void handleServerError(Exception e) {
    logger.error("Server error", e);
    showError("Failed to start server: " + e.getMessage());

    startServerButton.setDisable(false);
    selectFolderButton.setDisable(false);
    stopServerButton.setDisable(true);
    serverProgress.setVisible(false);
  }

  private void updateStatus(String message, String type) {
    statusLabel.setText(message);
    statusLabel.getStyleClass().removeAll("status-info", "status-success", "status-error");
    statusLabel.getStyleClass().add("status-" + type);
  }

  private void showError(String message) {
    updateStatus(message, "error");

    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  public void cleanup() {
    if (fileServer != null) {
      fileServer.stop();
    }
  }
}




