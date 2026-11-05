import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestNameUpdateViaUpload {
    public static void main(String[] args) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String xmlFileName = "Update_Dealer_23986_34601_" + timestamp + ".xml";
        File xmlFile = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\" + xmlFileName);

        // Prepare XML with Exact Official Dealers: 23986 -> UFTTRDR021, 34601 -> UFTTRDR051
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                     "<Clients ProcessingMode=\"BatchInsertOrUpdate\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                     "<Register>\n" +
                     "  <ClientCode>23986</ClientCode>\n" +
                     "  <DealerID>UFTTRDR021</DealerID>\n" +
                     "  <BOID>1203119999999999</BOID>\n" +
                     "  <WithNetAdjustment>Yes</WithNetAdjustment>\n" +
                     "  <Name>MDZAHID HASSAN</Name>\n" +
                     "  <Address>55 NIK Tower</Address>\n" +
                     "  <ICNo>123456789</ICNo>\n" +
                     "  <AccountType>N</AccountType>\n" +
                     "  <ShortSellingAllowed>No</ShortSellingAllowed>\n" +
                     "  <Email>user23986@gmail.com</Email>\n" +
                     "  <Phone>01711239860</Phone>\n" +
                     "</Register>\n" +
                     "<Register>\n" +
                     "  <ClientCode>34601</ClientCode>\n" +
                     "  <DealerID>UFTTRDR051</DealerID>\n" +
                     "  <BOID>1203119999999998</BOID>\n" +
                     "  <WithNetAdjustment>Yes</WithNetAdjustment>\n" +
                     "  <Name>MD MONIRUL ISLAM</Name>\n" +
                     "  <Address>55 NIK Tower</Address>\n" +
                     "  <ICNo>123456789</ICNo>\n" +
                     "  <AccountType>N</AccountType>\n" +
                     "  <ShortSellingAllowed>No</ShortSellingAllowed>\n" +
                     "  <Email>user34601@gmail.com</Email>\n" +
                     "  <Phone>01711346010</Phone>\n" +
                     "</Register>\n" +
                     "</Clients>\n";

        try (FileWriter w = new FileWriter(xmlFile)) {
            w.write(xml);
            System.out.println("Created XML: " + xmlFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Upload via Selenium
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
            if (!continueBtns.isEmpty()) { continueBtns.get(0).click(); Thread.sleep(1500); }
            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2500);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Dismiss modal
            js.executeScript("var m = document.getElementById('main-page-modal'); if (m && window.jQuery) window.jQuery(m).modal('hide');");
            Thread.sleep(1000);

            // Open Client upload modal
            js.executeScript("document.getElementById('dropdownMenuButtonUpload').click(); var link = document.querySelector('a[href*=\"file_type=Client\"]'); if (link) link.click();");
            Thread.sleep(2000);

            WebElement input = driver.findElement(By.id("increment_active_clients_xml_upload"));
            input.sendKeys(xmlFile.getAbsolutePath());
            Thread.sleep(1000);

            // Submit upload
            js.executeScript("var btn = document.getElementById('uploadButton'); if (btn) { btn.classList.remove('disabled'); btn.click(); }");
            Thread.sleep(4000);

            Object flash = js.executeScript("var f = document.querySelector('.alert, .flash-message, #flash-messages'); return f ? f.innerText.replace(/\\s+/g, ' ').trim() : '';");
            System.out.println("Upload Flash: " + flash);

            // Wait a few seconds for backend processing
            Thread.sleep(5000);
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(2000);

            // Check getclientdata endpoint
            Object res23986 = js.executeScript(
                "var out = 'none';\n" +
                "$.ajax({\n" +
                "    url: '/shared/getclientdata/?cln_code=23986',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = JSON.stringify(data); }\n" +
                "});\n" +
                "return out;"
            );
            Object res34601 = js.executeScript(
                "var out = 'none';\n" +
                "$.ajax({\n" +
                "    url: '/shared/getclientdata/?cln_code=34601',\n" +
                "    async: false,\n" +
                "    success: function(data) { out = JSON.stringify(data); }\n" +
                "});\n" +
                "return out;"
            );

            System.out.println("\n--- Post-Upload /shared/getclientdata/ Results ---");
            System.out.println("23986: " + res23986);
            System.out.println("34601: " + res34601);

            // Check database directly
            try (Connection conn = DriverManager.getConnection("jdbc:postgresql://192.168.155.22:5443/qtraderdb", "qtraderdata", "pass4321");
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT \"ClientCode\", \"DealerID\", \"Name\", \"BOID\" FROM public.clients_active WHERE \"ClientCode\" IN ('23986', '34601');");
                System.out.println("\n--- Post-Upload public.clients_active Database Results ---");
                while (rs.next()) {
                    System.out.printf("  ClientCode: %s | DealerID: %s | Name: %s | BOID: %s%n",
                            rs.getString("ClientCode"), rs.getString("DealerID"), rs.getString("Name"), rs.getString("BOID"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
