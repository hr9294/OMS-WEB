import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverLogLevel;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * UI_testing - Cross-Browser Automated Tester (Functionality, Integration, UI)
 * Connects to existing OMS WEB resolved classes:
 *   OMSwebmain, OMSweb_Login, OMSweb_DashboardAudit, RMS,
 *   OMSweb_UploadData, OMSweb_ExportData, Day_Netting
 * Target Modules: Admin Dashboard, RMS, File Manager, Account, Trade Logs, Setting
 * Target Environments: Chrome, Firefox, Eclipse IDE, Safari (capability check)
 */
public class UI_testing {

    private static final String BASE_URL = "https://devoms.quantbd.com";
    private static final String OUT_DIR = System.getProperty(
            "ui.out.dir",
            "C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\scratch"
    );

    private static final String HOOK_JS =
        "if (!window.__uiTesterHooked) {\n" +
        "  window.__uiTesterHooked = true;\n" +
        "  window.__uiErrs = [];\n" +
        "  window.__uiConsoleErrs = [];\n" +
        "  window.__uiHttpFails = [];\n" +
        "  window.addEventListener('error', function(e) {\n" +
        "    window.__uiErrs.push((e.message || 'JS Error') + ' @ ' + (e.filename || '') + ':' + (e.lineno || 0));\n" +
        "  });\n" +
        "  window.addEventListener('unhandledrejection', function(e) {\n" +
        "    window.__uiErrs.push('PromiseRejection: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason)));\n" +
        "  });\n" +
        "  var origConsoleErr = console.error;\n" +
        "  console.error = function() {\n" +
        "    try { window.__uiConsoleErrs.push(Array.prototype.slice.call(arguments).map(String).join(' ').slice(0, 200)); } catch(ex) {}\n" +
        "    return origConsoleErr.apply(console, arguments);\n" +
        "  };\n" +
        "  if (window.fetch) {\n" +
        "    var origFetch = window.fetch;\n" +
        "    window.fetch = function() {\n" +
        "      var u = (arguments[0] && arguments[0].url) ? arguments[0].url : String(arguments[0]);\n" +
        "      return origFetch.apply(this, arguments).then(function(r) {\n" +
        "        if (r && r.status >= 400) window.__uiHttpFails.push('FETCH ' + r.status + ' ' + u);\n" +
        "        return r;\n" +
        "      }).catch(function(err) {\n" +
        "        window.__uiHttpFails.push('FETCH_ERR ' + u + ' -> ' + err);\n" +
        "        throw err;\n" +
        "      });\n" +
        "    };\n" +
        "  }\n" +
        "  var origOpen = XMLHttpRequest.prototype.open;\n" +
        "  XMLHttpRequest.prototype.open = function(m, url) {\n" +
        "    this.__reqUrl = m + ' ' + url;\n" +
        "    return origOpen.apply(this, arguments);\n" +
        "  };\n" +
        "  var origSend = XMLHttpRequest.prototype.send;\n" +
        "  XMLHttpRequest.prototype.send = function() {\n" +
        "    var self = this;\n" +
        "    this.addEventListener('load', function() {\n" +
        "      if (self.status >= 400) window.__uiHttpFails.push('XHR ' + self.status + ' ' + (self.__reqUrl || ''));\n" +
        "    });\n" +
        "    this.addEventListener('error', function() {\n" +
        "      window.__uiHttpFails.push('XHR_ERR ' + (self.__reqUrl || ''));\n" +
        "    });\n" +
        "    return origSend.apply(this, arguments);\n" +
        "  };\n" +
        "}\n";

    static class TargetPage {
        final String key;
        final String label;
        final String[] linkKeywords;
        final String[] textKeywords;

        TargetPage(String key, String label, String[] linkKeywords, String[] textKeywords) {
            this.key = key;
            this.label = label;
            this.linkKeywords = linkKeywords;
            this.textKeywords = textKeywords;
        }
    }

    private static final List<TargetPage> TARGET_PAGES = Arrays.asList(
        new TargetPage("admin_dashboard", "Admin Dashboard",
            new String[]{"home"},
            new String[]{"home", "admin dashboard", "dashboard"}),
        new TargetPage("rms", "RMS",
            new String[]{"rms/rms_ccd", "rms"},
            new String[]{"rms"}),
        new TargetPage("file_manager", "File Manager",
            new String[]{"broker_it/file_manager", "file_manager"},
            new String[]{"file manager"}),
        new TargetPage("account", "Account",
            new String[]{"account/"},
            new String[]{"account"}),
        new TargetPage("trade_logs", "Trade Logs (Order Logs)",
            new String[]{"live_orders/fix_order_logs", "fix_order_logs"},
            new String[]{"order logs", "trade logs"}),
        new TargetPage("setting", "Setting",
            new String[]{"settings/edit", "settings"},
            new String[]{"settings", "setting"})
    );

    public static void main(String[] args) {
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.out.println("=========================================================================");
        System.out.println("  UI_testing | Full Cross-Browser Suite (Connected to OMS WEB Classes)   ");
        System.out.println("=========================================================================");

        Map<String, Object> masterReport = new LinkedHashMap<>();
        masterReport.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        masterReport.put("baseUrl", BASE_URL);
        masterReport.put("eclipseExecution", Boolean.TRUE);
        masterReport.put("osName", System.getProperty("os.name"));
        masterReport.put("javaVersion", System.getProperty("java.version"));

        Map<String, Object> browsersReport = new LinkedHashMap<>();
        browsersReport.put("Chrome", runBrowserSuite("Chrome"));
        browsersReport.put("Firefox", runBrowserSuite("Firefox"));
        browsersReport.put("Safari", probeSafariAvailability());
        masterReport.put("browsers", browsersReport);

        Path jsonPath = Paths.get(OUT_DIR, "ui_testing_report_data.json");
        try {
            Files.createDirectories(Paths.get(OUT_DIR, "ui_shots"));
            Files.write(jsonPath, toJson(masterReport).getBytes(StandardCharsets.UTF_8));
            System.out.println("\n[UI_testing] Report JSON saved -> " + jsonPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[UI_testing: ERROR] Saving JSON failed: " + e.getMessage());
        }
        System.out.println("[UI_testing] Completed all browser checks.");
    }

    private static Map<String, Object> probeSafariAvailability() {
        Map<String, Object> res = new LinkedHashMap<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) {
            res.put("status", "BLOCKED_ON_WINDOWS");
            res.put("reason", "Apple Safari and safaridriver require macOS; native Safari is unavailable on " + System.getProperty("os.name"));
            System.out.println("\n[Safari] BLOCKED_ON_WINDOWS: safaridriver requires macOS (using WebKit CSS/JS static probes from DOM instead).");
            return res;
        }
        WebDriver drv = null;
        try {
            drv = new SafariDriver();
            res = executeSuiteOnDriver(drv, "Safari");
        } catch (Exception e) {
            res.put("status", "FAILED");
            res.put("reason", e.getMessage());
        } finally {
            if (drv != null) {
                try { drv.quit(); } catch (Exception ignored) {}
            }
        }
        return res;
    }

    private static Map<String, Object> runBrowserSuite(String browserName) {
        System.out.println("\n>>> Starting Browser Suite: " + browserName);
        WebDriver driver = null;
        try {
            if ("Chrome".equals(browserName)) {
                // Reuse OMSwebmain.createDriver() which resolves all Chrome popup, password leak,
                // download warning, SSL, and window configuration issues.
                driver = OMSwebmain.createDriver();
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(4));
            } else {
                System.setProperty("webdriver.firefox.logfile", new File(OUT_DIR, "geckodriver.log").getAbsolutePath());
                FirefoxOptions opts = new FirefoxOptions();
                opts.setAcceptInsecureCerts(true);
                opts.setLogLevel(FirefoxDriverLogLevel.ERROR);
                opts.addPreference("dom.webnotifications.enabled", false);
                opts.addPreference("dom.push.enabled", false);
                opts.addPreference("signon.rememberSignons", false);
                opts.addArguments("--width=1920", "--height=1080");
                driver = new FirefoxDriver(opts);
                driver.manage().window().setSize(new Dimension(1920, 1080));
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(25));
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(4));
            }
            return executeSuiteOnDriver(driver, browserName);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "ERROR");
            err.put("reason", e.getMessage());
            System.err.println("[" + browserName + " ERROR] " + e.getMessage());
            return err;
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
    }

    private static Map<String, Object> executeSuiteOnDriver(WebDriver driver, String browserName) {
        Map<String, Object> browserRes = new LinkedHashMap<>();
        browserRes.put("status", "EXECUTED");
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 1. Login
        long loginStart = System.currentTimeMillis();
        driver.get(BASE_URL);
        sleepMs(1500);
        try { js.executeScript(HOOK_JS); } catch (Exception ignored) {}
        String user = System.getenv("OMS_USERNAME");
        if (user == null || user.trim().isEmpty()) user = "Testadmin";
        String pass = System.getenv("OMS_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) pass = "12345";

        boolean loggedIn = false;
        try {
            List<WebElement> uInputs = driver.findElements(By.id("username"));
            if (!uInputs.isEmpty()) {
                uInputs.get(0).clear();
                uInputs.get(0).sendKeys(user);
                WebElement pInput = driver.findElement(By.id("password"));
                pInput.clear();
                pInput.sendKeys(pass);
                driver.findElement(By.xpath("//button[normalize-space()='Login' or @type='submit']")).click();
                sleepMs(2500);
                // Reuse OMSweb_Login & OMSwebmain popup/modal cleanup
                OMSweb_Login.pressOkAutomatically(driver);
                OMSwebmain.closeAllBrowserPopupsAndModals(driver);
            }
            loggedIn = driver.findElements(By.id("username")).isEmpty();
        } catch (Exception e) {
            browserRes.put("loginError", e.getMessage());
        }
        browserRes.put("loginSuccess", loggedIn);
        browserRes.put("loginMs", System.currentTimeMillis() - loginStart);
        browserRes.put("postLoginUrl", driver.getCurrentUrl());
        saveShot(driver, browserName, "00_post_login");

        // 2. Discover sidebar & header navigation items
        try { js.executeScript(HOOK_JS); } catch (Exception ignored) {}
        Object navRaw = js.executeScript(
            "var items = [];\n" +
            "var els = document.querySelectorAll('aside a, .sidebar a, nav a, [data-link], .nav-sidebar a, .menu-item a, .left-sidebar a, .sidebar li');\n" +
            "var seen = {};\n" +
            "for (var i = 0; i < els.length; i++) {\n" +
            "  var el = els[i];\n" +
            "  var dl = el.getAttribute('data-link') || '';\n" +
            "  var href = el.getAttribute('href') || '';\n" +
            "  var title = el.getAttribute('title') || el.getAttribute('data-original-title') || el.getAttribute('data-bs-original-title') || el.getAttribute('aria-label') || '';\n" +
            "  var txt = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim();\n" +
            "  var icon = el.querySelector('i, svg');\n" +
            "  var iconCls = icon ? (icon.getAttribute('class') || '') : '';\n" +
            "  var key = dl + '|' + href + '|' + title + '|' + txt;\n" +
            "  if ((dl || (href && href !== '#') || title || txt) && !seen[key]) {\n" +
            "    seen[key] = true;\n" +
            "    var r = el.getBoundingClientRect();\n" +
            "    items.push({ index: i, dataLink: dl, href: href, title: title, text: txt, iconClass: iconCls, visible: r.width > 0 && r.height > 0, x: Math.round(r.left), y: Math.round(r.top) });\n" +
            "  }\n" +
            "}\n" +
            "return items;"
        );
        browserRes.put("discoveredNavItems", navRaw);

        // 3. Global WebKit/Safari compatibility hazards
        Object webkitRisks = js.executeScript(
            "var hazards = { unprefixedBackdropFilter: 0, overflowOverlay: 0, dateDashStrings: 0, cssGridSubgrid: 0 };\n" +
            "try {\n" +
            "  for (var s = 0; s < document.styleSheets.length; s++) {\n" +
            "    var rules;\n" +
            "    try { rules = document.styleSheets[s].cssRules; } catch(ex) { continue; }\n" +
            "    if (!rules) continue;\n" +
            "    for (var r = 0; r < rules.length; r++) {\n" +
            "      var ct = rules[r].cssText || '';\n" +
            "      if (ct.indexOf('backdrop-filter') !== -1 && ct.indexOf('-webkit-backdrop-filter') === -1) hazards.unprefixedBackdropFilter++;\n" +
            "      if (ct.indexOf('overflow: overlay') !== -1 || ct.indexOf('overflow-y: overlay') !== -1) hazards.overflowOverlay++;\n" +
            "      if (ct.indexOf('subgrid') !== -1) hazards.cssGridSubgrid++;\n" +
            "    }\n" +
            "  }\n" +
            "} catch(e) {}\n" +
            "return hazards;"
        );
        browserRes.put("webkitCompatibilityAudit", webkitRisks);

        // 4. Test each of the 6 target pages (DOM + Functional + Connected Class Checks)
        List<Map<String, Object>> pagesRes = new ArrayList<>();
        for (TargetPage tp : TARGET_PAGES) {
            Map<String, Object> pRes = testTargetPage(driver, js, browserName, tp);
            pagesRes.add(pRes);
            System.out.printf("  [%s | %-23s] nav=%s | loadMs=%s | httpFails=%s | jsErr=%s | overflow1366=%s%n",
                    browserName, tp.label, pRes.get("navMethod"),
                    pRes.get("loadMs"), pRes.get("httpFailureCount"), pRes.get("jsErrorCount"), pRes.get("overflowAt1366"));
        }
        browserRes.put("pages", pagesRes);
        return browserRes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> testTargetPage(WebDriver driver, JavascriptExecutor js, String browserName, TargetPage tp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", tp.key);
        out.put("label", tp.label);

        long t0 = System.currentTimeMillis();
        try { js.executeScript(HOOK_JS + "window.__uiErrs=[]; window.__uiConsoleErrs=[]; window.__uiHttpFails=[];"); } catch (Exception ignored) {}

        String navMethod = navigateToPage(driver, js, tp);
        sleepMs(2000);
        OMSwebmain.closeAllBrowserPopupsAndModals(driver);
        try { js.executeScript(HOOK_JS); } catch (Exception ignored) {}

        out.put("navMethod", navMethod);
        out.put("finalUrl", driver.getCurrentUrl());
        out.put("pageTitle", driver.getTitle());
        out.put("loadMs", System.currentTimeMillis() - t0);
        saveShot(driver, browserName, tp.key + "_1920");

        // A. General DOM + UI Audit
        Map<String, Object> domAudit = (Map<String, Object>) js.executeScript(
            "var res = {};\n" +
            "var bodyText = (document.body ? document.body.innerText : '').trim();\n" +
            "res.bodyTextLength = bodyText.length;\n" +
            "var mainArea = document.querySelector('#main-content, #content, .content-wrapper, .page-content, main') || document.body;\n" +
            "var mainText = (mainArea ? mainArea.innerText : bodyText).trim();\n" +
            "res.mainContentSample = mainText.slice(0, 500).replace(/\\s+/g, ' ');\n" +
            "var h = (mainArea || document).querySelector('h1, h2, h3, h4, .page-title, .card-title, .header-title');\n" +
            "res.primaryHeading = h ? (h.innerText || '').trim() : '';\n" +
            "res.tableCount = document.querySelectorAll('table').length;\n" +
            "var visibleTables = 0, maxRows = 0, totalRows = 0, emptyTables = 0;\n" +
            "document.querySelectorAll('table').forEach(function(tb) {\n" +
            "  if (tb.offsetParent !== null) visibleTables++;\n" +
            "  var rc = tb.querySelectorAll('tbody tr').length;\n" +
            "  totalRows += rc;\n" +
            "  if (rc > maxRows) maxRows = rc;\n" +
            "  if (rc === 0) emptyTables++;\n" +
            "});\n" +
            "res.visibleTables = visibleTables;\n" +
            "res.totalTableRows = totalRows;\n" +
            "res.maxTableRows = maxRows;\n" +
            "res.emptyTables = emptyTables;\n" +
            "res.buttonCount = Array.from(document.querySelectorAll('button:not([disabled]), a.btn')).filter(function(b){ return b.offsetParent !== null; }).length;\n" +
            "res.inputCount = Array.from(document.querySelectorAll('input:not([type=hidden]), select, textarea')).filter(function(i){ return i.offsetParent !== null; }).length;\n" +
            "res.tabCount = document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link').length;\n" +
            "var brokenImgs = [];\n" +
            "document.querySelectorAll('img').forEach(function(img){\n" +
            "  if (img.offsetParent !== null && (!img.complete || img.naturalWidth === 0)) brokenImgs.push(img.getAttribute('src') || 'img');\n" +
            "});\n" +
            "res.brokenImages = brokenImgs;\n" +
            "res.brokenImageCount = brokenImgs.length;\n" +
            "res.hasNaNText = /\\b(NaN|undefined|null)\\b/.test(mainText);\n" +
            "res.hasServerErrorText = /\\b(404 Not Found|500 Internal Server|Whoops|Fatal error|SQLSTATE|Exception in)\\b/i.test(mainText);\n" +
            "res.horizontalOverflow1920 = document.documentElement.scrollWidth > document.documentElement.clientWidth;\n" +
            "res.scrollWidth1920 = document.documentElement.scrollWidth;\n" +
            "res.clientWidth1920 = document.documentElement.clientWidth;\n" +
            "var clipped = [];\n" +
            "document.querySelectorAll('table, .card, .btn, th, td').forEach(function(el){\n" +
            "  var r = el.getBoundingClientRect();\n" +
            "  if (r.width > 0 && r.height > 0 && r.right > window.innerWidth + 4 && clipped.length < 10) {\n" +
            "    clipped.push(el.tagName + (el.id ? '#' + el.id : '') + ':' + Math.round(r.right) + '/' + window.innerWidth);\n" +
            "  }\n" +
            "});\n" +
            "res.clippedElements1920 = clipped;\n" +
            "return res;"
        );
        if (domAudit != null) out.putAll(domAudit);

        // B. Connected Day_Netting UI Layout Health check
        try {
            String health = Day_Netting.verifyUiLayoutHealth(driver);
            out.put("dayNettingUiHealth", health != null ? health.replaceAll("\\s+", " ").trim() : "OK");
        } catch (Exception e) {
            out.put("dayNettingUiHealth", "ERR: " + e.getMessage());
        }

        // C. Module-Specific Deep Functional & Integration Tests
        Map<String, Object> funcTests = new LinkedHashMap<>();
        try {
            if ("admin_dashboard".equals(tp.key)) {
                funcTests = testAdminDashboard(driver, js, browserName);
            } else if ("rms".equals(tp.key)) {
                funcTests = testRmsModule(driver, js);
            } else if ("file_manager".equals(tp.key)) {
                funcTests = testFileManagerModule(driver, js);
            } else if ("account".equals(tp.key)) {
                funcTests = testAccountModule(driver, js);
            } else if ("trade_logs".equals(tp.key)) {
                funcTests = testTradeLogsModule(driver, js);
            } else if ("setting".equals(tp.key)) {
                funcTests = testSettingModule(driver, js);
            }
        } catch (Exception e) {
            funcTests.put("moduleError", e.getMessage());
        }
        out.put("functionalTests", funcTests);

        // D. Responsive check at 1366x768
        try {
            driver.manage().window().setSize(new Dimension(1366, 768));
            sleepMs(450);
            Map<String, Object> resp1366 = (Map<String, Object>) js.executeScript(
                "var clipped = 0;\n" +
                "document.querySelectorAll('table, .card, .btn').forEach(function(el){\n" +
                "  var r = el.getBoundingClientRect();\n" +
                "  if (r.width > 0 && r.right > window.innerWidth + 4) clipped++;\n" +
                "});\n" +
                "return { overflowAt1366: document.documentElement.scrollWidth > document.documentElement.clientWidth, scrollWidth1366: document.documentElement.scrollWidth, clientWidth1366: document.documentElement.clientWidth, clipped1366: clipped };"
            );
            out.putAll(resp1366);
            saveShot(driver, browserName, tp.key + "_1366");
            driver.manage().window().setSize(new Dimension(1920, 1080));
            sleepMs(250);
        } catch (Exception e) {
            out.put("overflowAt1366", "ERR");
        }

        // E. Collect runtime JS & HTTP errors
        List<String> jsErrs = (List<String>) js.executeScript("return window.__uiErrs || [];");
        List<String> conErrs = (List<String>) js.executeScript("return window.__uiConsoleErrs || [];");
        List<String> httpFails = (List<String>) js.executeScript("return window.__uiHttpFails || [];");
        out.put("jsErrors", jsErrs);
        out.put("jsErrorCount", jsErrs != null ? jsErrs.size() : 0);
        out.put("consoleErrors", conErrs);
        out.put("consoleErrorCount", conErrs != null ? conErrs.size() : 0);
        out.put("httpFailures", httpFails);
        out.put("httpFailureCount", httpFails != null ? httpFails.size() : 0);

        return out;
    }

    private static String navigateToPage(WebDriver driver, JavascriptExecutor js, TargetPage tp) {
        // Ensure we are inside the authenticated SPA shell (/home) so sidebar [data-link] items exist
        try {
            Long navCount = (Long) js.executeScript("return document.querySelectorAll('[data-link]').length;");
            if (navCount == null || navCount == 0) {
                driver.get(BASE_URL + "/home");
                sleepMs(1800);
                OMSwebmain.closeAllBrowserPopupsAndModals(driver);
            }
        } catch (Exception ignored) {}

        try {
            if ("admin_dashboard".equals(tp.key)) {
                if (OMSweb_DashboardAudit.navigateToDashboard(driver)) return "CONNECTED:OMSweb_DashboardAudit";
            } else if ("file_manager".equals(tp.key)) {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                if (OMSweb_UploadData.navigateToUploadDataPage(driver, wait)) return "CONNECTED:OMSweb_UploadData";
            }
        } catch (Exception ignored) {}

        Object clicked = js.executeScript(
            "var linkKws = arguments[0], textKws = arguments[1];\n" +
            "var els = Array.from(document.querySelectorAll('[data-link], aside a, .sidebar a, nav a, .nav-sidebar a, .menu-item a, .left-sidebar a, .sidebar li a'));\n" +
            "for (var k = 0; k < linkKws.length; k++) {\n" +
            "  var kw = linkKws[k].toLowerCase();\n" +
            "  for (var i = 0; i < els.length; i++) {\n" +
            "    var dl = (els[i].getAttribute('data-link') || '').toLowerCase();\n" +
            "    if (dl === kw) { try { els[i].click(); return 'SIDEBAR_EXACT:' + dl; } catch(e) {} }\n" +
            "  }\n" +
            "}\n" +
            "for (var k = 0; k < textKws.length; k++) {\n" +
            "  var kw = textKws[k].toLowerCase();\n" +
            "  for (var i = 0; i < els.length; i++) {\n" +
            "    var el = els[i];\n" +
            "    var title = (el.getAttribute('title') || el.getAttribute('data-original-title') || el.getAttribute('data-bs-original-title') || '').toLowerCase().trim();\n" +
            "    var txt = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();\n" +
            "    if (title === kw || txt === kw) { try { el.click(); return 'SIDEBAR_TEXT:' + kw; } catch(e) {} }\n" +
            "  }\n" +
            "}\n" +
            "return 'NOT_FOUND';"
            , Arrays.asList(tp.linkKeywords), Arrays.asList(tp.textKeywords)
        );
        return String.valueOf(clicked);
    }

    // -------------------------------------------------------------------------
    // 1. ADMIN DASHBOARD (Connected to OMSweb_DashboardAudit)
    // -------------------------------------------------------------------------
    private static Map<String, Object> testAdminDashboard(WebDriver driver, JavascriptExecutor js, String browserName) {
        Map<String, Object> m = new LinkedHashMap<>();
        OMSweb_DashboardAudit.waitForDashboardReady(driver, 8);
        OMSweb_DashboardAudit.DashboardSnapshot snap = OMSweb_DashboardAudit.captureSnapshot(driver, browserName);

        double turnover = OMSweb_DashboardAudit.parseNumSafe(snap.turnover.bigValue);
        double buyVal = OMSweb_DashboardAudit.parseNumSafe(snap.buyValue.bigValue);
        double sellVal = OMSweb_DashboardAudit.parseNumSafe(snap.sellValue.bigValue);
        double diff = Math.abs(turnover - (buyVal + sellVal));

        m.put("turnover", snap.turnover.bigValue);
        m.put("buyValue", snap.buyValue.bigValue);
        m.put("sellValue", snap.sellValue.bigValue);
        m.put("turnoverMatchesBuyPlusSell", diff <= 1.0 || turnover == 0.0);
        m.put("brokerLimitDseUsed", snap.brokerLimit.dseUsed);
        m.put("brokerLimitCseUsed", snap.brokerLimit.cseUsed);
        m.put("tradesCount", snap.trades.bigCount);
        m.put("activeUsersOnline", snap.activeUsers.usersOnline);
        m.put("activeUsersTotal", snap.activeUsers.usersTotal);
        m.put("roleTotalsSum", snap.activeUsers.dealersTotal + snap.activeUsers.clientsTotal + snap.activeUsers.associatesTotal);
        m.put("mostTradedStocksRows", snap.mostTradedStocks.size());
        m.put("exchangeContributionRows", snap.exchangeContribution.size());
        m.put("topDealersRows", snap.topDealers.size());
        m.put("topAssociatesRows", snap.topAssociates.size());
        m.put("mostTradedClientsRows", snap.mostTradedClients.size());

        // Interactive tab switch test (Dealers <-> Associates if tabbed)
        Object tabTest = js.executeScript(
            "var tabs = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link')).filter(function(t){ return t.offsetParent !== null; });\n" +
            "if (tabs.length < 2) return 'NO_TABS(' + tabs.length + ')';\n" +
            "var orig = tabs[0];\n" +
            "tabs[1].click();\n" +
            "var switchedText = (tabs[1].innerText || '').trim();\n" +
            "orig.click();\n" +
            "return 'SWITCHED_OK:' + switchedText;"
        );
        m.put("tabSwitchTest", String.valueOf(tabTest));
        return m;
    }

    // -------------------------------------------------------------------------
    // 2. RMS MODULE (Connected to RMS.java)
    // -------------------------------------------------------------------------
    private static Map<String, Object> testRmsModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();

        // Fetch Application Settings via RMS.fetchApplicationSettings()
        RMS.RMSApplicationSettings settings = RMS.fetchApplicationSettings(driver);
        m.put("apiDayNettingMode", settings.dayNettingSettings);
        m.put("apiAllowZAndSpot", settings.allowZAndSpotNetting);
        m.put("apiDseNettingEnabled", settings.isDseDayNettingEnabled);
        m.put("apiCseNettingEnabled", settings.isCseDayNettingEnabled);

        // Scan all visible RMS tabs and exercise connected RMS navigators
        Object tabsScan = js.executeScript(
            "var tabs = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link')).filter(function(t){ return t.offsetParent !== null; });\n" +
            "return tabs.map(function(t){ return (t.innerText || '').replace(/\\s+/g,' ').trim(); });"
        );
        m.put("visibleTabs", tabsScan);

        boolean clientsNav = RMS.navigateToClientsRmsTab(driver);
        sleepMs(500);
        // Check Save Changes disabled state before selecting a client
        Object saveBtnDisabled = js.executeScript(
            "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return /save changes/i.test(b.innerText || ''); });\n" +
            "if (!btn) return 'NOT_FOUND';\n" +
            "return btn.disabled || btn.classList.contains('disabled') ? 'DISABLED_SAFE' : 'ENABLED_WITHOUT_CLIENT';"
        );
        m.put("clientsTabLoaded", clientsNav);
        m.put("saveChangesWithoutClientState", String.valueOf(saveBtnDisabled));

        // Search client "23986" on Client RMS tab
        Object clientSearch = js.executeScript(
            "var inp = Array.from(document.querySelectorAll('input[type=text], input[type=search], input[placeholder*=\"client\" i], input[placeholder*=\"search\" i]')).find(function(el){ return el.offsetParent !== null; });\n" +
            "if (!inp) return 'NO_SEARCH_INPUT';\n" +
            "inp.value = '23986';\n" +
            "inp.dispatchEvent(new Event('input', {bubbles:true}));\n" +
            "inp.dispatchEvent(new Event('change', {bubbles:true}));\n" +
            "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /search/i.test(b.innerText || ''); });\n" +
            "if (btn) btn.click();\n" +
            "return 'SEARCH_TRIGGERED';"
        );
        sleepMs(900);
        Object clientRowsAfter = js.executeScript(
            "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });\n" +
            "return tb ? tb.querySelectorAll('tbody tr').length : -1;"
        );
        m.put("clientSearch23986", clientSearch + "|rows=" + clientRowsAfter);

        m.put("applicableSymbolTabNav", RMS.navigateToApplicableSymbolTab(driver));
        sleepMs(400);
        m.put("unauthorizedBoardsTabNav", RMS.navigateToUnauthorizedBoardsTab(driver));
        sleepMs(400);
        m.put("appSettingsTabNav", RMS.navigateToApplicationSettings(driver));
        sleepMs(500);

        return m;
    }

    // -------------------------------------------------------------------------
    // 3. FILE MANAGER MODULE (Connected to OMSweb_UploadData & OMSweb_ExportData)
    // -------------------------------------------------------------------------
    private static Map<String, Object> testFileManagerModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();

        // Verify all 9 Upload Data sub-menus (read-only check; zero uploads)
        boolean uploadMenuOpened = OMSweb_UploadData.openUploadDataMenu(driver);
        List<String> missingUploadMenus = new ArrayList<>();
        for (OMSweb_UploadData.UploadSubMenu sub : OMSweb_UploadData.UploadSubMenu.values()) {
            if (!OMSweb_UploadData.verifySubMenuPresent(driver, sub)) {
                missingUploadMenus.add(sub.name());
            }
        }
        m.put("uploadMenuOpened", uploadMenuOpened);
        m.put("uploadSubMenusExpected", OMSweb_UploadData.UploadSubMenu.values().length);
        m.put("uploadSubMenusMissing", missingUploadMenus);

        // Verify all 13 Export Files sub-menus (read-only check; zero exports)
        boolean exportMenuOpened = OMSweb_ExportData.openExportFilesMenu(driver);
        List<String> missingExportMenus = new ArrayList<>();
        for (OMSweb_ExportData.ExportSubMenu sub : OMSweb_ExportData.ExportSubMenu.values()) {
            if (!OMSweb_ExportData.verifySubMenuPresent(driver, sub)) {
                missingExportMenus.add(sub.name());
            }
        }
        m.put("exportMenuOpened", exportMenuOpened);
        m.put("exportSubMenusExpected", OMSweb_ExportData.ExportSubMenu.values().length);
        m.put("exportSubMenusMissing", missingExportMenus);

        // Close any open dropdown cleanly
        try { js.executeScript("document.body.click();"); } catch (Exception ignored) {}
        sleepMs(300);

        // Audit History table columns, badges, actions, and search/pagination presence
        Object historyAudit = js.executeScript(
            "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });\n" +
            "if (!tb) return { found: false };\n" +
            "var headers = Array.from(tb.querySelectorAll('thead th')).map(function(th){ return (th.innerText || '').trim(); });\n" +
            "var rows = tb.querySelectorAll('tbody tr');\n" +
            "var hasSearch = Array.from(document.querySelectorAll('input[type=search], input[placeholder*=\"search\" i]')).some(function(i){ return i.offsetParent !== null; });\n" +
            "var hasPagination = !!document.querySelector('.pagination, .dataTables_paginate');\n" +
            "var actionIcons = rows.length > 0 ? rows[0].querySelectorAll('a, button, i, svg').length : 0;\n" +
            "return { found: true, headers: headers, rowCount: rows.length, hasSearch: hasSearch, hasPagination: hasPagination, firstRowActionControls: actionIcons };"
        );
        m.put("historyTableAudit", historyAudit);
        return m;
    }

    // -------------------------------------------------------------------------
    // 4. ACCOUNT MODULE
    // -------------------------------------------------------------------------
    private static Map<String, Object> testAccountModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();

        Object profileCheck = js.executeScript(
            "var txt = (document.body ? document.body.innerText : '');\n" +
            "var pwdInputs = Array.from(document.querySelectorAll('input[type=password]')).filter(function(i){ return i.offsetParent !== null; });\n" +
            "var eyeIcons = Array.from(document.querySelectorAll('i.fa-eye, i.fa-eye-slash, .password-toggle, [data-toggle=password]')).filter(function(i){ return i.offsetParent !== null; });\n" +
            "var activeBadge = Array.from(document.querySelectorAll('.badge, span')).find(function(b){ return /^active$/i.test((b.innerText || '').trim()); });\n" +
            "return {\n" +
            "  hasUsernameLabel: /username/i.test(txt),\n" +
            "  hasUserRoleLabel: /user role/i.test(txt),\n" +
            "  hasAccountStatusLabel: /account status/i.test(txt),\n" +
            "  hasActiveBadge: !!activeBadge,\n" +
            "  passwordInputCount: pwdInputs.length,\n" +
            "  passwordEyeToggleCount: eyeIcons.length\n" +
            "};"
        );
        m.put("profileAndForm", profileCheck);

        // Test empty password form submission safety (should validate client-side without crashing/navigating)
        String beforeUrl = driver.getCurrentUrl();
        Object submitRes = js.executeScript(
            "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /(change|update|save).*password/i.test(b.innerText || ''); });\n" +
            "if (!btn) return 'NO_SUBMIT_BTN';\n" +
            "btn.click();\n" +
            "return 'CLICKED:' + (btn.innerText || '').trim();"
        );
        sleepMs(500);
        m.put("emptySubmitTest", submitRes + "|stayedOnPage=" + beforeUrl.equals(driver.getCurrentUrl()));

        // Test eye toggle if present
        Object eyeTest = js.executeScript(
            "var inp = document.querySelector('input[type=password]');\n" +
            "var eye = document.querySelector('i.fa-eye, i.fa-eye-slash, .password-toggle');\n" +
            "if (!inp || !eye) return 'NO_TOGGLE_OR_INPUT';\n" +
            "var t1 = inp.type;\n" +
            "eye.click();\n" +
            "var t2 = inp.type;\n" +
            "if (t2 !== t1) eye.click();\n" +
            "return 'before=' + t1 + ',after=' + t2;"
        );
        m.put("passwordVisibilityToggle", String.valueOf(eyeTest));
        return m;
    }

    // -------------------------------------------------------------------------
    // 5. TRADE LOGS / FIX ORDER LOGS MODULE
    // -------------------------------------------------------------------------
    private static Map<String, Object> testTradeLogsModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();

        Object logsAudit = js.executeScript(
            "var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]), select')).filter(function(el){ return el.offsetParent !== null; });\n" +
            "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });\n" +
            "var thCount = tb ? tb.querySelectorAll('thead th').length : 0;\n" +
            "var rows = tb ? tb.querySelectorAll('tbody tr').length : 0;\n" +
            "var rejectedRows = 0, rejectedWithMsg = 0;\n" +
            "if (tb) {\n" +
            "  tb.querySelectorAll('tbody tr').forEach(function(tr){\n" +
            "    if (/rejected/i.test(tr.innerText || '')) {\n" +
            "      rejectedRows++;\n" +
            "      var tds = tr.querySelectorAll('td');\n" +
            "      if (tds.length > 0 && (tds[tds.length - 1].innerText || '').trim().length > 0) rejectedWithMsg++;\n" +
            "    }\n" +
            "  });\n" +
            "}\n" +
            "var txt = (document.body ? document.body.innerText : '');\n" +
            "var countMatch = txt.match(/(\\d+)\\s+of\\s+(\\d+)\\s+logs/i);\n" +
            "return {\n" +
            "  filterControlCount: inputs.length,\n" +
            "  tableColumns: thCount,\n" +
            "  initialRowCount: rows,\n" +
            "  rejectedRows: rejectedRows,\n" +
            "  rejectedWithErrorMsg: rejectedWithMsg,\n" +
            "  logBadgeText: countMatch ? countMatch[0] : 'NOT_FOUND',\n" +
            "  hasStatusLegend: /success/i.test(txt) && /pending/i.test(txt) && /failed/i.test(txt)\n" +
            "};"
        );
        m.put("logsOverview", logsAudit);

        // Test filter + Clear button
        Object filterCycle = js.executeScript(
            "var inp = Array.from(document.querySelectorAll('input[type=text], input[type=search]')).find(function(i){ return i.offsetParent !== null; });\n" +
            "var searchBtn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /^search$/i.test((b.innerText || '').trim()); });\n" +
            "var clearBtn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /^clear$/i.test((b.innerText || '').trim()); });\n" +
            "if (!inp || !searchBtn) return 'MISSING_CONTROLS';\n" +
            "var beforeRows = document.querySelectorAll('table tbody tr').length;\n" +
            "inp.value = 'ZZZ_NO_MATCH';\n" +
            "inp.dispatchEvent(new Event('input', {bubbles:true}));\n" +
            "searchBtn.click();\n" +
            "return { beforeRows: beforeRows, hasClearBtn: !!clearBtn };"
        );
        sleepMs(800);
        Object afterFilterRows = js.executeScript(
            "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });\n" +
            "var afterRows = tb ? tb.querySelectorAll('tbody tr').length : -1;\n" +
            "var clearBtn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /^clear$/i.test((b.innerText || '').trim()); });\n" +
            "if (clearBtn) clearBtn.click();\n" +
            "return afterRows;"
        );
        sleepMs(600);
        m.put("filterSearchAndClearTest", filterCycle + "|afterFilterRows=" + afterFilterRows);
        return m;
    }

    // -------------------------------------------------------------------------
    // 6. SETTING MODULE
    // -------------------------------------------------------------------------
    private static Map<String, Object> testSettingModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();

        Object settingsMeta = js.executeScript(
            "var txt = (document.body ? document.body.innerText : '');\n" +
            "var tabs = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link')).filter(function(t){ return t.offsetParent !== null; }).map(function(t){ return (t.innerText || '').trim(); });\n" +
            "var fontInp = Array.from(document.querySelectorAll('input')).find(function(i){ return i.offsetParent !== null && /^\\.?\\d+(\\.\\d+)?$/.test((i.value || '').trim()); });\n" +
            "return {\n" +
            "  tabs: tabs,\n" +
            "  hasProductUftfast: /UFTFAST OMS/i.test(txt),\n" +
            "  hasVersion: /v\\d+\\.\\d+\\.\\d+/i.test(txt),\n" +
            "  hasFixVersion: /5\\.0\\s*SP1/i.test(txt),\n" +
            "  fontSizeValue: fontInp ? fontInp.value : 'NOT_FOUND',\n" +
            "  fontSizeLeadingZeroMissing: fontInp ? /^\\.\\d+$/.test((fontInp.value || '').trim()) : false\n" +
            "};"
        );
        m.put("settingsMetadata", settingsMeta);

        // Check Save Settings button visibility at 1366x768
        driver.manage().window().setSize(new Dimension(1366, 768));
        sleepMs(350);
        Object saveBtn1366 = js.executeScript(
            "var btn = Array.from(document.querySelectorAll('button')).find(function(b){ return b.offsetParent !== null && /save settings/i.test(b.innerText || ''); });\n" +
            "if (!btn) return 'NOT_FOUND';\n" +
            "var r = btn.getBoundingClientRect();\n" +
            "var obscured = r.bottom > window.innerHeight - 28;\n" +
            "return { top: Math.round(r.top), bottom: Math.round(r.bottom), innerHeight: window.innerHeight, obscuredByBottomBar: obscured };"
        );
        m.put("saveSettingsAt1366", saveBtn1366);
        driver.manage().window().setSize(new Dimension(1920, 1080));
        sleepMs(250);

        // Test Activity Logs tab switch
        Object activitySwitch = js.executeScript(
            "var tab = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link, a, button')).find(function(el){ return el.offsetParent !== null && /activity logs/i.test(el.innerText || ''); });\n" +
            "if (!tab) return 'NO_ACTIVITY_LOGS_TAB';\n" +
            "tab.click();\n" +
            "return 'CLICKED_ACTIVITY_LOGS';"
        );
        sleepMs(700);
        Object activityContent = js.executeScript(
            "var tb = Array.from(document.querySelectorAll('table')).find(function(t){ return t.offsetParent !== null; });\n" +
            "return { hasTable: !!tb, rows: tb ? tb.querySelectorAll('tbody tr').length : 0 };"
        );
        // Switch back to Settings tab
        try {
            js.executeScript(
                "var tab = Array.from(document.querySelectorAll('[role=tab], .nav-tabs .nav-link, .nav-pills .nav-link, a, button')).find(function(el){ return el.offsetParent !== null && /^settings$/i.test((el.innerText || '').trim()); });\n" +
                "if (tab) tab.click();"
            );
        } catch (Exception ignored) {}
        m.put("activityLogsTabTest", activitySwitch + "|" + activityContent);
        return m;
    }

    private static void saveShot(WebDriver driver, String browserName, String name) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dst = Paths.get(OUT_DIR, "ui_shots", browserName + "_" + name + ".png");
            Files.createDirectories(dst.getParent());
            Files.copy(src.toPath(), dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // Compact JSON serializer (zero external dependencies)
    private static String toJson(Object val) {
        if (val == null) return "null";
        if (val instanceof Boolean || val instanceof Number) return String.valueOf(val);
        if (val instanceof Map<?, ?>) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(esc(String.valueOf(e.getKey()))).append("\":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (val instanceof List<?>) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) val) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            return sb.append("]").toString();
        }
        return "\"" + esc(String.valueOf(val)) + "\"";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
