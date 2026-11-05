import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class DualSyncUploadRunner {

    public static void main(String[] args) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File masterFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\all_active_clients_31864.xml");
        File uploadFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\All_Active_Clients_DualSync_" + timestamp + ".xml");

        try {
            Files.copy(masterFile.toPath(), uploadFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.printf("[PREPARE] Created upload copy: %s (%.2f MB)%n", uploadFile.getName(), uploadFile.length() / (1024.0 * 1024.0));
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to copy master file: " + e.getMessage());
            return;
        }

        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

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

            System.out.println("[NAV] Navigating to Upload Data page via OMSweb_UploadData...");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            boolean navSuccess = OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            System.out.println("[NAV] Arrived on Upload Data page: " + navSuccess);
            Thread.sleep(2000);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Dismiss modal
            js.executeScript(
                "var m = document.getElementById('main-page-modal');\n" +
                "if (m && window.jQuery) window.jQuery(m).modal('hide');"
            );
            Thread.sleep(1000);

            // Open Client upload modal
            System.out.println("[MODAL] Opening Client upload modal...");
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Client\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            WebElement input = driver.findElement(By.id("increment_active_clients_xml_upload"));
            input.sendKeys(uploadFile.getAbsolutePath());
            System.out.println("[INPUT] File set: " + uploadFile.getAbsolutePath());
            Thread.sleep(1000);

            // Trigger Upload Button
            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );
            System.out.println("[SUBMIT] Upload triggered. Monitoring response...");
            Thread.sleep(4000);

            Object flash = js.executeScript(
                "var f = document.querySelector('.alert, .flash-message, #flash-messages');\n" +
                "return f ? f.innerText.replace(/\\s+/g, ' ').trim() : '';"
            );
            System.out.println("[UPLOAD NOTIFICATION] " + flash);

            // Wait for backend processing and check file manager table
            System.out.println("\n[POLL] Waiting for file processing in File Manager table...");
            for (int i = 1; i <= 8; i++) {
                Thread.sleep(4000);
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

                if (rows != null && !rows.isEmpty()) {
                    String topRow = String.valueOf(rows.get(0));
                    System.out.printf("  Poll %d: %s%n", i, topRow);
                    if (topRow.contains(uploadFile.getName()) || topRow.contains("All_Active_Clients_DualSync")) {
                        if (topRow.contains("Completed") || topRow.contains("uploaded")) {
                            System.out.println(">>> File successfully processed and visible in front end!");
                            break;
                        }
                    }
                }
            }

            // Scrape top 6 rows
            List<?> finalRows = (List<?>) js.executeScript(
                "var out = [];\n" +
                "document.querySelectorAll('#file_manager_table tbody tr').forEach(function(r) {\n" +
                "    var tds = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    if (tds.length > 0) out.push(tds.join(' | '));\n" +
                "});\n" +
                "return out.slice(0, 6);"
            );

            System.out.println("\n=== TOP ROWS IN FILE MANAGER AFTER DUAL TABLE SYNC UPLOAD ===");
            if (finalRows != null) {
                int rIdx = 1;
                for (Object r : finalRows) {
                    System.out.printf("Row %d: %s%n", rIdx++, r);
                }
            }

            // Capture screenshot
            TakesScreenshot ts = (TakesScreenshot) driver;
            File scrFile = ts.getScreenshotAs(OutputType.FILE);
            File destArtifact = new File("C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\file_manager_dual_sync_verified.png");
            Files.copy(scrFile.toPath(), destArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("\n[SCREENSHOT] Screenshot saved to: " + destArtifact.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
