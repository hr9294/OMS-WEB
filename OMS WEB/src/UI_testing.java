import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.*;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * UI_testing - Cross-Browser QA Suite connected to OMSwebmain, OMSweb_Login,
 * OMSweb_DashboardAudit, RMS, OMSweb_UploadData, OMSweb_ExportData, Day_Netting.
 */
public class UI_testing {
    private static final String BASE_URL = "https://devoms.quantbd.com";
    private static final String OUT_DIR = System.getProperty("ui.out.dir",
            "C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\scratch");
    static class TargetPage {
        final String key, label;
        final String[] linkKeywords, textKeywords;
        TargetPage(String key, String label, String[] linkKeywords, String[] textKeywords) {
            this.key = key; this.label = label;
            this.linkKeywords = linkKeywords; this.textKeywords = textKeywords;
        }
    }
    private static final List<TargetPage> TARGET_PAGES = Arrays.asList(
        new TargetPage("admin_dashboard", "Admin Dashboard", new String[]{"home"}, new String[]{"home", "admin dashboard", "dashboard"}),
        new TargetPage("rms", "RMS", new String[]{"rms/rms_ccd", "rms"}, new String[]{"rms"}),
        new TargetPage("file_manager", "File Manager", new String[]{"broker_it/file_manager", "file_manager"}, new String[]{"file manager"}),
        new TargetPage("account", "Account", new String[]{"account/"}, new String[]{"account"}),
        new TargetPage("trade_logs", "Trade Logs (Order Logs)", new String[]{"live_orders/fix_order_logs", "fix_order_logs"}, new String[]{"order logs", "trade logs"}),
        new TargetPage("setting", "Setting", new String[]{"settings/edit", "settings"}, new String[]{"settings", "setting"})
    );
    private static final String HOOK_JS =
        "if (!window.__uiTesterHooked) {" +
        "  window.__uiTesterHooked=true; window.__uiErrs=[]; window.__uiConsoleErrs=[]; window.__uiHttpFails=[];" +
        "  window.addEventListener('error', function(e){ window.__uiErrs.push((e.message||'JS Error')+' @ '+(e.filename||'')+':'+(e.lineno||0)); });" +
        "  window.addEventListener('unhandledrejection', function(e){ window.__uiErrs.push('PromiseRejection: '+(e.reason&&e.reason.message?e.reason.message:String(e.reason))); });" +
        "  var ce=console.error; console.error=function(){ try{ window.__uiConsoleErrs.push(Array.prototype.slice.call(arguments).map(String).join(' ').slice(0,200)); }catch(x){} return ce.apply(console,arguments); };" +
        "  if (window.fetch) { var of=window.fetch; window.fetch=function(){ var u=(arguments[0]&&arguments[0].url)?arguments[0].url:String(arguments[0]); return of.apply(this,arguments).then(function(r){ if(r&&r.status>=400) window.__uiHttpFails.push('FETCH '+r.status+' '+u); return r; }).catch(function(err){ window.__uiHttpFails.push('FETCH_ERR '+u+' -> '+err); throw err; }); }; }" +
        "  var oo=XMLHttpRequest.prototype.open; XMLHttpRequest.prototype.open=function(m,u){ this.__reqUrl=m+' '+u; return oo.apply(this,arguments); };" +
        "  var os=XMLHttpRequest.prototype.send; XMLHttpRequest.prototype.send=function(){ var s=this; this.addEventListener('load',function(){ if(s.status>=400) window.__uiHttpFails.push('XHR '+s.status+' '+(s.__reqUrl||'')); }); this.addEventListener('error',function(){ window.__uiHttpFails.push('XHR_ERR '+(s.__reqUrl||'')); }); return os.apply(this,arguments); };" +
        "}";

    public static void main(String[] args) {
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.out.println("=== UI_testing | Full Cross-Browser Suite (Connected to OMS WEB Classes) ===");
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
            System.out.println("[UI_testing] Report JSON saved -> " + jsonPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[UI_testing: ERROR] Saving JSON failed: " + e.getMessage());
        }
    }

    // --- Browser Setup ---
    private static Map<String, Object> runBrowserSuite(String browserName) {
        System.out.println("\n>>> Starting Browser Suite: " + browserName);
        WebDriver driver = null;
        try {
            if ("Chrome".equals(browserName)) {
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
            if (driver != null) try { driver.quit(); } catch (Exception ignored) {}
        }
    }
    private static Map<String, Object> probeSafariAvailability() {
        Map<String, Object> res = new LinkedHashMap<>();
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            res.put("status", "BLOCKED_ON_WINDOWS");
            res.put("reason", "Apple Safari and safaridriver require macOS; unavailable on " + System.getProperty("os.name"));
            System.out.println("[Safari] BLOCKED_ON_WINDOWS: safaridriver requires macOS.");
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
            if (drv != null) try { drv.quit(); } catch (Exception ignored) {}
        }
        return res;
    }

    // --- Main Test Flow ---
    private static Map<String, Object> executeSuiteOnDriver(WebDriver driver, String browserName) {
        Map<String, Object> browserRes = new LinkedHashMap<>();
        browserRes.put("status", "EXECUTED");
        JavascriptExecutor js = (JavascriptExecutor) driver;
        loginToOms(driver, js, browserName, browserRes);
        try { js.executeScript(HOOK_JS); } catch (Exception ignored) {}
        browserRes.put("discoveredNavItems", discoverSidebarItems(js));
        browserRes.put("webkitCompatibilityAudit", auditWebKitCss(js));
        List<Map<String, Object>> pagesRes = new ArrayList<>();
        for (TargetPage tp : TARGET_PAGES) {
            Map<String, Object> pRes = testTargetPage(driver, js, browserName, tp);
            pagesRes.add(pRes);
            System.out.printf("  [%s | %-23s] nav=%s | loadMs=%s | httpFails=%s | jsErr=%s | overflow1366=%s%n",
                    browserName, tp.label, pRes.get("navMethod"), pRes.get("loadMs"),
                    pRes.get("httpFailureCount"), pRes.get("jsErrorCount"), pRes.get("overflowAt1366"));
        }
        browserRes.put("pages", pagesRes);
        return browserRes;
    }

    private static void loginToOms(WebDriver driver, JavascriptExecutor js, String browserName, Map<String, Object> browserRes) {
        long t0 = System.currentTimeMillis();
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
                uInputs.get(0).clear(); uInputs.get(0).sendKeys(user);
                WebElement pInput = driver.findElement(By.id("password"));
                pInput.clear(); pInput.sendKeys(pass);
                driver.findElement(By.xpath("//button[normalize-space()='Login' or @type='submit']")).click();
                sleepMs(2500);
                OMSweb_Login.pressOkAutomatically(driver);
                OMSwebmain.closeAllBrowserPopupsAndModals(driver);
            }
            loggedIn = driver.findElements(By.id("username")).isEmpty();
        } catch (Exception e) {
            browserRes.put("loginError", e.getMessage());
        }
        browserRes.put("loginSuccess", loggedIn);
        browserRes.put("loginMs", System.currentTimeMillis() - t0);
        browserRes.put("postLoginUrl", driver.getCurrentUrl());
        saveShot(driver, browserName, "00_post_login");
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
        Map<String, Object> domAudit = (Map<String, Object>) js.executeScript(
            "var res={}, bt=(document.body?document.body.innerText:'').trim(), ma=document.querySelector('#main-content,#content,.content-wrapper,.page-content,main')||document.body, mt=(ma?ma.innerText:bt).trim();" +
            "res.bodyTextLength=bt.length; res.mainContentSample=mt.slice(0,500).replace(/\\s+/g,' ');" +
            "var h=(ma||document).querySelector('h1,h2,h3,h4,.page-title,.card-title,.header-title'); res.primaryHeading=h?(h.innerText||'').trim():'';" +
            "var tables=document.querySelectorAll('table'), vis=0, maxR=0, totR=0, emp=0;" +
            "tables.forEach(function(tb){ if(tb.offsetParent!==null) vis++; var rc=tb.querySelectorAll('tbody tr').length; totR+=rc; if(rc>maxR) maxR=rc; if(rc===0) emp++; });" +
            "res.tableCount=tables.length; res.visibleTables=vis; res.totalTableRows=totR; res.maxTableRows=maxR; res.emptyTables=emp;" +
            "res.buttonCount=Array.from(document.querySelectorAll('button:not([disabled]),a.btn')).filter(function(b){return b.offsetParent!==null;}).length;" +
            "res.inputCount=Array.from(document.querySelectorAll('input:not([type=hidden]),select,textarea')).filter(function(i){return i.offsetParent!==null;}).length;" +
            "res.tabCount=document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link').length;" +
            "var broken=[]; document.querySelectorAll('img').forEach(function(img){ if(img.offsetParent!==null&&(!img.complete||img.naturalWidth===0)) broken.push(img.getAttribute('src')||'img'); });" +
            "res.brokenImages=broken; res.brokenImageCount=broken.length;" +
            "res.hasNaNText=/\\b(NaN|undefined|null)\\b/.test(mt); res.hasServerErrorText=/\\b(404 Not Found|500 Internal Server|Whoops|Fatal error|SQLSTATE|Exception in)\\b/i.test(mt);" +
            "res.horizontalOverflow1920=document.documentElement.scrollWidth>document.documentElement.clientWidth;" +
            "res.scrollWidth1920=document.documentElement.scrollWidth; res.clientWidth1920=document.documentElement.clientWidth;" +
            "var clipped=[]; document.querySelectorAll('table,.card,.btn,th,td').forEach(function(el){ var r=el.getBoundingClientRect(); if(r.width>0&&r.height>0&&r.right>window.innerWidth+4&&clipped.length<10) clipped.push(el.tagName+(el.id?'#'+el.id:'')+':'+Math.round(r.right)+'/'+window.innerWidth); });" +
            "res.clippedElements1920=clipped; return res;"
        );
        if (domAudit != null) out.putAll(domAudit);
        try {
            String health = Day_Netting.verifyUiLayoutHealth(driver);
            out.put("dayNettingUiHealth", health != null ? health.replaceAll("\\s+", " ").trim() : "OK");
        } catch (Exception e) {
            out.put("dayNettingUiHealth", "ERR: " + e.getMessage());
        }
        out.put("functionalTests", runModuleFunctionalTests(driver, js, browserName, tp.key));
        out.putAll(checkResponsive1366(driver, js, browserName, tp.key));
        List<String> jsErrs = (List<String>) js.executeScript("return window.__uiErrs || [];");
        List<String> conErrs = (List<String>) js.executeScript("return window.__uiConsoleErrs || [];");
        List<String> httpFails = (List<String>) js.executeScript("return window.__uiHttpFails || [];");
        out.put("jsErrors", jsErrs); out.put("jsErrorCount", jsErrs != null ? jsErrs.size() : 0);
        out.put("consoleErrors", conErrs); out.put("consoleErrorCount", conErrs != null ? conErrs.size() : 0);
        out.put("httpFailures", httpFails); out.put("httpFailureCount", httpFails != null ? httpFails.size() : 0);
        return out;
    }

    private static String navigateToPage(WebDriver driver, JavascriptExecutor js, TargetPage tp) {
        try {
            Long navCount = (Long) js.executeScript("return document.querySelectorAll('[data-link]').length;");
            if (navCount == null || navCount == 0) {
                driver.get(BASE_URL + "/home");
                sleepMs(1800);
                OMSwebmain.closeAllBrowserPopupsAndModals(driver);
            }
        } catch (Exception ignored) {}
        try {
            if ("admin_dashboard".equals(tp.key) && OMSweb_DashboardAudit.navigateToDashboard(driver)) return "CONNECTED:OMSweb_DashboardAudit";
            if ("file_manager".equals(tp.key) && OMSweb_UploadData.navigateToUploadDataPage(driver, new WebDriverWait(driver, Duration.ofSeconds(10)))) return "CONNECTED:OMSweb_UploadData";
        } catch (Exception ignored) {}
        return String.valueOf(js.executeScript(
            "var lk=arguments[0], tk=arguments[1], els=Array.from(document.querySelectorAll('[data-link],aside a,.sidebar a,nav a,.nav-sidebar a,.menu-item a,.left-sidebar a,.sidebar li a'));" +
            "for(var k=0;k<lk.length;k++){ var kw=lk[k].toLowerCase(); for(var i=0;i<els.length;i++){ var dl=(els[i].getAttribute('data-link')||'').toLowerCase(); if(dl===kw){ try{ els[i].click(); return 'SIDEBAR_EXACT:'+dl; }catch(e){} } } }" +
            "for(var k=0;k<tk.length;k++){ var kw=tk[k].toLowerCase(); for(var i=0;i<els.length;i++){ var el=els[i], t=(el.getAttribute('title')||el.getAttribute('data-original-title')||el.getAttribute('data-bs-original-title')||'').toLowerCase().trim(), x=(el.innerText||el.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase(); if(t===kw||x===kw){ try{ el.click(); return 'SIDEBAR_TEXT:'+kw; }catch(e){} } } }" +
            "return 'NOT_FOUND';", Arrays.asList(tp.linkKeywords), Arrays.asList(tp.textKeywords)
        ));
    }

    // --- Module-Specific Tests ---
    private static Map<String, Object> runModuleFunctionalTests(WebDriver driver, JavascriptExecutor js, String browserName, String key) {
        try {
            switch (key) {
                case "admin_dashboard": return testAdminDashboard(driver, js, browserName);
                case "rms":             return testRmsModule(driver, js);
                case "file_manager":    return testFileManagerModule(driver, js);
                case "account":         return testAccountModule(driver, js);
                case "trade_logs":      return testTradeLogsModule(driver, js);
                case "setting":         return testSettingModule(driver, js);
                default:                return new LinkedHashMap<>();
            }
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("moduleError", e.getMessage());
            return err;
        }
    }
    private static Map<String, Object> testAdminDashboard(WebDriver driver, JavascriptExecutor js, String browserName) {
        Map<String, Object> m = new LinkedHashMap<>();
        OMSweb_DashboardAudit.waitForDashboardReady(driver, 8);
        OMSweb_DashboardAudit.DashboardSnapshot snap = OMSweb_DashboardAudit.captureSnapshot(driver, browserName);
        double turnover = OMSweb_DashboardAudit.parseNumSafe(snap.turnover.bigValue);
        double buyVal = OMSweb_DashboardAudit.parseNumSafe(snap.buyValue.bigValue);
        double sellVal = OMSweb_DashboardAudit.parseNumSafe(snap.sellValue.bigValue);
        m.put("turnover", snap.turnover.bigValue); m.put("buyValue", snap.buyValue.bigValue); m.put("sellValue", snap.sellValue.bigValue);
        m.put("turnoverMatchesBuyPlusSell", Math.abs(turnover - (buyVal + sellVal)) <= 1.0 || turnover == 0.0);
        m.put("brokerLimitDseUsed", snap.brokerLimit.dseUsed); m.put("brokerLimitCseUsed", snap.brokerLimit.cseUsed);
        m.put("tradesCount", snap.trades.bigCount);
        m.put("activeUsersOnline", snap.activeUsers.usersOnline); m.put("activeUsersTotal", snap.activeUsers.usersTotal);
        m.put("roleTotalsSum", snap.activeUsers.dealersTotal + snap.activeUsers.clientsTotal + snap.activeUsers.associatesTotal);
        m.put("mostTradedStocksRows", snap.mostTradedStocks.size()); m.put("exchangeContributionRows", snap.exchangeContribution.size());
        m.put("topDealersRows", snap.topDealers.size()); m.put("topAssociatesRows", snap.topAssociates.size());
        m.put("mostTradedClientsRows", snap.mostTradedClients.size());
        m.put("tabSwitchTest", String.valueOf(js.executeScript(
            "var tabs=Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link')).filter(function(t){return t.offsetParent!==null;});" +
            "if(tabs.length<2) return 'NO_TABS('+tabs.length+')'; tabs[1].click(); var sw=(tabs[1].innerText||'').trim(); tabs[0].click(); return 'SWITCHED_OK:'+sw;"
        )));
        return m;
    }
    private static Map<String, Object> testRmsModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();
        RMS.RMSApplicationSettings s = RMS.fetchApplicationSettings(driver);
        m.put("apiDayNettingMode", s.dayNettingSettings); m.put("apiAllowZAndSpot", s.allowZAndSpotNetting);
        m.put("apiDseNettingEnabled", s.isDseDayNettingEnabled); m.put("apiCseNettingEnabled", s.isCseDayNettingEnabled);
        m.put("visibleTabs", js.executeScript("return Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link')).filter(function(t){return t.offsetParent!==null;}).map(function(t){return (t.innerText||'').replace(/\\s+/g,' ').trim();});"));
        m.put("clientsTabLoaded", RMS.navigateToClientsRmsTab(driver));
        sleepMs(500);
        m.put("saveChangesWithoutClientState", String.valueOf(js.executeScript(
            "var btn=Array.from(document.querySelectorAll('button')).find(function(b){return /save changes/i.test(b.innerText||'');});" +
            "return !btn ? 'NOT_FOUND' : ((btn.disabled||btn.classList.contains('disabled')) ? 'DISABLED_SAFE' : 'ENABLED_WITHOUT_CLIENT');"
        )));
        Object cs = js.executeScript(
            "var inp=Array.from(document.querySelectorAll('input[type=text],input[type=search],input[placeholder*=\"client\" i],input[placeholder*=\"search\" i]')).find(function(el){return el.offsetParent!==null;});" +
            "if(!inp) return 'NO_SEARCH_INPUT'; inp.value='23986'; inp.dispatchEvent(new Event('input',{bubbles:true})); inp.dispatchEvent(new Event('change',{bubbles:true}));" +
            "var btn=Array.from(document.querySelectorAll('button')).find(function(b){return b.offsetParent!==null&&/search/i.test(b.innerText||'');}); if(btn) btn.click(); return 'SEARCH_TRIGGERED';"
        );
        sleepMs(900);
        Object rows = js.executeScript("var tb=Array.from(document.querySelectorAll('table')).find(function(t){return t.offsetParent!==null;}); return tb?tb.querySelectorAll('tbody tr').length:-1;");
        m.put("clientSearch23986", cs + "|rows=" + rows);
        m.put("applicableSymbolTabNav", RMS.navigateToApplicableSymbolTab(driver)); sleepMs(400);
        m.put("unauthorizedBoardsTabNav", RMS.navigateToUnauthorizedBoardsTab(driver)); sleepMs(400);
        m.put("appSettingsTabNav", RMS.navigateToApplicationSettings(driver)); sleepMs(500);
        return m;
    }
    private static Map<String, Object> testFileManagerModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uploadMenuOpened", OMSweb_UploadData.openUploadDataMenu(driver));
        List<String> missUp = new ArrayList<>();
        for (OMSweb_UploadData.UploadSubMenu sub : OMSweb_UploadData.UploadSubMenu.values()) {
            if (!OMSweb_UploadData.verifySubMenuPresent(driver, sub)) missUp.add(sub.name());
        }
        m.put("uploadSubMenusExpected", OMSweb_UploadData.UploadSubMenu.values().length);
        m.put("uploadSubMenusMissing", missUp);
        m.put("exportMenuOpened", OMSweb_ExportData.openExportFilesMenu(driver));
        List<String> missEx = new ArrayList<>();
        for (OMSweb_ExportData.ExportSubMenu sub : OMSweb_ExportData.ExportSubMenu.values()) {
            if (!OMSweb_ExportData.verifySubMenuPresent(driver, sub)) missEx.add(sub.name());
        }
        m.put("exportSubMenusExpected", OMSweb_ExportData.ExportSubMenu.values().length);
        m.put("exportSubMenusMissing", missEx);
        try { js.executeScript("document.body.click();"); } catch (Exception ignored) {}
        sleepMs(300);
        m.put("historyTableAudit", js.executeScript(
            "var tb=Array.from(document.querySelectorAll('table')).find(function(t){return t.offsetParent!==null;}); if(!tb) return {found:false};" +
            "var rows=tb.querySelectorAll('tbody tr');" +
            "return { found:true, headers:Array.from(tb.querySelectorAll('thead th')).map(function(th){return (th.innerText||'').trim();}), rowCount:rows.length," +
            "  hasSearch:Array.from(document.querySelectorAll('input[type=search],input[placeholder*=\"search\" i]')).some(function(i){return i.offsetParent!==null;})," +
            "  hasPagination:!!document.querySelector('.pagination,.dataTables_paginate'), firstRowActionControls:rows.length>0?rows[0].querySelectorAll('a,button,i,svg').length:0 };"
        ));
        return m;
    }
    private static Map<String, Object> testAccountModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profileAndForm", js.executeScript(
            "var txt=(document.body?document.body.innerText:'');" +
            "return { hasUsernameLabel:/username/i.test(txt), hasUserRoleLabel:/user role/i.test(txt), hasAccountStatusLabel:/account status/i.test(txt)," +
            "  hasActiveBadge:!!Array.from(document.querySelectorAll('.badge,span')).find(function(b){return /^active$/i.test((b.innerText||'').trim());})," +
            "  passwordInputCount:Array.from(document.querySelectorAll('input[type=password]')).filter(function(i){return i.offsetParent!==null;}).length," +
            "  passwordEyeToggleCount:Array.from(document.querySelectorAll('i.fa-eye,i.fa-eye-slash,.password-toggle,[data-toggle=password]')).filter(function(i){return i.offsetParent!==null;}).length };"
        ));
        String beforeUrl = driver.getCurrentUrl();
        Object sub = js.executeScript("var b=Array.from(document.querySelectorAll('button')).find(function(x){return x.offsetParent!==null&&/(change|update|save).*password/i.test(x.innerText||'');}); if(!b) return 'NO_SUBMIT_BTN'; b.click(); return 'CLICKED:'+(b.innerText||'').trim();");
        sleepMs(500);
        m.put("emptySubmitTest", sub + "|stayedOnPage=" + beforeUrl.equals(driver.getCurrentUrl()));
        m.put("passwordVisibilityToggle", String.valueOf(js.executeScript(
            "var inp=document.querySelector('input[type=password]'), eye=document.querySelector('i.fa-eye,i.fa-eye-slash,.password-toggle');" +
            "if(!inp||!eye) return 'NO_TOGGLE_OR_INPUT'; var t1=inp.type; eye.click(); var t2=inp.type; if(t2!==t1) eye.click(); return 'before='+t1+',after='+t2;"
        )));
        return m;
    }
    private static Map<String, Object> testTradeLogsModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logsOverview", js.executeScript(
            "var inputs=Array.from(document.querySelectorAll('input:not([type=hidden]),select')).filter(function(el){return el.offsetParent!==null;})," +
            "    tb=Array.from(document.querySelectorAll('table')).find(function(t){return t.offsetParent!==null;}), rej=0, rejMsg=0;" +
            "if(tb) tb.querySelectorAll('tbody tr').forEach(function(tr){ if(/rejected/i.test(tr.innerText||'')){ rej++; var tds=tr.querySelectorAll('td'); if(tds.length>0&&(tds[tds.length-1].innerText||'').trim().length>0) rejMsg++; } });" +
            "var txt=(document.body?document.body.innerText:''), cm=txt.match(/(\\d+)\\s+of\\s+(\\d+)\\s+logs/i);" +
            "return { filterControlCount:inputs.length, tableColumns:tb?tb.querySelectorAll('thead th').length:0, initialRowCount:tb?tb.querySelectorAll('tbody tr').length:0," +
            "  rejectedRows:rej, rejectedWithErrorMsg:rejMsg, logBadgeText:cm?cm[0]:'NOT_FOUND', hasStatusLegend:/success/i.test(txt)&&/pending/i.test(txt)&&/failed/i.test(txt) };"
        ));
        Object fc = js.executeScript(
            "var inp=Array.from(document.querySelectorAll('input[type=text],input[type=search]')).find(function(i){return i.offsetParent!==null;})," +
            "    sBtn=Array.from(document.querySelectorAll('button')).find(function(b){return b.offsetParent!==null&&/^search$/i.test((b.innerText||'').trim());})," +
            "    cBtn=Array.from(document.querySelectorAll('button')).find(function(b){return b.offsetParent!==null&&/^clear$/i.test((b.innerText||'').trim());});" +
            "if(!inp||!sBtn) return 'MISSING_CONTROLS'; var br=document.querySelectorAll('table tbody tr').length;" +
            "inp.value='ZZZ_NO_MATCH'; inp.dispatchEvent(new Event('input',{bubbles:true})); sBtn.click(); return {beforeRows:br, hasClearBtn:!!cBtn};"
        );
        sleepMs(800);
        Object ar = js.executeScript(
            "var tb=Array.from(document.querySelectorAll('table')).find(function(t){return t.offsetParent!==null;}), r=tb?tb.querySelectorAll('tbody tr').length:-1," +
            "    cBtn=Array.from(document.querySelectorAll('button')).find(function(b){return b.offsetParent!==null&&/^clear$/i.test((b.innerText||'').trim());});" +
            "if(cBtn) cBtn.click(); return r;"
        );
        sleepMs(600);
        m.put("filterSearchAndClearTest", fc + "|afterFilterRows=" + ar);
        return m;
    }
    private static Map<String, Object> testSettingModule(WebDriver driver, JavascriptExecutor js) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settingsMetadata", js.executeScript(
            "var txt=(document.body?document.body.innerText:''), tabs=Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link')).filter(function(t){return t.offsetParent!==null;}).map(function(t){return (t.innerText||'').trim();})," +
            "    fi=Array.from(document.querySelectorAll('input')).find(function(i){return i.offsetParent!==null&&/^\\.?\\d+(\\.\\d+)?$/.test((i.value||'').trim());});" +
            "return { tabs:tabs, hasProductUftfast:/UFTFAST OMS/i.test(txt), hasVersion:/v\\d+\\.\\d+\\.\\d+/i.test(txt), hasFixVersion:/5\\.0\\s*SP1/i.test(txt), fontSizeValue:fi?fi.value:'NOT_FOUND', fontSizeLeadingZeroMissing:fi?/^\\.\\d+$/.test((fi.value||'').trim()):false };"
        ));
        driver.manage().window().setSize(new Dimension(1366, 768)); sleepMs(350);
        m.put("saveSettingsAt1366", js.executeScript(
            "var btn=Array.from(document.querySelectorAll('button')).find(function(b){return b.offsetParent!==null&&/save settings/i.test(b.innerText||'');});" +
            "if(!btn) return 'NOT_FOUND'; var r=btn.getBoundingClientRect(); return {top:Math.round(r.top), bottom:Math.round(r.bottom), innerHeight:window.innerHeight, obscuredByBottomBar:r.bottom>window.innerHeight-28};"
        ));
        driver.manage().window().setSize(new Dimension(1920, 1080)); sleepMs(250);
        Object act = js.executeScript("var tab=Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link,a,button')).find(function(el){return el.offsetParent!==null&&/activity logs/i.test(el.innerText||'');}); if(!tab) return 'NO_ACTIVITY_LOGS_TAB'; tab.click(); return 'CLICKED_ACTIVITY_LOGS';");
        sleepMs(700);
        Object actC = js.executeScript("var tb=Array.from(document.querySelectorAll('table')).find(function(t){return t.offsetParent!==null;}); return {hasTable:!!tb, rows:tb?tb.querySelectorAll('tbody tr').length:0};");
        try { js.executeScript("var tab=Array.from(document.querySelectorAll('[role=tab],.nav-tabs .nav-link,.nav-pills .nav-link,a,button')).find(function(el){return el.offsetParent!==null&&/^settings$/i.test((el.innerText||'').trim());}); if(tab) tab.click();"); } catch (Exception ignored) {}
        m.put("activityLogsTabTest", act + "|" + actC);
        return m;
    }

    // --- Shared Audit & Utility Helpers ---
    private static Object discoverSidebarItems(JavascriptExecutor js) {
        return js.executeScript(
            "var items=[], seen={}, els=document.querySelectorAll('aside a,.sidebar a,nav a,[data-link],.nav-sidebar a,.menu-item a,.left-sidebar a,.sidebar li');" +
            "for(var i=0;i<els.length;i++){ var el=els[i], dl=el.getAttribute('data-link')||'', href=el.getAttribute('href')||'', title=el.getAttribute('title')||el.getAttribute('data-original-title')||el.getAttribute('data-bs-original-title')||el.getAttribute('aria-label')||'', txt=(el.innerText||el.textContent||'').replace(/\\s+/g,' ').trim(), key=dl+'|'+href+'|'+title+'|'+txt;" +
            "  if((dl||(href&&href!=='#')||title||txt)&&!seen[key]){ seen[key]=true; var r=el.getBoundingClientRect(); items.push({index:i,dataLink:dl,href:href,title:title,text:txt,visible:r.width>0&&r.height>0,x:Math.round(r.left),y:Math.round(r.top)}); } } return items;"
        );
    }

    private static Object auditWebKitCss(JavascriptExecutor js) {
        return js.executeScript(
            "var h={unprefixedBackdropFilter:0, overflowOverlay:0, cssGridSubgrid:0};" +
            "try{ for(var s=0;s<document.styleSheets.length;s++){ var rules; try{ rules=document.styleSheets[s].cssRules; }catch(ex){ continue; } if(!rules) continue;" +
            "  for(var r=0;r<rules.length;r++){ var ct=rules[r].cssText||''; if(ct.indexOf('backdrop-filter')!==-1&&ct.indexOf('-webkit-backdrop-filter')===-1) h.unprefixedBackdropFilter++; if(ct.indexOf('overflow: overlay')!==-1||ct.indexOf('overflow-y: overlay')!==-1) h.overflowOverlay++; if(ct.indexOf('subgrid')!==-1) h.cssGridSubgrid++; } } }catch(e){} return h;"
        );
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> checkResponsive1366(WebDriver driver, JavascriptExecutor js, String browserName, String key) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            driver.manage().window().setSize(new Dimension(1366, 768)); sleepMs(450);
            Map<String, Object> r = (Map<String, Object>) js.executeScript(
                "var clipped=0; document.querySelectorAll('table,.card,.btn').forEach(function(el){ var b=el.getBoundingClientRect(); if(b.width>0&&b.right>window.innerWidth+4) clipped++; });" +
                "return {overflowAt1366:document.documentElement.scrollWidth>document.documentElement.clientWidth, scrollWidth1366:document.documentElement.scrollWidth, clientWidth1366:document.documentElement.clientWidth, clipped1366:clipped};"
            );
            if (r != null) resp.putAll(r);
            saveShot(driver, browserName, key + "_1366");
            driver.manage().window().setSize(new Dimension(1920, 1080)); sleepMs(250);
        } catch (Exception e) { resp.put("overflowAt1366", "ERR"); }
        return resp;
    }

    private static void saveShot(WebDriver driver, String browserName, String name) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dst = Paths.get(OUT_DIR, "ui_shots", browserName + "_" + name + ".png");
            Files.createDirectories(dst.getParent());
            Files.copy(src.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String toJson(Object val) {
        if (val == null) return "null";
        if (val instanceof Boolean || val instanceof Number) return String.valueOf(val);
        if (val instanceof Map<?, ?>) {
            StringBuilder sb = new StringBuilder("{"); boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                if (!first) sb.append(","); first = false;
                sb.append("\"").append(esc(String.valueOf(e.getKey()))).append("\":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (val instanceof List<?>) {
            StringBuilder sb = new StringBuilder("["); boolean first = true;
            for (Object item : (List<?>) val) { if (!first) sb.append(","); first = false; sb.append(toJson(item)); }
            return sb.append("]").toString();
        }
        return "\"" + esc(String.valueOf(val)) + "\"";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
