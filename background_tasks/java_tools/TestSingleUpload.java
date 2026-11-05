import java.io.File;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestSingleUpload {
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

            // Open Stock Modal
            System.out.println("Opening Stock modal...");
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Stock\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            File stockFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\increment_stock.xml");
            System.out.println("Attaching file: " + stockFile.getAbsolutePath() + " (exists: " + stockFile.exists() + ")");

            WebElement input = driver.findElement(By.id("increment_stock_xml_upload"));
            input.sendKeys(stockFile.getAbsolutePath());
            Thread.sleep(1000);

            // Hook into XHR or listen
            js.executeScript(
                "window._uploadStatus = 'waiting';\n" +
                "window._uploadResponse = '';\n" +
                "var origOpen = XMLHttpRequest.prototype.open;\n" +
                "XMLHttpRequest.prototype.open = function(m, u, a) {\n" +
                "    this.addEventListener('load', function() {\n" +
                "        window._uploadStatus = this.status;\n" +
                "        window._uploadResponse = this.responseText;\n" +
                "    });\n" +
                "    origOpen.apply(this, arguments);\n" +
                "};\n" +
                "var btn = document.getElementById('uploadButton');\n" +
                "btn.classList.remove('disabled');\n" +
                "btn.click();"
            );
            System.out.println("Clicked upload button for Stock!");

            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                Object status = js.executeScript("return window._uploadStatus;");
                Object resp = js.executeScript("return window._uploadResponse;");
                System.out.println("Poll " + (i+1) + " -> Status: " + status + " | Response: " + resp);
                if (!"waiting".equals(status)) break;
            }

            Thread.sleep(3000);
            List<?> tableRows = (List<?>) js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 5).map(function(r) { return r.innerText.trim(); });"
            );
            System.out.println("Top rows:\n" + tableRows);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
