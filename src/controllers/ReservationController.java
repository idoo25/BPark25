package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import entities.ParkingOrder;
import entities.ParkingSubscriber;
import server.DBController;
import services.EmailService;

/**
 * ReservationController handles all reservation-related operations following the Single Responsibility Principle.
 * This includes making, canceling, and managing parking reservations.
 */
public class ReservationController {
    
    private final UserController userController;
    private final ParkingSpotController spotController;

    public ReservationController(UserController userController, ParkingSpotController spotController) {
        this.userController = userController;
        this.spotController = spotController;
    }

    /**
     * Makes a new parking reservation
     */
    public String makeReservation(String userName, String reservationDate) {
        // Check if user exists
        ParkingSubscriber user = userController.getUserInfo(userName);
        if (user == null) {
            return "ERROR: User not found";
        }

        // Check if reservations are allowed
        if (!spotController.canMakeReservation()) {
            return "ERROR: Reservations not available - parking lot too full";
        }

        // Parse and validate reservation time
        LocalDateTime reservationTime;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            reservationTime = LocalDateTime.parse(reservationDate, formatter);
            
            // Check if reservation is for the future
            if (reservationTime.isBefore(LocalDateTime.now())) {
                return "ERROR: Cannot make reservations for past times";
            }
        } catch (Exception e) {
            return "ERROR: Invalid date format. Use yyyy-MM-dd HH:mm";
        }

        // Allocate a spot for the reservation
        int spotId = spotController.allocateSpot();
        if (spotId == -1) {
            return "ERROR: No available spots for reservation";
        }

        // Create reservation in database
        String insertQry = """
                INSERT INTO parkinginfo (User_ID, ParkingSpot_ID, IsOrderedEnum, statusEnum, 
                    Ordered_start_time, Ordered_end_time) 
                VALUES (?, ?, 'preorder', 'waitingList', ?, ?)
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(insertQry, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, user.getSubscriberID());
            stmt.setInt(2, spotId);
            stmt.setTimestamp(3, Timestamp.valueOf(reservationTime));
            stmt.setTimestamp(4, Timestamp.valueOf(reservationTime.plusHours(4))); // Default 4-hour reservation
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int reservationCode = generatedKeys.getInt(1);
                        
                        // Send confirmation email
                        String formattedDate = reservationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        EmailService.sendReservationConfirmation(user.getEmail(), user.getFirstName(),
                            String.valueOf(reservationCode), formattedDate, String.valueOf(spotId));
                        
                        return "SUCCESS: Reservation made. Code: " + reservationCode;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during reservation: " + e.getMessage();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return "ERROR: Reservation failed";
    }

    /**
     * Cancels a parking reservation
     */
    public String cancelReservation(String userName, int reservationCode) {
        // Verify user owns this reservation
        String checkQry = """
                SELECT pi.*, u.Name, u.Email 
                FROM parkinginfo pi 
                JOIN users u ON pi.User_ID = u.User_ID 
                WHERE pi.ParkingInfo_ID = ? AND u.UserName = ? AND pi.statusEnum = 'waitingList'
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement checkStmt = conn.prepareStatement(checkQry)) {
            checkStmt.setInt(1, reservationCode);
            checkStmt.setString(2, userName);
            
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    return "ERROR: Reservation not found or cannot be cancelled";
                }
                
                String userEmail = rs.getString("Email");
                String fullName = rs.getString("Name");
                
                // Delete the reservation
                String deleteQry = "DELETE FROM parkinginfo WHERE ParkingInfo_ID = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQry)) {
                    deleteStmt.setInt(1, reservationCode);
                    int deleted = deleteStmt.executeUpdate();
                    
                    if (deleted > 0) {
                        // Send cancellation email
                        EmailService.sendReservationCancelled(userEmail, fullName, String.valueOf(reservationCode));
                        return "SUCCESS: Reservation cancelled successfully";
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during cancellation: " + e.getMessage();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return "ERROR: Cancellation failed";
    }

    /**
     * Activates a reservation when user enters parking
     */
    public String enterParkingWithReservation(int reservationCode) {
        String checkQry = """
                SELECT pi.*, u.Name 
                FROM parkinginfo pi 
                JOIN users u ON pi.User_ID = u.User_ID 
                WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'waitingList'
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement checkStmt = conn.prepareStatement(checkQry)) {
            checkStmt.setInt(1, reservationCode);
            
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    return "ERROR: Invalid reservation code or reservation not found";
                }
                
                // Activate the reservation
                String activateQry = """
                        UPDATE parkinginfo 
                        SET statusEnum = 'active', Actual_start_time = CURRENT_TIMESTAMP 
                        WHERE ParkingInfo_ID = ?
                        """;
                
                try (PreparedStatement activateStmt = conn.prepareStatement(activateQry)) {
                    activateStmt.setInt(1, reservationCode);
                    int updated = activateStmt.executeUpdate();
                    
                    if (updated > 0) {
                        return "SUCCESS: Welcome! Your reservation is now active. Code: " + reservationCode;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during activation: " + e.getMessage();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return "ERROR: Activation failed";
    }

    /**
     * Gets parking history for a user
     */
    public ArrayList<ParkingOrder> getParkingHistory(String userName) {
        ArrayList<ParkingOrder> history = new ArrayList<>();
        String qry = """
                SELECT pi.*, ps.ParkingSpot_ID, u.Name 
                FROM parkinginfo pi 
                JOIN users u ON pi.User_ID = u.User_ID 
                JOIN parkingspot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID 
                WHERE u.UserName = ? 
                ORDER BY pi.Actual_start_time DESC
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
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
                    
                    Timestamp exitTime = rs.getTimestamp("Actual_exit_time");
                    if (exitTime != null) {
                        order.setExitTime(exitTime.toLocalDateTime());
                    }
                    
                    Timestamp expectedExit = rs.getTimestamp("Ordered_end_time");
                    if (expectedExit != null) {
                        order.setExpectedExitTime(expectedExit.toLocalDateTime());
                    }
                    
                    history.add(order);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return history;
    }
}