import java.io.File;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OMSweb_FullAutonomousPipeline {

    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        // Run with normal browser or headless; let's test headless first to verify execution, then run visible if needed
        options.addArguments("--headless=new");

        // Download folder
        String sampleDir = "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files";
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", sampleDir);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1500);

            // 1. Auto-login
            System.out.println("[STEP 1] Logging in as Testadmin...");
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

            // 2. Navigate to File Manager / Upload Data
            System.out.println("[STEP 2] Navigating to Upload Data / File Manager...");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2000);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            File cashFile = new File(sampleDir + "\\increment_cash.xml");
            File stockFile = new File(sampleDir + "\\increment_stock.xml");
            File clientFile = new File(sampleDir + "\\increment_active_clients.xml");

            // 3. Upload INCREMENT CASH FILE
            System.out.println("\n[STEP 3] Uploading Incremental Cash XML File...");
            uploadFileViaModal(driver, js, "Cash", "increment_cash_xml_upload", cashFile);
            Thread.sleep(3000);

            // 4. Upload INCREMENT STOCK FILE
            System.out.println("\n[STEP 4] Uploading Incremental Stock XML File...");
            uploadFileViaModal(driver, js, "Stock", "increment_stock_xml_upload", stockFile);
            Thread.sleep(3000);

            // 5. Upload INCREMENT ACTIVE CLIENT FILE
            System.out.println("\n[STEP 5] Uploading Incremental Active Client XML File...");
            uploadFileViaModal(driver, js, "Client", "increment_active_clients_xml_upload", clientFile);
            Thread.sleep(3000);

            // 6. Export Client Commission File
            System.out.println("\n[STEP 6] Triggering Client Commission File Generation...");
            js.executeScript(
                "if (typeof generate_file === 'function') {\n" +
                "    generate_file('/broker_it/generate_report?file_type=Commission');\n" +
                "} else {\n" +
                "    var commLink = document.querySelector('a[onclick*=\"Commission\"]');\n" +
                "    if (commLink) commLink.click();\n" +
                "}"
            );
            Thread.sleep(3000);

            // 7. Export XML Trade File (All)
            System.out.println("\n[STEP 7] Exporting XML Trade File (All)...");
            js.executeScript(
                "var link = document.querySelector('a[href*=\"download_trade_file?exchange=default\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(3000);

            // 8. Refresh and scrape File Manager Table
            System.out.println("\n[STEP 8] Refreshing File Manager Table...");
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(3000);

            List<?> tableRows = (List<?>) js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 10).map(function(r) {\n" +
                "    var cells = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    return cells.join(' | ');\n" +
                "});"
            );

            System.out.println("\n==========================================================================");
            System.out.println("            FILE MANAGER TABLE - TOP LIVE RECORDS SCORING                ");
            System.out.println("==========================================================================");
            if (tableRows != null) {
                int i = 1;
                for (Object row : tableRows) {
                    System.out.println(String.format("Row %2d: %s", i++, row));
                }
            }

            // Capture screenshot
            File ss = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File dest = new File("C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\file_manager_live_result.png");
            ss.renameTo(dest);
            System.out.println("\nScreenshot saved to: " + dest.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    private static void uploadFileViaModal(WebDriver driver, JavascriptExecutor js, String fileTypeParam, String inputId, File file) {
        try {
            // Close any existing modal
            js.executeScript(
                "var m = document.getElementById('main-page-modal');\n" +
                "if (m && window.jQuery) window.jQuery(m).modal('hide');"
            );
            Thread.sleep(1000);

            // Click upload dropdown and specific submenu
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=" + fileTypeParam + "\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            // Locate file input
            WebElement input = driver.findElement(By.id(inputId));
            input.sendKeys(file.getAbsolutePath());
            Thread.sleep(800);

            // Trigger submit
            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );

            // Wait for response
            Thread.sleep(4000);

            // Check flash message
            Object flash = js.executeScript(
                "var f = document.querySelector('.alert, .flash-message, #flash-messages, .jconfirm-content');\n" +
                "return f ? f.innerText.replace(/\\s+/g, ' ').trim() : 'No flash message';"
            );
            System.out.println("  -> [" + fileTypeParam + "] Flash notification: " + flash);

        } catch (Exception e) {
            System.out.println("  -> [" + fileTypeParam + "] Upload failed: " + e.getMessage());
        }
    }
}
