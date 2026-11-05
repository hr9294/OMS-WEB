import java.sql.*;

public class CheckDatabasesList {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://10.20.8.29:5445/marketdb";
        try (Connection conn = DriverManager.getConnection(url, "marketdata", "pass4321")) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false;")) {
                while (rs.next()) {
                    System.out.println("DB: " + rs.getString(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
