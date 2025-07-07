//package client;
//
//import controllers.AttendantController;
//import controllers.ExtendParkingController;
//import controllers.ManagerController;
//import controllers.SubscriberController;
//import controllers.UpdateProfileController;
//import entities.Message;
//import javafx.application.Application;
//import javafx.application.Platform;
//import javafx.fxml.FXMLLoader;
//import javafx.scene.Parent;
//import javafx.scene.Scene;
//import javafx.stage.Stage;
//import ocsf.client.ObservableClient;
//
//public class BParkClientApp extends Application {
//
//    private static BParkClient client;
//    private static String serverIP = "localhost";
//    private static int serverPort = 5555;
//    
//    private static AttendantController attendantController;
//    private static ManagerController managerController;
//
//    // Current user info
//    private static String currentUser;
//    private static String userType; // "sub", "emp", "mng"
//    
//    @Override
//    public void start(Stage primaryStage) throws Exception {
//        // Connect to server first
//        connectToServer();
//        
//        // Start with login screen
//        showLoginScreen(primaryStage);
//    }
//    
//    public static UpdateProfileController getUpdateProfileController() {
//        return UpdateProfileController.instance;
//    }
//    
//    public static ExtendParkingController getExtendParkingController() {
//        return ExtendParkingController.instance;
//    }
//    
//    private void showLoginScreen(Stage stage) throws Exception {
//        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/Login.fxml"));
//        Parent root = loader.load();
//        
//        Scene scene = new Scene(root);
//        scene.getStylesheets().add(getClass().getResource("/css/BParkStyle.css").toExternalForm());
//        stage.setTitle("BPark - Login");
//        stage.setScene(scene);
//        stage.setResizable(false);
//        stage.show();
//    }
//    
//    public static void connectToServer() {
//        // Run connection in background thread to avoid freezing UI
//        Thread connectionThread = new Thread(() -> {
//            try {
//                client = new BParkClient(serverIP, serverPort);
//                client.openConnection();
//            } catch (Exception e) {
//                e.printStackTrace();
//                
//                // Show error dialog on UI thread
//                Platform.runLater(() -> {
//                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
//                        javafx.scene.control.Alert.AlertType.ERROR);
//                    alert.setTitle("Connection Error");
//                    alert.setHeaderText("Failed to connect to server");
//                    alert.setContentText("Could not connect to server at " + serverIP + ":" + serverPort + 
//                                       "\n\nError: " + e.getMessage());
//                    alert.showAndWait();
//                });
//            }
//        });
//        
//        connectionThread.setDaemon(true);
//        connectionThread.setName("ServerConnectionThread");
//        connectionThread.start();
//        
//        // Give the connection thread a moment to start
//        try {
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            // Ignore
//        }
//    }
//    
//    public static boolean isConnected() {
//        return client != null && client.isConnected();
//    }
//    
//    public static void switchToMainScreen(String userType) {
//        try {
//            Stage stage = new Stage();
//            Parent root = null;
//            
//            switch (userType) {
//            case "sub":
//                FXMLLoader subLoader = new FXMLLoader(BParkClientApp.class.getResource("/client/SubscriberMain.fxml"));
//                root = subLoader.load();
//                SubscriberController controller = subLoader.getController();
//
//                // Move loadHomeView AFTER controller is fully loaded
//                Platform.runLater(controller::loadHomeView);
//
//                // Set the user name in the bottom label
//                controller.setUserName(getCurrentUser());
//
//                stage.setTitle("BPark - Subscriber Portal");
//                break;
//                
//            case "emp":
//                FXMLLoader empLoader = new FXMLLoader(BParkClientApp.class.getResource("/client/AttendantMain.fxml"));
//                root = empLoader.load();
//                AttendantController attendantController = empLoader.getController(); 
//                attendantController.setUserName(getCurrentUser());
//                stage.setTitle("BPark - Attendant Portal");
//                break;
//                
//            case "mng":
//                FXMLLoader mngLoader = new FXMLLoader(BParkClientApp.class.getResource("/client/ManagerMain.fxml"));
//                root = mngLoader.load();
//                stage.setTitle("BPark - Manager Portal");
//                break;
//            }
//            
//            if (root != null) {
//                Scene scene = new Scene(root);
//                stage.setScene(scene);
//                stage.show();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//    
//    // Client communication class
//    static class BParkClient extends ObservableClient {
//        public BParkClient(String host, int port) {
//            super(host, port);
//        }
//        
//        @Override
//        protected void handleMessageFromServer(Object msg) {
//            Platform.runLater(() -> {
//                try {
//                    Object message = msg;
//                    if (message instanceof byte[]) {
//                        message = ClientMessageHandler.deserialize(message);
//                    }
//                    
//                    if (message instanceof Message) {
//                        ClientMessageHandler.handleMessage((Message) message);
//                    } else if (message instanceof String) {
//                        ClientMessageHandler.handleStringMessage((String) message);
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//        }
//        
//        @Override
//        protected void connectionClosed() {
//            Platform.runLater(() -> {
//                System.out.println("Connection closed");
//                // Show reconnect dialog
//            });
//        }
//        
//        @Override
//        protected void connectionException(Exception exception) {
//            Platform.runLater(() -> {
//                System.out.println("Connection error: " + exception.getMessage());
//                // Show error dialog
//            });
//        }
//    }
//    
//    // Utility methods for sending messages
//    public static void sendMessage(Message msg) {
//        try {
//            if (client != null && client.isConnected()) {
//                client.sendToServer(ClientMessageHandler.serialize(msg));
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//    
//    public static void sendStringMessage(String msg) {
//        try {
//            if (client != null && client.isConnected()) {
//                client.sendToServer(msg);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//    
//    // Getters and setters
//    public static BParkClient getClient() {
//        return client;
//    } 
//    
//    public static String getCurrentUser() {
//        return currentUser;
//    }
//    
//    public static void setCurrentUser(String user) {
//        currentUser = user;
//    }
//    
//    public static String getUserType() {
//        return userType;
//    }
//    
//    public static void setUserType(String type) {
//        userType = type;
//    }
//    
//    public static void setServerIP(String ip) {
//        serverIP = ip;
//    }
//    
//    public static void setServerPort(int port) {
//        serverPort = port;
//    }
//    
//    public static String getServerIP() {
//        return serverIP;
//    }
//    
//    public static int getServerPort() {
//        return serverPort;
//    }
//    
//    public static void disconnect() {
//        try {
//            if (client != null && client.isConnected()) {
//                client.sendToServer("ClientDisconnect");
//                client.closeConnection();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//    
//    @Override
//    public void stop() throws Exception {
//        // Clean up when application closes
//        disconnect();
//        super.stop();
//    }
//    
//    public static void main(String[] args) {
//        launch(args);
//    }
//    
//    public static void setAttendantController(AttendantController controller) {
//        attendantController = controller;
//    }
//
//    public static AttendantController getAttendantController() {
//        return attendantController;
//    }
//
//    public static void setManagerController(controllers.ManagerController controller) {
//        managerController = controller;
//    }
//
//    public static controllers.ManagerController getManagerController() {
//        return managerController;
//    }
//    
//    public static void returnToLogin() {
//        try {
//            // Send disconnect notification if connected
//            if (client != null && client.isConnected()) {
//                sendStringMessage("ClientDisconnect");
//            }
//            
//            // Close current connection
//            if (client != null && client.isConnected()) {
//                client.closeConnection();
//            }
//            
//            // Get current stage
//            Stage currentStage = getCurrentStage();
//            
//            // Load login screen
//            FXMLLoader loader = new FXMLLoader(BParkClientApp.class.getResource("/client/Login.fxml"));
//            Parent root = loader.load();
//            
//            Scene scene = new Scene(root);
//            scene.getStylesheets().add(BParkClientApp.class.getResource("/css/BParkStyle.css").toExternalForm());
//            
//            currentStage.setScene(scene);
//            currentStage.setTitle("BPark - Login");
//            currentStage.setResizable(false);
//            
//            // Clear current user data
//            currentUser = null;
//            userType = null;
//            
//            // Reconnect to server for next login
//            connectToServer();
//            
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.err.println("Error returning to login: " + e.getMessage());
//        }
//    }
//
//    public static void exitApplication() {
//        try {
//            // Send disconnect notification if connected
//            if (client != null && client.isConnected()) {
//                sendStringMessage("ClientDisconnect");
//            }
//            
//            // Close connection
//            if (client != null && client.isConnected()) {
//                client.closeConnection();
//            }
//            
//            // Exit application
//            Platform.exit();
//            System.exit(0);
//            
//        } catch (Exception e) {
//            e.printStackTrace();
//            // Force exit even if there's an error
//            System.exit(0);
//        }
//    }
//    
//    private static Stage getCurrentStage() {
//        // Get the primary stage or any showing stage
//        return Stage.getWindows().stream()
//            .filter(window -> window instanceof Stage && window.isShowing())
//            .map(window -> (Stage) window)
//            .findFirst()
//            .orElse(new Stage());
//    }
//}