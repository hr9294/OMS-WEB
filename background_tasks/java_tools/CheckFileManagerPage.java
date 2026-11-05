import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class CheckFileManagerPage {
    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new");
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

            // Navigate to file_manager
            driver.get("https://devoms.quantbd.com/broker_it/file_manager");
            Thread.sleep(2000);

            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object tableHtml = js.executeScript(
                "var t = document.querySelector('table');\n" +
                "return t ? t.outerHTML : document.body.innerHTML.substring(0, 3000);"
            );
            System.out.println("=== FILE MANAGER CONTENT ===");
            System.out.println(tableHtml);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
