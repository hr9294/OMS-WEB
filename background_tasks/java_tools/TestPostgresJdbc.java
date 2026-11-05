import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestPostgresJdbc {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://192.168.155.22:5443/qtraderdb";
        String user = "qtraderdata";
        String pass = "pass4321";

        System.out.println("Connecting to PostgreSQL: " + url);
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            System.out.println("PostgreSQL JDBC Connection SUCCESSFUL!");

            String sql = "SELECT \"ClientCode\", \"DealerID\", \"BOID\", \"WithNetAdjustment\", \"Name\", \"Address\", \"ICNo\", \"AccountType\", \"ShortSellingAllowed\", email, phone " +
                         "FROM public.clients_active ORDER BY id DESC LIMIT 5;";
            ResultSet rs = stmt.executeQuery(sql);
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("[%d] Code: %s | Dealer: %s | BOID: %s | Name: %s | Type: %s%n",
                        count,
                        rs.getString("ClientCode"),
                        rs.getString("DealerID"),
                        rs.getString("BOID"),
                        rs.getString("Name"),
                        rs.getString("AccountType"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
