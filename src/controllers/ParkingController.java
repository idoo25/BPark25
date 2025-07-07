package controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import entities.ParkingOrder;
import entities.ParkingSubscriber;
import server.DBController;
import services.EmailServiceStub;

/**
 * Enhanced ParkingController with email notifications Updated to work with
 * unified parkinginfo table structure
 */

public class ParkingController {

	/**
	 * The unique ID of the subscriber (customer) in the system.
	 */
	private int subscriberID;

	/**
	 * The first name of the subscriber for identification and display.
	 */
	private String firstName;

	/**
	 * The contact phone number of the subscriber.
	 */
	private String phoneNumber;

	/**
	 * The email address of the subscriber for notifications and receipts.
	 */
	private String email;

	/**
	 * The license plate number of the subscriber's car.
	 */
	private String carNumber;

	/**
	 * The user login code or subscriber code used for authentication.
	 */
	private String subscriberCode;

	/**
	 * Indicates the type of user: subscriber ("sub"), employee ("emp"), or manager
	 * ("mng").
	 */
	private String userType;

	/**
	 * The active SQL database connection used for running queries.
	 */
	private Connection conn;

	/**
	 * A flag used to indicate success (1) or failure (0) of certain operations.
	 */
	public int successFlag;

	/**
	 * The total number of parking spots in the parking lot (constant).
	 */
	private static final int TOTAL_PARKING_SPOTS = 10;

	/**
	 * The fraction of occupancy above which new reservations are restricted
	 * (constant).
	 */
	private static final double RESERVATION_THRESHOLD = 0.4;
	
	// ========== SMART PARKING CONFIGURATION ==========
	
	/**
	 * Smart parking configuration constants for enhanced features
	 */
	private static final double AVAILABILITY_THRESHOLD = 0.4; // 40% rule for smart features
	private static final int PREFERRED_WINDOW_HOURS = 8;
	private static final int STANDARD_BOOKING_HOURS = 4;
	private static final int MINIMUM_SPONTANEOUS_HOURS = 2;
	private static final int TIME_SLOT_MINUTES = 15; // 15-minute precision
	private static final int DISPLAY_WINDOW_HOURS = 1; // ±1 hour around selected time
	private static final int MINIMUM_EXTENSION_HOURS = 2;
	private static final int MAXIMUM_EXTENSION_HOURS = 4;

	/**
	 * Service that periodically checks and cancels overdue reservations
	 * automatically.
	 */
	private SimpleAutoCancellationService autoCancellationService;

	/**
	 * Constructs a ParkingController instance by initializing the database
	 * connection with the given database name and password. Also retrieves the
	 * success flag to verify the connection status.
	 *
	 * @param dbname the name of the database to connect to
	 * @param pass   the password for the database connection
	 */
	public ParkingController(String dbname, String pass) {
		DBController.initializeConnection(dbname, pass);
		successFlag = DBController.getInstance().getSuccessFlag();
		autoCancellationService = new SimpleAutoCancellationService(this);

		if (successFlag == 1) {
			startAutoCancellationService();
		}
	}

	/**
	 * Represents the role-based access control levels for the parking system. Each
	 * role is mapped to a database string value for persistence.
	 *
	 * Roles include: - SUBSCRIBER: Regular user with subscription privileges
	 * ("sub") - ATTENDANT: Employee responsible for day-to-day operations ("emp") -
	 * MANAGER: Administrative user with elevated access ("mng")
	 */
	public enum UserRole {
		SUBSCRIBER("sub"), ATTENDANT("emp"), MANAGER("mng");

		private final String dbValue;

		/**
		 * Constructs a UserRole with the associated database string value.
		 *
		 * @param dbValue the string value stored in the database
		 */
		UserRole(String dbValue) {
			this.dbValue = dbValue;
		}

		/**
		 * Returns the database string value for this user role.
		 *
		 * @return the string representation of the role in the database
		 */
		public String getDbValue() {
			return dbValue;
		}

		/**
		 * Retrieves the corresponding UserRole enum for a given database string value.
		 *
		 * @param dbValue the string stored in the database
		 * @return the matching UserRole, or null if no match is found
		 */
		public static UserRole fromDbValue(String dbValue) {
			for (UserRole role : values()) {
				if (role.dbValue.equals(dbValue)) {
					return role;
				}
			}
			return null;
		}
	}

	// ========== SMART PARKING DATA STRUCTURES ==========
	
	/**
	 * Represents a 15-minute time slot availability for smart booking
	 */
	public static class TimeSlot {
		public LocalDateTime startTime;
		public boolean isAvailable;
		public int availableSpots;
		public boolean meetsFortyPercentRule;
		
		public TimeSlot(LocalDateTime startTime, boolean isAvailable, int availableSpots, boolean meetsFortyPercentRule) {
			this.startTime = startTime;
			this.isAvailable = isAvailable;
			this.availableSpots = availableSpots;
			this.meetsFortyPercentRule = meetsFortyPercentRule;
		}
		
		public String getFormattedTime() {
			return startTime.format(DateTimeFormatter.ofPattern("HH:mm"));
		}
	}
	
	/**
	 * Represents parking spot availability window for smart allocation
	 */
	public static class SpotAvailability {
		public int spotId;
		public LocalDateTime availableFrom;
		public LocalDateTime availableUntil;
		public long availabilityDurationHours;
		
		public SpotAvailability(int spotId, LocalDateTime from, LocalDateTime until) {
			this.spotId = spotId;
			this.availableFrom = from;
			this.availableUntil = until;
			this.availabilityDurationHours = Duration.between(from, until).toHours();
		}
		
		public boolean hasEightHourWindow(LocalDateTime bookingStart) {
			LocalDateTime eightHourEnd = bookingStart.plusHours(PREFERRED_WINDOW_HOURS);
			return !bookingStart.isBefore(availableFrom) && !eightHourEnd.isAfter(availableUntil);
		}
		
		public boolean canAccommodateBooking(LocalDateTime bookingStart, LocalDateTime bookingEnd) {
			return !bookingStart.isBefore(availableFrom) && !bookingEnd.isAfter(availableUntil);
		}
	}
	
	/**
	 * Internal class for smart spot allocation results
	 */
	private static class SpotAllocation {
		int spotId;
		int allocatedHours;
		boolean hasEightHourWindow;
		
		SpotAllocation(int spotId, int allocatedHours, boolean hasEightHourWindow) {
			this.spotId = spotId;
			this.allocatedHours = allocatedHours;
			this.hasEightHourWindow = hasEightHourWindow;
		}
	}

	// Getters
	public int getSubscriberID() {
		return subscriberID;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getEmail() {
		return email;
	}

	public String getCarNumber() {
		return carNumber;
	}

	public String getSubscriberCode() {
		return subscriberCode;
	}

	public String getUserType() {
		return userType;
	}

	// Setters
	public void setSubscriberID(int subscriberID) {
		this.subscriberID = subscriberID;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setCarNumber(String carNumber) {
		this.carNumber = carNumber;
	}

	public void setSubscriberCode(String subscriberCode) {
		this.subscriberCode = subscriberCode;
	}

	public void setUserType(String userType) {
		this.userType = userType;
	}

	/**
	 * Get user role from database
	 */
	private UserRole getUserRole(String userName) {
		String qry = "SELECT UserTypeEnum FROM users WHERE UserName = ?";
		Connection conn = DBController.getInstance().getConnection();

		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, userName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					String userType = rs.getString("UserTypeEnum");
					return UserRole.fromDbValue(userType);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting user role: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Check if user has required role for operation
	 */
	private boolean hasRole(String userName, UserRole requiredRole) {
		UserRole userRole = getUserRole(userName);
		return userRole == requiredRole;
	}

	public Connection getConnection() {
		return conn;
	}

	/**
	 * Start the automatic monitoring service (cancellations + late pickups)
	 */
	public void startAutoCancellationService() {
		if (autoCancellationService != null) {
			autoCancellationService.startService();
			System.out.println("✅ Auto-monitoring service started:");
			System.out.println("   - Monitoring preorder reservations (auto-cancel after 15 min)");
			System.out.println("   - Monitoring active parkings (notify late pickups after 15 min)");
		}
	}

	/**
	 * Stop the automatic monitoring service
	 */
	public void stopAutoCancellationService() {
		if (autoCancellationService != null) {
			autoCancellationService.stopService();
			System.out.println("⛔ Auto-monitoring service stopped");
		}
	}

	/**
	 * Cleanup method - call when shutting down the controller
	 */
	public void shutdown() {
		if (autoCancellationService != null) {
			autoCancellationService.shutdown();
		}
	}

	// ========== ALL YOUR EXISTING METHODS UPDATED ==========

	public String checkLogin(String userName, String userCode) {
		String qry = "SELECT UserTypeEnum FROM users WHERE UserName = ? AND User_ID = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, userName);
			stmt.setString(2, userCode);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getString("UserTypeEnum");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking login: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return "None";
	}

	/**
	 * Gets user information by userName
	 */
	public ParkingSubscriber getUserInfo(String userName) {
		String qry = "SELECT * FROM users WHERE UserName = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, userName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					ParkingSubscriber user = new ParkingSubscriber();
					user.setSubscriberID(rs.getInt("User_ID"));
					user.setFirstName(rs.getString("Name"));
					user.setPhoneNumber(rs.getString("Phone"));
					user.setEmail(rs.getString("Email"));
					user.setCarNumber(rs.getString("CarNum"));
					user.setSubscriberCode(userName);
					user.setUserType(rs.getString("UserTypeEnum"));
					return user;
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting user info: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return null;
	}

	/**
	 * Checks if reservation is possible (40% of spots must be available)
	 */
	public boolean canMakeReservation() {
		int availableSpots = getAvailableParkingSpots();
		return availableSpots >= (TOTAL_PARKING_SPOTS * RESERVATION_THRESHOLD);
	}

	/**
	 * Makes a parking reservation with specific DATE and TIME FIXED: Now properly
	 * checks for time conflicts to prevent double-booking
	 */
	public String makeReservation(String userName, String reservationDateTimeStr) {
		// Check if reservation is possible (40% rule)
		if (!canMakeReservation()) {
			return "Not enough available spots for reservation (need 40% available)";
		}
		Connection conn = DBController.getInstance().getConnection();
		try {
			// Parse the datetime string
			LocalDateTime reservationDateTime = parseDateTime(reservationDateTimeStr);

			// Validate reservation is within allowed time range (24 hours to 7 days)
			LocalDateTime now = LocalDateTime.now();
			if (reservationDateTime.isBefore(now.plusHours(24))) {
				return "Reservation must be at least 24 hours in advance";
			}
			if (reservationDateTime.isAfter(now.plusDays(7))) {
				return "Reservation cannot be more than 7 days in advance";
			}

			// Get user ID
			int userID = getUserID(userName);
			if (userID == -1) {
				return "User not found";
			}

			// Calculate end time (default 4 hours)
			LocalDateTime estimatedEndTime = reservationDateTime.plusHours(4);

			// CRITICAL FIX: Use findAvailableSpotForTimeSlot instead of
			// getAvailableParkingSpotID
			int parkingSpotID = findAvailableSpotForTimeSlot(reservationDateTime, estimatedEndTime);
			if (parkingSpotID == -1) {
				return "No parking spots available for the requested time slot";
			}

			// Create reservation in parkinginfo table with statusEnum='preorder'
			String qry = """
					INSERT INTO parkinginfo
					(ParkingSpot_ID, User_ID, Date_Of_Placing_Order, Estimated_start_time,
					 Estimated_end_time, IsOrderedEnum, IsLate, IsExtended, statusEnum)
					VALUES (?, ?, NOW(), ?, ?, 'yes', 'no', 'no', 'preorder')
					""";

			try (PreparedStatement stmt = conn.prepareStatement(qry, PreparedStatement.RETURN_GENERATED_KEYS)) {
				stmt.setInt(1, parkingSpotID);
				stmt.setInt(2, userID);
				stmt.setTimestamp(3, Timestamp.valueOf(reservationDateTime));
				stmt.setTimestamp(4, Timestamp.valueOf(estimatedEndTime));
				stmt.executeUpdate();

				// Get the generated ParkingInfo_ID (this is our reservation code)
				try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						int reservationCode = generatedKeys.getInt(1);
						System.out.println("New preorder reservation created: " + reservationCode + " for "
								+ reservationDateTime + " (15-min auto-cancel rule applies)");

						// Send email confirmation
						ParkingSubscriber user = getUserInfo(userName);
						if (user != null && user.getEmail() != null) {
							String formattedDateTime = reservationDateTime
									.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
							EmailServiceStub.sendReservationConfirmation(user.getEmail(), user.getFirstName(),
									String.valueOf(reservationCode), formattedDateTime, "Spot " + parkingSpotID);
						}

						return "Reservation confirmed for "
								+ reservationDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
								+ ". Confirmation code: " + reservationCode + ". Spot: " + parkingSpotID;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Error making reservation: " + e.getMessage());
			return "Reservation failed: " + e.getMessage();
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return "Reservation failed";
	}

	/**
	 * Handles a direct parking entry request for a given user. Checks if the user
	 * already has an active parking, if parking is full, and creates a new active
	 * parking record if possible.
	 *
	 * @param userID The ID of the user entering the parking.
	 * @return A string describing the result: success message with parking code and
	 *         spot, or an error message if parking could not be assigned.
	 */
	public String enterParking(int userID) {
		// Check if user already has active parking
		String checkActiveQry = "SELECT COUNT(*) FROM parkinginfo WHERE User_ID = ? AND statusEnum = 'active'";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement activeStmt = conn.prepareStatement(checkActiveQry)) {
			activeStmt.setInt(1, userID);
			try (ResultSet rs = activeStmt.executeQuery()) {
				if (rs.next() && rs.getInt(1) > 0) {
					return "You already have an active parking session.";
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking active parking: " + e.getMessage());
			return "Could not verify active parking.";
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		// Check if parking is full
		if (isParkingFull()) {
			return "Parking is full. Try later.";
		}

		// Find first available parking spot
		int spotID = getAvailableParkingSpotID();
		if (spotID == -1) {
			return "No parking spots available.";
		}

		// Insert new active parking record with estimated times
		String insertQry = """
				    INSERT INTO parkinginfo
				    (ParkingSpot_ID, User_ID, Actual_start_time, Estimated_start_time, Estimated_end_time,
				     IsOrderedEnum, IsLate, IsExtended, statusEnum)
				    VALUES (?, ?, NOW(), NOW(), NOW() + INTERVAL 4 HOUR, 'no', 'no', 'no', 'active')
				""";
		conn = DBController.getInstance().getConnection();
		try (PreparedStatement insertStmt = conn.prepareStatement(insertQry, PreparedStatement.RETURN_GENERATED_KEYS)) {
			insertStmt.setInt(1, spotID);
			insertStmt.setInt(2, userID);
			insertStmt.executeUpdate();

			try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
				if (generatedKeys.next()) {
					int parkingCode = generatedKeys.getInt(1);

					// Mark spot as occupied
					updateParkingSpotStatus(spotID, true);

					return "Entry successful. Parking code: " + parkingCode + ". Spot: " + spotID;
				} else {
					return "Entry failed: No parking code generated.";
				}
			}
		} catch (SQLException e) {
			System.out.println("Error handling entry: " + e.getMessage());
			return "Entry failed due to database error.";
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
	}

	/**
	 * Handles parking entry with reservation code - NOW SUPPORTS PREORDER->ACTIVE
	 */
	public String enterParkingWithReservation(int reservationCode) {
		String checkQry = """
				SELECT pi.*, u.User_ID,
				       TIMESTAMPDIFF(MINUTE, pi.Estimated_start_time, NOW()) as minutes_since_start
				FROM parkinginfo pi
				JOIN users u ON pi.User_ID = u.User_ID
				WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'preorder'
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
			stmt.setInt(1, reservationCode);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					int minutesSinceStart = rs.getInt("minutes_since_start");
					int parkingSpotID = rs.getInt("ParkingSpot_ID");

					LocalDateTime estimatedStartTime = rs.getTimestamp("Estimated_start_time").toLocalDateTime();
					LocalDateTime now = LocalDateTime.now();

					// Check if it's today
					if (!estimatedStartTime.toLocalDate().equals(now.toLocalDate())) {
						if (estimatedStartTime.isBefore(now)) {
							// Expired reservation
							cancelReservation(reservationCode);
							return "Reservation expired (wrong date).";
						} else {
							return "Reservation is for a future date.";
						}
					}

					// Check if within 15 min after reserved time
					if (minutesSinceStart > 15) {
						cancelReservation(reservationCode);
						return "Reservation expired: arrived more than 15 min late.";
					}

					// Update reservation to active and set actual start time
					String updateQry = """
							UPDATE parkinginfo
							SET statusEnum = 'active', Actual_start_time = NOW()
							WHERE ParkingInfo_ID = ?
							""";

					try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
						updateStmt.setInt(1, reservationCode);
						updateStmt.executeUpdate();

						// Mark spot as occupied
						updateParkingSpotStatus(parkingSpotID, true);

						System.out.println("Reservation " + reservationCode + " activated (preorder → active)");
						return "Entry successful! Reservation activated. Parking code: " + reservationCode + ". Spot: "
								+ parkingSpotID;
					}
				}
			}
		} catch (SQLException e) {
			System.out.println("Error handling reservation entry: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return "Invalid reservation code or reservation not in preorder status.";
	}

	/**
	 * Gets the number of available parking spots
	 */
	public int getAvailableParkingSpots() {
		String qry = "SELECT COUNT(*) as available FROM ParkingSpot WHERE isOccupied = false";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("available");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting available spots: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return 0;
	}

	/**
	 * Retrieves the active parking code for a user by their userID, sends an email
	 * notification, and returns the code as a string.
	 */
	public String sendLostParkingCode(int userID) {
		String qry = """
				    SELECT pi.ParkingInfo_ID, u.Email, u.Phone, u.Name
				    FROM parkinginfo pi
				    JOIN users u ON pi.User_ID = u.User_ID
				    WHERE u.User_ID = ? AND pi.statusEnum = 'active'
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setInt(1, userID);

			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					int parkingCode = rs.getInt("ParkingInfo_ID");
					String email = rs.getString("Email");
					String phone = rs.getString("Phone");
					String name = rs.getString("Name");

					// Send recovery email
					EmailServiceStub.sendParkingCodeRecovery(email, name, String.valueOf(parkingCode));

					return "Your active parking code is: " + parkingCode;
				}
			}
		} catch (SQLException e) {
			System.out.println("Error sending lost code: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return "No active parking session found for this user.";
	}

	/**
	 * ADD THIS NEW METHOD - Find an available spot for a specific time slot This
	 * prevents double-booking by checking for time conflicts
	 */
	private int findAvailableSpotForTimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
		String qry = """
				SELECT ps.ParkingSpot_ID
				FROM parkingspot ps
				WHERE ps.ParkingSpot_ID NOT IN (
				    SELECT DISTINCT pi.ParkingSpot_ID
				    FROM parkinginfo pi
				    WHERE pi.statusEnum IN ('preorder', 'active')
				    AND pi.ParkingSpot_ID IS NOT NULL
				    AND (
				        -- Check if times overlap
				        (pi.Estimated_start_time < ? AND pi.Estimated_end_time > ?)
				        OR
				        (pi.Estimated_start_time >= ? AND pi.Estimated_start_time < ?)
				    )
				)
				ORDER BY ps.ParkingSpot_ID
				LIMIT 1
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			// Set parameters for overlap check
			stmt.setTimestamp(1, Timestamp.valueOf(endTime)); // existing end > new start
			stmt.setTimestamp(2, Timestamp.valueOf(startTime)); // existing start < new end
			stmt.setTimestamp(3, Timestamp.valueOf(startTime)); // existing start >= new start
			stmt.setTimestamp(4, Timestamp.valueOf(endTime)); // existing start < new end

			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					int spotId = rs.getInt("ParkingSpot_ID");
					System.out.println(
							"Found available spot " + spotId + " for time slot " + startTime + " to " + endTime);
					return spotId;
				}
			}
		} catch (SQLException e) {
			System.out.println("Error finding available spot for time slot: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		System.out.println("No available spots for time slot " + startTime + " to " + endTime);
		return -1;
	}

	/**
	 * ADD THIS METHOD - Get count of available spots for a specific time slot
	 */
	public int getAvailableSpotsForTimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
		String qry = """
				SELECT COUNT(*) as available
				FROM parkingspot ps
				WHERE ps.ParkingSpot_ID NOT IN (
				    SELECT DISTINCT pi.ParkingSpot_ID
				    FROM parkinginfo pi
				    WHERE pi.statusEnum IN ('preorder', 'active')
				    AND pi.ParkingSpot_ID IS NOT NULL
				    AND (
				        -- Check if times overlap
				        (pi.Estimated_start_time < ? AND pi.Estimated_end_time > ?)
				        OR
				        (pi.Estimated_start_time >= ? AND pi.Estimated_start_time < ?)
				    )
				)
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setTimestamp(1, Timestamp.valueOf(endTime));
			stmt.setTimestamp(2, Timestamp.valueOf(startTime));
			stmt.setTimestamp(3, Timestamp.valueOf(startTime));
			stmt.setTimestamp(4, Timestamp.valueOf(endTime));

			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("available");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting available spots for time slot: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return 0;
	}

	/**
	 * Parse datetime string in various formats
	 */
	private LocalDateTime parseDateTime(String dateTimeStr) {
		try {
			// Try "YYYY-MM-DD HH:MM:SS" format first
			if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
				return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			}
			// Try "YYYY-MM-DD HH:MM" format
			else if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
				return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
			}
			// Try ISO format "YYYY-MM-DDTHH:MM"
			else if (dateTimeStr.contains("T")) {
				return LocalDateTime.parse(dateTimeStr);
			} else {
				throw new IllegalArgumentException("Unsupported datetime format: " + dateTimeStr);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(
					"Invalid datetime format: " + dateTimeStr + ". Use 'YYYY-MM-DD HH:MM' or 'YYYY-MM-DD HH:MM:SS'");
		}
	}

	/**
	 * ATTENDANT-ONLY: Register new subscriber (PDF requirement) Only attendants can
	 * register new users
	 */
	public String registerNewSubscriber(String attendantUserName, String name, String phone, String email,
			String carNumber, String userName) {
		// Verify caller is attendant
		if (!hasRole(attendantUserName, UserRole.ATTENDANT)) {
			return "ERROR: Only parking attendants can register new subscribers";
		}

		// Continue with existing registration logic
		return registerNewSubscriberInternal(name, phone, email, carNumber, userName);
	}

	/**
	 * Registers a new subscriber in the system - WITH EMAIL NOTIFICATIONS
	 */
	private String registerNewSubscriberInternal(String name, String phone, String email, String carNumber,
			String userName) {
		// Validate input
		if (name == null || name.trim().isEmpty()) {
			return "Name is required";
		}
		if (phone == null || phone.trim().isEmpty()) {
			return "Phone number is required";
		}
		if (email == null || email.trim().isEmpty()) {
			return "Email is required";
		}
		if (userName == null || userName.trim().isEmpty()) {
			return "Username is required";
		}

		// Check if username already exists
		String checkQry = "SELECT COUNT(*) FROM users WHERE UserName = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement checkStmt = conn.prepareStatement(checkQry)) {
			checkStmt.setString(1, userName);
			try (ResultSet rs = checkStmt.executeQuery()) {
				if (rs.next() && rs.getInt(1) > 0) {
					return "Username already exists. Please choose a different username.";
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking username: " + e.getMessage());
			return "Error checking username availability";
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		// Insert new subscriber
		String insertQry = "INSERT INTO users (UserName, Name, Phone, Email, CarNum, UserTypeEnum) VALUES (?, ?, ?, ?, ?, 'sub')";
		try (PreparedStatement stmt = conn.prepareStatement(insertQry, PreparedStatement.RETURN_GENERATED_KEYS)) {
			stmt.setString(1, userName);
			stmt.setString(2, name);
			stmt.setString(3, phone);
			stmt.setString(4, email);
			stmt.setString(5, carNumber);

			int rowsInserted = stmt.executeUpdate();
			if (rowsInserted > 0) {
				// Get the generated User_ID
				int userID = -1;
				try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						userID = generatedKeys.getInt(1);
					}
				}

				System.out.println("New subscriber registered: " + userName + " with User_ID: " + userID);

				// SEND EMAIL NOTIFICATIONS with User_ID
				EmailServiceStub.sendRegistrationConfirmation(email, name, userName, userID);
				EmailServiceStub.sendWelcomeMessage(email, name, userName, userID);

				return "SUCCESS:Subscriber registered successfully. Username: " + userName + ", User ID: " + userID;
			}
		} catch (SQLException e) {
			System.out.println("Registration failed: " + e.getMessage());
			return "Registration failed: " + e.getMessage();
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return "Registration failed: Unknown error";
	}

	/**
	 * Generates a unique subscriber code/username
	 */
	public String generateUniqueUsername(String baseName) {
		// Remove spaces and special characters
		String cleanName = baseName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

		// Try the clean name first
		if (isUsernameAvailable(cleanName)) {
			return cleanName;
		}

		// If taken, try with numbers
		for (int i = 1; i <= 999; i++) {
			String candidate = cleanName + i;
			if (isUsernameAvailable(candidate)) {
				return candidate;
			}
		}

		// Fallback to random number
		return cleanName + System.currentTimeMillis() % 10000;
	}

	/**
	 * Handles parking exit - NOW SUPPORTS FINISHING RESERVATIONS
	 */
	public String exitParking(String parkingCodeStr) {
		Connection conn = DBController.getInstance().getConnection();

		try {
			int parkingCode = Integer.parseInt(parkingCodeStr);
			String qry = """
					SELECT pi.*, ps.ParkingSpot_ID
					FROM parkinginfo pi
					JOIN parkingspot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID
					WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
					""";
			try (PreparedStatement stmt = conn.prepareStatement(qry)) {
				stmt.setInt(1, parkingCode);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						int parkingInfoID = rs.getInt("ParkingInfo_ID");
						int spotID = rs.getInt("ParkingSpot_ID");
						Timestamp estimatedEndTime = rs.getTimestamp("Estimated_end_time");
						int userID = rs.getInt("User_ID");
						String orderType = rs.getString("IsOrderedEnum");

						LocalDateTime now = LocalDateTime.now();
						LocalDateTime estimatedEnd = estimatedEndTime.toLocalDateTime();

						// Check if parking exceeded estimated time
						boolean isLate = now.isAfter(estimatedEnd);

						// Update parking info with exit time and finish status
						String updateQry = """
								UPDATE parkinginfo
								SET Actual_end_time = ?, IsLate = ?, statusEnum = 'finished'
								WHERE ParkingInfo_ID = ?
								""";

						try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
							updateStmt.setTimestamp(1, Timestamp.valueOf(now));
							updateStmt.setString(2, isLate ? "yes" : "no");
							updateStmt.setInt(3, parkingInfoID);
							updateStmt.executeUpdate();

							// Free the parking spot
							updateParkingSpotStatus(spotID, false);

							if (isLate) {
								sendLateExitNotification(userID);
								return "Exit successful. You were late - please arrive on time for future reservations";
							}

							return "Exit successful. Thank you for using ParkB!";
						}
					}
				}
			}
		} catch (NumberFormatException e) {
			return "Invalid parking code format";
		} catch (SQLException e) {
			System.out.println("Error handling exit: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return "Invalid parking code or already exited";
	}

	/**
	 * Sends lost parking code to user
	 */
	public String sendLostParkingCode(String userName) {
		String qry = """
				SELECT pi.ParkingInfo_ID, u.Email, u.Phone, u.Name
				FROM parkinginfo pi
				JOIN users u ON pi.User_ID = u.User_ID
				WHERE u.UserName = ? AND pi.statusEnum = 'active'
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, userName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					int parkingCode = rs.getInt("ParkingInfo_ID");
					String email = rs.getString("Email");
					String phone = rs.getString("Phone");
					String name = rs.getString("Name");

					// SEND EMAIL NOTIFICATION
					EmailServiceStub.sendParkingCodeRecovery(email, name, String.valueOf(parkingCode));

					return String.valueOf(parkingCode);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error sending lost code: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return "No active parking session found";
	}

	/**
	 * Gets parking history for a user
	 */
	public ArrayList<ParkingOrder> getParkingHistory(String userName) {
		ArrayList<ParkingOrder> history = new ArrayList<>();
		String qry = """
				SELECT pi.*, ps.ParkingSpot_ID
				FROM parkinginfo pi
				JOIN users u ON pi.User_ID = u.User_ID
				JOIN parkingspot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID
				WHERE u.UserName = ?
				ORDER BY pi.Date_Of_Placing_Order DESC
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
					order.setSpotNumber("Spot " + rs.getInt("ParkingSpot_ID"));

					// Convert Timestamps to LocalDateTime
					Timestamp actualStart = rs.getTimestamp("Actual_start_time");
					Timestamp actualEnd = rs.getTimestamp("Actual_end_time");
					Timestamp estimatedEnd = rs.getTimestamp("Estimated_end_time");
					Timestamp estimatedStart = rs.getTimestamp("Estimated_start_time");

					if (actualStart != null) {
						order.setEntryTime(actualStart.toLocalDateTime());
					}
					if (actualEnd != null) {
						order.setExitTime(actualEnd.toLocalDateTime());
					}
					if (estimatedEnd != null) {
						order.setExpectedExitTime(estimatedEnd.toLocalDateTime());
					}
					if (estimatedStart != null) {
						order.setEstimatedStartTime(estimatedStart.toLocalDateTime());
					}

					order.setLate("yes".equals(rs.getString("IsLate")));
					order.setExtended("yes".equals(rs.getString("IsExtended")));
					order.setStatus(rs.getString("statusEnum"));

					history.add(order);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting parking history: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return history;
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
					order.setSpotNumber("Spot " + rs.getInt("ParkingSpot_ID"));

					// Convert Timestamps to LocalDateTime
					Timestamp actualStart = rs.getTimestamp("Actual_start_time");
					Timestamp estimatedEnd = rs.getTimestamp("Estimated_end_time");

					if (actualStart != null) {
						order.setEntryTime(actualStart.toLocalDateTime());
					}
					if (estimatedEnd != null) {
						order.setExpectedExitTime(estimatedEnd.toLocalDateTime());
					}

					order.setStatus("active");
					activeParkings.add(order);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting active parkings: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return activeParkings;
	}

	/**
	 * Cancels a reservation
	 */
	public String cancelReservation(int reservationCode) {
		// Get user info before cancelling for email notification
		String getUserQry = """
				SELECT u.Email, u.Name
				FROM parkinginfo pi
				JOIN users u ON pi.User_ID = u.User_ID
				WHERE pi.ParkingInfo_ID = ?
				""";
		String userEmail = null;
		String userName = null;
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
			stmt.setInt(1, reservationCode);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					userEmail = rs.getString("Email");
					userName = rs.getString("Name");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting user info for cancellation: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		String qry = """
				UPDATE parkinginfo
				SET statusEnum = 'cancelled'
				WHERE ParkingInfo_ID = ? AND statusEnum IN ('preorder', 'active')
				""";
		conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setInt(1, reservationCode);
			int rowsUpdated = stmt.executeUpdate();

			if (rowsUpdated > 0) {
				// Also free up the spot if it was assigned
				freeSpotForReservation(reservationCode);

				// SEND EMAIL NOTIFICATION
				if (userEmail != null && userName != null) {
					EmailServiceStub.sendReservationCancelled(userEmail, userName, String.valueOf(reservationCode));
				}

				return "Reservation cancelled successfully";
			}
		} catch (SQLException e) {
			System.out.println("Error cancelling reservation: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return "Reservation not found or already cancelled/finished";
	}

	/**
	 * Logs out a user (for future use if needed)
	 */
	public void logoutUser(String userName) {
		System.out.println("User logged out: " + userName);
	}

	/**
	 * Initializes parking spots if they don't exist
	 */
	public void initializeParkingSpots() {
		Connection conn = DBController.getInstance().getConnection();

		try {
			// Check if spots already exist
			String checkQry = "SELECT COUNT(*) FROM ParkingSpot";
			try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next() && rs.getInt(1) == 0) {
						// Initialize parking spots - AUTO_INCREMENT will handle ParkingSpot_ID
						String insertQry = "INSERT INTO ParkingSpot (isOccupied) VALUES (false)";
						try (PreparedStatement insertStmt = conn.prepareStatement(insertQry)) {
							for (int i = 1; i <= TOTAL_PARKING_SPOTS; i++) {
								insertStmt.executeUpdate();
							}
						}
						System.out.println("Successfully initialized " + TOTAL_PARKING_SPOTS
								+ " parking spots with AUTO_INCREMENT");
					} else {
						System.out.println("Parking spots already exist: " + rs.getInt(1) + " spots found");
					}
				}
			}
		} catch (SQLException e) {
			System.out.println("Error initializing parking spots: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		if (autoCancellationService != null && !autoCancellationService.isRunning()) {
			startAutoCancellationService();
		}
	}

	// ========== SMART PARKING FEATURES ==========
	
	/**
	 * Get available 15-minute time slots for a specific date and preferred time
	 */
	public List<TimeSlot> getAvailableTimeSlots(LocalDate date, LocalTime preferredTime) {
		List<TimeSlot> timeSlots = new ArrayList<>();
		
		try {
			if (!dateHasValidBookingWindow(date)) {
				return timeSlots;
			}
			
			LocalDateTime preferredDateTime = LocalDateTime.of(date, preferredTime);
			LocalDateTime startRange = preferredDateTime.minusHours(DISPLAY_WINDOW_HOURS);
			LocalDateTime endRange = preferredDateTime.plusHours(DISPLAY_WINDOW_HOURS);
			
			LocalDateTime currentSlot = startRange;
			while (!currentSlot.isAfter(endRange)) {
				LocalDateTime bookingEnd = currentSlot.plusHours(STANDARD_BOOKING_HOURS);
				boolean hasValidWindow = hasValidFourHourWindow(currentSlot, bookingEnd);
				int availableSpots = countAvailableSpotsForWindow(currentSlot, bookingEnd);
				boolean meetsFortyPercent = availableSpots >= (TOTAL_PARKING_SPOTS * AVAILABILITY_THRESHOLD);
				
				timeSlots.add(new TimeSlot(
					currentSlot, 
					hasValidWindow && meetsFortyPercent, 
					availableSpots,
					meetsFortyPercent
				));
				
				currentSlot = currentSlot.plusMinutes(TIME_SLOT_MINUTES);
			}
			
		} catch (Exception e) {
			System.out.println("Error getting available time slots: " + e.getMessage());
		}
		
		return timeSlots;
	}
	
	/**
	 * Make a pre-booking reservation with 15-minute precision and enhanced validation
	 */
	public String makePreBooking(String userName, String dateTimeStr) {
		try {
			LocalDateTime bookingStart = parseSmartDateTime(dateTimeStr);
			LocalDateTime bookingEnd = bookingStart.plusHours(STANDARD_BOOKING_HOURS);
			
			if (bookingStart.getMinute() % TIME_SLOT_MINUTES != 0) {
				return "Booking time must be in 15-minute intervals (00, 15, 30, 45)";
			}
			
			LocalDateTime now = LocalDateTime.now();
			if (bookingStart.isBefore(now.plusHours(24))) {
				return "Pre-booking must be at least 24 hours in advance";
			}
			if (bookingStart.isAfter(now.plusDays(7))) {
				return "Pre-booking cannot be more than 7 days in advance";
			}
			
			if (!hasValidFourHourWindow(bookingStart, bookingEnd)) {
				return "No available 4-hour window with required capacity at selected time";
			}
			
			int optimalSpotId = findOptimalSpotForPreBooking(bookingStart, bookingEnd);
			if (optimalSpotId == -1) {
				return "No optimal parking spot available for selected time";
			}
			
			int userID = getUserID(userName);
			if (userID == -1) {
				return "User not found";
			}
			
			return createSmartReservation(userID, optimalSpotId, bookingStart, bookingEnd, "pre-booking");
			
		} catch (Exception e) {
			System.out.println("Error making pre-booking: " + e.getMessage());
			return "Pre-booking failed: " + e.getMessage();
		}
	}
	
	/**
	 * Handle spontaneous parking entry with dynamic hour allocation
	 */
	public String enterSpontaneousParking(String userName) {
		LocalDateTime now = LocalDateTime.now();
		
		try {
			SpotAllocation allocation = findOptimalSpontaneousAllocation(now);
			if (allocation == null) {
				return "No parking spots available for spontaneous parking (minimum 2 hours required)";
			}
			
			int userID = getUserID(userName);
			if (userID == -1) {
				return "Invalid user";
			}
			
			// Check if user already has active parking
			String checkActiveQry = "SELECT COUNT(*) FROM parkinginfo WHERE User_ID = ? AND statusEnum = 'active'";
			Connection conn = DBController.getInstance().getConnection();
			try (PreparedStatement activeStmt = conn.prepareStatement(checkActiveQry)) {
				activeStmt.setInt(1, userID);
				try (ResultSet rs = activeStmt.executeQuery()) {
					if (rs.next() && rs.getInt(1) > 0) {
						return "You already have an active parking session";
					}
				}
			} finally {
				DBController.getInstance().releaseConnection(conn);
			}
			
			LocalDateTime sessionEnd = now.plusHours(allocation.allocatedHours);
			
			String insertQuery = """
				INSERT INTO parkinginfo 
				(ParkingSpot_ID, User_ID, Actual_start_time, Estimated_start_time, 
				 Estimated_end_time, IsOrderedEnum, IsLate, IsExtended, statusEnum) 
				VALUES (?, ?, NOW(), NOW(), ?, 'no', 'no', 'no', 'active')
				""";

			conn = DBController.getInstance().getConnection();
			try (PreparedStatement stmt = conn.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
				stmt.setInt(1, allocation.spotId);
				stmt.setInt(2, userID);
				stmt.setTimestamp(3, Timestamp.valueOf(sessionEnd));
				stmt.executeUpdate();
				
				try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						int parkingCode = generatedKeys.getInt(1);
						
						updateParkingSpotStatus(allocation.spotId, true);
						
						return String.format("Spontaneous parking successful! Code: %d, Spot: %d, Duration: %d hours%s",
										   parkingCode, allocation.spotId, allocation.allocatedHours,
										   allocation.hasEightHourWindow ? " (8+ hour window)" : "");
					}
				}
			} finally {
				DBController.getInstance().releaseConnection(conn);
			}
			
		} catch (Exception e) {
			System.out.println("Error in spontaneous parking: " + e.getMessage());
			return "Spontaneous parking failed: " + e.getMessage();
		}
		
		return "Spontaneous parking failed";
	}
	
	/**
	 * Enhanced parking extension request - can only be requested during last hour
	 */
	public String requestSmartParkingExtension(String parkingCodeStr) {
		Connection conn = DBController.getInstance().getConnection();

		try {
			int parkingCode = Integer.parseInt(parkingCodeStr);
			
			String sessionQuery = """
				SELECT pi.*, ps.ParkingSpot_ID 
				FROM parkinginfo pi 
				JOIN parkingspot ps ON pi.ParkingSpot_ID = ps.ParkingSpot_ID 
				WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
				""";
			
			try (PreparedStatement stmt = conn.prepareStatement(sessionQuery)) {
				stmt.setInt(1, parkingCode);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						int spotId = rs.getInt("ParkingSpot_ID");
						Timestamp estimatedEndTime = rs.getTimestamp("Estimated_end_time");
						int userID = rs.getInt("User_ID");
						
						LocalDateTime currentEndTime = estimatedEndTime.toLocalDateTime();
						LocalDateTime now = LocalDateTime.now();
						
						// Check if already extended
						String isExtended = rs.getString("IsExtended");
						if ("yes".equalsIgnoreCase(isExtended)) {
							return "Extension already granted for this parking session";
						}
						
						// Check if within last hour
						if (now.isBefore(currentEndTime.minusHours(1))) {
							return "Extensions can only be requested during the last hour of parking";
						}
						
						if (now.isAfter(currentEndTime)) {
							return "Parking session has already ended";
						}
						
						int maxExtensionHours = findMaximumSmartExtension(spotId, currentEndTime);
						if (maxExtensionHours < MINIMUM_EXTENSION_HOURS) {
							return "No extension available - spot not free for minimum required time";
						}
						
						LocalDateTime newEndTime = currentEndTime.plusHours(maxExtensionHours);
						
						String updateQuery = """
							UPDATE parkinginfo 
							SET Estimated_end_time = ?, IsExtended = 'yes' 
							WHERE ParkingInfo_ID = ?
							""";
						
						try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
							updateStmt.setTimestamp(1, Timestamp.valueOf(newEndTime));
							updateStmt.setInt(2, parkingCode);
							updateStmt.executeUpdate();
							
							// Send email notification
							ParkingSubscriber user = getUserInfo(getUserNameByID(userID));
							if (user != null && user.getEmail() != null) {
								EmailServiceStub.sendExtensionConfirmation(user.getEmail(), user.getFirstName(), 
									parkingCodeStr, maxExtensionHours, newEndTime.toString());
							}
							
							return String.format("Smart extension successful! Parking extended by %d hours until %s",
											   maxExtensionHours, newEndTime.format(DateTimeFormatter.ofPattern("HH:mm")));
						}
					}
				}
			}
			
		} catch (NumberFormatException e) {
			return "Invalid parking code format";
		} catch (Exception e) {
			System.out.println("Error requesting smart extension: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		
		return "Invalid parking code or parking session not found";
	}
	
	/**
	 * Get comprehensive parking system status
	 */
	public String getSmartSystemStatus() {
		try {
			int totalSpots = TOTAL_PARKING_SPOTS;
			int occupiedSpots = getCurrentlyOccupiedSpots();
			int availableSpots = totalSpots - occupiedSpots;
			int activeReservations = getActiveReservationsCount();
			
			double availabilityPercent = (double) availableSpots / totalSpots * 100;
			String statusLevel = availabilityPercent >= 40 ? "GOOD" : 
								availabilityPercent >= 20 ? "LIMITED" : "CRITICAL";
			
			return String.format("""
				Smart Parking System Status:
				Total Spots: %d
				Occupied: %d
				Available: %d (%.1f%%)
				Active Reservations: %d
				Status: %s
				Reservations Allowed: %s""",
				totalSpots, occupiedSpots, availableSpots, availabilityPercent,
				activeReservations, statusLevel, availabilityPercent >= 40 ? "YES" : "NO");
				               
		} catch (Exception e) {
			return "Error getting system status: " + e.getMessage();
		}
	}

	// ========== HELPER METHODS ==========

	private int getUserID(String userName) {
		String qry = "SELECT User_ID FROM users WHERE UserName = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, userName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("User_ID");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting user ID: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return -1;
	}

	private int getAvailableParkingSpotID() {
		String qry = "SELECT ParkingSpot_ID FROM ParkingSpot WHERE isOccupied = false LIMIT 1";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("ParkingSpot_ID");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting available spot ID: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return -1;
	}

	private void updateParkingSpotStatus(int spotID, boolean isOccupied) {
		String qry = "UPDATE ParkingSpot SET isOccupied = ? WHERE ParkingSpot_ID = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setBoolean(1, isOccupied);
			stmt.setInt(2, spotID);
			stmt.executeUpdate();
		} catch (SQLException e) {
			System.out.println("Error updating parking spot status: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
	}

	/**
	 * Send late exit notification
	 */
	private void sendLateExitNotification(int userID) {
		String qry = "SELECT Email, Phone, Name FROM users WHERE User_ID = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setInt(1, userID);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					String email = rs.getString("Email");
					String phone = rs.getString("Phone");
					String name = rs.getString("Name");

					// SEND EMAIL NOTIFICATION
					EmailServiceStub.sendLatePickupNotification(email, name);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error sending late notification: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
	}

	private boolean isUsernameAvailable(String userName) {
		String checkQry = "SELECT COUNT(*) FROM users WHERE UserName = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(checkQry)) {
			stmt.setString(1, userName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1) == 0;
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking username availability: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return false;
	}

	private void freeSpotForReservation(int reservationCode) {
		String query = """
				UPDATE parkingspot ps
				JOIN parkinginfo pi ON ps.ParkingSpot_ID = pi.ParkingSpot_ID
				SET ps.isOccupied = FALSE
				WHERE pi.ParkingInfo_ID = ?
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setInt(1, reservationCode);
			stmt.executeUpdate();
		} catch (SQLException e) {
			System.out.println("Error freeing spot for reservation: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
	}

	/**
	 * Cancel reservation
	 */
	public String cancelReservation(String subscriberUserName, int reservationCode) {
		return cancelReservationInternal(reservationCode, "User requested cancellation");
	}

	/**
	 * Internal cancellation method (used by auto-cancel and manual cancel)
	 */
	private String cancelReservationInternal(int reservationCode, String reason) {
		// Get reservation info first for email notification
		String getUserQry = """
				SELECT u.Email, u.Name, pi.statusEnum, pi.ParkingSpot_ID
				FROM parkinginfo pi
				JOIN users u ON pi.User_ID = u.User_ID
				WHERE pi.ParkingInfo_ID = ?
				""";

		String userEmail = null;
		String userName = null;
		String currentStatus = null;
		Integer spotId = null;
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
			stmt.setInt(1, reservationCode);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					userEmail = rs.getString("Email");
					userName = rs.getString("Name");
					currentStatus = rs.getString("statusEnum");
					spotId = rs.getObject("ParkingSpot_ID", Integer.class);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting reservation info for cancellation: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		// Update reservation status to cancelled
		String qry = """
				UPDATE parkinginfo
				SET statusEnum = 'cancelled'
				WHERE ParkingInfo_ID = ? AND statusEnum IN ('preorder', 'active')
				""";

		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setInt(1, reservationCode);
			int rowsUpdated = stmt.executeUpdate();

			if (rowsUpdated > 0) {
				// Free up the spot if it was assigned
				if (spotId != null) {
					updateParkingSpotStatus(spotId, false);
				}

				// Send email notification
				if (userEmail != null && userName != null) {
					EmailServiceStub.sendReservationCancelled(userEmail, userName, String.valueOf(reservationCode));
				}

				System.out.println("Reservation " + reservationCode + " cancelled (" + currentStatus
						+ " → cancelled) - " + reason);
				return "Reservation cancelled successfully";
			}
		} catch (SQLException e) {
			System.out.println("Error cancelling reservation: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return "Reservation not found or already cancelled/finished";
	}

	public ParkingSubscriber getSubscriberByName(String name) {
		ParkingSubscriber subscriber = null;
		String query = "SELECT * FROM users WHERE Name = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setString(1, name);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				subscriber = new ParkingSubscriber(rs.getInt("User_ID"), rs.getString("UserName"), rs.getString("Name"),
						rs.getString("Phone"), rs.getString("Email"), rs.getString("CarNum"),
						rs.getString("UserTypeEnum"));
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return subscriber;
	}

	public List<ParkingSubscriber> getAllSubscribers() {
		List<ParkingSubscriber> list = new ArrayList<>();
		String query = "SELECT * FROM users WHERE UserTypeEnum = 'sub'";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(query); ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				ParkingSubscriber subscriber = new ParkingSubscriber(rs.getInt("User_ID"), rs.getString("UserName"), // ←
																														// subscriberCode
						rs.getString("Name"), // ← firstName
						rs.getString("Phone"), rs.getString("Email"), rs.getString("CarNum"),
						rs.getString("UserTypeEnum"));
				list.add(subscriber);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return list;
	}

	/**
	 * Extends parking time
	 */
	public String extendParkingTime(String parkingCodeStr, int additionalHours) {
		if (additionalHours < 1 || additionalHours > 4) {
			return "Can only extend parking by 1–4 hours.";
		}
		Connection conn = DBController.getInstance().getConnection();

		try {
			int parkingCode = Integer.parseInt(parkingCodeStr);

			// Get current parking info and user data
			String getUserQry = """
					    SELECT pi.*, u.Email, u.Name
					    FROM parkinginfo pi
					    JOIN users u ON pi.User_ID = u.User_ID
					    WHERE pi.ParkingInfo_ID = ? AND pi.statusEnum = 'active'
					""";
			try (PreparedStatement stmt = conn.prepareStatement(getUserQry)) {
				stmt.setInt(1, parkingCode);

				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						String isExtended = rs.getString("IsExtended");

						// ❌ Block if already extended once
						if ("yes".equalsIgnoreCase(isExtended)) {
							return "Cannot extend again: You already extended this active parking session.";
						}

						Timestamp currentEstimatedEnd = rs.getTimestamp("Estimated_end_time");
						String userEmail = rs.getString("Email");
						String userName = rs.getString("Name");
						int parkingSpotId = rs.getInt("ParkingSpot_ID");

						LocalDateTime newEstimatedEnd = currentEstimatedEnd.toLocalDateTime()
								.plusHours(additionalHours);

						// Check for conflicting reservation
						String conflictCheckQry = """
								    SELECT 1 FROM parkinginfo
								    WHERE ParkingSpot_ID = ?
								      AND statusEnum = 'preorder'
								      AND Estimated_start_time > ?
								      AND Estimated_start_time < ?
								""";

						try (PreparedStatement checkStmt = conn.prepareStatement(conflictCheckQry)) {
							checkStmt.setInt(1, parkingSpotId);
							checkStmt.setTimestamp(2, currentEstimatedEnd);
							checkStmt.setTimestamp(3, Timestamp.valueOf(newEstimatedEnd));

							try (ResultSet conflictRs = checkStmt.executeQuery()) {
								if (conflictRs.next()) {
									return "Cannot extend parking: A reservation is scheduled during the extension period.";
								}
							}
						}

						// ✅ Update estimated end time and mark as extended
						String updateQry = """
								    UPDATE parkinginfo
								    SET Estimated_end_time = ?, IsExtended = 'yes'
								    WHERE ParkingInfo_ID = ?
								""";

						try (PreparedStatement updateStmt = conn.prepareStatement(updateQry)) {
							updateStmt.setTimestamp(1, Timestamp.valueOf(newEstimatedEnd));
							updateStmt.setInt(2, parkingCode);
							updateStmt.executeUpdate();

							try {
								if (userEmail != null && userName != null) {
									EmailServiceStub.sendExtensionConfirmation(userEmail, userName, parkingCodeStr,
											additionalHours, newEstimatedEnd.toString());
								}
							} catch (Exception e) {
								System.out.println("Email sending failed: " + e.getMessage());
							}

							return "Parking time extended by " + additionalHours + " hours until " + newEstimatedEnd;
						}
					}
				}
			}
		} catch (NumberFormatException e) {
			return "Invalid parking code format.";
		} catch (SQLException e) {
			System.out.println("Error extending parking time: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return "Invalid parking code or parking session not active.";
	}

	/**
	 * Updates subscriber information
	 */
	public String updateSubscriberInfo(String updateData) {
		// Format: userName,phone,email,carNumber
		String[] data = updateData.split(",", -1); // -1 keeps empty
		if (data.length != 4) {
			return "Invalid update data format";
		}

		String userName = data[0];
		String phone = data[1];
		String email = data[2];
		String carNumber = data[3];

		StringBuilder queryBuilder = new StringBuilder("UPDATE users SET ");
		List<String> fields = new ArrayList<>();
		List<String> values = new ArrayList<>();

		if (!phone.isEmpty()) {
			fields.add("Phone = ?");
			values.add(phone);
		}
		if (!email.isEmpty()) {
			fields.add("Email = ?");
			values.add(email);
		}
		if (!carNumber.isEmpty()) {
			fields.add("CarNum = ?");
			values.add(carNumber);
		}

		if (fields.isEmpty()) {
			return "No changes to update.";
		}

		queryBuilder.append(String.join(", ", fields));
		queryBuilder.append(" WHERE UserName = ?");
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
			for (int i = 0; i < values.size(); i++) {
				stmt.setString(i + 1, values.get(i));
			}
			stmt.setString(values.size() + 1, userName);

			int rowsUpdated = stmt.executeUpdate();
			if (rowsUpdated > 0) {
				return "Subscriber information updated successfully";
			}
		} catch (SQLException e) {
			System.out.println("Error updating subscriber info: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}

		return "Failed to update subscriber information";
	}

	/**
	 * Checks whether a user with the given user ID exists in the database.
	 *
	 * @param userID The user ID to check for.
	 * @return true if a user with the given ID exists; false otherwise.
	 */
	public boolean doesUserIDExist(int userID) {
		String qry = "SELECT COUNT(*) FROM users WHERE User_ID = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setInt(1, userID);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1) > 0;
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking user ID: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return false;
	}

	/**
	 * Checks whether a user with the given username exists in the database.
	 *
	 * @param username The username to check for.
	 * @return true if the username exists; false otherwise.
	 */
	public boolean doesUsernameExist(String username) {
		String qry = "SELECT COUNT(*) FROM users WHERE UserName = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, username);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1) > 0;
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking username: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return false;
	}

	public String getNameByUsernameAndUserID(String username, int userID) {
		String qry = "SELECT Name FROM users WHERE UserName = ? AND User_ID = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setString(1, username);
			stmt.setInt(2, userID);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					System.out.println(rs.getString("Name"));
					return rs.getString("Name");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting name by username and userID: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return null;
	}

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
			System.out.println("Error getting name by user ID: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return null;
	}

	public boolean isParkingFull() {
		String qry = "SELECT COUNT(*) FROM ParkingSpot WHERE isOccupied = false";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					int availableSpots = rs.getInt(1);
					System.out.println("Available spots: " + availableSpots);
					return availableSpots <= 0; // explicit: true if full
				}
			}
		} catch (SQLException e) {
			System.out.println("Error checking parking availability: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return true; // assume full on DB error
	}

	/**
	 * Handles car retrieval given a ParkingInfo_ID. It sets statusEnum to
	 * 'retrieved', sets Actual_end_time to now, and frees up the parking spot
	 * (isOccupied = 0).
	 */
	public String retrieveCarByCode(int parkingInfoID) {
		System.out.println("[DEBUG] retrieveCarByCode called with ParkingInfo_ID: " + parkingInfoID);

		String selectQry = """
				    SELECT ParkingSpot_ID
				    FROM parkinginfo
				    WHERE ParkingInfo_ID = ? AND statusEnum = 'active'
				""";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement selectStmt = conn.prepareStatement(selectQry)) {
			selectStmt.setInt(1, parkingInfoID);

			try (ResultSet rs = selectStmt.executeQuery()) {
				if (rs.next()) {
					int parkingSpotID = rs.getInt("ParkingSpot_ID");
					System.out.println("[DEBUG] Found active parking: Spot ID = " + parkingSpotID);

					// Update parkinginfo
					String updateParkingInfo = """
							    UPDATE parkinginfo
							    SET Actual_end_time = NOW(), statusEnum = 'finished'
							    WHERE ParkingInfo_ID = ?
							""";
					try (PreparedStatement updateInfoStmt = conn.prepareStatement(updateParkingInfo)) {
						updateInfoStmt.setInt(1, parkingInfoID);
						int rowsUpdated = updateInfoStmt.executeUpdate();
						System.out.println("[DEBUG] Updated parkinginfo rows: " + rowsUpdated);
					}

					// Update parking spot
					String updateSpot = """
							    UPDATE parkingspot
							    SET isOccupied = 0
							    WHERE ParkingSpot_ID = ?
							""";
					try (PreparedStatement updateSpotStmt = conn.prepareStatement(updateSpot)) {
						updateSpotStmt.setInt(1, parkingSpotID);
						int spotRows = updateSpotStmt.executeUpdate();
						System.out.println("[DEBUG] Updated parkingspot rows: " + spotRows);
					}

					return "Car retrieved successfully from spot " + parkingSpotID;
				} else {
					System.out.println("[DEBUG] No active parking found for ParkingInfo_ID: " + parkingInfoID);
					return "No active parking session found for this code.";
				}
			}

		} catch (SQLException e) {
			System.out.println("Error retrieving car: " + e.getMessage());
			e.printStackTrace();
			return "Error retrieving car.";
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
	}
	
	// ========== SMART PARKING HELPER METHODS ==========
	
	/**
	 * Check if a date has any valid 4-hour booking windows
	 */
	private boolean dateHasValidBookingWindow(LocalDate date) {
		try {
			LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.of(0, 0));
			LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.of(23, 45));
			
			LocalDateTime currentTime = dayStart;
			while (!currentTime.isAfter(dayEnd.minusHours(STANDARD_BOOKING_HOURS))) {
				LocalDateTime windowEnd = currentTime.plusHours(STANDARD_BOOKING_HOURS);
				if (hasValidFourHourWindow(currentTime, windowEnd)) {
					return true;
				}
				currentTime = currentTime.plusMinutes(TIME_SLOT_MINUTES);
			}
		} catch (Exception e) {
			System.out.println("Error checking date validity: " + e.getMessage());
		}
		return false;
	}
	
	/**
	 * Check if a 4-hour window meets the 40% availability threshold
	 */
	private boolean hasValidFourHourWindow(LocalDateTime startTime, LocalDateTime endTime) {
		try {
			int availableSpots = countAvailableSpotsForWindow(startTime, endTime);
			return availableSpots >= (TOTAL_PARKING_SPOTS * AVAILABILITY_THRESHOLD);
		} catch (Exception e) {
			System.out.println("Error checking four-hour window: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Count available spots for a specific time window
	 */
	private int countAvailableSpotsForWindow(LocalDateTime startTime, LocalDateTime endTime) {
		try {
			int reservedSpots = countReservationOverlaps(startTime, endTime);
			return Math.max(0, TOTAL_PARKING_SPOTS - reservedSpots);
		} catch (Exception e) {
			System.out.println("Error counting available spots: " + e.getMessage());
			return 0;
		}
	}
	
	/**
	 * Find optimal spot for pre-booking
	 */
	private int findOptimalSpotForPreBooking(LocalDateTime bookingStart, LocalDateTime bookingEnd) {
		try {
			List<Integer> availableSpots = getAllAvailableSpots(bookingStart, bookingEnd);
			
			if (availableSpots.isEmpty()) {
				return -1;
			}
			
			// Return first available spot (simple allocation)
			return availableSpots.get(0);
			
		} catch (Exception e) {
			System.out.println("Error finding optimal spot: " + e.getMessage());
			return -1;
		}
	}
	
	/**
	 * Find optimal allocation for spontaneous parking
	 */
	private SpotAllocation findOptimalSpontaneousAllocation(LocalDateTime startTime) {
		try {
			for (int hours = STANDARD_BOOKING_HOURS; hours >= MINIMUM_SPONTANEOUS_HOURS; hours--) {
				LocalDateTime endTime = startTime.plusHours(hours);
				List<Integer> availableSpots = getAllAvailableSpots(startTime, endTime);
				
				if (!availableSpots.isEmpty()) {
					return new SpotAllocation(availableSpots.get(0), hours, hours >= PREFERRED_WINDOW_HOURS);
				}
			}
			
			return null;
			
		} catch (Exception e) {
			System.out.println("Error finding spontaneous allocation: " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Get all available spots for a time period
	 */
	private List<Integer> getAllAvailableSpots(LocalDateTime startTime, LocalDateTime endTime) throws SQLException {
		List<Integer> availableSpots = new ArrayList<>();
		
		String spotsQuery = "SELECT ParkingSpot_ID FROM ParkingSpot WHERE isOccupied = false ORDER BY ParkingSpot_ID";
		Connection conn = DBController.getInstance().getConnection();

		try (PreparedStatement stmt = conn.prepareStatement(spotsQuery)) {
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					int spotId = rs.getInt("ParkingSpot_ID");
					if (isSpotAvailableForPeriod(spotId, startTime, endTime)) {
						availableSpots.add(spotId);
					}
				}
			}
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		
		return availableSpots;
	}
	
	/**
	 * Check if spot is available for a specific period
	 */
	private boolean isSpotAvailableForPeriod(int spotId, LocalDateTime startTime, LocalDateTime endTime) throws SQLException {
		String conflictQuery = """
			SELECT COUNT(*) FROM parkinginfo 
			WHERE ParkingSpot_ID = ? 
			AND statusEnum IN ('preorder', 'active')
			AND NOT (Estimated_end_time <= ? OR Estimated_start_time >= ?)
			""";
		Connection conn = DBController.getInstance().getConnection();

		try (PreparedStatement stmt = conn.prepareStatement(conflictQuery)) {
			stmt.setInt(1, spotId);
			stmt.setTimestamp(2, Timestamp.valueOf(startTime));
			stmt.setTimestamp(3, Timestamp.valueOf(endTime));
			
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1) == 0;
				}
			}
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		
		return false;
	}
	
	/**
	 * Find maximum extension hours available
	 */
	private int findMaximumSmartExtension(int spotId, LocalDateTime currentEndTime) {
		try {
			for (int hours = MAXIMUM_EXTENSION_HOURS; hours >= MINIMUM_EXTENSION_HOURS; hours--) {
				LocalDateTime testEndTime = currentEndTime.plusHours(hours);
				if (isSpotAvailableForPeriod(spotId, currentEndTime, testEndTime)) {
					return hours;
				}
			}
		} catch (Exception e) {
			System.out.println("Error finding maximum extension: " + e.getMessage());
		}
		return 0;
	}
	
	/**
	 * Parse datetime string for smart features
	 */
	private LocalDateTime parseSmartDateTime(String dateTimeStr) {
		try {
			if (dateTimeStr.contains("T")) {
				return LocalDateTime.parse(dateTimeStr);
			} else if (dateTimeStr.contains(" ")) {
				if (dateTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
					return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
				} else {
					return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
				}
			} else {
				throw new IllegalArgumentException("Unsupported datetime format: " + dateTimeStr);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid datetime format: " + dateTimeStr + ". Use 'YYYY-MM-DD HH:MM' or 'YYYY-MM-DDTHH:MM'");
		}
	}
	
	/**
	 * Create smart reservation with enhanced features
	 */
	private String createSmartReservation(int userID, int spotId, LocalDateTime startTime, LocalDateTime endTime, String type) throws SQLException {
		String insertQuery = """
			INSERT INTO parkinginfo 
			(ParkingSpot_ID, User_ID, Date_Of_Placing_Order, Estimated_start_time, 
			 Estimated_end_time, IsOrderedEnum, IsLate, IsExtended, statusEnum) 
			VALUES (?, ?, NOW(), ?, ?, 'yes', 'no', 'no', 'preorder')
			""";
		Connection conn = DBController.getInstance().getConnection();

		try (PreparedStatement stmt = conn.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
			stmt.setInt(1, spotId);
			stmt.setInt(2, userID);
			stmt.setTimestamp(3, Timestamp.valueOf(startTime));
			stmt.setTimestamp(4, Timestamp.valueOf(endTime));
			stmt.executeUpdate();
			
			try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
				if (generatedKeys.next()) {
					int reservationCode = generatedKeys.getInt(1);
					
					// Send email notification
					String userName = getUserNameByID(userID);
					ParkingSubscriber user = getUserInfo(userName);
					if (user != null && user.getEmail() != null) {
						String formattedDateTime = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
						EmailServiceStub.sendReservationConfirmation(user.getEmail(), user.getFirstName(),
								String.valueOf(reservationCode), formattedDateTime, "Spot " + spotId);
					}
					
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
					return String.format("%s successful! Code: %d, Spot: %d, Time: %s to %s",
									   type.substring(0, 1).toUpperCase() + type.substring(1),
									   reservationCode, spotId, 
									   startTime.format(formatter), endTime.format(formatter));
				}
			}
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		
		return "Reservation creation failed";
	}
	
	/**
	 * Count overlapping reservations for a time period
	 */
	private int countReservationOverlaps(LocalDateTime startTime, LocalDateTime endTime) throws SQLException {
		String query = """
			SELECT COUNT(DISTINCT ParkingSpot_ID) 
			FROM parkinginfo 
			WHERE ParkingSpot_ID IS NOT NULL 
			AND statusEnum IN ('preorder', 'active')
			AND NOT (Estimated_end_time <= ? OR Estimated_start_time >= ?)
			""";
		Connection conn = DBController.getInstance().getConnection();

		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setTimestamp(1, Timestamp.valueOf(startTime));
			stmt.setTimestamp(2, Timestamp.valueOf(endTime));
			
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		
		return 0;
	}
	
	/**
	 * Get currently occupied spots count
	 */
	private int getCurrentlyOccupiedSpots() throws SQLException {
		String query = "SELECT COUNT(*) FROM ParkingSpot WHERE isOccupied = true";
		Connection conn = DBController.getInstance().getConnection();

		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return 0;
	}
	
	/**
	 * Get active reservations count
	 */
	private int getActiveReservationsCount() {
		String query = "SELECT COUNT(*) FROM parkinginfo WHERE statusEnum = 'preorder'";
		Connection conn = DBController.getInstance().getConnection();
		
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting active reservations count: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return 0;
	}
	
	/**
	 * Get username by user ID
	 */
	private String getUserNameByID(int userID) {
		String qry = "SELECT UserName FROM users WHERE User_ID = ?";
		Connection conn = DBController.getInstance().getConnection();
		try (PreparedStatement stmt = conn.prepareStatement(qry)) {
			stmt.setInt(1, userID);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getString("UserName");
				}
			}
		} catch (SQLException e) {
			System.out.println("Error getting username by ID: " + e.getMessage());
		} finally {
			DBController.getInstance().releaseConnection(conn);
		}
		return null;
	}
}
