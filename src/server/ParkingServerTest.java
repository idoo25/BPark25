package server;

/**
 * Simple test to verify the ParkingServer singleton pattern works correctly
 */
public class ParkingServerTest {
    public static void main(String[] args) {
        // Test singleton behavior
        ParkingServer server1 = ParkingServer.getInstance(5555);
        ParkingServer server2 = ParkingServer.getInstance(6666); // Different port should be ignored
        
        System.out.println("Server1 instance: " + server1.hashCode());
        System.out.println("Server2 instance: " + server2.hashCode());
        System.out.println("Are they the same instance? " + (server1 == server2));
        System.out.println("Server port: " + server1.getPort());
    }
}