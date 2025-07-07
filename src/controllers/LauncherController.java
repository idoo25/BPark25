package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;
import java.util.ResourceBundle;

import client.BParkLauncherApp;

/**
 * Controller for the main launcher interface that allows users to configure
 * server connection settings and choose between client and kiosk applications.
 */
public class LauncherController implements Initializable {

    @FXML private TextField txtServerIP;
    @FXML private TextField txtServerPort;
    @FXML private Button btnTestConnection;
    @FXML private Label lblStatus;
    @FXML private VBox vboxAppSelection;
    @FXML private Button btnClientApp;
    @FXML private Button btnKioskApp;

    private Stage mainStage;
    private boolean isConnecting = false;

    /**
     * Initializes the controller by setting default values and initial UI state.
     * 
     * @param location the location used to resolve relative paths for the root object
     * @param resources the resources used to localize the root object
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        txtServerIP.setText("localhost");
        txtServerPort.setText("5555");
        
        vboxAppSelection.setVisible(false);
        vboxAppSelection.setManaged(false);
    }

    /**
     * Sets the main stage reference for this controller.
     * 
     * @param stage the main application stage
     */
    public void setMainStage(Stage stage) {
        this.mainStage = stage;
    }

    @FXML
    private void handleTestConnection() {
        if (isConnecting) {
            return; // Prevent multiple connection attempts
        }

        String serverIP = txtServerIP.getText().trim();
        String serverPortText = txtServerPort.getText().trim();

        // Validate input
        if (serverIP.isEmpty()) {
            showError("Please enter server IP address");
            txtServerIP.requestFocus();
            return;
        }

        if (serverPortText.isEmpty()) {
            showError("Please enter server port");
            txtServerPort.requestFocus();
            return;
        }

        int serverPort;
        try {
            serverPort = Integer.parseInt(serverPortText);
            if (serverPort < 1 || serverPort > 65535) {
                throw new NumberFormatException("Port out of range");
            }
        } catch (NumberFormatException e) {
            showError("Please enter a valid port number (1-65535)");
            txtServerPort.requestFocus();
            return;
        }

        // Update UI for connection attempt
        isConnecting = true;
        btnTestConnection.setDisable(true);
        btnTestConnection.setText("Testing...");
        lblStatus.setText("Testing connection...");
        lblStatus.getStyleClass().removeAll("status-success", "status-error");
        lblStatus.getStyleClass().add("status-info");

        // Test connection in background thread
        Platform.runLater(() -> {
            new Thread(() -> {
                boolean connected = BParkLauncherApp.testConnection(serverIP, serverPort);
                
                Platform.runLater(() -> {
                    isConnecting = false;
                    btnTestConnection.setDisable(false);
                    btnTestConnection.setText("Test Connection");
                    
                    if (connected) {
                        showSuccess("Connection successful! Select application mode.");
                        showAppSelection();
                    } else {
                        showError("Connection failed. Please check server address and port.");
                        hideAppSelection();
                    }
                });
            }).start();
        });
    }

    @FXML
    private void handleClientApp() {
        try {
            BParkLauncherApp.showClientScene();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to launch client application: " + e.getMessage());
        }
    }

    @FXML
    private void handleKioskApp() {
        try {
            BParkLauncherApp.showKioskScene();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to launch kiosk application: " + e.getMessage());
        }
    }

    private void showSuccess(String message) {
        lblStatus.setText("✓ " + message);
        lblStatus.getStyleClass().removeAll("status-error", "status-info");
        lblStatus.getStyleClass().add("status-success");
    }

    private void showError(String message) {
        lblStatus.setText("✗ " + message);
        lblStatus.getStyleClass().removeAll("status-success", "status-info");
        lblStatus.getStyleClass().add("status-error");
    }

    private void showAppSelection() {
        vboxAppSelection.setVisible(true);
        vboxAppSelection.setManaged(true);
    }

    private void hideAppSelection() {
        vboxAppSelection.setVisible(false);
        vboxAppSelection.setManaged(false);
    }
}