package services;

/**
 * Temporary stub for EmailService to enable compilation during refactoring
 */
public class EmailServiceStub {
    
    public static void sendReservationConfirmation(String email, String name, String details, String code, String additionalInfo) {
        // Stub implementation - just log
    }
    
    public static void sendParkingCodeRecovery(String email, String name, String code) {
    }
    
    public static void sendRegistrationConfirmation(String email, String name, String userName, int userID) {
    }
    
    public static void sendWelcomeMessage(String email, String name, String userName, int userID) {
    }
    
    public static void sendReservationCancelled(String email, String name, String code) {
    }
    
    public static void sendExtensionConfirmation(String email, String name, String details, int duration, String time) {
    }
    
    public static void sendLatePickupNotification(String email, String name) {
    }
}