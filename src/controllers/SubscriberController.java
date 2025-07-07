package controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

import client.BParkClientApp;
import entities.Message;
import entities.Message.MessageType;
import entities.ParkingOrder;

public class SubscriberController implements Initializable {
    
    // Main menu buttons
    @FXML private Button btnCheckAvailability;
    @FXML private Button btnEnterParking;
    @FXML private Button btnExitParking;
    @FXML private Button btnMakeReservation;
    @FXML private Button btnViewHistory;
    @FXML private Button btnLostCode;
    @FXML private Button btnUpdateProfile;
    @FXML private Button btnLogout;
    
    
    // Parking entry/exit controls
    @FXML private TextField txtParkingCode;
    @FXML private TextField txtSubscriberCode;
    @FXML private TextField txtCancelCode;
    @FXML private Label lblAvailableSpots;
    @FXML private Label lblUserInfo;
    
    // Reservation controls
    @FXML private DatePicker datePickerReservation;
    @FXML private ComboBox<String> comboTimeSlot;
    @FXML private Label lblReservationStatus;
    
    // Parking history table
    @FXML private TableView<ParkingOrder> tableParkingHistory;
    @FXML private TableColumn<ParkingOrder, String> colDate;
    @FXML private TableColumn<ParkingOrder, String> colEntry;
    @FXML private TableColumn<ParkingOrder, String> colExit;
    @FXML private TableColumn<ParkingOrder, String> colSpot;
    @FXML private TableColumn<ParkingOrder, String> colStatus;
    
    // Profile update
    @FXML private TextField txtPhone;
    @FXML private TextField txtEmail;
    
    // Current view container
    @FXML private VBox mainContent;
    
    
    
    @FXML private Button btnExit;
    
    private static boolean manualCheckRequested = false;
    
    
    
    public static void setManualCheckRequested(boolean value) {
        manualCheckRequested = value;
    }
    
    public void setUserName(String userName) {
        lblUserInfo.setText("User: " + userName);
    }
    
    private ObservableList<ParkingOrder> parkingHistory = FXCollections.observableArrayList();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
    	 setupUI();
    	
       
        
       
    }
    
    private void setupUI() {
        // Initialize time slots for reservation (15-minute intervals)
        ObservableList<String> timeSlots = FXCollections.observableArrayList();
        for (int hour = 6; hour <= 22; hour++) {
            for (int minute = 0; minute < 60; minute += 15) {
                timeSlots.add(String.format("%02d:%02d", hour, minute));
            }
        }
        if (comboTimeSlot != null) {
            comboTimeSlot.setItems(timeSlots);
        }
        
        // Set date picker constraints (1-7 days from today)
        if (datePickerReservation != null) {
            datePickerReservation.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    LocalDate today = LocalDate.now();
                    setDisable(empty || date.isBefore(today.plusDays(1)) || date.isAfter(today.plusDays(7)));
                }
            });
        }
        
        // Setup parking history table
        if (tableParkingHistory != null) {
            tableParkingHistory.setItems(parkingHistory);
            // Setup column cell value factories here
        }
    }
    
    public void loadHomeView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/HomeView.fxml"));
            Parent homeView = loader.load();
            mainContent.getChildren().clear();
            mainContent.getChildren().add(homeView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    

    
    // ===== Action Handlers =====
    
    @FXML
    private void handleGoHome() {
        loadHomeView();
    }
    
    @FXML
    private void handleShowAvailableSpots() {
    	setManualCheckRequested(true);
        Message checkMsg = new Message(Message.MessageType.CHECK_PARKING_AVAILABILITY, null);
        BParkClientApp.sendMessage(checkMsg);
    }
    
    @FXML
    private void checkParkingAvailability() {
        Message msg = new Message(MessageType.CHECK_PARKING_AVAILABILITY, null);
        BParkClientApp.sendMessage(msg);
    }
    
    @FXML
    private void handleImmediateParking() {
        String subscriberCode = BParkClientApp.getCurrentUser();
        if (subscriberCode != null && !subscriberCode.isEmpty()) {
            Message msg = new Message(MessageType.ENTER_PARKING, subscriberCode);
            BParkClientApp.sendMessage(msg);
        } else {
            showAlert("Error", "Subscriber code not found");
        }
    }
    
    @FXML
    private void handleExitParking() {
        String parkingCode = txtParkingCode.getText().trim();
        if (parkingCode.isEmpty()) {
            showAlert("Error", "Please enter your parking code");
            return;
        }
        
        Message msg = new Message(MessageType.EXIT_PARKING, parkingCode);
        BParkClientApp.sendMessage(msg);
    }
    
    @FXML
    private void handleMakeReservation() {
        LocalDate selectedDate = datePickerReservation.getValue();
        String selectedTime = comboTimeSlot.getValue();
        
        if (selectedDate == null || selectedTime == null) {
            showAlert("Error", "Please select both date and time");
            return;
        }
        
        // Format: "YYYY-MM-DD HH:MM"
        String dateTimeStr = selectedDate.toString() + " " + selectedTime;
        String reservationData = BParkClientApp.getCurrentUser() + "," + dateTimeStr;
        
        Message msg = new Message(MessageType.RESERVE_PARKING, reservationData);
        BParkClientApp.sendMessage(msg);
    }
    
    @FXML
    private void handleActivateReservation() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Activate Reservation");
        dialog.setHeaderText("Enter your reservation code:");
        dialog.setContentText("Code:");
        
        dialog.showAndWait().ifPresent(code -> {
            if (!code.trim().isEmpty()) {
                String activationData = BParkClientApp.getCurrentUser() + "," + code;
                Message msg = new Message(MessageType.ACTIVATE_RESERVATION, activationData);
                BParkClientApp.sendMessage(msg);
            }
        });
    }
    
//    @FXML
//    private void handleCancelReservation() {
//        TextInputDialog dialog = new TextInputDialog();
//        dialog.setTitle("Cancel Reservation");
//        dialog.setHeaderText("Enter reservation code to cancel:");
//        dialog.setContentText("Code:");
//        
//        dialog.showAndWait().ifPresent(code -> {
//            if (!code.trim().isEmpty()) {
//                String cancellationData = BParkClientApp.getCurrentUser() + "," + code;
//                Message msg = new Message(MessageType.CANCEL_RESERVATION, cancellationData);
//                BParkClientApp.sendMessage(msg);
//            }
//        });
//    }
    
    @FXML
    private void handleCancelReservationFromPage() {
        String code = txtCancelCode.getText();

        if (code != null && !code.trim().isEmpty()) {
            // Step 1: Show confirmation alert
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Cancellation");
            confirmAlert.setHeaderText("Are you sure you want to cancel your reservation?");
            confirmAlert.setContentText("Reservation code: " + code);

            // Step 2: Wait for user response
            Optional<ButtonType> result = confirmAlert.showAndWait();

            // Step 3: If user confirms, proceed with cancellation
            if (result.isPresent() && result.get() == ButtonType.OK) {
                String cancellationData = BParkClientApp.getCurrentUser() + "," + code;
                Message msg = new Message(MessageType.CANCEL_RESERVATION, cancellationData);
                BParkClientApp.sendMessage(msg);
                txtCancelCode.clear();
            }
        }
    }
    
    @FXML
    private void handleViewHistory() {
        Message msg = new Message(MessageType.GET_PARKING_HISTORY, BParkClientApp.getCurrentUser());
        BParkClientApp.sendMessage(msg);
    }
    
    @FXML
    private void handleLostCode() {
        Message msg = new Message(MessageType.REQUEST_LOST_CODE, BParkClientApp.getCurrentUser());
        BParkClientApp.sendMessage(msg);
    }
    
    @FXML
    private void handleExtendParking() { //***
    	   try {
    	        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ExtendParkingView.fxml"));
    	        Node extendParkingView = loader.load();
    	        mainContent.getChildren().setAll(extendParkingView); 
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    }
    }
   


    
    @FXML
    private void handleLogout() {
        // Return to login screen instead of closing
        BParkClientApp.returnToLogin();
    }
    
    @FXML
    private void handleExit() {
        // This maintains the old logout behavior (exit application)
        BParkClientApp.exitApplication();
    }
    
    @FXML
    private void showExitParkingView() {
        // Show exit parking section
        showAlert("Exit Parking", "Enter your parking code in the field below and click Exit");
    }
    
    @FXML
    private void showReservationView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ReservationView.fxml"));
            Parent reservationView = loader.load();

            mainContent.getChildren().clear();
            mainContent.getChildren().add(reservationView);

            setupUI();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void setupReservationForm() {
        datePickerReservation.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate today = LocalDate.now();
                setDisable(empty || date.isBefore(today.plusDays(1)) || date.isAfter(today.plusDays(7)));
            }
        });

        comboTimeSlot.getItems().clear();
        LocalTime time = LocalTime.of(6, 0); // from 06:00
        while (!time.isAfter(LocalTime.of(22, 45))) {
            comboTimeSlot.getItems().add(time.toString());
            time = time.plusMinutes(15);
        }
    }
    
    @FXML
    private void showProfileView() {
    	  try {
    		// Load the FXML file for the profile update screen
              FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/UpdateProfileView.fxml"));
           // Load the actual UI components from the FXML file into a Node object
              Node profileView = loader.load();
              mainContent.getChildren().setAll(profileView);  // This replaces the center of the UI
          } catch (IOException e) {
              e.printStackTrace();
          }
    }
    
   
    // ===== UI Update Methods =====
    
    public void updateAvailableSpots(int spots) {
        if (lblAvailableSpots != null) {
            lblAvailableSpots.setText("Available Spots: " + spots);
            
            // Update UI based on availability
            boolean canReserve = spots >= (10 * 0.4); // 40% rule
            if (btnMakeReservation != null) {
                btnMakeReservation.setDisable(!canReserve);
            }
            if (lblReservationStatus != null && !canReserve) {
                lblReservationStatus.setText("Reservations unavailable (less than 40% spots free)");
            }
        }
    }
    
    public void updateParkingHistory(ObservableList<ParkingOrder> history) {
        this.parkingHistory.clear();
        this.parkingHistory.addAll(history);
    }
    
    // ===== Utility Methods =====
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}