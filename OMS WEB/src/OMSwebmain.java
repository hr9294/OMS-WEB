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
import org.openqa.selenium.JavascriptExecutor;



public class OMSwebmain {

    static WebDriver driver;
    public static final boolean isCi = "true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("HEADLESS"));



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


    /**
     * Closes all browser-related notifications, modals, bootstrap dialogs, jconfirm popups,
     * alert banners, and backdrop overlays to ensure a completely clean UI.
     */
    public static void closeAllBrowserPopupsAndModals(WebDriver driver) {
        if (driver == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "try {\n" +
                "    // 1. Close all jconfirm dialogs\n" +
                "    if (window.jconfirm && window.jconfirm.instances) {\n" +
                "        for (var i = 0; i < window.jconfirm.instances.length; i++) {\n" +
                "            try { if (window.jconfirm.instances[i]) window.jconfirm.instances[i].close(); } catch(e) {}\n" +
                "        }\n" +
                "    }\n" +
                "    // 2. Hide Bootstrap and custom modals\n" +
                "    if (window.jQuery) {\n" +
                "        try { window.jQuery('.modal').modal('hide'); } catch(e) {}\n" +
                "    }\n" +
                "    // 3. Click any visible close buttons on modals, popups, and alerts\n" +
                "    var closeBtns = document.querySelectorAll(\".modal .close, .modal [data-dismiss='modal'], button.btn-close, .close-modal, .swal2-close, .alert .close, .toast .close\");\n" +
                "    for (var c = 0; c < closeBtns.length; c++) {\n" +
                "        try { closeBtns[c].click(); } catch(e) {}\n" +
                "    }\n" +
                "    // 4. Remove lingering backdrops and overlays\n" +
                "    var overlays = document.querySelectorAll('.modal-backdrop, .jconfirm-bg, .jconfirm-overlay, .blockOverlay, .swal2-container');\n" +
                "    for (var o = 0; o < overlays.length; o++) {\n" +
                "        try { overlays[o].remove(); } catch(e) {}\n" +
                "    }\n" +
                "    // 5. Re-enable body scrolling\n" +
                "    if (document.body) {\n" +
                "        document.body.classList.remove('modal-open');\n" +
                "        document.body.style.removeProperty('padding-right');\n" +
                "        document.body.style.removeProperty('overflow');\n" +
                "    }\n" +
                "} catch(err) {}"
            );
            // Dismiss native alerts if present
            try { driver.switchTo().alert().dismiss(); } catch (Exception ignoredAlert) {}
        } catch (Exception ignored) {}
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



        // 3. CONTINUOUS ACTIVE SESSION LOOP (NEVER TERMINATE, AUTO-CLOSE ALL POPUPS & MODALS)
        boolean keepSessionAlive = true;
        boolean isFirstRun = true;

        while (keepSessionAlive) {
            closeAllBrowserPopupsAndModals(driver);

            System.out.println("\n=============================================================================");
            System.out.println("                     [UFTFAST OMS ACTIVE SESSION]                           ");
            System.out.println("  Browser & trading session are LIVE and persistent. Never terminating.     ");
            System.out.println("=============================================================================");
            System.out.println("Select Action:");
            System.out.println("  1. Normal Trade (DSE default / CSE) [Default]");
            System.out.println("  2. Multi-Order Execution (Single/Multi-Client, DSE/CSE, Market Execution)");
            System.out.println("  3. High-Speed Multiple Trade Engine (2-Min Simultaneous Trading, DSE/CSE)");
            System.out.println("  4. Upload File Engine (User Choice File Upload: Client, Stock, Cash, etc.)");
            System.out.println("  5. Export File Engine (Trade Files, EOD Price, Commission & Reports)");
            System.out.println("  6. Widget UI Data Check (Maximize -> Snapshot -> Minimize Each Widget)");
            System.out.println("  0. Exit Application");

            String defaultChoice = isFirstRun ? "1" : "";
            int timeoutSecs = isFirstRun ? 4 : 0;
            System.out.print(isFirstRun ? "Enter Choice (1-6 to execute, 0=Exit, default=1 in 4s): " : "Enter Choice (1-6 to execute, 0=Exit): ");

            String engChoice = timeoutSecs > 0 ? OMSweb_Trade.readLineWithTimeout(sc, defaultChoice, timeoutSecs) : sc.nextLine().trim();
            isFirstRun = false;

            if ("0".equals(engChoice) || "exit".equalsIgnoreCase(engChoice) || "quit".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] User requested exit. Browser detached. Goodbye!");
                keepSessionAlive = false;
                break;
            } else if ("6".equals(engChoice) || "widget".equalsIgnoreCase(engChoice) || "snapshot".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Widget UI Data Check (Maximize -> Snapshot -> Minimize)...");
                OMSweb_Trade.checkAllWidgetsWithMaximizeAndSnapshot(driver, "WIDGET_DATA_CHECK", "23986", "GP");
            } else if ("5".equals(engChoice) || "export".equalsIgnoreCase(engChoice) || "trade-export".equalsIgnoreCase(engChoice)) {
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

            // Immediately close all modals/popups after execution to keep browser clean
            closeAllBrowserPopupsAndModals(driver);

            if (isCi) {
                System.out.println("\n[AUTOMATION: DONE] Automated container/CI execution finished successfully. Exiting cleanly.");
                keepSessionAlive = false;
                break;
            }

            System.out.println("\n[SESSION LIVE] Action completed. Session remains ACTIVE in Eclipse (never terminating).");
        }
    }
}