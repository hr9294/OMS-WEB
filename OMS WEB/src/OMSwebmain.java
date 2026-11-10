import java.io.File;

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



        public static WebDriver createDriver() {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(java.util.logging.Level.SEVERE);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--silent");

        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("password_manager_leak_detection", false);
        prefs.put("safebrowsing.enabled", false);
        prefs.put("safebrowsing.enhanced", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("safebrowsing.disable_download_protection", true);
        String downloadPath = System.getProperty("user.home") + File.separator + "Downloads";
        prefs.put("download.default_directory", downloadPath);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        options.setExperimentalOption("prefs", prefs);

        options.addArguments("--disable-features=PasswordLeakDetection,PasswordCheck,PasswordLeakToggleMove,SafeBrowsing,ProfileCustomization,CustomizeChromeSidePanel,ChromeRefresh2023,DownloadBubble,DownloadBubbleV2,InsecureDownloadWarnings");
        options.addArguments("--safebrowsing-disable-download-protection");
        options.addArguments("--safebrowsing-disable-extension-blacklist");
        options.addArguments("--profile-directory=Default");
        options.addArguments("--start-maximized");
        options.addArguments("--window-position=0,0");
        options.addArguments("--window-size=1920,1080");
        options.setExperimentalOption("detach", true);
        options.setAcceptInsecureCerts(true);

        boolean isCi = "true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("HEADLESS"));
        if (isCi) {
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
        }

        WebDriver drv = new ChromeDriver(options);
        try {
            Map<String, Object> cdpParams = new HashMap<>();
            cdpParams.put("behavior", "allow");
            cdpParams.put("downloadPath", downloadPath);
            cdpParams.put("eventsEnabled", true);
            ((org.openqa.selenium.chromium.ChromiumDriver) drv).executeCdpCommand("Browser.setDownloadBehavior", cdpParams);
            ((org.openqa.selenium.chromium.ChromiumDriver) drv).executeCdpCommand("Page.setDownloadBehavior", cdpParams);
        } catch (Exception ignored) {}
        drv.manage().window().maximize();
        drv.manage().deleteAllCookies();
        drv.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
        drv.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        return drv;
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        driver = createDriver();
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



        // 3. SELECT EXECUTION ENGINE
        System.out.println("\n[UFTFAST OMS] 3. Select Execution Engine:");
        System.out.println("  1. Normal Trade (DSE default / CSE) [Default]");
        System.out.println("  2. Multi-Order Execution (Single/Multi-Client, DSE/CSE, Market Execution)");
        System.out.println("  3. High-Speed Multiple Trade Engine (2-Min Simultaneous Trading, DSE/CSE)");
        System.out.println("  4. Upload File Engine (User Choice File Upload: Client, Stock, Cash, etc.)");
        System.out.println("  5. Export File Engine (Trade Files, EOD Price, Commission & Reports)");
        System.out.print("Enter Choice (1-5, default=1 in 4s): ");
        String engChoice = OMSweb_Trade.readLineWithTimeout(sc, "1", 4);

        if ("5".equals(engChoice) || "export".equalsIgnoreCase(engChoice) || "trade-export".equalsIgnoreCase(engChoice)) {
            OMSweb_ExportData.main(driver, sc);
        } else if ("4".equals(engChoice) || "upload".equalsIgnoreCase(engChoice)) {
            OMSweb_UploadData.main(driver, sc);
        } else if ("3".equals(engChoice) || "multitrade".equalsIgnoreCase(engChoice)) {
            OMSweb_MultipleTrade.main(driver, sc);
        } else if ("2".equals(engChoice) || "multiorder".equalsIgnoreCase(engChoice)) {
            OMSweb_MultiOrderExecution.main(driver, sc);
        } else if ("CSE".equalsIgnoreCase(engChoice)) {
            OMSweb_Trade.activeExchange = "CSE";
            OMSweb_Trade.runNormalTradeCSE(driver, sc);
        } else {
            OMSweb_Trade.main(driver, sc);
        }

        System.out.println("\n=============================================================================");
        System.out.println(" [SUCCESS] Action completed. Chrome browser remains active on your desktop.");
        System.out.println("=============================================================================\n");
    }
}