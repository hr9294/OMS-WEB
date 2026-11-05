import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestAllExports {
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

            String[] testUrls = {
                "/shared/download_trade_file?exchange=default",
                "/shared/download_trade_file?exchange=DSE",
                "/shared/download_trade_file?exchange=CSE",
                "/shared/download_csv_trade_file?exchange=default",
                "/shared/download_eod_price_file",
                "/broker_it/generate_report?file_type=Associate",
                "/broker_it/generate_report?file_type=Commission",
                "/broker_it/generate_report?file_type=Rating",
                "/broker_it/generate_report?file_type=Dealer%20Terminal",
                "/broker_it/generate_all_reports"
            };

            for (String u : testUrls) {
                Object res = js.executeScript(
                    "var out = 'waiting';\n" +
                    "$.ajax({\n" +
                    "    url: '" + u + "',\n" +
                    "    async: false,\n" +
                    "    success: function(data, st, xhr) { out = 'STATUS ' + xhr.status + ': OK'; },\n" +
                    "    error: function(xhr) { out = 'STATUS ' + xhr.status + ': ' + xhr.responseText.substring(0, 60); }\n" +
                    "});\n" +
                    "return out;"
                );
                System.out.println(u + " => " + res);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
