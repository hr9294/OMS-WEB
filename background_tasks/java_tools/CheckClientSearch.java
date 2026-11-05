import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class CheckClientSearch {
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

            String[] testCodes = {"23986", "34601", "15740", "00463", "00006"};
            for (String code : testCodes) {
                Object res = js.executeScript(
                    "var out = 'none';\n" +
                    "$.ajax({\n" +
                    "    url: '/bo_accounts/client_list?client_code=' + arguments[0],\n" +
                    "    async: false,\n" +
                    "    success: function(data) {\n" +
                    "        var div = document.createElement('div');\n" +
                    "        div.innerHTML = data;\n" +
                    "        var r = div.querySelector('table tbody tr');\n" +
                    "        if (r) out = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); }).join(' | ');\n" +
                    "    }\n" +
                    "});\n" +
                    "return out;",
                    code
                );
                System.out.println(code + " => " + res);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
