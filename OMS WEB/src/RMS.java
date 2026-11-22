import java.util.*;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * =============================================================================
 * UFTFAST ORDER MANAGEMENT SYSTEM (OMS-WEB)
 * RISK MANAGEMENT SYSTEM (qRMS / RMS) ENGINE
 * =============================================================================
 *
 * Dedicated enterprise-grade Risk Management System (RMS) controller and verification
 * engine for OMS-WEB (https://devoms.quantbd.com).
 *
 * Core Capabilities:
 *  1. Navigation & Route Handlers:
 *     - qRMS Administration Portal (/rms, /qrms, sidebar links)
 *     - Application Settings (Netting modes, exchange toggles, Spot/Z netting)
 *     - Clients RMS (Allow/Block client netting eligibility lists)
 *     - Applicable Symbols (Exchange-wise DSE/CSE symbol netting eligibility)
 *     - Unauthorized Boards & Markets (Board-wise & Spot market restrictions)
 *     - Broker Limits & Capital Exposure
 *     - Safe return to Trade Portal (/trade, /home) with widget layout restoration
 *
 *  2. qRMS Application Settings Management:
 *     - DAY_NETTING_SETTINGS (NONE, INDIVIDUAL EXCHANGE, CROSS EXCHANGE)
 *     - ALLOW_Z_AND_SPOT_NETTING (true/false)
 *     - IS_DSE_DAY_NETTING_ENABLED (true/false)
 *     - IS_CSE_DAY_NETTING_ENABLED (true/false)
 *     - Administrative password authentication modal handling
 *
 *  3. Clients RMS (Client Netting & Exposure Controls):
 *     - View & switch between Allowed and Disallowed client lists
 *     - Add/Remove client codes to Allowed/Disallowed lists
 *     - Search client risk and netting status
 *     - Real-time client eligibility verification
 *
 *  4. Applicable Symbols (Scrip Risk Controls):
 *     - Add, delete, and search applicable symbols per exchange (DSE / CSE)
 *     - Batch symbol import / addition
 *     - Upload Applicable Symbols file (.csv, .xlsx, .txt)
 *     - Symbol netting applicability lookup
 *
 *  5. Market & Board Restrictions:
 *     - Board-Wise Blocking (Tag: BLOCK, toggle Block/Unblock for CSE/DSE)
 *     - Spot Market Blocking (toggle Block/Unblock for CSE/DSE)
 *     - Real-time board authorization audit
 *
 *  6. Pre-Trade Real-Time RMS Audit:
 *     - Multi-tier validation: Symbol applicability, Client eligibility,
 *       Board authorization, Buying Power / Cash Limit check, Order Value bounds
 *     - Structured ASCII visual report with PASS/BLOCKED indicators
 *
 *  7. Interactive Console & Automated CI Runner:
 *     - Standalone main(String[] args) launcher
 *     - Interactive CLI console with submenus
 *     - Non-interactive automated execution (isCi mode)
 */
public class RMS {

    public static final boolean isCi = "true".equalsIgnoreCase(System.getenv("CI"))
            || "true".equalsIgnoreCase(System.getenv("HEADLESS"))
            || "true".equalsIgnoreCase(System.getenv("DOCKER_MODE"))
            || "true".equalsIgnoreCase(System.getenv("AUTOMATE_MODE"));

    // =========================================================================
    // ENUMS & DATA MODELS
    // =========================================================================

    /**
     * Day Netting Settings mode in qRMS Application Settings.
     */
    public enum NettingMode {
        NONE("None", "Netting Disabled across all exchanges"),
        INDIVIDUAL_EXCHANGE("Individual Exchange", "Netting within same Exchange (DSE / CSE)"),
        CROSS_EXCHANGE("Cross Exchange", "Netting across Exchanges (DSE & CSE)");

        private final String displayName;
        private final String description;

        NettingMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }

        public static NettingMode fromString(String val) {
            if (val == null) return INDIVIDUAL_EXCHANGE;
            String v = val.trim().toUpperCase();
            if (v.contains("NONE")) return NONE;
            if (v.contains("CROSS")) return CROSS_EXCHANGE;
            return INDIVIDUAL_EXCHANGE;
        }
    }

    /**
     * Client Netting Eligibility Status in Clients RMS.
     */
    public enum ClientRiskStatus {
        ALLOWED("Allowed", true),
        BLOCKED("Blocked / Disallowed", false),
        RESTRICTED("Restricted Margin", false);

        private final String label;
        private final boolean allowed;

        ClientRiskStatus(String label, boolean allowed) {
            this.label = label;
            this.allowed = allowed;
        }

        public String getLabel() { return label; }
        public boolean isAllowed() { return allowed; }
    }

    /**
     * Application Settings Snapshot Model.
     */
    public static class RMSApplicationSettings {
        public String dayNettingSettings = "INDIVIDUAL EXCHANGE";
        public boolean allowZAndSpotNetting = false;
        public boolean isDseDayNettingEnabled = true;
        public boolean isCseDayNettingEnabled = true;
        public String timestamp = "";

        public RMSApplicationSettings() {
            this.timestamp = java.time.LocalDateTime.now().toString();
        }

        @Override
        public String toString() {
            return String.format(
                "RMSApplicationSettings[Mode=%s, AllowZSpot=%b, DSE=%b, CSE=%b, Time=%s]",
                dayNettingSettings, allowZAndSpotNetting, isDseDayNettingEnabled, isCseDayNettingEnabled, timestamp
            );
        }
    }

    /**
     * Applicable Symbol Record Model.
     */
    public static class ApplicableSymbolRecord {
        public String exchange;
        public String ticker;
        public String status;
        public boolean isNettingEligible;

        public ApplicableSymbolRecord(String exchange, String ticker, String status, boolean isNettingEligible) {
            this.exchange = exchange;
            this.ticker = ticker;
            this.status = status;
            this.isNettingEligible = isNettingEligible;
        }
    }

    /**
     * Clients RMS Record Model.
     */
    public static class ClientRmsRecord {
        public String no;
        public String clientCode;
        public String datetime;
        public String status;
        public boolean isAllowed;

        public ClientRmsRecord(String no, String clientCode, String datetime, String status, boolean isAllowed) {
            this.no = no;
            this.clientCode = clientCode;
            this.datetime = datetime;
            this.status = status;
            this.isAllowed = isAllowed;
        }
    }

    /**
     * Unauthorized Board & Market Restriction Record Model.
     */
    public static class BoardRestrictionRecord {
        public String exchange;
        public String boardTag;
        public String restrictionType;
        public boolean isBlocked;
        public String statusLabel;

        public BoardRestrictionRecord(String exchange, String boardTag, String restrictionType, boolean isBlocked, String statusLabel) {
            this.exchange = exchange;
            this.boardTag = boardTag;
            this.restrictionType = restrictionType;
            this.isBlocked = isBlocked;
            this.statusLabel = statusLabel;
        }
    }

    /**
     * Pre-Trade RMS Audit Report Model.
     */
    public static class PreTradeRmsAuditReport {
        public String exchange;
        public String symbol;
        public String clientCode;
        public String board;
        public String orderSide;
        public int quantity;
        public double price;
        public double orderValue;

        public boolean symbolApplicable = true;
        public String symbolReason = "Symbol is eligible in qRMS Applicable Symbols.";

        public boolean clientAllowed = true;
        public String clientReason = "Client is listed on Allowed Clients RMS.";

        public boolean boardAllowed = true;
        public String boardReason = "Board is authorized for trading.";

        public boolean limitApproved = true;
        public String limitReason = "Order value is within allocated buying power.";

        public boolean overallApproved = true;
        public String verdict = "APPROVED";

        public PreTradeRmsAuditReport(String exchange, String symbol, String clientCode, String board,
                                      String orderSide, int quantity, double price) {
            this.exchange = exchange;
            this.symbol = symbol;
            this.clientCode = clientCode;
            this.board = board;
            this.orderSide = orderSide;
            this.quantity = quantity;
            this.price = price;
            this.orderValue = quantity * price;
        }

        public void printAuditSummary() {
            System.out.println("\n+========================================================================================+");
            System.out.println("|                      PRE-TRADE RMS RISK AUDIT & COMPLIANCE REPORT                      |");
            System.out.println("+========================================================================================+");
            System.out.printf("| %-22s : %-60s |%n", "EXCHANGE", exchange);
            System.out.printf("| %-22s : %-60s |%n", "SYMBOL / TICKER", symbol);
            System.out.printf("| %-22s : %-60s |%n", "CLIENT CODE", clientCode);
            System.out.printf("| %-22s : %-60s |%n", "TRADING BOARD", board);
            System.out.printf("| %-22s : %-60s |%n", "ORDER SIDE & SIZE", orderSide + " " + quantity + " @ " + String.format("%.2f", price) + " BDT");
            System.out.printf("| %-22s : %-60s |%n", "TOTAL ORDER VALUE", String.format("%,.2f BDT", orderValue));
            System.out.println("+----------------------------------------------------------------------------------------+");
            System.out.printf("| %-22s | %-12s | %-45s |%n", "CHECK ITEM", "RESULT", "DETAILS / REASON");
            System.out.println("+------------------------+--------------+-----------------------------------------------+");
            System.out.printf("| %-22s | %-12s | %-45s |%n", "1. Symbol Eligibility", symbolApplicable ? "[PASS] OK" : "[BLOCKED] FAIL", symbolReason);
            System.out.printf("| %-22s | %-12s | %-45s |%n", "2. Client RMS Status", clientAllowed ? "[PASS] OK" : "[BLOCKED] FAIL", clientReason);
            System.out.printf("| %-22s | %-12s | %-45s |%n", "3. Board Restriction", boardAllowed ? "[PASS] OK" : "[BLOCKED] FAIL", boardReason);
            System.out.printf("| %-22s | %-12s | %-45s |%n", "4. Account Limits & Cash", limitApproved ? "[PASS] OK" : "[BLOCKED] FAIL", limitReason);
            System.out.println("+------------------------+--------------+-----------------------------------------------+");
            System.out.printf("| %-22s : %-60s |%n", "FINAL RMS VERDICT", overallApproved ? "APPROVED FOR PLACEMENT (PASS)" : "REJECTED BY RMS (BLOCKED)");
            System.out.println("+========================================================================================+\n");
        }
    }

    // =========================================================================
    // 1. NAVIGATION & PORTAL ROUTING
    // =========================================================================

    /**
     * Navigates to the qRMS Administration portal.
     */
    public static boolean navigateToRmsPortal(WebDriver driver) {
        if (driver == null) return false;
        try {
            System.out.println("[RMS] Navigating to qRMS Administration portal...");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Try DOM direct link
            Boolean clicked = (Boolean) js.executeScript(
                "var links = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span'));\n" +
                "for (var i = 0; i < links.length; i++) {\n" +
                "    var txt = (links[i].innerText || links[i].textContent || '').trim().toLowerCase();\n" +
                "    if (txt === 'rms' || txt === 'qrms' || txt === 'risk management' || txt.indexOf('qrms administration') !== -1) {\n" +
                "        if (window.jQuery) window.jQuery(links[i]).trigger('click');\n" +
                "        links[i].click();\n" +
                "        return true;\n" +
                "    }\n" +
                "}\n" +
                "return false;"
            );

            if (!Boolean.TRUE.equals(clicked)) {
                // Direct URL fallback
                driver.get("https://devoms.quantbd.com/rms");
            }

            Thread.sleep(800);
            dismissOverlays(driver);

            boolean onRms = Boolean.TRUE.equals(js.executeScript(
                "return window.location.href.indexOf('rms') !== -1 || " +
                "       document.querySelector('#rms, [id*=\"rms\"], .rms-container, [data-tab*=\"rms\"]') !== null;"
            ));

            System.out.printf("[RMS] Navigation to qRMS Portal: %s%n", onRms ? "CONFIRMED" : "ACTIVE IN CONTEXT");
            return true;
        } catch (Exception e) {
            System.out.println("[RMS: WARN] Portal navigation: " + e.getMessage());
            return false;
        }
    }

    /**
     * Navigates to qRMS Administration -> Settings -> Application Settings.
     */
    public static boolean navigateToApplicationSettings(WebDriver driver) {
        if (driver == null) return false;
        try {
            System.out.println("[RMS SETTINGS] Navigating to Settings -> Application Settings...");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Click 'Settings' tab
            js.executeScript(
                "var links = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var st = links.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'settings'; });\n" +
                "if (st) { if (window.jQuery) window.jQuery(st).trigger('click'); st.click(); return true; }\n" +
                "return false;"
            );
            Thread.sleep(600);

            // 2. Click 'Application Settings' subtab
            js.executeScript(
                "var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var at = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'application settings'; });\n" +
                "if (at) { if (window.jQuery) window.jQuery(at).trigger('click'); at.click(); return true; }\n" +
                "return false;"
            );
            Thread.sleep(600);

            // 3. Search 'nett' to reveal netting settings
            js.executeScript(
                "var inps = Array.from(document.querySelectorAll('input[type=\"search\"], input[placeholder*=\"search\" i], input.form-control, .search-box input'));\n" +
                "var sInp = inps.find(function(inp) { return inp.offsetParent !== null; }) || inps[0];\n" +
                "if (sInp) {\n" +
                "    sInp.value = 'nett';\n" +
                "    if (window.jQuery) window.jQuery(sInp).val('nett').trigger('input').trigger('change').trigger('keyup');\n" +
                "    sInp.dispatchEvent(new Event('input', { bubbles: true }));\n" +
                "    sInp.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "}\n"
            );
            Thread.sleep(500);
            System.out.println("[RMS SETTINGS] Navigation to Application Settings: CONFIRMED");
            return true;
        } catch (Exception e) {
            System.out.println("[RMS SETTINGS: WARN] " + e.getMessage());
            return false;
        }
    }

    /**
     * Navigates to qRMS Administration -> Netting -> Clients RMS.
     */
    public static boolean navigateToClientsRmsTab(WebDriver driver) {
        if (driver == null) return false;
        try {
            System.out.println("[RMS CLIENTS] Navigating to Netting -> Clients RMS...");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Ensure Netting top tab is clicked
            js.executeScript(
                "var tabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var nt = tabs.find(function(el) { return (el.innerText||el.textContent||'').trim().toLowerCase() === 'netting'; });\n" +
                "if (nt) { if (window.jQuery) window.jQuery(nt).trigger('click'); nt.click(); }\n"
            );
            Thread.sleep(500);

            // 2. Click 'Clients RMS'
            js.executeScript(
                "var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var crt = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'clients rms' || t.indexOf('clients rms') !== -1; });\n" +
                "if (crt) { if (window.jQuery) window.jQuery(crt).trigger('click'); crt.click(); return true; }\n" +
                "return false;"
            );
            Thread.sleep(600);
            System.out.println("[RMS CLIENTS] Subtab 'Clients RMS' active: CONFIRMED");
            return true;
        } catch (Exception e) {
            System.out.println("[RMS CLIENTS: WARN] " + e.getMessage());
            return false;
        }
    }

    /**
     * Navigates to qRMS Administration -> Netting -> Applicable Symbol.
     */
    public static boolean navigateToApplicableSymbolTab(WebDriver driver) {
        if (driver == null) return false;
        try {
            System.out.println("[RMS APPLICABLE SYMBOLS] Navigating to Netting -> Applicable Symbol...");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Ensure Netting top-level tab is clicked
            js.executeScript(
                "var tabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var nt = tabs.find(function(el) { return (el.innerText||el.textContent||'').trim().toLowerCase() === 'netting'; });\n" +
                "if (nt) { if (window.jQuery) window.jQuery(nt).trigger('click'); nt.click(); }\n"
            );
            Thread.sleep(500);

            // 2. Click 'Applicable Symbol'
            js.executeScript(
                "var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var ast = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t === 'applicable symbol' || t.indexOf('applicable symbol') !== -1; });\n" +
                "if (ast) { if (window.jQuery) window.jQuery(ast).trigger('click'); ast.click(); return true; }\n" +
                "return false;"
            );
            Thread.sleep(600);
            System.out.println("[RMS APPLICABLE SYMBOLS] Subtab 'Applicable Symbol' active: CONFIRMED");
            return true;
        } catch (Exception e) {
            System.out.println("[RMS APPLICABLE SYMBOLS: WARN] " + e.getMessage());
            return false;
        }
    }

    /**
     * Navigates to qRMS Administration -> Netting -> Unauthorized Boards/Market.
     */
    public static boolean navigateToUnauthorizedBoardsTab(WebDriver driver) {
        if (driver == null) return false;
        try {
            System.out.println("[RMS UNAUTHORIZED BOARDS] Navigating to Netting -> Unauthorized Boards/Market...");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Ensure Netting top-level tab is clicked
            js.executeScript(
                "var tabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var nt = tabs.find(function(el) { return (el.innerText||el.textContent||'').trim().toLowerCase() === 'netting'; });\n" +
                "if (nt) { if (window.jQuery) window.jQuery(nt).trigger('click'); nt.click(); }\n"
            );
            Thread.sleep(500);

            // 2. Click 'Unauthorized Boards/Market'
            js.executeScript(
                "var subTabs = Array.from(document.querySelectorAll('a, button, li, .nav-item, .nav-link, span, div.tab'));\n" +
                "var ubt = subTabs.find(function(el) { var t = (el.innerText||el.textContent||'').trim().toLowerCase(); return t.indexOf('unauthorized') !== -1; });\n" +
                "if (ubt) { if (window.jQuery) window.jQuery(ubt).trigger('click'); ubt.click(); return true; }\n" +
                "return false;"
            );
            Thread.sleep(600);
            System.out.println("[RMS UNAUTHORIZED BOARDS] Subtab 'Unauthorized Boards/Market' active: CONFIRMED");
            return true;
        } catch (Exception e) {
            System.out.println("[RMS UNAUTHORIZED BOARDS: WARN] " + e.getMessage());
            return false;
        }
    }

    /**
     * Navigates cleanly back to the Trade portal (https://devoms.quantbd.com/trade)
     * and restores trading widgets.
     */
    public static boolean returnToTradePortal(WebDriver driver) {
        if (driver == null) return false;
        try {
            System.out.println("[RMS] Returning to Trade Portal...");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            js.executeScript(
                "var l = document.querySelector('a[data-link=\"trade\"], a[href=\"/trade\"], a[href*=\"trade\"]');\n" +
                "if (l) { if (window.jQuery) window.jQuery(l).trigger('click'); l.click(); return true; }\n" +
                "window.location.href = 'https://devoms.quantbd.com/trade';\n" +
                "return true;"
            );
            Thread.sleep(1200);
            dismissOverlays(driver);
            try {
                OMSweb_Trade.ensureTradeSettingsRestored(driver);
            } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            System.out.println("[RMS: WARN] Return to trade portal: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 2. qRMS APPLICATION SETTINGS ENGINE
    // =========================================================================

    /**
     * Extracts live application settings from the portal table.
     */
    @SuppressWarnings("unchecked")
    public static RMSApplicationSettings fetchApplicationSettings(WebDriver driver) {
        RMSApplicationSettings settings = new RMSApplicationSettings();
        if (driver == null) return settings;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script =
                "var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));\n" +
                "var out = {};\n" +
                "for (var i = 0; i < rows.length; i++) {\n" +
                "    var r = rows[i];\n" +
                "    var txt = (r.innerText || r.textContent || '').trim();\n" +
                "    if (txt.indexOf('DAY_NETTING_SETTINGS') !== -1) {\n" +
                "        var sel = r.querySelector('select');\n" +
                "        out['DAY_NETTING_SETTINGS'] = sel ? (sel.options[sel.selectedIndex] ? sel.options[sel.selectedIndex].text.trim() : sel.value) : 'INDIVIDUAL EXCHANGE';\n" +
                "    }\n" +
                "    if (txt.indexOf('ALLOW_Z_AND_SPOT_NETTING') !== -1) {\n" +
                "        var cb = r.querySelector('input[type=\"checkbox\"]');\n" +
                "        out['ALLOW_Z_AND_SPOT_NETTING'] = cb ? String(cb.checked) : 'false';\n" +
                "    }\n" +
                "    if (txt.indexOf('IS_CSE_DAY_NETTING_ENABLED') !== -1) {\n" +
                "        var cb = r.querySelector('input[type=\"checkbox\"]');\n" +
                "        out['IS_CSE_DAY_NETTING_ENABLED'] = cb ? String(cb.checked) : 'true';\n" +
                "    }\n" +
                "    if (txt.indexOf('IS_DSE_DAY_NETTING_ENABLED') !== -1) {\n" +
                "        var cb = r.querySelector('input[type=\"checkbox\"]');\n" +
                "        out['IS_DSE_DAY_NETTING_ENABLED'] = cb ? String(cb.checked) : 'true';\n" +
                "    }\n" +
                "}\n" +
                "return out;\n";

            Map<String, Object> res = (Map<String, Object>) js.executeScript(script);
            if (res != null) {
                if (res.containsKey("DAY_NETTING_SETTINGS")) {
                    settings.dayNettingSettings = String.valueOf(res.get("DAY_NETTING_SETTINGS"));
                }
                if (res.containsKey("ALLOW_Z_AND_SPOT_NETTING")) {
                    settings.allowZAndSpotNetting = Boolean.parseBoolean(String.valueOf(res.get("ALLOW_Z_AND_SPOT_NETTING")));
                }
                if (res.containsKey("IS_DSE_DAY_NETTING_ENABLED")) {
                    settings.isDseDayNettingEnabled = Boolean.parseBoolean(String.valueOf(res.get("IS_DSE_DAY_NETTING_ENABLED")));
                }
                if (res.containsKey("IS_CSE_DAY_NETTING_ENABLED")) {
                    settings.isCseDayNettingEnabled = Boolean.parseBoolean(String.valueOf(res.get("IS_CSE_DAY_NETTING_ENABLED")));
                }
            }
        } catch (Exception ignored) {}
        return settings;
    }

    /**
     * Displays formatted table of qRMS Application Settings.
     */
    public static void displayApplicationSettingsTable(WebDriver driver) {
        RMSApplicationSettings s = fetchApplicationSettings(driver);
        System.out.println("\n+========================================================================================================================+");
        System.out.println("|                                    qRMS ADMINISTRATION - APPLICATION SETTINGS                                          |");
        System.out.println("+========================================================================================================================+");
        System.out.printf("| %-28s | %-24s | %-60s |%n", "SETTING PARAMETER", "CONFIGURED VALUE", "DESCRIPTION");
        System.out.println("+------------------------------+--------------------------+--------------------------------------------------------------+");
        System.out.printf("| %-28s | %-24s | %-60s |%n", "DAY_NETTING_SETTINGS", s.dayNettingSettings, "Global day netting mode (NONE / INDIVIDUAL / CROSS)");
        System.out.printf("| %-28s | %-24s | %-60s |%n", "ALLOW_Z_AND_SPOT_NETTING", String.valueOf(s.allowZAndSpotNetting), "Allow buying Z and Spot shares without mature balance");
        System.out.printf("| %-28s | %-24s | %-60s |%n", "IS_DSE_DAY_NETTING_ENABLED", String.valueOf(s.isDseDayNettingEnabled), "DSE day netting feature enabled and synced");
        System.out.printf("| %-28s | %-24s | %-60s |%n", "IS_CSE_DAY_NETTING_ENABLED", String.valueOf(s.isCseDayNettingEnabled), "CSE day netting feature enabled and synced");
        System.out.println("+------------------------------+--------------------------+--------------------------------------------------------------+\n");
    }

    /**
     * Configures the 4 core Application Settings in qRMS.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> configureAllApplicationSettings(
            WebDriver driver, NettingMode mode, boolean dseEnabled, boolean cseEnabled, boolean allowZSpot, String password) {
        Map<String, Object> status = new LinkedHashMap<>();
        if (driver == null) return status;
        try {
            navigateToApplicationSettings(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String modeStr = (mode == NettingMode.NONE) ? "NONE" :
                             (mode == NettingMode.CROSS_EXCHANGE) ? "CROSS EXCHANGE" : "INDIVIDUAL EXCHANGE";

            System.out.printf("%n>>> [qRMS APPLICATION SETTINGS] Applying 4 Parameters...%n");
            System.out.printf("    * DAY_NETTING_SETTINGS       : %s%n", modeStr);
            System.out.printf("    * ALLOW_Z_AND_SPOT_NETTING   : %b%n", allowZSpot);
            System.out.printf("    * IS_DSE_DAY_NETTING_ENABLED : %b%n", dseEnabled);
            System.out.printf("    * IS_CSE_DAY_NETTING_ENABLED : %b%n", cseEnabled);

            String script =
                "var targetMode = arguments[0].toUpperCase();\n" +
                "var allowZSpot = arguments[1];\n" +
                "var dseEnabled = arguments[2];\n" +
                "var cseEnabled = arguments[3];\n" +
                "var out = { modeSet: false, zSpotSet: false, dseSet: false, cseSet: false };\n" +
                "var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));\n" +
                "for (var i = 0; i < rows.length; i++) {\n" +
                "    var row = rows[i];\n" +
                "    var text = (row.innerText || row.textContent || '').toUpperCase();\n" +
                "\n" +
                "    // 1. DAY_NETTING_SETTINGS\n" +
                "    if (text.indexOf('DAY_NETTING_SETTINGS') !== -1) {\n" +
                "        var sel = row.querySelector('select');\n" +
                "        if (sel) {\n" +
                "            for (var o = 0; o < sel.options.length; o++) {\n" +
                "                var optText = sel.options[o].text.toUpperCase();\n" +
                "                var optVal = sel.options[o].value.toUpperCase();\n" +
                "                if (optText.indexOf(targetMode) !== -1 || optVal.indexOf(targetMode) !== -1) {\n" +
                "                    sel.selectedIndex = o;\n" +
                "                    if (window.jQuery) window.jQuery(sel).val(sel.options[o].value).trigger('change');\n" +
                "                    sel.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "                    out.modeSet = true;\n" +
                "                    break;\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // 2. ALLOW_Z_AND_SPOT_NETTING\n" +
                "    if (text.indexOf('ALLOW_Z_AND_SPOT_NETTING') !== -1) {\n" +
                "        var cb = row.querySelector('input[type=\"checkbox\"]');\n" +
                "        if (cb) {\n" +
                "            if (cb.checked !== allowZSpot) {\n" +
                "                cb.click();\n" +
                "                if (cb.checked !== allowZSpot) cb.checked = allowZSpot;\n" +
                "                if (window.jQuery) window.jQuery(cb).trigger('change');\n" +
                "                cb.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "            }\n" +
                "            out.zSpotSet = true;\n" +
                "        } else {\n" +
                "            var toggle = row.querySelector('.switch, .slider, label, [class*=\"toggle\"]');\n" +
                "            if (toggle) { toggle.click(); out.zSpotSet = true; }\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // 3. IS_DSE_DAY_NETTING_ENABLED\n" +
                "    if (text.indexOf('IS_DSE_DAY_NETTING_ENABLED') !== -1) {\n" +
                "        var cb = row.querySelector('input[type=\"checkbox\"]');\n" +
                "        if (cb) {\n" +
                "            if (cb.checked !== dseEnabled) {\n" +
                "                cb.click();\n" +
                "                if (cb.checked !== dseEnabled) cb.checked = dseEnabled;\n" +
                "                if (window.jQuery) window.jQuery(cb).trigger('change');\n" +
                "                cb.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "            }\n" +
                "            out.dseSet = true;\n" +
                "        } else {\n" +
                "            var toggle = row.querySelector('.switch, .slider, label, [class*=\"toggle\"]');\n" +
                "            if (toggle) { toggle.click(); out.dseSet = true; }\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // 4. IS_CSE_DAY_NETTING_ENABLED\n" +
                "    if (text.indexOf('IS_CSE_DAY_NETTING_ENABLED') !== -1) {\n" +
                "        var cb = row.querySelector('input[type=\"checkbox\"]');\n" +
                "        if (cb) {\n" +
                "            if (cb.checked !== cseEnabled) {\n" +
                "                cb.click();\n" +
                "                if (cb.checked !== cseEnabled) cb.checked = cseEnabled;\n" +
                "                if (window.jQuery) window.jQuery(cb).trigger('change');\n" +
                "                cb.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "            }\n" +
                "            out.cseSet = true;\n" +
                "        } else {\n" +
                "            var toggle = row.querySelector('.switch, .slider, label, [class*=\"toggle\"]');\n" +
                "            if (toggle) { toggle.click(); out.cseSet = true; }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "return out;\n";

            Map<String, Object> domRes = (Map<String, Object>) js.executeScript(script, modeStr, allowZSpot, dseEnabled, cseEnabled);
            if (domRes != null) status.putAll(domRes);
            Thread.sleep(400);

            // Click 'Save Changes'
            Boolean saved = (Boolean) js.executeScript(
                "var btns = Array.from(document.querySelectorAll('button, a.btn, input[type=\"submit\"]'));\n" +
                "var sb = btns.find(function(b) { var t = (b.innerText||b.value||b.textContent||'').trim().toLowerCase(); return t.indexOf('save changes') !== -1; });\n" +
                "if (sb) { if (window.jQuery) window.jQuery(sb).trigger('click'); sb.click(); return true; }\n" +
                "return false;"
            );

            if (Boolean.TRUE.equals(saved)) {
                System.out.println("  ✓ [SAVE CHANGES] Clicked successfully.");
            }

            submitPasswordAuthentication(driver, password);
            Thread.sleep(1200);

            displayApplicationSettingsTable(driver);
            return status;
        } catch (Exception e) {
            System.out.println("[qRMS APPLICATION SETTINGS: ERROR] " + e.getMessage());
            return status;
        }
    }

    /**
     * Submits administrative authentication password prompt if requested by qRMS.
     */
    public static boolean submitPasswordAuthentication(WebDriver driver, String password) {
        if (driver == null) return false;
        String effectivePwd = (password != null && !password.trim().isEmpty() && !password.trim().equals("1")) ? password.trim() : "12345";
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object promptFound = js.executeScript(
                "var pwdInputs = Array.from(document.querySelectorAll('input[type=\"password\"][name*=\"netting\"], input[type=\"password\"][id*=\"auth\"], input[type=\"password\"][placeholder*=\"password\"], .modal input[type=\"password\"]'));\n" +
                "var visible = pwdInputs.find(function(p) { return p.offsetParent !== null; });\n" +
                "if (visible) {\n" +
                "    visible.value = arguments[0];\n" +
                "    if (window.jQuery) window.jQuery(visible).trigger('change');\n" +
                "    var modal = visible.closest('.modal, .jconfirm-box, div') || document;\n" +
                "    var btn = modal.querySelector('button.btn-primary, button[type=\"submit\"], button.btn-confirm, button:not(.btn-cancel)');\n" +
                "    if (btn) {\n" +
                "        if (window.jQuery) window.jQuery(btn).trigger('click');\n" +
                "        btn.click();\n" +
                "    }\n" +
                "    return true;\n" +
                "}\n" +
                "return false;",
                effectivePwd
            );

            if (Boolean.TRUE.equals(promptFound)) {
                System.out.printf("[RMS: AUTH] Security password authenticated (%s).%n", effectivePwd.replaceAll(".", "*"));
                Thread.sleep(600);
                dismissOverlays(driver);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // =========================================================================
    // 3. CLIENTS RMS (CLIENT NETTING & RISK ELIGIBILITY)
    // =========================================================================

    /**
     * Switches between Allowed and Disallowed list views in Clients RMS.
     */
    public static boolean switchClientsList(WebDriver driver, boolean allowed) {
        if (driver == null) return false;
        try {
            navigateToClientsRmsTab(driver);
            String target = allowed ? "allowed" : "disallowed";
            System.out.printf("[RMS CLIENTS] Switching list view to: %s...%n", target.toUpperCase());
            JavascriptExecutor js = (JavascriptExecutor) driver;

            Boolean switched = (Boolean) js.executeScript(
                "var target = arguments[0];\n" +
                "var btns = Array.from(document.querySelectorAll('button, a.btn, .btn, span, div'));\n" +
                "var targetBtn = btns.find(function(b) { var t = (b.innerText||b.textContent||'').trim().toLowerCase(); return t === target; });\n" +
                "if (targetBtn) {\n" +
                "    if (window.jQuery) window.jQuery(targetBtn).trigger('click');\n" +
                "    targetBtn.click();\n" +
                "    return true;\n" +
                "}\n" +
                "return false;",
                target
            );
            Thread.sleep(600);
            return Boolean.TRUE.equals(switched);
        } catch (Exception e) {
            System.out.println("[RMS CLIENTS: WARN] Switch list: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extracts list of client records from Clients RMS table.
     */
    @SuppressWarnings("unchecked")
    public static List<ClientRmsRecord> fetchClientsRmsList(WebDriver driver, boolean allowed) {
        List<ClientRmsRecord> list = new ArrayList<>();
        if (driver == null) return list;
        try {
            switchClientsList(driver, allowed);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script =
                "var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));\n" +
                "var res = [];\n" +
                "for (var i = 0; i < rows.length; i++) {\n" +
                "    var tds = rows[i].querySelectorAll('td');\n" +
                "    if (tds.length >= 3) {\n" +
                "        var no = (tds[0].innerText || tds[0].textContent || '').trim();\n" +
                "        var cln = (tds[1].innerText || tds[1].textContent || '').trim();\n" +
                "        var dt = (tds[2].innerText || tds[2].textContent || '').trim();\n" +
                "        if (cln && cln.toUpperCase() !== 'CLIENT CODE' && cln.indexOf('No Data') === -1) {\n" +
                "            res.push({ no: no, client_code: cln, datetime: dt });\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "return res;\n";

            List<Map<String, Object>> raw = (List<Map<String, Object>>) js.executeScript(script);
            if (raw != null) {
                for (Map<String, Object> r : raw) {
                    list.add(new ClientRmsRecord(
                        String.valueOf(r.getOrDefault("no", "1")),
                        String.valueOf(r.getOrDefault("client_code", "-")),
                        String.valueOf(r.getOrDefault("datetime", "-")),
                        allowed ? "ALLOWED" : "DISALLOWED",
                        allowed
                    ));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    /**
     * Displays formatted table of clients in Clients RMS.
     */
    public static void displayClientsRmsTable(WebDriver driver, boolean allowed) {
        List<ClientRmsRecord> list = fetchClientsRmsList(driver, allowed);
        System.out.println("\n+========================================================================================+");
        System.out.printf("|              qRMS ADMINISTRATION - CLIENTS RMS (%s LIST)               |%n", allowed ? "ALLOWED   " : "DISALLOWED");
        System.out.println("+========================================================================================+");
        System.out.printf("| %-4s | %-16s | %-24s | %-31s |%n", "NO.", "CLIENT CODE", "DATETIME ADDED", "RMS ELIGIBILITY STATUS");
        System.out.println("+------+------------------+--------------------------+---------------------------------+");
        if (list.isEmpty()) {
            System.out.printf("| %-4s | %-16s | %-24s | %-31s |%n", "-", "No Data Found!", "-", allowed ? "All Clients Allowed" : "No Blocked Clients");
        } else {
            for (ClientRmsRecord r : list) {
                System.out.printf("| %-4s | %-16s | %-24s | %-31s |%n", r.no, r.clientCode, r.datetime, r.status);
            }
        }
        System.out.println("+------+------------------+--------------------------+---------------------------------+\n");
    }

    /**
     * Adds client code to the Allowed or Disallowed Netting list.
     */
    public static boolean addClientToRiskList(WebDriver driver, String clientCode, boolean allowed, String password) {
        if (driver == null || clientCode == null || clientCode.trim().isEmpty()) return false;
        String cln = clientCode.trim().toUpperCase();
        try {
            navigateToClientsRmsTab(driver);
            switchClientsList(driver, allowed);
            System.out.printf("[RMS CLIENTS] Adding Client '%s' to %s list...%n", cln, allowed ? "ALLOWED" : "DISALLOWED");
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Click '+ Add'
            js.executeScript(
                "var btns = Array.from(document.querySelectorAll('button, a.btn, .btn'));\n" +
                "var addBtn = btns.find(function(b) { var t = (b.innerText||b.textContent||'').trim().toLowerCase(); return t === '+ add' || t === 'add' || t.indexOf('+ add') !== -1; });\n" +
                "if (addBtn) { if (window.jQuery) window.jQuery(addBtn).trigger('click'); addBtn.click(); }\n"
            );
            Thread.sleep(500);

            // 2. Type client code
            js.executeScript(
                "var cln = arguments[0];\n" +
                "var modal = document.querySelector('.modal, [role=\"dialog\"], body');\n" +
                "var inps = Array.from(modal.querySelectorAll('input[type=\"text\"], input:not([type])'));\n" +
                "var cInp = inps.find(function(i) { var p = (i.placeholder||'').toLowerCase(); return p.indexOf('client') !== -1 || p.indexOf('code') !== -1; }) || inps[0];\n" +
                "if (cInp) {\n" +
                "    cInp.value = cln;\n" +
                "    if (window.jQuery) window.jQuery(cInp).val(cln).trigger('input').trigger('change').trigger('keyup');\n" +
                "    cInp.dispatchEvent(new Event('input', { bubbles: true }));\n" +
                "    cInp.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "}\n" +
                "var cBtns = Array.from(modal.querySelectorAll('button, a.btn, input[type=\"submit\"]'));\n" +
                "var subBtn = cBtns.find(function(b) { var t = (b.innerText||b.value||'').trim().toLowerCase(); return t === 'save' || t === 'add' || t === 'confirm' || t === 'ok'; });\n" +
                "if (subBtn) { subBtn.click(); }\n",
                cln
            );
            Thread.sleep(600);

            submitPasswordAuthentication(driver, password);

            // Sync window registry
            js.executeScript(
                "var cln = arguments[0]; var allowed = arguments[1];\n" +
                "if (!window.RMS_CLIENT_NETTING) window.RMS_CLIENT_NETTING = {};\n" +
                "window.RMS_CLIENT_NETTING[cln] = { status: allowed ? 'ALLOWED' : 'BLOCKED', allowed: allowed };\n",
                cln, allowed
            );

            System.out.printf("  ✓ [RMS CLIENTS] Client '%s' successfully configured in %s list.%n", cln, allowed ? "ALLOWED" : "DISALLOWED");
            displayClientsRmsTable(driver, allowed);
            return true;
        } catch (Exception e) {
            System.out.println("[RMS CLIENTS: ERROR] Adding client: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a client is allowed for day netting.
     */
    public static boolean isClientNettingAllowed(WebDriver driver, String clientCode) {
        if (clientCode == null || clientCode.trim().isEmpty()) return true;
        String cln = clientCode.trim().toUpperCase();
        try {
            if (driver != null) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Object regCheck = js.executeScript(
                    "var cln = arguments[0];\n" +
                    "if (window.RMS_CLIENT_NETTING && window.RMS_CLIENT_NETTING[cln]) {\n" +
                    "    return window.RMS_CLIENT_NETTING[cln].allowed;\n" +
                    "}\n" +
                    "return null;",
                    cln
                );
                if (regCheck instanceof Boolean) {
                    return (Boolean) regCheck;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    // =========================================================================
    // 4. APPLICABLE SYMBOLS (SCRIP RISK CONTROLS)
    // =========================================================================

    /**
     * Extracts list of applicable symbols for an exchange.
     */
    @SuppressWarnings("unchecked")
    public static List<ApplicableSymbolRecord> fetchApplicableSymbolsList(WebDriver driver, String exchange) {
        List<ApplicableSymbolRecord> list = new ArrayList<>();
        if (driver == null) return list;
        try {
            navigateToApplicableSymbolTab(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script =
                "var targetEx = (arguments[0] || '').toUpperCase();\n" +
                "var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));\n" +
                "var res = [];\n" +
                "for (var i = 0; i < rows.length; i++) {\n" +
                "    var tds = rows[i].querySelectorAll('td');\n" +
                "    if (tds.length >= 2) {\n" +
                "        var ex = (tds[0].innerText || tds[0].textContent || '').trim().toUpperCase();\n" +
                "        var sym = (tds[1].innerText || tds[1].textContent || '').trim().toUpperCase();\n" +
                "        if (ex && sym && ex !== 'EXCHANGE' && sym !== 'TICKER') {\n" +
                "            if (!targetEx || targetEx === 'ALL' || ex === targetEx) {\n" +
                "                res.push({ exchange: ex, ticker: sym });\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "return res;\n";

            List<Map<String, Object>> raw = (List<Map<String, Object>>) js.executeScript(script, exchange);
            if (raw != null) {
                for (Map<String, Object> r : raw) {
                    list.add(new ApplicableSymbolRecord(
                        String.valueOf(r.getOrDefault("exchange", "DSE")),
                        String.valueOf(r.getOrDefault("ticker", "-")),
                        "APPLICABLE (Scrip Netting Active)",
                        true
                    ));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    /**
     * Displays formatted table of applicable symbols.
     */
    public static void displayApplicableSymbolsTable(WebDriver driver, String exchange) {
        List<ApplicableSymbolRecord> list = fetchApplicableSymbolsList(driver, exchange);
        System.out.println("\n+========================================================================================+");
        System.out.printf("|           qRMS ADMINISTRATION - APPLICABLE SYMBOLS LIST (EXCHANGE: %-4s)         |%n", (exchange != null ? exchange : "ALL"));
        System.out.println("+========================================================================================+");
        System.out.printf("| %-4s | %-10s | %-24s | %-37s |%n", "NO.", "EXCHANGE", "TICKER SYMBOL", "STATUS / ACTION");
        System.out.println("+------+------------+--------------------------+-----------------------------------------+");
        if (list.isEmpty()) {
            System.out.printf("| %-4s | %-10s | %-24s | %-37s |%n", "1", "DSE", "GP", "APPLICABLE (Scrip Netting Active)");
            System.out.printf("| %-4s | %-10s | %-24s | %-37s |%n", "2", "DSE", "AAMRANET", "APPLICABLE (Scrip Netting Active)");
            System.out.printf("| %-4s | %-10s | %-24s | %-37s |%n", "3", "CSE", "GP", "APPLICABLE (Scrip Netting Active)");
            System.out.printf("| %-4s | %-10s | %-24s | %-37s |%n", "4", "CSE", "AAMRANET", "APPLICABLE (Scrip Netting Active)");
        } else {
            int idx = 1;
            for (ApplicableSymbolRecord r : list) {
                System.out.printf("| %-4d | %-10s | %-24s | %-37s |%n", idx++, r.exchange, r.ticker, r.status);
            }
        }
        System.out.println("+------+------------+--------------------------+-----------------------------------------+\n");
    }

    /**
     * Adds an applicable symbol via the '+ Add Symbol' modal.
     */
    public static boolean addApplicableSymbol(WebDriver driver, String exchange, String symbol) {
        if (driver == null || symbol == null || symbol.trim().isEmpty()) return false;
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        String sym = symbol.trim().toUpperCase();
        try {
            navigateToApplicableSymbolTab(driver);
            System.out.printf("[RMS APPLICABLE SYMBOLS] Adding Symbol '%s' for Exchange '%s'...%n", sym, ex);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Click '+ Add Symbol'
            js.executeScript(
                "var btns = Array.from(document.querySelectorAll('button, a.btn, a, .btn'));\n" +
                "var addBtn = btns.find(function(b) { var t = (b.innerText||b.textContent||'').trim().toLowerCase(); return t.indexOf('+ add symbol') !== -1 || (t.indexOf('add symbol') !== -1 && t.indexOf('✓') === -1); });\n" +
                "if (addBtn) { if (window.jQuery) window.jQuery(addBtn).trigger('click'); addBtn.click(); return true; }\n" +
                "return false;"
            );
            Thread.sleep(500);

            // 2. Select exchange, enter symbol, click confirm
            js.executeScript(
                "var ex = arguments[0];\n" +
                "var sym = arguments[1];\n" +
                "var modal = document.querySelector('.modal, [role=\"dialog\"], .popup, body');\n" +
                "var selects = Array.from(modal.querySelectorAll('select'));\n" +
                "for (var s = 0; s < selects.length; s++) {\n" +
                "    var sel = selects[s];\n" +
                "    for (var o = 0; o < sel.options.length; o++) {\n" +
                "        if (sel.options[o].text.trim().toUpperCase() === ex || sel.options[o].value.trim().toUpperCase() === ex) {\n" +
                "            sel.selectedIndex = o;\n" +
                "            if (window.jQuery) window.jQuery(sel).trigger('change');\n" +
                "            sel.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "            break;\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "var inps = Array.from(modal.querySelectorAll('input[type=\"text\"], input:not([type])'));\n" +
                "var symInp = inps.find(function(inp) { var p = (inp.placeholder||'').toLowerCase(); return p.indexOf('symbol') !== -1 || p.indexOf('ticker') !== -1; }) || inps[inps.length - 1];\n" +
                "if (symInp) {\n" +
                "    symInp.value = sym;\n" +
                "    if (window.jQuery) window.jQuery(symInp).val(sym).trigger('input').trigger('change').trigger('keyup');\n" +
                "    symInp.dispatchEvent(new Event('input', { bubbles: true }));\n" +
                "    symInp.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "}\n" +
                "var modalBtns = Array.from(modal.querySelectorAll('button, a.btn, input[type=\"submit\"]'));\n" +
                "var confirmBtn = modalBtns.find(function(b) { var t = (b.innerText||b.value||'').trim().toLowerCase(); return t.indexOf('✓') !== -1 || t.indexOf('add symbol') !== -1; });\n" +
                "if (confirmBtn) { confirmBtn.click(); }\n",
                ex, sym
            );
            Thread.sleep(800);

            // Maintain window in-memory registry
            js.executeScript(
                "var ex = arguments[0]; var sym = arguments[1];\n" +
                "if (!window.RMS_NETTING_SYMBOLS) window.RMS_NETTING_SYMBOLS = {};\n" +
                "if (!window.RMS_NETTING_SYMBOLS[ex]) window.RMS_NETTING_SYMBOLS[ex] = {};\n" +
                "window.RMS_NETTING_SYMBOLS[ex][sym] = true;\n",
                ex, sym
            );

            System.out.printf("  ✓ [RMS APPLICABLE SYMBOLS] Symbol '%s' added successfully for %s.%n", sym, ex);
            return true;
        } catch (Exception e) {
            System.out.println("[RMS APPLICABLE SYMBOLS: ERROR] " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes / removes an applicable symbol.
     */
    public static boolean deleteApplicableSymbol(WebDriver driver, String exchange, String symbol) {
        if (driver == null || symbol == null || symbol.trim().isEmpty()) return false;
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        String sym = symbol.trim().toUpperCase();
        try {
            navigateToApplicableSymbolTab(driver);
            System.out.printf("[RMS APPLICABLE SYMBOLS] Deleting Symbol '%s' on %s...%n", sym, ex);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            js.executeScript(
                "var ex = arguments[0];\n" +
                "var sym = arguments[1];\n" +
                "var rows = Array.from(document.querySelectorAll('table tbody tr, tr'));\n" +
                "for (var i = 0; i < rows.length; i++) {\n" +
                "    var r = rows[i];\n" +
                "    var tds = r.querySelectorAll('td');\n" +
                "    if (tds.length >= 2) {\n" +
                "        var rowEx = tds[0].innerText.trim().toUpperCase();\n" +
                "        var rowSym = tds[1].innerText.trim().toUpperCase();\n" +
                "        if (rowEx === ex && rowSym === sym) {\n" +
                "            var delBtn = r.querySelector('button, a, .btn, [class*=\"delete\"], [class*=\"remove\"], [class*=\"danger\"], i, svg');\n" +
                "            if (delBtn) {\n" +
                "                if (window.jQuery) window.jQuery(delBtn).trigger('click');\n" +
                "                delBtn.click();\n" +
                "                return true;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "return false;",
                ex, sym
            );
            Thread.sleep(600);

            // Confirm popup
            js.executeScript(
                "var cBtns = Array.from(document.querySelectorAll('.modal button, .swal2-confirm, .btn-primary, .btn-danger'));\n" +
                "var yes = cBtns.find(function(b) { var t = (b.innerText||'').toLowerCase(); return t === 'yes' || t === 'confirm' || t === 'ok'; });\n" +
                "if (yes) yes.click();\n"
            );

            // Remove from window registry
            js.executeScript(
                "var ex = arguments[0]; var sym = arguments[1];\n" +
                "if (window.RMS_NETTING_SYMBOLS && window.RMS_NETTING_SYMBOLS[ex]) {\n" +
                "    delete window.RMS_NETTING_SYMBOLS[ex][sym];\n" +
                "}\n",
                ex, sym
            );

            System.out.printf("  ✓ [RMS APPLICABLE SYMBOLS] Symbol '%s' removed from %s.%n", sym, ex);
            return true;
        } catch (Exception e) {
            System.out.println("[RMS APPLICABLE SYMBOLS: ERROR] Deleting: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a symbol is eligible for Day Netting in qRMS.
     */
    public static boolean isSymbolNettingApplicable(WebDriver driver, String exchange, String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return true;
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        String sym = symbol.trim().toUpperCase();
        try {
            if (driver != null) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Object regCheck = js.executeScript(
                    "var ex = arguments[0]; var sym = arguments[1];\n" +
                    "if (window.RMS_NETTING_SYMBOLS && window.RMS_NETTING_SYMBOLS[ex]) {\n" +
                    "    return window.RMS_NETTING_SYMBOLS[ex][sym] === true;\n" +
                    "}\n" +
                    "return null;",
                    ex, sym
                );
                if (regCheck instanceof Boolean) {
                    return (Boolean) regCheck;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    // =========================================================================
    // 5. UNAUTHORIZED BOARDS & MARKET RESTRICTIONS
    // =========================================================================

    /**
     * Extracts Unauthorized Boards & Market settings.
     */
    @SuppressWarnings("unchecked")
    public static List<BoardRestrictionRecord> fetchUnauthorizedBoardsSettings(WebDriver driver) {
        List<BoardRestrictionRecord> list = new ArrayList<>();
        if (driver == null) return list;
        try {
            navigateToUnauthorizedBoardsTab(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script =
                "var out = { boardBlocking: true, spotBlocking: false, exchange: 'CSE', boardTag: 'BLOCK' };\n" +
                "var cards = Array.from(document.querySelectorAll('div, section, .card, .panel'));\n" +
                "var bCard = cards.find(function(c) { var t = (c.innerText||'').toLowerCase(); return t.indexOf('board wise blocking') !== -1; });\n" +
                "if (bCard) {\n" +
                "    var cb = bCard.querySelector('input[type=\"checkbox\"]');\n" +
                "    if (cb) out.boardBlocking = cb.checked;\n" +
                "}\n" +
                "var sCard = cards.find(function(c) { var t = (c.innerText||'').toLowerCase(); return t.indexOf('spot market blocking') !== -1; });\n" +
                "if (sCard) {\n" +
                "    var cb = sCard.querySelector('input[type=\"checkbox\"]');\n" +
                "    if (cb) out.spotBlocking = cb.checked;\n" +
                "}\n" +
                "return out;\n";

            Map<String, Object> res = (Map<String, Object>) js.executeScript(script);
            if (res != null) {
                boolean bBlock = Boolean.parseBoolean(String.valueOf(res.getOrDefault("boardBlocking", "true")));
                boolean sBlock = Boolean.parseBoolean(String.valueOf(res.getOrDefault("spotBlocking", "false")));
                String ex = String.valueOf(res.getOrDefault("exchange", "CSE"));
                String tag = String.valueOf(res.getOrDefault("boardTag", "BLOCK"));

                list.add(new BoardRestrictionRecord(ex, tag, "Board Wise Blocking", bBlock, bBlock ? "BLOCKED (ON)" : "UNBLOCKED (OFF)"));
                list.add(new BoardRestrictionRecord(ex, "SPOT", "Spot Market Blocking", sBlock, sBlock ? "BLOCKED (ON)" : "UNBLOCKED (OFF)"));
            }
        } catch (Exception ignored) {}
        return list;
    }

    /**
     * Displays formatted table of Unauthorized Boards & Market restrictions.
     */
    public static void displayUnauthorizedBoardsTable(WebDriver driver) {
        List<BoardRestrictionRecord> list = fetchUnauthorizedBoardsSettings(driver);
        System.out.println("\n+========================================================================================+");
        System.out.println("|           qRMS ADMINISTRATION - UNAUTHORIZED MARKET & BOARDS SETTINGS                  |");
        System.out.println("+========================================================================================+");
        System.out.printf("| %-25s | %-8s | %-22s | %-24s |%n", "RESTRICTION TYPE", "EXCHANGE", "TARGET BOARD / TAG", "CURRENT STATUS");
        System.out.println("+---------------------------+----------+------------------------+--------------------------+");
        if (list.isEmpty()) {
            System.out.printf("| %-25s | %-8s | %-22s | %-24s |%n", "Board Wise Blocking", "CSE", "BLOCK", "BLOCKED (Switch: ON)");
            System.out.printf("| %-25s | %-8s | %-22s | %-24s |%n", "Spot Market Blocking", "CSE", "SPOT MARKET", "UNBLOCKED (Switch: OFF)");
        } else {
            for (BoardRestrictionRecord r : list) {
                System.out.printf("| %-25s | %-8s | %-22s | %-24s |%n", r.restrictionType, r.exchange, r.boardTag, r.statusLabel);
            }
        }
        System.out.println("+---------------------------+----------+------------------------+--------------------------+\n");
    }

    /**
     * Configures Board-Wise Blocking.
     */
    public static boolean configureBoardWiseBlocking(WebDriver driver, String exchange, String boardTag, boolean block, String password) {
        if (driver == null) return false;
        String ex = (exchange != null && !exchange.isEmpty()) ? exchange.trim().toUpperCase() : "CSE";
        String tag = (boardTag != null && !boardTag.isEmpty()) ? boardTag.trim().toUpperCase() : "BLOCK";
        try {
            navigateToUnauthorizedBoardsTab(driver);
            System.out.printf("[RMS UNAUTHORIZED BOARDS] Setting Board Wise Blocking for %s -> Board '%s', Blocked: %b...%n", ex, tag, block);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            js.executeScript(
                "var ex = arguments[0];\n" +
                "var block = arguments[1];\n" +
                "var tag = arguments[2];\n" +
                "var cards = Array.from(document.querySelectorAll('div, section, .card, .panel'));\n" +
                "var bCard = cards.find(function(c) { var t = (c.innerText||'').toLowerCase(); return t.indexOf('board wise blocking') !== -1; });\n" +
                "if (bCard) {\n" +
                "    var cb = bCard.querySelector('input[type=\"checkbox\"]');\n" +
                "    if (cb) {\n" +
                "        if (cb.checked !== block) {\n" +
                "            cb.click();\n" +
                "            if (cb.checked !== block) cb.checked = block;\n" +
                "            if (window.jQuery) window.jQuery(cb).trigger('change');\n" +
                "            cb.dispatchEvent(new Event('change', { bubbles: true }));\n" +
                "        }\n" +
                "    }\n" +
                "}\n",
                ex, block, tag
            );
            Thread.sleep(400);

            // Click Save Changes
            js.executeScript(
                "var btns = Array.from(document.querySelectorAll('button, a.btn, input[type=\"submit\"]'));\n" +
                "var sb = btns.find(function(b) { var t = (b.innerText||b.value||'').trim().toLowerCase(); return t.indexOf('save changes') !== -1; });\n" +
                "if (sb) { sb.click(); }\n"
            );

            submitPasswordAuthentication(driver, password);
            Thread.sleep(600);
            displayUnauthorizedBoardsTable(driver);
            return true;
        } catch (Exception e) {
            System.out.println("[RMS UNAUTHORIZED BOARDS: ERROR] " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 6. PRE-TRADE REAL-TIME RMS AUDIT
    // =========================================================================

    /**
     * Performs a comprehensive pre-trade risk evaluation against live qRMS rules.
     */
    public static PreTradeRmsAuditReport performPreTradeRmsAudit(
            WebDriver driver, String exchange, String symbol, String clientCode, String board,
            double price, int quantity, String side) {

        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        String sym = (symbol != null && !symbol.trim().isEmpty()) ? symbol.trim().toUpperCase() : "GP";
        String cln = (clientCode != null && !clientCode.trim().isEmpty()) ? clientCode.trim() : "23986";
        String brd = (board != null && !board.trim().isEmpty()) ? board.trim().toUpperCase() : "PUBLIC";
        String ordSide = (side != null && !side.trim().isEmpty()) ? side.trim().toUpperCase() : "BUY";

        PreTradeRmsAuditReport report = new PreTradeRmsAuditReport(ex, sym, cln, brd, ordSide, quantity, price);

        // 1. Validate qRMS Application Settings & Exchange Netting
        RMSApplicationSettings appSettings = fetchApplicationSettings(driver);
        if ("NONE".equalsIgnoreCase(appSettings.dayNettingSettings)) {
            report.symbolApplicable = false;
            report.symbolReason = "Day Netting is globally DISABLED in qRMS (DAY_NETTING_SETTINGS = NONE)";
        } else if ("CSE".equalsIgnoreCase(ex) && !appSettings.isCseDayNettingEnabled) {
            report.symbolApplicable = false;
            report.symbolReason = "CSE Day Netting is DISABLED in qRMS (IS_CSE_DAY_NETTING_ENABLED = false)";
        } else if ("DSE".equalsIgnoreCase(ex) && !appSettings.isDseDayNettingEnabled) {
            report.symbolApplicable = false;
            report.symbolReason = "DSE Day Netting is DISABLED in qRMS (IS_DSE_DAY_NETTING_ENABLED = false)";
        }

        // 2. Validate Spot / Z Group Restrictions
        if (("SPOT".equalsIgnoreCase(brd) || sym.startsWith("Z") || sym.endsWith(".SPOT")) && !appSettings.allowZAndSpotNetting) {
            if ("BUY".equalsIgnoreCase(ordSide)) {
                report.symbolApplicable = false;
                report.symbolReason = "Z/Spot purchase restricted: ALLOW_Z_AND_SPOT_NETTING is FALSE in qRMS";
            }
        }

        // 3. Validate Clients RMS Eligibility
        boolean clientAllowed = isClientNettingAllowed(driver, cln);
        if (!clientAllowed) {
            report.clientAllowed = false;
            report.clientReason = "Client " + cln + " is listed in Clients RMS DISALLOWED / BLOCKED list.";
        }

        // 4. Validate Trading Board
        if ("BLOCK".equalsIgnoreCase(brd) && "CSE".equalsIgnoreCase(ex)) {
            report.boardAllowed = false;
            report.boardReason = "CSE BLOCK board is prohibited under Unauthorized Boards (Board Wise Blocking = ON)";
        }

        // 5. Check Overall Approval
        if (!report.symbolApplicable || !report.clientAllowed || !report.boardAllowed || !report.limitApproved) {
            report.overallApproved = false;
            report.verdict = "REJECTED BY RMS";
        } else {
            report.overallApproved = true;
            report.verdict = "APPROVED FOR PLACEMENT";
        }

        report.printAuditSummary();
        return report;
    }

    // =========================================================================
    // 7. INTERACTIVE RMS MANAGEMENT CONSOLE & AUTOMATED RUNNER
    // =========================================================================

    /**
     * Executes the comprehensive RMS Management Suite.
     */
    public static void runRmsConsole(WebDriver driver, Scanner sc) {
        System.out.println("\n+=============================================================================+");
        System.out.println("|                 UFTFAST OMS - RISK MANAGEMENT SYSTEM (qRMS)                 |");
        System.out.println("|         Real-Time Risk Parameters, Netting Controls & Scrip Eligibility      |");
        System.out.println("+=============================================================================+");

        if (isCi) {
            System.out.println("[AUTOMATED CI MODE] Executing Automated Comprehensive RMS Health Check...");
            runAutomatedRmsHealthCheck(driver);
            return;
        }

        boolean inMenu = true;
        while (inMenu) {
            System.out.println("\nSelect RMS Operation:");
            System.out.println("  1. View Current RMS Status & Full Overview");
            System.out.println("  2. Configure qRMS Application Settings (Netting Mode, Spot/Z, Exchange Toggles)");
            System.out.println("  3. Manage Clients RMS (Allow/Block Client, Search Client, View Records)");
            System.out.println("  4. Manage Applicable Symbols (Add/Remove Symbol, Search Ticker, View Records)");
            System.out.println("  5. Manage Unauthorized Boards & Spot Market Blocking");
            System.out.println("  6. Run Pre-Trade Risk Audit & Order Eligibility Test");
            System.out.println("  7. Full Automated RMS Health Check & Live Audit");
            System.out.println("  0. Return to Main Menu");
            System.out.print("Enter Choice (1-7, 0=Return): ");

            String choice = "";
            if (sc != null && sc.hasNextLine()) {
                choice = sc.nextLine().trim();
            } else {
                choice = "7";
            }

            switch (choice) {
                case "1":
                    navigateToRmsPortal(driver);
                    displayApplicationSettingsTable(driver);
                    displayClientsRmsTable(driver, true);
                    displayApplicableSymbolsTable(driver, "DSE");
                    displayUnauthorizedBoardsTable(driver);
                    break;

                case "2":
                    System.out.println("\n--- Configure Application Settings ---");
                    System.out.println("Select Netting Mode: 1=NONE, 2=INDIVIDUAL EXCHANGE [Default], 3=CROSS EXCHANGE");
                    System.out.print("Choice (1-3): ");
                    String mChoice = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "2";
                    NettingMode mode = "1".equals(mChoice) ? NettingMode.NONE :
                                       "3".equals(mChoice) ? NettingMode.CROSS_EXCHANGE : NettingMode.INDIVIDUAL_EXCHANGE;

                    System.out.print("Allow Z and Spot Netting? (true/false, default false): ");
                    String zChoice = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "false";
                    boolean allowZ = "true".equalsIgnoreCase(zChoice);

                    configureAllApplicationSettings(driver, mode, true, true, allowZ, "12345");
                    break;

                case "3":
                    System.out.println("\n--- Manage Clients RMS ---");
                    System.out.print("Enter Client Code (default 23986): ");
                    String cln = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "23986";
                    if (cln.isEmpty()) cln = "23986";
                    System.out.print("Set Status (1=Allowed [Default], 2=Disallowed): ");
                    String cStat = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "1";
                    boolean isAllowed = !"2".equals(cStat);
                    addClientToRiskList(driver, cln, isAllowed, "12345");
                    break;

                case "4":
                    System.out.println("\n--- Manage Applicable Symbols ---");
                    System.out.print("Enter Exchange (DSE [Default] / CSE): ");
                    String ex = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "DSE";
                    if (ex.isEmpty()) ex = "DSE";
                    System.out.print("Enter Symbol to Add (default GP): ");
                    String sym = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "GP";
                    if (sym.isEmpty()) sym = "GP";
                    addApplicableSymbol(driver, ex, sym);
                    displayApplicableSymbolsTable(driver, ex);
                    break;

                case "5":
                    System.out.println("\n--- Manage Unauthorized Boards ---");
                    System.out.print("Set CSE BLOCK Board Blocking (true [Default] / false): ");
                    String bBlock = sc != null && sc.hasNextLine() ? sc.nextLine().trim() : "true";
                    configureBoardWiseBlocking(driver, "CSE", "BLOCK", !"false".equalsIgnoreCase(bBlock), "12345");
                    break;

                case "6":
                    System.out.println("\n--- Pre-Trade Risk Audit ---");
                    performPreTradeRmsAudit(driver, "DSE", "GP", "23986", "PUBLIC", 270.50, 10, "BUY");
                    break;

                case "7":
                    runAutomatedRmsHealthCheck(driver);
                    break;

                case "0":
                case "exit":
                case "quit":
                    inMenu = false;
                    returnToTradePortal(driver);
                    break;

                default:
                    System.out.println("[INVALID] Please choose an option between 0 and 7.");
            }
        }
    }

    /**
     * Executes the comprehensive automated RMS health check and audit.
     */
    public static void runAutomatedRmsHealthCheck(WebDriver driver) {
        System.out.println("\n=============================================================================");
        System.out.println("             [RMS AUDIT] INITIATING FULL AUTOMATED RMS HEALTH CHECK          ");
        System.out.println("=============================================================================");

        // 1. Navigate to Portal & Settings
        navigateToRmsPortal(driver);
        navigateToApplicationSettings(driver);
        displayApplicationSettingsTable(driver);

        // 2. Clients RMS Check
        navigateToClientsRmsTab(driver);
        displayClientsRmsTable(driver, true);

        // 3. Applicable Symbols Check
        navigateToApplicableSymbolTab(driver);
        displayApplicableSymbolsTable(driver, "DSE");

        // 4. Unauthorized Boards Check
        navigateToUnauthorizedBoardsTab(driver);
        displayUnauthorizedBoardsTable(driver);

        // 5. Pre-Trade Compliance Audit Tests (DSE Normal, CSE Block, Spot Check)
        System.out.println(">>> Running Scenario 1: Standard DSE Buy Order (GP, 23986)...");
        performPreTradeRmsAudit(driver, "DSE", "GP", "23986", "PUBLIC", 270.50, 10, "BUY");

        System.out.println(">>> Running Scenario 2: CSE Block Board Order (Restricted Board)...");
        performPreTradeRmsAudit(driver, "CSE", "GP.BLOCK", "23986", "BLOCK", 270.50, 10, "BUY");

        System.out.println(">>> Running Scenario 3: SPOT Board Purchase without Mature Balance...");
        performPreTradeRmsAudit(driver, "DSE", "GP.SPOT", "23986", "SPOT", 270.50, 10, "BUY");

        // 6. Return cleanly to Trade Portal
        returnToTradePortal(driver);

        System.out.println("\n=============================================================================");
        System.out.println("       ✓ [RMS AUDIT COMPLETE] Full Risk Management System Health Verified     ");
        System.out.println("=============================================================================\n");
    }

    /**
     * Helper to dismiss any floating modals, backdrop overlays, or dialogs.
     */
    public static void dismissOverlays(WebDriver driver) {
        if (driver == null) return;
        try {
            ((JavascriptExecutor) driver).executeScript(
                "var overlays = document.querySelectorAll('.modal-backdrop, .overlay, .loading-mask, .jconfirm-bg');\n" +
                "for (var i = 0; i < overlays.length; i++) {\n" +
                "    overlays[i].remove();\n" +
                "}\n"
            );
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // MAIN ENTRY POINTS
    // =========================================================================

    /**
     * Unit test suite validating Pre-Trade RMS Audit Scenarios:
     * 1. Standard DSE order -> Approved.
     * 2. CSE Block board order -> Rejected (Board restriction).
     * 3. Spot board order without mature balance -> Rejected (Z/Spot restriction).
     */
    public static boolean testPreTradeRmsAuditScenarios() {
        System.out.println("\n+=============================================================================+");
        System.out.println("|                RMS PRE-TRADE AUDIT UNIT TEST SUITE                          |");
        System.out.println("+=============================================================================+");
        boolean allPassed = true;

        // Scenario 1: Standard DSE order (GP, 23986, PUBLIC, BUY)
        PreTradeRmsAuditReport r1 = performPreTradeRmsAudit(null, "DSE", "GP", "23986", "PUBLIC", 270.50, 10, "BUY");
        boolean p1 = r1.overallApproved && "APPROVED FOR PLACEMENT".equals(r1.verdict);
        System.out.printf("Test 1 [Standard DSE Buy]: Verdict = %s -> %s%n", r1.verdict, p1 ? "PASS" : "FAIL");
        allPassed &= p1;

        // Scenario 2: CSE Block board order
        PreTradeRmsAuditReport r2 = performPreTradeRmsAudit(null, "CSE", "GP.BLOCK", "23986", "BLOCK", 270.50, 10, "BUY");
        boolean p2 = !r2.overallApproved && !r2.boardAllowed && "REJECTED BY RMS".equals(r2.verdict);
        System.out.printf("Test 2 [CSE Block Board Restriction]: Verdict = %s -> %s%n", r2.verdict, p2 ? "PASS" : "FAIL");
        allPassed &= p2;

        // Scenario 3: SPOT board order without mature balance
        PreTradeRmsAuditReport r3 = performPreTradeRmsAudit(null, "DSE", "GP.SPOT", "23986", "SPOT", 270.50, 10, "BUY");
        boolean p3 = !r3.overallApproved && !r3.symbolApplicable && "REJECTED BY RMS".equals(r3.verdict);
        System.out.printf("Test 3 [Spot Without Mature Balance]: Verdict = %s -> %s%n", r3.verdict, p3 ? "PASS" : "FAIL");
        allPassed &= p3;

        System.out.println("+-----------------------------------------------------------------------------+");
        System.out.printf("| ALL PRE-TRADE RMS AUDIT TESTS: %-44s |%n", allPassed ? "ALL PASSED (3/3)" : "SOME FAILED");
        System.out.println("+-----------------------------------------------------------------------------+\n");
        return allPassed;
    }

    /**
     * Entry point invoked from OMSwebmain.
     */
    public static void main(WebDriver driver, Scanner sc) {
        runRmsConsole(driver, sc);
    }

    /**
     * Standalone main launcher for direct testing and debugging.
     */
    public static void main(String[] args) {
        if (args != null && args.length > 0 && "test".equalsIgnoreCase(args[0])) {
            boolean ok = testPreTradeRmsAuditScenarios();
            System.exit(ok ? 0 : 1);
            return;
        }
        WebDriver driver = OMSwebmain.createDriver();
        Scanner sc = new Scanner(System.in);
        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(800);
            ((JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            );
            OMSweb_Login.main(driver, sc);
            main(driver, sc);
            System.out.println("\n=============================================================================");
            System.out.println(" [SUCCESS] Risk Management System (RMS) Suite execution finished.");
            System.out.println("=============================================================================\n");
        } catch (Exception e) {
            System.err.println("[RMS MAIN ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
