import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class CheckClientList {
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

            // Load bo_accounts/client_list
            Object clnHtml = js.executeScript(
                "var out = 'waiting';\n" +
                "$.ajax({\n" +
                "    url: '/bo_accounts/client_list',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = data.substring(0, 1500); },\n" +
                "    error: function(xhr) { out = 'ERR: ' + xhr.status; }\n" +
                "});\n" +
                "return out;"
            );
            System.out.println("=== BO ACCOUNTS CLIENT LIST ===");
            System.out.println(clnHtml);

            // Also check users/
            Object usersHtml = js.executeScript(
                "var out = 'waiting';\n" +
                "$.ajax({\n" +
                "    url: '/users/',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = data.substring(0, 1500); },\n" +
                "    error: function(xhr) { out = 'ERR: ' + xhr.status; }\n" +
                "});\n" +
                "return out;"
            );
            System.out.println("=== USERS LIST ===");
            System.out.println(usersHtml);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
