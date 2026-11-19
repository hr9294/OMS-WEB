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
    public static final boolean isCi = "true".equalsIgnoreCase(System.getenv("CI"))
            || "true".equalsIgnoreCase(System.getenv("HEADLESS"))
            || "true".equalsIgnoreCase(System.getenv("DOCKER_MODE"))
            || "true".equalsIgnoreCase(System.getenv("AUTOMATE_MODE"));

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

        while (keepSessionAlive) {
            closeAllBrowserPopupsAndModals(driver);

            System.out.println("\n=============================================================================");
            System.out.println("                     [UFTFAST OMS ACTIVE SESSION]                           ");
            System.out.println("  Browser & trading session are LIVE and persistent. Never terminating.     ");
            System.out.println("=============================================================================");
            System.out.println("Select Action:");
            System.out.println("  1. Normal Trade (DSE default / CSE)");
            System.out.println("  2. Multi-Order Execution (Single/Multi-Client, DSE/CSE, Market Execution)");
            System.out.println("  3. High-Speed Multiple Trade Engine (2-Min Simultaneous Trading, DSE/CSE)");
            System.out.println("  4. Upload File Engine (User Choice File Upload: Client, Stock, Cash, etc.)");
            System.out.println("  5. Export File Engine (Trade Files, EOD Price, Commission & Reports)");
            System.out.println("  6. Day Netting Engine (Scrip Netting, RMS Controls & UI Audit)");
            System.out.println("  7. Dashboard Reflection Audit (12-Model Verification, Live Trade & Diff Comparison)");
            System.out.println("  0. Exit Application");

            String engChoice = "";

            if (isCi) {
                // AUTOMATED DOCKER MODE: Automatically select and run action without waiting
                String envAction = System.getenv("ACTION_CHOICE");
                if (envAction == null || envAction.trim().isEmpty()) {
                    envAction = System.getenv("AUTOMATE_ACTION");
                }
                engChoice = (envAction != null && !envAction.trim().isEmpty()) ? envAction.trim() : "1";
                System.out.printf("\n[DOCKER AUTOMATE MODE] Automatically selected action: %s%n", engChoice);
            } else {
                // MANUAL MODE: Wait for user choice with 5-minute idle auto-reload watchdog
                while (engChoice.isEmpty()) {
                    System.out.print("Enter Choice (1-7 to execute, 0=Exit): ");
                    engChoice = readLineWithIdleWatchdog(driver, sc, 300);
                    if (engChoice.isEmpty()) {
                        break;
                    }
                }
            }

            if (!"0".equals(engChoice) && !"exit".equalsIgnoreCase(engChoice) && !"quit".equalsIgnoreCase(engChoice)) {
                try {
                    OMSweb_Trade.ensureTradeSettingsRestored(driver);
                } catch (Exception ignored) {}
            }

            if ("0".equals(engChoice) || "exit".equalsIgnoreCase(engChoice) || "quit".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] User requested exit. Browser detached. Goodbye!");
                keepSessionAlive = false;
                break;
            } else if ("1".equals(engChoice) || "normal".equalsIgnoreCase(engChoice) || "trade".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 1: Normal Trade (DSE default)...");
                OMSweb_Trade.main(driver, sc);
            } else if ("2".equals(engChoice) || "multiorder".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 2: Multi-Order Execution...");
                OMSweb_MultiOrderExecution.main(driver, sc);
            } else if ("3".equals(engChoice) || "multitrade".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 3: High-Speed Multiple Trade Engine...");
                OMSweb_MultipleTrade.main(driver, sc);
            } else if ("4".equals(engChoice) || "upload".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 4: Upload File Engine...");
                OMSweb_UploadData.main(driver, sc);
            } else if ("5".equals(engChoice) || "export".equalsIgnoreCase(engChoice) || "trade-export".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 5: Export File Engine...");
                OMSweb_ExportData.main(driver, sc);
            } else if ("6".equals(engChoice) || "netting".equalsIgnoreCase(engChoice) || "daynetting".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 6: Day Netting Engine (Scrip Netting Suite)...");
                Day_Netting.main(driver, sc);
            } else if ("7".equals(engChoice) || "dashboard".equalsIgnoreCase(engChoice) || "audit".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Action 7: Dashboard Reflection Audit (12 Models)...");
                OMSweb_DashboardAudit.main(driver, sc);
            } else if ("CSE".equalsIgnoreCase(engChoice)) {
                System.out.println("\n[UFTFAST OMS] Executing Normal Trade for CSE...");
                OMSweb_Trade.activeExchange = "CSE";
                OMSweb_Trade.runNormalTradeCSE(driver, sc);
            } else {
                System.out.printf("\n[INVALID SELECTION] '%s' is not recognized. Only your chosen action (1-7, or 0) will execute.%n", engChoice);
                continue;
            }

            // Immediately close all modals/popups after execution to keep browser clean
            closeAllBrowserPopupsAndModals(driver);

            if (isCi) {
                System.out.println("\n[AUTOMATION: DONE] Automated container/CI execution finished successfully. Exiting cleanly.");
                keepSessionAlive = false;
                break;
            }

            System.out.println("\n=============================================================================");
            System.out.println(" [ACTION FINISHED] Completed successfully. Session is ACTIVE in browser.");
            System.out.println(" Selection menu will now reappear below for your further action.");
            System.out.println("=============================================================================");
        }
    }

    public static String readLineWithIdleWatchdog(WebDriver driver, Scanner sc, int idleSeconds) {
        if (sc == null) return "";
        long timeoutMs = idleSeconds * 1000L;
        long idleStart = System.currentTimeMillis();

        // Ensure watchdog is installed in browser
        try {
            if (driver != null) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript(
                    "var idleLimit = arguments[0] || 300000;\n" +
                    "try {\n" +
                    "    if (!window._idleAutoReloadWatchdogInstalled) {\n" +
                    "        window._idleAutoReloadWatchdogInstalled = true;\n" +
                    "        window._lastActivityTimestamp = Date.now();\n" +
                    "        var markActive = function() { window._lastActivityTimestamp = Date.now(); };\n" +
                    "        ['mousemove', 'mousedown', 'keydown', 'keypress', 'click', 'scroll', 'touchstart', 'focus'].forEach(function(ev) {\n" +
                    "            window.addEventListener(ev, markActive, { passive: true, capture: true });\n" +
                    "            document.addEventListener(ev, markActive, { passive: true, capture: true });\n" +
                    "        });\n" +
                    "        setInterval(function() {\n" +
                    "            try {\n" +
                    "                var m = document.getElementById('autoReloadModal');\n" +
                    "                if (m && (m.classList.contains('show') || window.getComputedStyle(m).display !== 'none' || (m.innerText && m.innerText.indexOf('You were idle') !== -1 && m.offsetParent !== null))) {\n" +
                    "                    console.log('[WATCHDOG] Idle modal detected! Reloading page...');\n" +
                    "                    location.reload();\n" +
                    "                    return;\n" +
                    "                }\n" +
                    "                var elapsed = Date.now() - (window._lastActivityTimestamp || Date.now());\n" +
                    "                if (elapsed >= idleLimit) { // 5 minutes idle = 300000 ms\n" +
                    "                    console.log('[WATCHDOG] ' + Math.round(idleLimit / 60000) + ' minutes idle reached. Reloading page...');\n" +
                    "                    window._lastActivityTimestamp = Date.now();\n" +
                    "                    location.reload();\n" +
                    "                }\n" +
                    "            } catch(e) {}\n" +
                    "        }, 1000);\n" +
                    "    }\n" +
                    "} catch(e) {}\n",
                    timeoutMs
                );
            }
        } catch (Exception ignored) {}

        while (true) {
            try {
                if (System.in.available() > 0) {
                    if (sc.hasNextLine()) {
                        String input = sc.nextLine().trim();
                        return input;
                    }
                }
            } catch (Exception ignored) {}

            // 1. Check if browser idle modal appeared
            try {
                if (driver != null) {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    Boolean isIdleModalVisible = (Boolean) js.executeScript(
                        "try {\n" +
                        "    var m = document.getElementById('autoReloadModal');\n" +
                        "    if (m && (m.classList.contains('show') || m.style.display === 'block' || window.getComputedStyle(m).display !== 'none')) return true;\n" +
                        "    var btn = Array.from(document.querySelectorAll('button, a')).find(function(b) { return (b.innerText || '').indexOf('Reload Manually') !== -1; });\n" +
                        "    if (btn && btn.offsetParent !== null) return true;\n" +
                        "    var txt = (document.body ? document.body.innerText : '');\n" +
                        "    return txt.indexOf('You were idle!') !== -1 && txt.indexOf('Reload Manually') !== -1;\n" +
                        "} catch(e) { return false; }"
                    );
                    if (Boolean.TRUE.equals(isIdleModalVisible)) {
                        System.out.println("\n[IDLE DETECTED] 'You were idle!' popup appeared on screen. Reloading page...");
                        js.executeScript(
                            "try {\n" +
                            "    var btn = document.querySelector('#autoReloadModal button, button[onclick*=\"reload\"]');\n" +
                            "    if (btn) btn.click(); else location.reload();\n" +
                            "} catch(e) { location.reload(); }\n"
                        );
                        Thread.sleep(2500);
                        try { OMSweb_Trade.restoreAllPreviousSettings(driver); } catch (Exception ignored) {}
                        idleStart = System.currentTimeMillis();
                        System.out.println("\n[IDLE WATCHDOG] Page reloaded & UI state restored. You are at the Main Menu.");
                        System.out.print("Enter Choice (1-6 to execute, 0=Exit): ");
                    }
                }
            } catch (Exception ignored) {}

            // 2. Check if idle timeout reached (e.g. 5 minutes)
            if (driver != null && System.currentTimeMillis() - idleStart >= timeoutMs) {
                System.out.printf("%n[IDLE %d-MIN WATCHDOG] %d minutes (%ds) idle without input. Reloading page and restoring session...%n", (idleSeconds / 60), (idleSeconds / 60), idleSeconds);
                try {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    js.executeScript("location.reload();");
                    Thread.sleep(2500);
                    try { OMSweb_Trade.restoreAllPreviousSettings(driver); } catch (Exception ignored) {}
                } catch (Exception e) {
                    try { driver.navigate().refresh(); } catch (Exception ignored) {}
                }
                idleStart = System.currentTimeMillis();
                System.out.println("\n[IDLE WATCHDOG] Page reloaded & UI state restored. You are at the Main Menu.");
                System.out.print("Enter Choice (1-6 to execute, 0=Exit): ");
            }

            try { Thread.sleep(200); } catch (Exception ignored) {}
        }
    }
}