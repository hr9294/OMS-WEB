import java.sql.*;

public class CheckPostgresDb {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://192.168.155.22:5445/postgres";
        try (Connection conn = DriverManager.getConnection(url, "marketdata", "pass4321")) {
            System.out.println("Connected to postgres db!");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog', 'information_schema');")) {
                while (rs.next()) {
                    System.out.println(" - " + rs.getString(1) + "." + rs.getString(2));
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
