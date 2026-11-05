import java.sql.*;

public class CheckPort5433 {
    public static void main(String[] args) {
        String[] hosts = {"192.168.155.22", "10.20.8.29"};
        String[] users = {"postgres", "marketdata", "readdata", "oms", "admin", "devoms"};
        String[] passes = {"pass4321", "postgres", "12345", "root", "admin", "password"};
        String[] dbs = {"postgres", "oms", "omsdb", "marketdb", "brokerdb", "clientdb"};

        for (String host : hosts) {
            for (String user : users) {
                for (String pass : passes) {
                    for (String db : dbs) {
                        String url = "jdbc:postgresql://" + host + ":5433/" + db;
                        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                            System.out.println("CONNECTED to " + url + " as " + user);
                            try (Statement stmt = conn.createStatement();
                                 ResultSet rs = stmt.executeQuery("SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog', 'information_schema') ORDER BY table_name;")) {
                                while (rs.next()) {
                                    System.out.println("  Table: " + rs.getString(1) + "." + rs.getString(2));
                                }
                            }
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        System.out.println("Finished test 5433.");
    }
}
