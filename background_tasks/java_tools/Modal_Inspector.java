import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Modal_Inspector {
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
            Thread.sleep(1500);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Open Cash Modal
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Cash\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            Object cashModalHtml = js.executeScript(
                "var m = document.getElementById('main-page-modal') || document.querySelector('.modal.show');\n" +
                "return m ? m.innerHTML : 'No modal found';"
            );
            System.out.println("=== CASH MODAL INNER HTML ===");
            System.out.println(cashModalHtml);

            // Close modal
            js.executeScript(
                "var closeBtn = document.querySelector('#main-page-modal .close, #main-page-modal button');\n" +
                "if (closeBtn) closeBtn.click();"
            );
            Thread.sleep(1000);

            // Open Stock Modal
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Stock\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            Object stockModalHtml = js.executeScript(
                "var m = document.getElementById('main-page-modal') || document.querySelector('.modal.show');\n" +
                "return m ? m.innerHTML : 'No modal found';"
            );
            System.out.println("=== STOCK MODAL INNER HTML ===");
            System.out.println(stockModalHtml);

            // Close modal
            js.executeScript(
                "var closeBtn = document.querySelector('#main-page-modal .close, #main-page-modal button');\n" +
                "if (closeBtn) closeBtn.click();"
            );
            Thread.sleep(1000);

            // Open Users Modal
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Users\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            Object usersModalHtml = js.executeScript(
                "var m = document.getElementById('main-page-modal') || document.querySelector('.modal.show');\n" +
                "return m ? m.innerHTML : 'No modal found';"
            );
            System.out.println("=== USERS MODAL INNER HTML ===");
            System.out.println(usersModalHtml);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
