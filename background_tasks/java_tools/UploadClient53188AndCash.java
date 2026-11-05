import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class UploadClient53188AndCash {

    public static void main(String[] args) {
        String baseDir = "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\";
        
        // Find latest files for 53188
        File dir = new File(baseDir);
        File[] activeFiles = dir.listFiles((d, name) -> name.startsWith("Increment_Active_Client_53188_") && name.endsWith(".xml"));
        File[] cashFiles = dir.listFiles((d, name) -> name.startsWith("Increment_Cash_53188_") && name.endsWith(".xml"));

        if (activeFiles == null || activeFiles.length == 0 || cashFiles == null || cashFiles.length == 0) {
            System.err.println("Could not find generated XML files for 53188!");
            return;
        }

        // Pick latest
        File activeFile = activeFiles[activeFiles.length - 1];
        File cashFile = cashFiles[cashFiles.length - 1];

        System.out.println("Active Client File: " + activeFile.getAbsolutePath());
        System.out.println("Cash File:          " + cashFile.getAbsolutePath());

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

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2500);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // ==========================================
            // STEP 1: UPLOAD ACTIVE CLIENT XML FOR 53188
            // ==========================================
            System.out.println("\n>>> [STEP 1] Uploading Active Client XML for 53188...");
            js.executeScript("var m = document.getElementById('main-page-modal'); if (m && window.jQuery) window.jQuery(m).modal('hide');");
            Thread.sleep(1000);

            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Client\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            WebElement clientInput = driver.findElement(By.id("increment_active_clients_xml_upload"));
            clientInput.sendKeys(activeFile.getAbsolutePath());
            Thread.sleep(1000);

            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );
            Thread.sleep(4000);

            Object flashClient = js.executeScript("var f = document.querySelector('.alert, .flash-message, #flash-messages'); return f ? f.innerText.replace(/\\s+/g, ' ').trim() : '';");
            System.out.println("Active Client Upload Result: " + flashClient);

            // ==========================================
            // STEP 2: UPLOAD CASH XML FOR 53188 (10,000,000)
            // ==========================================
            System.out.println("\n>>> [STEP 2] Uploading Cash XML for 53188 (Cash: 10,000,000)...");
            js.executeScript("var m = document.getElementById('main-page-modal'); if (m && window.jQuery) window.jQuery(m).modal('hide');");
            Thread.sleep(1000);

            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Cash\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            WebElement cashInput = driver.findElement(By.id("increment_cash_xml_upload"));
            cashInput.sendKeys(cashFile.getAbsolutePath());
            Thread.sleep(1000);

            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );
            Thread.sleep(4000);

            Object flashCash = js.executeScript("var f = document.querySelector('.alert, .flash-message, #flash-messages'); return f ? f.innerText.replace(/\\s+/g, ' ').trim() : '';");
            System.out.println("Cash Upload Result: " + flashCash);

            // Wait 6 seconds for backend queue processing
            System.out.println("\nWaiting 6 seconds for backend asynchronous file queues...");
            Thread.sleep(6000);

            // Refresh page to view latest file manager status
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(2000);

            // ==========================================
            // STEP 3: SCRAPE FILE MANAGER TABLE
            // ==========================================
            Object tableRows = js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 5).map(function(r) {\n" +
                "    return Array.from(r.querySelectorAll('td')).map(td => td.innerText.trim());\n" +
                "});"
            );
            System.out.println("\n--- Latest File Manager Table in Web App ---");
            if (tableRows instanceof List) {
                for (Object row : (List<?>) tableRows) {
                    System.out.println("  " + row);
                }
            }

            // Save Screenshot
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File destFile = new File("C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\file_manager_53188_upload.png");
            scrFile.renameTo(destFile);
            System.out.println("\nSaved Screenshot: " + destFile.getAbsolutePath());

            // ==========================================
            // STEP 4: VERIFY VIA LIVE WEB API
            // ==========================================
            Object res53188 = js.executeScript(
                "var out = 'none';\n" +
                "$.ajax({\n" +
                "    url: '/shared/getclientdata/?cln_code=53188',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = JSON.stringify(data); }\n" +
                "});\n" +
                "return out;"
            );
            System.out.println("\n--- Live /shared/getclientdata/?cln_code=53188 Result ---");
            System.out.println(res53188);

            // Direct Database checks
            try (Connection conn = DriverManager.getConnection("jdbc:postgresql://192.168.155.22:5443/qtraderdb", "qtraderdata", "pass4321");
                 Statement stmt = conn.createStatement()) {
                
                System.out.println("\n--- Database Verification for Client 53188 ---");
                ResultSet rs1 = stmt.executeQuery("SELECT \"ClientCode\", \"DealerID\", \"BOID\", \"Name\", email, phone FROM public.clients_active WHERE \"ClientCode\" = '53188';");
                while (rs1.next()) {
                    System.out.printf("  clients_active: Code=%s | Dealer=%s | BOID=%s | Name=%s | Email=%s%n",
                            rs1.getString("ClientCode"), rs1.getString("DealerID"), rs1.getString("BOID"), rs1.getString("Name"), rs1.getString("email"));
                }
                ResultSet rs2 = stmt.executeQuery("SELECT \"ClientCode\", \"Cash\", initial_cash, ledger_balance, mature_balance FROM public.clients_cash WHERE \"ClientCode\" = '53188';");
                while (rs2.next()) {
                    System.out.printf("  clients_cash:   Code=%s | Cash=%s | InitCash=%s | Ledger=%s | Mature=%s%n",
                            rs2.getString("ClientCode"), rs2.getString("Cash"), rs2.getString("initial_cash"), rs2.getString("ledger_balance"), rs2.getString("mature_balance"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
