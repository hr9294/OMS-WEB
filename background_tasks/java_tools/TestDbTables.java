import java.sql.*;
import java.util.*;

public class TestDbTables {
    public static void main(String[] args) {
        String[] hosts = {"192.168.155.22", "10.20.8.29", "localhost", "127.0.0.1"};
        int port = 5445;
        String user = "marketdata";
        String pass = "pass4321";

        for (String host : hosts) {
            String url = "jdbc:postgresql://" + host + ":" + port + "/marketdb";
            System.out.println("Trying connection to: " + url);
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                System.out.println("CONNECTED to " + host + "!");

                // List databases
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false;")) {
                    System.out.println("Databases:");
                    while (rs.next()) {
                        System.out.println(" - " + rs.getString(1));
                    }
                }

                // List tables in current db
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog', 'information_schema') ORDER BY table_name;")) {
                    System.out.println("Tables in marketdb:");
                    while (rs.next()) {
                        System.out.println(" - " + rs.getString("table_schema") + "." + rs.getString("table_name"));
                    }
                }
                return;
            } catch (Exception e) {
                System.out.println("Failed to connect to " + host + ": " + e.getMessage());
            }
        }
    }
}
