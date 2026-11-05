import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class CheckBoHeaders {
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

            Object res = js.executeScript(
                "var out = 'waiting';\n" +
                "$.ajax({\n" +
                "    url: '/bo_accounts/client_list',\n" +
                "    async: false,\n" +
                "    success: function(data) {\n" +
                "        var div = document.createElement('div');\n" +
                "        div.innerHTML = data;\n" +
                "        var ths = Array.from(div.querySelectorAll('table thead th')).map(function(th) { return th.innerText.trim(); });\n" +
                "        out = ths.join(' | ');\n" +
                "    },\n" +
                "    error: function(xhr) { out = 'ERR ' + xhr.status; }\n" +
                "});\n" +
                "return out;"
            );
            System.out.println("Headers: " + res);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
