import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;

public class CheckTableNow {
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

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2000);

            JavascriptExecutor js = (JavascriptExecutor) driver;
            List<?> tableRows = (List<?>) js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 15).map(function(r) {\n" +
                "    var cells = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    return cells.join(' | ');\n" +
                "});"
            );

            System.out.println("=== CURRENT TABLE ROWS ===");
            int i = 1;
            for (Object row : tableRows) {
                System.out.println(String.format("Row %2d: %s", i++, row));
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
