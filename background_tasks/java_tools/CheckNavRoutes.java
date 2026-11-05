import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

public class CheckNavRoutes {
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
            List<?> links = (List<?>) js.executeScript(
                "var links = Array.from(document.querySelectorAll('.sidebar a, nav a, [data-link], a[href]'));\n" +
                "return links.map(function(a) {\n" +
                "    return (a.innerText || a.getAttribute('title') || '').trim() + ' => ' + (a.getAttribute('href') || a.getAttribute('data-link') || '');\n" +
                "}).filter(function(x) { return x.length > 5; });"
            );

            System.out.println("=== SIDEBAR / NAV LINKS ===");
            for (Object l : links) {
                System.out.println(l);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
