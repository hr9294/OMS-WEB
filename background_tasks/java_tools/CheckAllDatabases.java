import java.sql.*;
import java.util.*;

public class CheckAllDatabases {
    public static void main(String[] args) {
        int[] ports = {5445, 5432};
        String[] hosts = {"192.168.155.22", "10.20.8.29", "127.0.0.1"};
        String[] users = {"postgres", "marketdata", "oms", "admin", "devoms"};
        String[] passes = {"pass4321", "postgres", "12345", "root", "admin", "password"};

        for (String host : hosts) {
            for (int port : ports) {
                for (String user : users) {
                    for (String pass : passes) {
                        String url = "jdbc:postgresql://" + host + ":" + port + "/postgres";
                        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                            System.out.println("FOUND DB CONNECTION: " + url + " as " + user);
                            try (Statement stmt = conn.createStatement();
                                 ResultSet rs = stmt.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false;")) {
                                System.out.println("Databases on " + host + ":" + port + ":");
                                while (rs.next()) {
                                    System.out.println("  * " + rs.getString(1));
                                }
                            }
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        System.out.println("Scan complete.");
    }
}
