import java.sql.*;

public class CheckTableDetailsView {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://192.168.155.22:5445/marketdb";
        try (Connection conn = DriverManager.getConnection(url, "marketdata", "pass4321")) {
            System.out.println("Querying table_details_view...");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM table_details_view LIMIT 10;")) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                for (int i = 1; i <= cols; i++) System.out.print(meta.getColumnName(i) + "\t");
                System.out.println();
                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) System.out.print(rs.getString(i) + "\t");
                    System.out.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
