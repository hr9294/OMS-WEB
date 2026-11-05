import java.sql.*;

public class TestLocalPostgres {
    public static void main(String[] args) {
        String[] users = {"postgres", "admin", "marketdata", "devoms"};
        String[] passes = {"12345", "root", "postgres", "password", "pass4321", "admin"};

        for (String user : users) {
            for (String pass : passes) {
                String url = "jdbc:postgresql://127.0.0.1:5432/postgres";
                try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                    System.out.println("SUCCESS! Connected as " + user + " with pass: " + pass);

                    // List databases
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false;")) {
                        System.out.println("Databases:");
                        while (rs.next()) {
                            System.out.println("  * " + rs.getString(1));
                        }
                    }
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        System.out.println("Could not connect with standard test credentials.");
    }
}
