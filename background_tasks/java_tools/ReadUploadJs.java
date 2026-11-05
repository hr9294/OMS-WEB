import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class ReadUploadJs {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--headless=new");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get("https://devoms.quantbd.com");
            driver.findElement(By.id("username")).sendKeys("Testadmin");
            driver.findElement(By.id("password")).sendKeys("12345");
            driver.findElement(By.xpath("//button[normalize-space()='Login']")).click();
            Thread.sleep(1500);

            java.util.List<WebElement> continueBtns = driver.findElements(By.id("continue_to_force_login"));
            if (!continueBtns.isEmpty()) { continueBtns.get(0).click(); Thread.sleep(1500); }
            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);

            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var text = '';\n" +
                "$.ajax({\n" +
                "    url: '/static/js/page-js/broker_it/upload.js',\n" +
                "    async: false,\n" +
                "    success: function(data) { text = data; }\n" +
                "});\n" +
                "return text;"
            );
            System.out.println("=== UPLOAD.JS CONTENT ===");
            System.out.println(res);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
