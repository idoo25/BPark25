package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import client.BParkClientApp;
import entities.Message;
import entities.Message.MessageType;

public class ExtendParkingController {
	
	
	 public static ExtendParkingController instance;

	

    @FXML private TextField codeField;

    
    /**
     * ComboBox for selecting the number of hours to extend the parking.
     */

    @FXML private ComboBox<String> hoursCombo;
    @FXML private Label statusLabel;
   

    @FXML
    public void initialize() {
    	instance = this;
        hoursCombo.getItems().addAll("1", "2", "3", "4");
        hoursCombo.setValue("1");
    }

    @FXML
    private void handleSubmit() {
        String code = codeField.getText();
        String hours = hoursCombo.getValue();

        if (code == null || code.trim().isEmpty()) {
            statusLabel.setText("Please enter a valid code.");
            return;
        }

        String extensionData = code + "," + hours;
        Message msg = new Message(MessageType.REQUEST_EXTENSION, extensionData);
        BParkClientApp.sendMessage(msg);
        
      
        
    }
    

    
    /**
     * Updates the status label with a custom message and text color.
     * <p>
     * This method is typically used to inform the user of the result of an action, 
     * such as a successful extension or an input error.
     * It can be called from other parts of the application.
     *
     * @param msg   The text message to be displayed in the status label.
     * @param color The color of the text, defined using a CSS color name  
     *              or a hexadecimal color code.
     */

    public void setStatusMessage(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }
  
}