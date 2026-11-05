import java.sql.*;

public class CheckSecondaryDb {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://10.20.8.29:5445/marketdb";
        try (Connection conn = DriverManager.getConnection(url, "marketdata", "pass4321")) {
            System.out.println("Connected to 10.20.8.29:5445/marketdb!");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;")) {
                while (rs.next()) {
                    System.out.println(" - " + rs.getString(1));
                }
            }
        } catch (Exception e) {
            System.out.println("10.20.8.29 error: " + e.getMessage());
        }
    }
}
