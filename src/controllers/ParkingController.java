package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import entities.ParkingOrder;
import entities.ParkingSubscriber;
import server.DBController;
import services.EmailServiceStub;

/**
 * Simplified ParkingController that coordinates other controllers and handles core parking operations.
 * This follows the Single Responsibility Principle by delegating specific operations to specialized controllers.
 */
public class ParkingController {
    
    private final UserController userController;
    private final ParkingSpotController spotController;
    private final ReservationController reservationController;
    private SimpleAutoCancellationService autoCancellationService;
    
    public int successFlag;

    /**
     * Constructs a ParkingController and initializes the database connection and specialized controllers.
     * 
     * @param dbname the database name
     * @param pass the database password
     */
    public ParkingController(String dbname, String pass) {
        DBController.initializeConnection(dbname, pass);
        successFlag = DBController.getInstance().getSuccessFlag();
        
        userController = new UserController();
        spotController = new ParkingSpotController();
        reservationController = new ReservationController(userController, spotController);
    }

    // ========== DELEGATION METHODS TO SPECIALIZED CONTROLLERS ==========
    
    // User-related operations
    public ParkingSubscriber getUserInfo(String userName) {
        return userController.getUserInfo(userName);
    }
    
    public ParkingSubscriber getSubscriberByName(String name) {
        return userController.getSubscriberByName(name);
    }
    
    public List<ParkingSubscriber> getAllSubscribers() {
        return userController.getAllSubscribers();
    }
    
    public String getNameByUsernameAndUserID(String username, int userID) {
        return userController.getNameByUsernameAndUserID(username, userID);
    }
    
    public String getNameByUserID(int userID) {
        return userController.getNameByUserID(userID);
    }
    
    public String registerNewSubscriber(String attendantUserName, String name, String phone, String email, String carNumber, String subscriberUserName) {
        return userController.registerNewSubscriber(attendantUserName, name, phone, email, carNumber, subscriberUserName);
    }
    
    public String updateSubscriberInfo(String updateData) {
        return userController.updateSubscriberInfo(updateData);
    }
    
    // Parking spot operations
    public void initializeParkingSpots() {
        spotController.initializeParkingSpots();
    }
    
    public int getAvailableParkingSpots() {
        return spotController.getAvailableParkingSpots();
    }
    
    public boolean isParkingFull() {
        return spotController.isParkingFull();
    }
    
    public boolean canMakeReservation() {
        return spotController.canMakeReservation();
    }
    
    // Reservation operations
    public String makeReservation(String userName, String reservationDate) {
        return reservationController.makeReservation(userName, reservationDate);
    }
    
    public String cancelReservation(String userName, int reservationCode) {
        return reservationController.cancelReservation(userName, reservationCode);
    }
    
    public String enterParkingWithReservation(int reservationCode) {
        return reservationController.enterParkingWithReservation(reservationCode);
    }
    
    public ArrayList<ParkingOrder> getParkingHistory(String userName) {
        return reservationController.getParkingHistory(userName);
    }

    // ========== CORE PARKING OPERATIONS ==========
    
    /**
     * Handles immediate parking entry (spontaneous parking)
     */
    public String enterParking(int userID) {
        if (isParkingFull()) {
            return "FULL";
        }
        
        String userName = getNameByUserID(userID);
        if (userName == null) {
            return "ERROR: User not found";
        }
        
        int spotId = spotController.allocateSpot();
        if (spotId == -1) {
            return "ERROR: No available spots";
        }
        
        String insertQry = """
                INSERT INTO parkinginfo (User_ID, ParkingSpot_ID, IsOrderedEnum, statusEnum, 
                    Actual_start_time, Ordered_end_time) 
                VALUES (?, ?, 'spontaneous', 'active', CURRENT_TIMESTAMP, ?)
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(insertQry, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userID);
            stmt.setInt(2, spotId);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusHours(2))); // Default 2-hour spontaneous parking
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int parkingCode = generatedKeys.getInt(1);
                        return "SUCCESS: Welcome! Your parking code is: " + parkingCode + 
                               ". Spot: " + spotId + ". Please remember your code.";
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during parking entry";
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return "ERROR: Parking entry failed";
    }

    /**
     * Handles car retrieval by parking code
     */
    public String retrieveCarByCode(int parkingCode) {
        String qry = """
                SELECT pi.*, u.Name, u.Email, ps.ParkingSpot_ID 
                FROM parkinginfo pi 
                JOIN users u ON pi.User_ID = u.User_ID 
                JOIN parkingspot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID 
                WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, parkingCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return "ERROR: Invalid parking code or car already retrieved";
                }
                
                String userName = rs.getString("Name");
                String userEmail = rs.getString("Email");
                int spotId = rs.getInt("ParkingSpot_ID");
                
                // Update parking record with exit time
                String updateQry = """
                        UPDATE parkinginfo 
                        SET statusEnum = 'completed', Actual_exit_time = CURRENT_TIMESTAMP 
                        WHERE ParkingInfo_ID = ?
                        """;
                
                try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                    updateStmt.setInt(1, parkingCode);
                    int updated = updateStmt.executeUpdate();
                    
                    if (updated > 0) {
                        return "SUCCESS: Thank you " + userName + "! Your car is ready for pickup from spot " + spotId + 
                               ". Have a safe drive!";
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during car retrieval";
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return "ERROR: Car retrieval failed";
    }

    /**
     * Sends lost parking code via email or returns the code
     */
    public String sendLostParkingCode(String userName) {
        ParkingSubscriber user = getUserInfo(userName);
        if (user == null) {
            return "ERROR: User not found";
        }
        
        String qry = """
                SELECT ParkingInfo_ID 
                FROM parkinginfo pi 
                JOIN users u ON pi.User_ID = u.User_ID 
                WHERE u.UserName = ? AND pi.statusEnum = 'active' 
                ORDER BY pi.Actual_start_time DESC 
                LIMIT 1
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int parkingCode = rs.getInt("ParkingInfo_ID");
                    
                    // Send email with parking code
                    EmailServiceStub.sendParkingCodeRecovery(user.getEmail(), user.getFirstName(), String.valueOf(parkingCode));
                    
                    return "SUCCESS: Your parking code is: " + parkingCode + ". Recovery email sent to " + user.getEmail();
                } else {
                    return "ERROR: No active parking session found for this user";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during code recovery";
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
    }

    /**
     * Overloaded method for lost code by user ID (for kiosk)
     */
    public String sendLostParkingCode(int userID) {
        String userName = getNameByUserID(userID);
        if (userName == null) {
            return "ERROR: User not found";
        }
        
        // For kiosk, we return the code directly instead of sending email
        String qry = """
                SELECT ParkingInfo_ID 
                FROM parkinginfo 
                WHERE User_ID = ? AND statusEnum = 'active' 
                ORDER BY Actual_start_time DESC 
                LIMIT 1
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, userID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return String.valueOf(rs.getInt("ParkingInfo_ID"));
                } else {
                    return "ERROR: No active parking session found";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error";
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
    }

    /**
     * Gets all active parking sessions (for attendant view)
     */
    public ArrayList<ParkingOrder> getActiveParkings() {
        ArrayList<ParkingOrder> activeParkings = new ArrayList<>();
        String qry = """
                SELECT pi.*, u.Name, ps.ParkingSpot_ID
                FROM parkinginfo pi
                JOIN users u ON pi.User_ID = u.User_ID
                JOIN parkingspot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID
                WHERE pi.statusEnum = 'active'
                ORDER BY pi.Actual_start_time
                """;
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParkingOrder order = new ParkingOrder();
                    order.setOrderID(rs.getInt("ParkingInfo_ID"));
                    order.setParkingCode(String.valueOf(rs.getInt("ParkingInfo_ID")));
                    order.setOrderType(rs.getString("IsOrderedEnum"));
                    order.setSubscriberName(rs.getString("Name"));
                    order.setSpotNumber(String.valueOf(rs.getInt("ParkingSpot_ID")));
                    order.setStatus(rs.getString("statusEnum"));
                    
                    // Set timestamps
                    Timestamp entryTime = rs.getTimestamp("Actual_start_time");
                    if (entryTime != null) {
                        order.setEntryTime(entryTime.toLocalDateTime());
                    }
                    
                    Timestamp expectedExit = rs.getTimestamp("Ordered_end_time");
                    if (expectedExit != null) {
                        order.setExpectedExitTime(expectedExit.toLocalDateTime());
                    }
                    
                    activeParkings.add(order);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return activeParkings;
    }

    /**
     * Extends parking time for a given parking code
     */
    public String extendParkingTime(String parkingCode, int additionalHours) {
        try {
            int code = Integer.parseInt(parkingCode);
            
            String qry = """
                    SELECT pi.*, u.Name, u.Email 
                    FROM parkinginfo pi 
                    JOIN users u ON pi.User_ID = u.User_ID 
                    WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
                    """;
            
            Connection conn = DBController.getInstance().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(qry)) {
                stmt.setInt(1, code);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return "ERROR: Invalid parking code or session not active";
                    }
                    
                    String userName = rs.getString("Name");
                    String userEmail = rs.getString("Email");
                    
                    // Update the expected end time
                    String updateQry = """
                            UPDATE parkinginfo 
                            SET Ordered_end_time = DATE_ADD(Ordered_end_time, INTERVAL ? HOUR) 
                            WHERE ParkingInfo_ID = ?
                            """;
                    
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
                        updateStmt.setInt(1, additionalHours);
                        updateStmt.setInt(2, code);
                        
                        int updated = updateStmt.executeUpdate();
                        if (updated > 0) {
                            // Send confirmation email
                            EmailServiceStub.sendExtensionConfirmation(userEmail, userName, 
                                "Parking code: " + parkingCode, additionalHours, "hours");
                            
                            return "SUCCESS: Parking extended by " + additionalHours + " hours";
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            return "ERROR: Invalid parking code format";
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during extension";
        } finally {
            DBController.getInstance().releaseConnection(DBController.getInstance().getConnection());
        }
        return "ERROR: Extension failed";
    }

    /**
     * Shuts down the controller and its services
     */
    public void shutdown() {
        if (autoCancellationService != null) {
            autoCancellationService.shutdown();
        }
    }
}