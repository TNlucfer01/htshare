package com.htshare;

import com.htshare.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {
  private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
  private static final int WINDOW_WIDTH = 600;
  private static final int WINDOW_HEIGHT = 700;

  @Override
  public void start(Stage primaryStage) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
      Parent root = loader.load();

      MainController controller = loader.getController();
      controller.setStage(primaryStage);

      Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

      // Load default theme (light)
      scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());

      primaryStage.setTitle("File Share - Desktop");
      primaryStage.setScene(scene);
      primaryStage.setMinWidth(500);
      primaryStage.setMinHeight(600);
      primaryStage.show();

      // Cleanup on close
      primaryStage.setOnCloseRequest(
          event -> {
            controller.cleanup();
            logger.info("Application closed");
          });

    } catch (Exception e) {
      logger.error("Failed to start application", e);
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
