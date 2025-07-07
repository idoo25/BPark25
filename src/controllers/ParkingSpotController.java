package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import server.DBController;

/**
 * ParkingSpotController handles all parking spot allocation and management operations.
 * This includes spot availability checking, initialization, and allocation logic.
 */
public class ParkingSpotController {
    
    /** The total number of parking spots in the parking lot (constant). */
    private static final int TOTAL_PARKING_SPOTS = 10;
    
    /** The fraction of occupancy above which new reservations are restricted (constant). */
    private static final double RESERVATION_THRESHOLD = 0.4;

    /**
     * Initializes all parking spots in the database if they don't exist
     */
    public void initializeParkingSpots() {
        String checkQry = "SELECT COUNT(*) FROM parkingspot";
        String insertQry = "INSERT IGNORE INTO parkingspot (ParkingSpot_ID) VALUES (?)";
        
        Connection conn = DBController.getInstance().getConnection();
        try {
            // Check if spots already exist
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQry);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= TOTAL_PARKING_SPOTS) {
                    System.out.println("Parking spots already initialized.");
                    return;
                }
            }

            // Initialize missing spots
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQry)) {
                for (int i = 1; i <= TOTAL_PARKING_SPOTS; i++) {
                    insertStmt.setInt(1, i);
                    insertStmt.executeUpdate();
                }
            }
            System.out.println("Parking spots initialized successfully.");
            
        } catch (SQLException e) {
            System.err.println("Error initializing parking spots: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
    }

    /**
     * Gets the number of available parking spots
     */
    public int getAvailableParkingSpots() {
        String qry = """
                SELECT COUNT(*) 
                FROM parkingspot ps 
                LEFT JOIN parkinginfo pi ON ps.ParkingSpot_ID = pi.ParkingSpot_ID 
                    AND pi.statusEnum = 'active'
                WHERE pi.ParkingSpot_ID IS NULL
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return 0;
    }

    /**
     * Checks if the parking lot is full
     */
    public boolean isParkingFull() {
        return getAvailableParkingSpots() == 0;
    }

    /**
     * Checks if reservation is possible (40% of spots must be available)
     */
    public boolean canMakeReservation() {
        int availableSpots = getAvailableParkingSpots();
        return availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
    }

    /**
     * Allocates a parking spot for immediate use
     */
    public int allocateSpot() {
        String qry = """
                SELECT ps.ParkingSpot_ID 
                FROM parkingspot ps 
                LEFT JOIN parkinginfo pi ON ps.ParkingSpot_ID = pi.ParkingSpot_ID 
                    AND pi.statusEnum = 'active'
                WHERE pi.ParkingSpot_ID IS NULL 
                ORDER BY ps.ParkingSpot_ID 
                LIMIT 1
                """;
        
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("ParkingSpot_ID");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return -1; // No spots available
    }

    /**
     * Gets the total number of parking spots
     */
    public int getTotalParkingSpots() {
        return TOTAL_PARKING_SPOTS;
    }

    /**
     * Gets the reservation threshold
     */
    public double getReservationThreshold() {
        return RESERVATION_THRESHOLD;
    }
}