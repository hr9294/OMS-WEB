import java.sql.*;

public class CheckUsers {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://192.168.155.22:5445/marketdb";
        try (Connection conn = DriverManager.getConnection(url, "marketdata", "pass4321")) {
            System.out.println("Querying pg_user on 192.168.155.22:5445...");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT usename FROM pg_user;")) {
                while (rs.next()) {
                    System.out.println("User: " + rs.getString(1));
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
