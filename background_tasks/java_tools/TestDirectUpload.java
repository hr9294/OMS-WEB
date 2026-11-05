import java.io.File;
import java.time.Duration;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestDirectUpload {
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

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2500);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Dismiss modal
            js.executeScript(
                "var m = document.getElementById('main-page-modal');\n" +
                "if (m && window.jQuery) window.jQuery(m).modal('hide');"
            );
            Thread.sleep(1000);

            // Open Client upload modal
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=Client\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            File clientFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\increment_active_clients.xml");
            System.out.println("Uploading file: " + clientFile.getAbsolutePath() + " (" + clientFile.length() + " bytes)");

            WebElement input = driver.findElement(By.id("increment_active_clients_xml_upload"));
            input.sendKeys(clientFile.getAbsolutePath());
            Thread.sleep(1000);

            // Execute upload with full response tracking
            Object result = js.executeAsyncScript(
                "var callback = arguments[arguments.length - 1];\n" +
                "var form = document.getElementById('broker_it-add-form');\n" +
                "var fd = new FormData(form);\n" +
                "var input = document.getElementById('increment_active_clients_xml_upload');\n" +
                "if (input && input.files.length > 0) {\n" +
                "    console.log('Attaching file:', input.files[0].name, input.files[0].size);\n" +
                "    fd.set('increment_active_clients_xml_upload', input.files[0]);\n" +
                "}\n" +
                "var xhr = new XMLHttpRequest();\n" +
                "xhr.open('POST', '/broker_it/upload', true);\n" +
                "xhr.onload = function() {\n" +
                "    callback({ status: xhr.status, response: xhr.responseText });\n" +
                "};\n" +
                "xhr.onerror = function(err) {\n" +
                "    callback({ status: 'error', response: String(err) });\n" +
                "};\n" +
                "xhr.send(fd);"
            );

            System.out.println("\nUPLOAD HTTP RESPONSE: " + result);

            // Wait a few seconds to check what happened
            Thread.sleep(4000);
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(3000);

            Object rows = js.executeScript(
                "var out = [];\n" +
                "document.querySelectorAll('#file_manager_table tbody tr').forEach(function(r) {\n" +
                "    var tds = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    if (tds.length > 0) out.push(tds.join(' | '));\n" +
                "});\n" +
                "return out.slice(0, 5);"
            );
            System.out.println("\nTOP 5 ROWS IN FILE MANAGER: " + rows);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
