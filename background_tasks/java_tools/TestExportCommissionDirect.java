import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestExportCommissionDirect {
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

            Object res = js.executeScript(
                "var out = 'waiting';\n" +
                "$.ajax({\n" +
                "    url: '/broker_it/generate_report?file_type=Commission',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = 'SUCCESS: ' + JSON.stringify(data); },\n" +
                "    error: function(xhr) { out = 'ERROR ' + xhr.status + ': ' + xhr.responseText; }\n" +
                "});\n" +
                "return out;"
            );
            System.out.println("Commission report generation result: " + res);

            // Also test download_trade_file
            Object tradeRes = js.executeScript(
                "var out = 'waiting';\n" +
                "$.ajax({\n" +
                "    url: '/shared/download_trade_file?exchange=default',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = 'SUCCESS length: ' + (data ? data.length : 0); },\n" +
                "    error: function(xhr) { out = 'ERROR ' + xhr.status + ': ' + xhr.responseText; }\n" +
                "});\n" +
                "return out;"
            );
            System.out.println("Trade file download result: " + tradeRes);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
