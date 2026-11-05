import java.net.*;

public class PortScan {
    public static void main(String[] args) {
        String[] hosts = {"192.168.155.22", "10.20.8.29"};
        int[] ports = {5432, 5433, 5445, 3306, 27017, 6379, 8000, 8080, 5000};

        for (String host : hosts) {
            for (int port : ports) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, port), 500);
                    System.out.println("OPEN PORT: " + host + ":" + port);
                } catch (Exception ignored) {}
            }
        }
        System.out.println("Scan finished.");
    }
}
