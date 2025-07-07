package services;

/**
 * Temporary stub for EmailService to enable compilation during refactoring
 */
public class EmailServiceStub {
    
    public static void sendReservationConfirmation(String email, String name, String details, String code, String additionalInfo) {
        // Stub implementation - just log
        System.out.println("Email would be sent to: " + email + " for reservation confirmation");
    }
    
    public static void sendParkingCodeRecovery(String email, String name, String code) {
        System.out.println("Email would be sent to: " + email + " for parking code recovery");
    }
    
    public static void sendRegistrationConfirmation(String email, String name, String userName, int userID) {
        System.out.println("Email would be sent to: " + email + " for registration confirmation");
    }
    
    public static void sendWelcomeMessage(String email, String name, String userName, int userID) {
        System.out.println("Email would be sent to: " + email + " for welcome message");
    }
    
    public static void sendReservationCancelled(String email, String name, String code) {
        System.out.println("Email would be sent to: " + email + " for reservation cancellation");
    }
    
    public static void sendExtensionConfirmation(String email, String name, String details, int duration, String time) {
        System.out.println("Email would be sent to: " + email + " for extension confirmation");
    }
    
    public static void sendLatePickupNotification(String email, String name) {
        System.out.println("Email would be sent to: " + email + " for late pickup notification");
    }
}