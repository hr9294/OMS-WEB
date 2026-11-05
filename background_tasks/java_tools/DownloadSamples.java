import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class DownloadSamples {
    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new");

        // Set download directory
        Map<String, Object> prefs = new HashMap<>();
        String downloadPath = "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files";
        prefs.put("download.default_directory", downloadPath);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1000);
            driver.findElement(By.id("username")).sendKeys("Testadmin");
            driver.findElement(By.id("password")).sendKeys("12345");
            driver.findElement(By.xpath("//button[normalize-space()='Login']")).click();
            Thread.sleep(1500);

            List<WebElement> continueBtns = driver.findElements(By.id("continue_to_force_login"));
            if (!continueBtns.isEmpty()) {
                continueBtns.get(0).click();
                Thread.sleep(1500);
            }
            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);

            String[] sampleUrls = {
                "https://devoms.quantbd.com/broker_it/download?file_name=increment_active_clients_sample.xml",
                "https://devoms.quantbd.com/broker_it/download?file_name=active_clients_sample.xml",
                "https://devoms.quantbd.com/broker_it/download?file_name=cash_sample.xml",
                "https://devoms.quantbd.com/broker_it/download?file_name=increment_positions_sample.xml",
                "https://devoms.quantbd.com/broker_it/download?file_name=positions_sample.xml"
            };

            for (String url : sampleUrls) {
                System.out.println("Downloading: " + url);
                driver.get(url);
                Thread.sleep(1500);
            }
            System.out.println("Downloads triggered!");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
