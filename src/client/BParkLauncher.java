package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Main launcher application for BPark system.
 * This replaces BParkClientApp and BParkKioskApp as the single entry point.
 */
public class BParkLauncher extends Application {
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            // Load the connection setup FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ConnectionSetup.fxml"));
            Parent root = loader.load();
            
            // Create scene
            Scene scene = new Scene(root);
            
            // Add stylesheets
            String css = getClass().getResource("/css/BParkModernStyle.css").toExternalForm();
            scene.getStylesheets().add(css);
            
            // Configure stage
            primaryStage.setTitle("BPark Launcher - Connection Setup");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.centerOnScreen();
            
            // Set application icon
            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/BParkImage.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("Could not load application icon");
            }
            
            // Show stage
            primaryStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start BPark Launcher: " + e.getMessage());
        }
    }
    
    @Override
    public void stop() throws Exception {
        // Cleanup any open connections
        System.out.println("BPark Launcher closing...");
        System.exit(0);
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}