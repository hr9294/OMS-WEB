import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class OMSwebmain {
    static WebDriver driver;

    public static void main(String[] args) throws InterruptedException, IOException {
        // Suppress ChromeDriver and Selenium CDP version fallback warnings
        System.setProperty("webdriver.chrome.silentOutput", "true");
        java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(java.util.logging.Level.SEVERE);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--silent");

        // Hide automation flags
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");

        // Privacy tweaks: disable password manager, leak detection, notifications, etc.
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("password_manager_leak_detection", false);
        prefs.put("safebrowsing.enabled", false);
        prefs.put("safebrowsing.enhanced", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);

        // Disable password leak detection & safe browsing check popups
        options.addArguments("--disable-features=PasswordLeakDetection,PasswordCheck,PasswordLeakToggleMove,SafeBrowsing");
        options.addArguments("--start-maximized");
        options.addArguments("--window-position=0,0");
        options.addArguments("--window-size=1920,1080");
        options.setExperimentalOption("detach", true); // Keeps browser visible and open on desktop
        options.setAcceptInsecureCerts(true);
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().deleteAllCookies();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.get("https://devoms.quantbd.com");
        Thread.sleep(1000);

        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        );
        System.out.println("Welcome to The UFTFAST Order Management System Application");
        Scanner sc = new Scanner(System.in);

        // Login
        OMSweb_Login.main(driver, sc);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // 1. FIRST OPEN THE TRADE PAGE (Force navigation to guarantee Trade Portal opens)
        System.out.println("\n[UFTFAST OMS] 1. Opening Trade Page...");
        OMSweb_Trade.navigateToTradePage(driver, wait, true);

        // 2. THEN SELECT WIDGET COLOR LINK
        System.out.println("[UFTFAST OMS] 2. Configuring Widget Color Link...");
        OMSweb_Trade.runWidgetColorSelection(driver, sc);

        // 3. THEN SELECT EXECUTION ENGINE
        System.out.println("\n[UFTFAST OMS] 3. Select Execution Engine:");
        System.out.println("  1. Normal Trade (DSE default / CSE) [Default]");
        System.out.println("  2. Multi-Order Execution (Single/Multi-Client, DSE/CSE, Market Execution)");
        System.out.println("  3. High-Speed Multiple Trade Engine (2-Min Simultaneous Trading, DSE/CSE)");
        System.out.println("  4. Upload File Engine (User Choice File Upload: Client, Stock, Cash, etc.)");
        System.out.println("  5. Export File Engine (Trade Files, EOD Price, Commission & Reports)");
        System.out.print("Enter Choice (1-5, default=1 in 4s): ");
        String engChoice = OMSweb_Trade.readLineWithTimeout(sc, "1", 4);

        // 4. DISPATCH TO CORRESPONDING CLASS
        if ("5".equals(engChoice) || "export".equalsIgnoreCase(engChoice) || "trade-export".equalsIgnoreCase(engChoice)) {
            // Trigger Export File Class
            OMSweb_ExportData.main(driver, sc);
        } else if ("4".equals(engChoice) || "upload".equalsIgnoreCase(engChoice)) {
            // Trigger Upload File Class
            OMSweb_UploadData.main(driver, sc);
        } else if ("3".equals(engChoice) || "multitrade".equalsIgnoreCase(engChoice)) {
            // Trigger Multiple Trade Class (handles DSE & CSE)
            OMSweb_MultipleTrade.main(driver, sc);
        } else if ("2".equals(engChoice) || "multiorder".equalsIgnoreCase(engChoice)) {
            // Trigger Multi-Order Execution Class (handles DSE & CSE)
            OMSweb_MultiOrderExecution.main(driver, sc);
        } else if ("CSE".equalsIgnoreCase(engChoice)) {
            // Direct CSE keyword routing to Trade Class
            OMSweb_Trade.activeExchange = "CSE";
            OMSweb_Trade.runNormalTradeCSE(driver, sc);
        } else {
            // Trigger Normal Trade Class (handles DSE default & CSE normal orders) [Default]
            OMSweb_Trade.main(driver, sc);
        }

        // OMSweb_MultipleTrade.main(driver, sc);
        // Position.main(driver, sc);
        // ClientPortfolio.main(driver, sc);
        // Order_Summary.main(driver, sc);
        // Account_Limits.main(driver, sc);
        // Market_Depth.main(driver, sc);
        // Order_Terminal.main(driver, sc);
        // Market_Watch.main(driver, sc);
        // OMSweb_MultiOrderExecution.main()driver,sc;

        System.out.println("\n[UFTFAST OMS] Trade session completed. Exiting application.");
        sc.close();
    }
}
