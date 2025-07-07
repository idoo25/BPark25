package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import entities.ParkingSubscriber;
import server.DBController;
import services.EmailServiceStub;

/**
 * UserController handles all user-related operations following the Single Responsibility Principle.
 * This includes user authentication, registration, profile management, and subscriber operations.
 */
public class UserController {
    
    /**
     * Represents the role-based access control levels for the parking system.
     */
    public enum UserRole {
        SUBSCRIBER("sub"), ATTENDANT("emp"), MANAGER("mng");

        private final String dbValue;

        UserRole(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static UserRole fromDbValue(String dbValue) {
            for (UserRole role : values()) {
                if (role.dbValue.equals(dbValue)) {
                    return role;
                }
            }
            return null;
        }
    }

    /**
     * Gets user role from database
     */
    public UserRole getUserRole(String userName) {
        String qry = "SELECT UserTypeEnum FROM users WHERE UserName = ?";
        Connection conn = DBController.getInstance().getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UserRole.fromDbValue(rs.getString("UserTypeEnum"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Retrieves subscriber information by username
     */
    public ParkingSubscriber getUserInfo(String userName) {
        String qry = "SELECT * FROM users WHERE UserName = ?";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, userName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ParkingSubscriber subscriber = new ParkingSubscriber();
                    subscriber.setSubscriberID(rs.getInt("User_ID"));
                    subscriber.setFirstName(rs.getString("Name"));
                    subscriber.setPhoneNumber(rs.getString("PhoneNumber"));
                    subscriber.setEmail(rs.getString("Email"));
                    subscriber.setCarNumber(rs.getString("CarNumber"));
                    subscriber.setSubscriberCode(rs.getString("UserName"));
                    subscriber.setUserType(rs.getString("UserTypeEnum"));
                    return subscriber;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Gets subscriber information by name for attendant lookup operations.
     * Only retrieves users with subscriber role.
     * 
     * @param name the full name of the subscriber to find
     * @return ParkingSubscriber object if found, null otherwise
     */
    public ParkingSubscriber getSubscriberByName(String name) {
        String qry = "SELECT * FROM users WHERE Name = ? AND UserTypeEnum = 'sub'";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ParkingSubscriber subscriber = new ParkingSubscriber();
                    subscriber.setSubscriberID(rs.getInt("User_ID"));
                    subscriber.setFirstName(rs.getString("Name"));
                    subscriber.setPhoneNumber(rs.getString("PhoneNumber"));
                    subscriber.setEmail(rs.getString("Email"));
                    subscriber.setCarNumber(rs.getString("CarNumber"));
                    subscriber.setSubscriberCode(rs.getString("UserName"));
                    subscriber.setUserType(rs.getString("UserTypeEnum"));
                    return subscriber;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Gets all subscribers for attendant management
     */
    public List<ParkingSubscriber> getAllSubscribers() {
        List<ParkingSubscriber> subscribers = new ArrayList<>();
        String qry = "SELECT * FROM users WHERE UserTypeEnum = 'sub' ORDER BY Name";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParkingSubscriber subscriber = new ParkingSubscriber();
                    subscriber.setSubscriberID(rs.getInt("User_ID"));
                    subscriber.setFirstName(rs.getString("Name"));
                    subscriber.setPhoneNumber(rs.getString("PhoneNumber"));
                    subscriber.setEmail(rs.getString("Email"));
                    subscriber.setCarNumber(rs.getString("CarNumber"));
                    subscriber.setSubscriberCode(rs.getString("UserName"));
                    subscriber.setUserType(rs.getString("UserTypeEnum"));
                    subscribers.add(subscriber);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return subscribers;
    }

    /**
     * Gets name by username and user ID (for kiosk login)
     */
    public String getNameByUsernameAndUserID(String username, int userID) {
        String qry = "SELECT Name FROM users WHERE UserName = ? AND User_ID = ?";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setString(1, username);
            stmt.setInt(2, userID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Gets name by user ID only (for RFID login)
     */
    public String getNameByUserID(int userID) {
        String qry = "SELECT Name FROM users WHERE User_ID = ?";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(qry)) {
            stmt.setInt(1, userID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Registers a new subscriber
     */
    public String registerNewSubscriber(String attendantUserName, String name, String phone, String email, String carNumber, String subscriberUserName) {
        UserRole attendantRole = getUserRole(attendantUserName);
        if (attendantRole != UserRole.ATTENDANT) {
            return "ERROR: Only attendants can register new subscribers";
        }

        // Check if username already exists
        if (getUserInfo(subscriberUserName) != null) {
            return "ERROR: Username already exists";
        }

        String insertQry = "INSERT INTO users (Name, PhoneNumber, Email, CarNumber, UserName, UserTypeEnum) VALUES (?, ?, ?, ?, ?, 'sub')";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(insertQry, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, phone);
            stmt.setString(3, email);
            stmt.setString(4, carNumber);
            stmt.setString(5, subscriberUserName);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int userID = generatedKeys.getInt(1);
                        
                        // Send registration emails
                        EmailServiceStub.sendRegistrationConfirmation(email, name, subscriberUserName, userID);
                        EmailServiceStub.sendWelcomeMessage(email, name, subscriberUserName, userID);
                        
                        return "SUCCESS: Subscriber registered successfully. User ID: " + userID;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during registration: " + e.getMessage();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
        return "ERROR: Registration failed";
    }

    /**
     * Updates subscriber information
     */
    public String updateSubscriberInfo(String updateData) {
        String[] parts = updateData.split(",");
        if (parts.length < 6) {
            return "ERROR: Invalid update data format";
        }

        String userName = parts[0].trim();
        String newName = parts[1].trim();
        String newPhone = parts[2].trim();
        String newEmail = parts[3].trim();
        String newCarNumber = parts[4].trim();
        String newUserName = parts[5].trim();

        String updateQry = "UPDATE users SET Name = ?, PhoneNumber = ?, Email = ?, CarNumber = ?, UserName = ? WHERE UserName = ?";
        Connection conn = DBController.getInstance().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(updateQry)) {
            stmt.setString(1, newName);
            stmt.setString(2, newPhone);
            stmt.setString(3, newEmail);
            stmt.setString(4, newCarNumber);
            stmt.setString(5, newUserName);
            stmt.setString(6, userName);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                return "SUCCESS: Profile updated successfully";
            } else {
                return "ERROR: User not found";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: Database error during update: " + e.getMessage();
        } finally {
            DBController.getInstance().releaseConnection(conn);
        }
    }
}