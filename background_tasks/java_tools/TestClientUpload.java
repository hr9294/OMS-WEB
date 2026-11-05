import java.io.File;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestClientUpload {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new");

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
            Thread.sleep(2000);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Open Client Modal
            System.out.println("Opening Client modal...");
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Client\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            File clientFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\increment_active_clients.xml");
            System.out.println("Attaching file: " + clientFile.getAbsolutePath() + " (exists: " + clientFile.exists() + ")");

            WebElement input = driver.findElement(By.id("increment_active_clients_xml_upload"));
            input.sendKeys(clientFile.getAbsolutePath());
            Thread.sleep(1000);

            // Trigger upload
            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "btn.classList.remove('disabled');\n" +
                "btn.click();"
            );
            System.out.println("Clicked upload button for Client!");
            Thread.sleep(4000);

            // Also trigger Client Commission File generation
            System.out.println("Triggering Commission export...");
            js.executeScript("if (typeof generate_file === 'function') generate_file('/broker_it/generate_report?file_type=Commission');");
            Thread.sleep(3000);

            // Refresh page
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(2000);

            List<?> tableRows = (List<?>) js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 8).map(function(r) {\n" +
                "    var cells = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    return cells.join(' | ');\n" +
                "});"
            );

            System.out.println("=== TOP TABLE ROWS AFTER ALL UPLOADS & EXPORT ===");
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
