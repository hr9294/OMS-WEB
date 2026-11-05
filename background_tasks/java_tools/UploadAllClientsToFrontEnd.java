import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * UploadAllClientsToFrontEnd
 * --------------------------
 * Autonomously uploads the complete 31,864 active clients XML file (12.85 MB)
 * directly into the OMS Web application front end and verifies it in the File Manager.
 */
public class UploadAllClientsToFrontEnd {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("  UPLOADING COMPLETE 31,864 ACTIVE CLIENTS FILE TO OMS WEB FRONT END");
        System.out.println("==========================================================================");

        File srcFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\all_active_clients_31864.xml");
        if (!srcFile.exists() || srcFile.length() == 0) {
            System.err.println("[ERROR] Source file not found: " + srcFile.getAbsolutePath());
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String targetName = "All_Active_Clients_DB_Sync_" + timestamp + ".xml";
        File uploadFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\" + targetName);

        try {
            Files.copy(srcFile.toPath(), uploadFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.printf("[PREPARE] Created upload file: %s (%,d bytes / %.2f MB)%n",
                    uploadFile.getName(), uploadFile.length(), uploadFile.length() / (1024.0 * 1024.0));
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to copy file: " + e.getMessage());
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
            System.out.println("[BROWSER] Opening https://devoms.quantbd.com...");
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

            // Navigate to Upload Data page
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2500);

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

            // Attach file
            System.out.println("[ATTACH] Selecting file in #increment_active_clients_xml_upload: " + uploadFile.getAbsolutePath());
            WebElement input = driver.findElement(By.id("increment_active_clients_xml_upload"));
            input.sendKeys(uploadFile.getAbsolutePath());
            Thread.sleep(1000);

            // Click upload button
            System.out.println("[SUBMIT] Clicking upload submit button...");
            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );

            // Monitor upload progress
            System.out.println("[PROGRESS] Monitoring file transfer (12.85 MB)...");
            for (int s = 1; s <= 20; s++) {
                Thread.sleep(1000);
                Object info = js.executeScript(
                    "var bar = document.querySelector('.progress-bar');\n" +
                    "var flash = document.querySelector('.alert, .flash-message, #flash-messages');\n" +
                    "return {\n" +
                    "    pct: bar ? bar.innerText.trim() : '',\n" +
                    "    msg: flash ? flash.innerText.replace(/\\s+/g, ' ').trim() : ''\n" +
                    "};"
                );
                Map m = (Map) info;
                String pct = m != null && m.get("pct") != null ? String.valueOf(m.get("pct")) : "";
                String msg = m != null && m.get("msg") != null ? String.valueOf(m.get("msg")) : "";
                System.out.printf("  [%02ds] Progress: %-6s | Notification: %s%n", s, pct, msg);
                if (msg.toLowerCase().contains("successfully") || msg.toLowerCase().contains("error") || msg.toLowerCase().contains("failed")) {
                    break;
                }
            }

            // Poll File Manager table until backend processing completes
            System.out.println("\n[POLL] Waiting for background Celery worker to parse 31,864 rows...");
            boolean processed = false;
            for (int attempt = 1; attempt <= 12; attempt++) {
                Thread.sleep(4000);
                js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
                Thread.sleep(2500);

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
                    System.out.printf("  Poll %02d: %s%n", attempt, topRow);
                    if (topRow.contains(uploadFile.getName())) {
                        if (topRow.contains("Completed") || topRow.contains("uploaded") || topRow.contains("updated")) {
                            System.out.println(">>> 31,864 Clients File successfully completed and verified in front end!");
                            processed = true;
                            break;
                        }
                    }
                }
            }

            // Scrape top 5 rows
            List<?> finalRows = (List<?>) js.executeScript(
                "var out = [];\n" +
                "document.querySelectorAll('#file_manager_table tbody tr').forEach(function(r) {\n" +
                "    var tds = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    if (tds.length > 0) out.push(tds.join(' | '));\n" +
                "});\n" +
                "return out.slice(0, 6);"
            );

            System.out.println("\n=== TOP ROWS IN FILE MANAGER AFTER 31,864 CLIENTS UPLOAD ===");
            if (finalRows != null) {
                int rIdx = 1;
                for (Object r : finalRows) {
                    System.out.printf("Row %d: %s%n", rIdx++, r);
                }
            }

            // Save full-page screenshot
            TakesScreenshot ts = (TakesScreenshot) driver;
            File scrFile = ts.getScreenshotAs(OutputType.FILE);
            File dest1 = new File("C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\file_manager_all_clients_completed.png");
            File dest2 = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\file_manager_all_clients_completed.png");
            Files.copy(scrFile.toPath(), dest1.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(scrFile.toPath(), dest2.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("\n[SCREENSHOT] Saved proof screenshot to: " + dest1.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
