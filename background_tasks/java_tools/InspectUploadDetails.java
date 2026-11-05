import java.io.File;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class InspectUploadDetails {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

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

            // Inspect the modal form
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Client\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            Object formAnalysis = js.executeScript(
                "var m = document.getElementById('main-page-modal');\n" +
                "var form = m ? m.querySelector('form') : null;\n" +
                "var inputs = form ? Array.from(form.querySelectorAll('input')).map(function(i){ return { id: i.id, name: i.name, type: i.type, value: i.value }; }) : [];\n" +
                "var btn = document.getElementById('uploadButton');\n" +
                "return {\n" +
                "    action: form ? form.getAttribute('action') : null,\n" +
                "    method: form ? form.getAttribute('method') : null,\n" +
                "    enctype: form ? form.getAttribute('enctype') : null,\n" +
                "    inputs: inputs,\n" +
                "    btnText: btn ? btn.innerText.trim() : null,\n" +
                "    btnOnclick: btn ? btn.getAttribute('onclick') : null\n" +
                "};"
            );
            System.out.println("FORM ANALYSIS: " + formAnalysis);

            // Let's also check static/js/page-js/broker_it/upload.js event listeners on uploadButton
            Object jsHandler = js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn && window.jQuery) {\n" +
                "    var events = jQuery._data(btn, 'events');\n" +
                "    return events ? Object.keys(events) : 'no jquery events';\n" +
                "} return 'no jquery';"
            );
            System.out.println("UPLOAD BUTTON EVENTS: " + jsHandler);

            // Check recent entries in file_manager_table including pagination or hidden rows
            Object fullTable = js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.map(function(r) {\n" +
                "    return Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); }).join(' | ');\n" +
                "});"
            );
            System.out.println("\nALL TABLE ROWS COUNT: " + (fullTable instanceof List ? ((List<?>) fullTable).size() : 0));
            if (fullTable instanceof List) {
                for (Object r : (List<?>) fullTable) {
                    System.out.println("  " + r);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
