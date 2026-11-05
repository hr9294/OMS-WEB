import java.sql.*;

public class CheckSchemas {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://192.168.155.22:5445/marketdb";
        try (Connection conn = DriverManager.getConnection(url, "marketdata", "pass4321")) {
            System.out.println("Connected to marketdb!");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT schema_name FROM information_schema.schemata;")) {
                while (rs.next()) {
                    System.out.println("Schema: " + rs.getString(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
