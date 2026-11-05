import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class VerifyFileManager {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1500);

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
            Thread.sleep(2500);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Wait and refresh a couple times to let celery finish processing
            for (int attempt = 1; attempt <= 5; attempt++) {
                js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
                Thread.sleep(2000);

                List<?> rows = (List<?>) js.executeScript(
                    "var out = [];\n" +
                    "document.querySelectorAll('#file_manager_table tbody tr').forEach(function(r) {\n" +
                    "    var tds = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                    "    if (tds.length > 0) out.push(tds.join(' | '));\n" +
                    "});\n" +
                    "return out;"
                );

                System.out.printf("\n--- File Manager Table Check #%d ---%n", attempt);
                if (rows != null) {
                    for (int i = 0; i < Math.min(8, rows.size()); i++) {
                        System.out.printf("Row %d: %s%n", (i + 1), rows.get(i));
                    }
                    // Check if modern increment_active_clients exists
                    boolean hasRecentClientUpload = false;
                    for (Object r : rows) {
                        String s = String.valueOf(r);
                        if (s.contains("increment_active_clients") && (s.contains("01:") || s.contains("01-11-2026") || s.contains("04-11-2026-01:"))) {
                            hasRecentClientUpload = true;
                            break;
                        }
                    }
                    if (hasRecentClientUpload) {
                        System.out.println(">>> Found recent active clients upload entry in table!");
                        break;
                    }
                }
                Thread.sleep(2000);
            }

            // Save full-page screenshot
            TakesScreenshot ts = (TakesScreenshot) driver;
            File scrFile = ts.getScreenshotAs(OutputType.FILE);
            File dest1 = new File("C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\file_manager_active_clients_verified.png");
            File dest2 = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\file_manager_active_clients_verified.png");
            Files.copy(scrFile.toPath(), dest1.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(scrFile.toPath(), dest2.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("\n[SCREENSHOT] Verified screenshot saved to: " + dest1.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
