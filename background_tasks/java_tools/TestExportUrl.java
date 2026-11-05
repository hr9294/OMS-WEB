import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class TestExportUrl {
    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1000);
            driver.findElement(By.id("username")).sendKeys("Testadmin");
            driver.findElement(By.id("password")).sendKeys("12345");
            driver.findElement(By.xpath("//button[normalize-space()='Login']")).click();
            Thread.sleep(1500);

            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            String[] urls = {
                "/broker_it/export?file_type=Commission",
                "/broker_it/download?file_name=client_commission.csv",
                "/broker_it/download?file_name=client_commission_sample.csv"
            };

            for (String u : urls) {
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
