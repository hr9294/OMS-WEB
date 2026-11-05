import java.io.File;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OMSweb_LiveFileTransferTest {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        // We can run in headless for quick verification, or normal
        options.addArguments("--headless=new");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1500);

            // Login
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

            // 1. Test uploading Increment Cash
            File cashFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\increment_cash.xml");
            System.out.println("Uploading cash file: " + cashFile.getAbsolutePath() + " (exists: " + cashFile.exists() + ")");

            // Open Cash Modal
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Cash\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            // Make sure the file input is ready
            WebElement cashInput = driver.findElement(By.id("increment_cash_xml_upload"));
            cashInput.sendKeys(cashFile.getAbsolutePath());
            Thread.sleep(1000);

            // Enable and trigger upload button
            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );
            System.out.println("Triggered upload button for Cash!");
            Thread.sleep(5000);

            // Check flash messages or table
            Object flashMsg = js.executeScript(
                "var flash = document.querySelector('.alert, .flash-message, #flash-messages, .jconfirm-content');\n" +
                "return flash ? flash.innerText : 'No flash message';"
            );
            System.out.println("Flash Message: " + flashMsg);

            // Inspect top 3 rows of file_manager_table
            Object rows = js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 5).map(function(r) { return r.innerText.replace(/\\s+/g, ' ').trim(); });"
            );
            System.out.println("Top File Manager Table Rows:\n" + rows);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
