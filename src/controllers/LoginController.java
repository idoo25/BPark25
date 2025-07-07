package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.net.URL;
import java.util.ResourceBundle;

import client.BParkClientApp;
import entities.Message;
import entities.Message.MessageType;

public class LoginController implements Initializable {
    

	/** Text field for entering the subscriber's user name. */

    @FXML private TextField txtUsername;
    @FXML private TextField txtUsercode;

    
    /** Text field for entering the server's IP address. */
    @FXML private TextField txtServerIP;
    
    /** Button that triggers the login process. */
    @FXML private Button btnLogin;
    
    /** Label used to display status message. */

    @FXML private Label lblStatus;
    
    private boolean isConnecting = false;
    private static LoginController instance;
    
    public LoginController() {
        instance = this;
    }
    
	public static LoginController getInstance() {
		return instance;
	}
	
	/**
	 * Initializes the login screen: connects to the server, sets default values, and configures user input behavior.
	 */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
    	 // If the client is a user - connect to the server
	   	 if (BParkClientApp.getClient() == null || !BParkClientApp.isConnected()) {
		        System.out.println("Connecting to server...");
		        BParkClientApp.connectToServer();
		 }
	   	 // Set default server IP
	   	 txtServerIP.setText("localhost");
	 
	   	 // Enable Enter key to login
	   	 txtUsername.setOnKeyPressed(this::handleEnterKey);
	   	 txtUsercode.setOnKeyPressed(this::handleEnterKey);
	 
	   	 // Focus on username field
	   	 Platform.runLater(() -> txtUsername.requestFocus());
    }
    

    
    /**
     * Sends a request to the server to retrieve current parking availability.
     * <p>
     * This method is typically triggered from the login screen to provide users who are not yet register
     * about available parking spots before logging in.
     */

    @FXML
    private void handleCheckAvailability() {
        // Send a request to the server 
        Message checkMsg = new Message(Message.MessageType.CHECK_PARKING_AVAILABILITY, null);
        BParkClientApp.sendMessage(checkMsg);
        
    }
    
    @FXML
    private void handleLogin() {
        if (isConnecting) {
            return; // Prevent multiple connection attempts
        }
        
        String username = txtUsername.getText().trim();
        String usercode = txtUsercode.getText().trim();
        String serverIP = txtServerIP.getText().trim();
        
        // Validate input
        if (username.isEmpty()) {
            showError("Please enter your username");
            txtUsername.requestFocus();
            return;
        }
        
        if (usercode.isEmpty()) {
            showError("Please enter your userCode");
            txtUsercode.requestFocus();
            return;
        }
        
        if (serverIP.isEmpty()) {
            showError("Please enter server IP address");
            txtServerIP.requestFocus();
            return;
        }
        
        // Update UI for connection attempt
        isConnecting = true;
        btnLogin.setDisable(true);
        btnLogin.setText("Connecting...");
        lblStatus.setText("Connecting to server...");
        lblStatus.setStyle("-fx-text-fill: #3498DB;");
        
        // Store server IP and connect
        BParkClientApp.setServerIP(serverIP);
        
        // Connect to server in background thread
        new Thread(() -> {
            try {
                BParkClientApp.connectToServer();
                
                // Wait a bit for connection to establish
                Thread.sleep(500);
                
                // Send login request
                Platform.runLater(() -> {
                    BParkClientApp.setCurrentUser(username);
                    
                    // Send login message
                    Message loginMsg = new Message(MessageType.SUBSCRIBER_LOGIN, username + "," + usercode);
                    BParkClientApp.sendMessage(loginMsg);
                    
                    lblStatus.setText("Authenticating...");
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Failed to connect to server: " + e.getMessage());
                    resetLoginButton();
                });
            }
        }).start();
    }
    
    /**

     * Triggered when the Enter key is pressed in the user name or user code fields.
     * <p>
     * Allows quick login by pressing Enter instead of clicking the login button.
     *
     * @param event The key event triggered by the user.

     */
    private void handleEnterKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleLogin();
        }
    }
    
    /**
     * Called when login fails
     */
    public void handleLoginFailed(String reason) {
        Platform.runLater(() -> {
            showError(reason != null ? reason : "Login failed. Please check your credentials.");
            resetLoginButton();
            txtUsername.requestFocus();
            txtUsername.selectAll();
        });
    }
    
    /**

     * Handles server response when login is successful.
     * <p>
     * Displays a success message and closes the login window.
     *
     * @param userType The type of user logged in (subscriber, attendant , manager).

     */
    public void handleLoginSuccess(String userType) {
        Platform.runLater(() -> {
            lblStatus.setText("Login successful! Loading interface...");
            lblStatus.setStyle("-fx-text-fill: #27AE60;");
            
            // Close login window
            btnLogin.getScene().getWindow().hide();
        });
    }
    
    /**
     * Reset login button state
     */
    private void resetLoginButton() {
        isConnecting = false;
        btnLogin.setDisable(false);
        btnLogin.setText("Login");
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        lblStatus.setText(message);
        lblStatus.setStyle("-fx-text-fill: #E74C3C;");
    }

   
}