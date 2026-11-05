import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class DOM_Inspector {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new"); // headless for fast inspection

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1500);

            // Login
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

            // 1. Inspect Upload Data Button & Dropdown HTML
            System.out.println("=== 1. UPLOAD DATA BUTTON & DROPDOWN DOM ===");
            Object res1 = js.executeScript(
                "var btn = Array.from(document.querySelectorAll('button, a')).find(function(b) { " +
                "    return (b.innerText || '').toLowerCase().indexOf('upload data') !== -1; " +
                "});" +
                "if (!btn) return 'Upload Data button not found';" +
                "var parent = btn.parentElement;" +
                "return { " +
                "    btnHtml: btn.outerHTML, " +
                "    parentHtml: parent ? parent.outerHTML.substring(0, 500) : 'none', " +
                "    classes: btn.className, " +
                "    id: btn.id, " +
                "    dataTarget: btn.getAttribute('data-target') " +
                "};"
            );
            System.out.println("Result 1: " + res1);

            // 2. Open dropdown and inspect items
            OMSweb_UploadData.openUploadDataMenu(driver);
            Thread.sleep(500);

            System.out.println("=== 2. DROPDOWN SUB-ITEMS ===");
            Object res2 = js.executeScript(
                "var items = Array.from(document.querySelectorAll('li, a, button, .dropdown-item'));" +
                "var found = [];" +
                "items.forEach(function(el) {" +
                "    var t = (el.innerText || '').trim();" +
                "    if (t.indexOf('File') !== -1) {" +
                "        found.push({ text: t, tag: el.tagName, outer: el.outerHTML, id: el.id, class: el.className });" +
                "    }" +
                "});" +
                "return found;"
            );
            System.out.println("Result 2: " + res2);

            // 3. Click one item (e.g. Cash File or Client File) and check what opens
            System.out.println("=== 3. CLICK CASH FILE & DUMP NEW MODALS/INPUTS ===");
            Object res3 = js.executeScript(
                "var items = Array.from(document.querySelectorAll('li, a, button'));" +
                "var cashItem = items.find(function(el) { return (el.innerText || '').trim() === 'Cash File'; });" +
                "if (cashItem) {" +
                "    cashItem.click();" +
                "    return 'Clicked Cash File';" +
                "}" +
                "return 'Cash File not found';"
            );
            System.out.println("Click result: " + res3);
            Thread.sleep(1500);

            Object res4 = js.executeScript(
                "var allInputs = Array.from(document.querySelectorAll('input')).map(function(i) {" +
                "    return { type: i.type, id: i.id, name: i.name, class: i.className, outer: i.outerHTML };" +
                "});" +
                "var visibleModals = Array.from(document.querySelectorAll('.modal, .jconfirm, [role=\"dialog\"], .show')).map(function(m) {" +
                "    return { id: m.id, class: m.className, text: (m.innerText || '').substring(0, 300) };" +
                "});" +
                "return { inputs: allInputs, modals: visibleModals };"
            );
            System.out.println("Post-click DOM: " + res4);

            // 4. Inspect Export Files Button & Dropdown
            System.out.println("=== 4. EXPORT FILES BUTTON ===");
            Object res5 = js.executeScript(
                "var expBtn = Array.from(document.querySelectorAll('button, a')).find(function(b) { " +
                "    return (b.innerText || '').toLowerCase().indexOf('export file') !== -1; " +
                "});" +
                "if (!expBtn) return 'Export Files button not found';" +
                "return { html: expBtn.outerHTML, text: expBtn.innerText, parent: expBtn.parentElement.outerHTML.substring(0, 300) };"
            );
            System.out.println("Result 5: " + res5);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
