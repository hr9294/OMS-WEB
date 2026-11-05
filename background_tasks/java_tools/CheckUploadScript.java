import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class CheckUploadScript {
    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://devoms.quantbd.com/static/js/page-js/broker_it/upload.js");
            System.out.println(driver.getPageSource());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
