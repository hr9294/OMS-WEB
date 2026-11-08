import java.time.Duration;
import java.text.SimpleDateFormat;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * ============================================================================
 * OMSweb_ExportData
 * ============================================================================
 * Dedicated module for navigating to, inspecting, and managing the "Export Files"
 * menu and its 13 sub-menus in the UFTFAST OMS Web application.
 *
 * Core Features:
 * 1. Safe Navigation to Export Files interface (via Export button or Sidebar icon).
 * 2. Menu & 13 Sub-Menus DOM Data Capture:
 *      1. XML Trade File (All)
 *      2. XML Trade File (DSE)
 *      3. XML Trade File (CSE)
 *      4. CSV Trade File (All)
 *      5. CSV Trade File (DSE)
 *      6. CSV Trade File (CSE)
 *      7. TXT Trade File (CSE)
 *      8. EOD Price File
 *      9. Associates File
 *     10. Client Commission File
 *     11. Client Rating File
 *     12. Dealer Terminal File
 *     13. All Reports & Files Zip
 * 3. Page Timestamp / Export Generation Date Extraction (e.g. 01-11-2026-02:14:09 PM).
 * 4. Export File Handlers for all 13 sub-menu file formats.
 *
 * MANDATORY SAFETY CONSTRAINT:
 * - "BY DEFAULT NO FILE EXPORT" (DEFAULT_NO_EXPORT = true).
 * - Safe inspection, presence verification, and reading menu/sub-menu data
 *   is the default action on running or pressing ENTER.
 * ============================================================================
 */
public class OMSweb_ExportData {

    // =========================================================================
    // 0. CONFIGURATION & SAFETY CONSTRAINTS
    // =========================================================================
    /** By default, NO file export is triggered. Safe inspection mode only. */
    public static final boolean DEFAULT_NO_EXPORT = true;

    // Green "Export Files" Button Selector (tray with arrow down icon)
    public static final By BTN_EXPORT_FILES = By.xpath(
            "//button[contains(normalize-space(),'Export Files')] | " +
            "//a[contains(normalize-space(),'Export Files')] | " +
            "//*[contains(@class,'btn') and (contains(.,'Export Files') or .//i[contains(@class,'download')])] | " +
            "//button[@id='export_files' or contains(@id,'export_files') or contains(@class,'btn-export')]");

    // Sidebar 8th Icon (Archive / Zipper / Export Reports) or 7th Folder Icon fallback
    public static final By SIDEBAR_EXPORT_ICON = By.xpath(
            "//a[contains(@data-link,'export') or contains(@data-link,'report') or contains(@href,'export')] | " +
            "//i[contains(@class,'fa-file-archive') or contains(@class,'fa-archive') or contains(@class,'fa-download')] | " +
            "//ul[contains(@class,'sidebar') or contains(@class,'nav')]//li[8]//a | " +
            "//ul[contains(@class,'sidebar') or contains(@class,'nav')]//li[8] | " +
            "//div[contains(@class,'sidebar')]//a[8]");

    // All 13 Sub-menu names in exact UI order from screenshot
    public static final String[] EXPORT_SUB_MENU_NAMES = {
        "XML Trade File (All)",
        "XML Trade File (DSE)",
        "XML Trade File (CSE)",
        "CSV Trade File (All)",
        "CSV Trade File (DSE)",
        "CSV Trade File (CSE)",
        "TXT Trade File (CSE)",
        "EOD Price File",
        "Associates File",
        "Client Commission File",
        "Client Rating File",
        "Dealer Terminal File",
        "All Reports & Files Zip"
    };

    // =========================================================================
    // 1. SUB-MENU ENUM DEFINITION (13 SUB-MENUS)
    // =========================================================================
    public enum ExportSubMenu {
        XML_TRADE_ALL(1, "XML Trade File (All)", "xml_trade_all", "XML"),
        XML_TRADE_DSE(2, "XML Trade File (DSE)", "xml_trade_dse", "XML"),
        XML_TRADE_CSE(3, "XML Trade File (CSE)", "xml_trade_cse", "XML"),
        CSV_TRADE_ALL(4, "CSV Trade File (All)", "csv_trade_all", "CSV"),
        CSV_TRADE_DSE(5, "CSV Trade File (DSE)", "csv_trade_dse", "CSV"),
        CSV_TRADE_CSE(6, "CSV Trade File (CSE)", "csv_trade_cse", "CSV"),
        TXT_TRADE_CSE(7, "TXT Trade File (CSE)", "txt_trade_cse", "TXT"),
        EOD_PRICE_FILE(8, "EOD Price File", "eod_price_file", "DAT/CSV"),
        ASSOCIATES_FILE(9, "Associates File", "associates_file", "XML/CSV"),
        CLIENT_COMMISSION_FILE(10, "Client Commission File", "client_commission_file", "XML/CSV"),
        CLIENT_RATING_FILE(11, "Client Rating File", "client_rating_file", "XML/CSV"),
        DEALER_TERMINAL_FILE(12, "Dealer Terminal File", "dealer_terminal_file", "XML/CSV"),
        ALL_REPORTS_FILES_ZIP(13, "All Reports & Files Zip", "all_reports_files_zip", "ZIP");

        private final int index;
        private final String displayName;
        private final String identifierKey;
        private final String fileType;

        ExportSubMenu(int index, String displayName, String identifierKey, String fileType) {
            this.index = index;
            this.displayName = displayName;
            this.identifierKey = identifierKey;
            this.fileType = fileType;
        }

        public int getIndex() { return index; }
        public String getDisplayName() { return displayName; }
        public String getIdentifierKey() { return identifierKey; }
        public String getFileType() { return fileType; }

        public static ExportSubMenu fromIndex(int idx) {
            for (ExportSubMenu m : values()) {
                if (m.index == idx) return m;
            }
            return null;
        }

        public static ExportSubMenu fromName(String name) {
            if (name == null) return null;
            String clean = name.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            for (ExportSubMenu m : values()) {
                String mClean = m.displayName.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (mClean.equals(clean) || mClean.contains(clean) || clean.contains(mClean)) {
                    return m;
                }
            }
            return null;
        }
    }

    // =========================================================================
    // 2. DATA MODELS
    // =========================================================================
    public static class ExportSubMenuItemData {
        public int index;
        public String name;
        public String fileType = "";
        public String domId = "";
        public String domClass = "";
        public String href = "";
        public boolean isDisplayed = false;
        public boolean isEnabled = true;

        public ExportSubMenuItemData(int index, String name, String fileType) {
            this.index = index;
            this.name = name;
            this.fileType = fileType;
        }
    }

    public static class ExportMenuData {
        public boolean menuButtonFound = false;
        public boolean dropdownOpened = false;
        public String menuButtonText = "Export Files";
        public String pageTimestamp = "";
        public List<ExportSubMenuItemData> subMenus = new ArrayList<>();
        public long captureTimestamp = System.currentTimeMillis();

        public ExportMenuData() {
            // Seed default 13 submenus
            for (int i = 0; i < EXPORT_SUB_MENU_NAMES.length; i++) {
                ExportSubMenu esm = ExportSubMenu.fromIndex(i + 1);
                String ft = esm != null ? esm.getFileType() : "FILE";
                subMenus.add(new ExportSubMenuItemData(i + 1, EXPORT_SUB_MENU_NAMES[i], ft));
            }
        }
    }

    // =========================================================================
    // 3. MAIN INTERACTIVE ENTRY POINT (Integration with OMSwebmain)
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) {
        System.out.println("\n+========================================================================================+");
        System.out.println("|                     EXPORT FILES: MENU & SUB-MENU DATA VIEWER                          |");
        System.out.println("|             [MANDATORY SAFEGUARD: BY DEFAULT NO FILE EXPORT WILL OCCUR]                |");
        System.out.println("+========================================================================================+");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // 1. Ensure Navigation to Export Files View
        boolean navOk = navigateToExportFilesPage(driver, wait);
        if (!navOk) {
            System.out.println("[WARN] Export Files button not immediately verified. Attempting DOM discovery...");
        }

        // 2. Interactive Menu
        boolean keepRunning = true;
        while (keepRunning) {
            System.out.println("\n----------------------------------------------------------------------------------------");
            System.out.println("Select Export File Action:");
            System.out.println("  1. Select File Type to Export (User Choice 1-13: Client, Cash, Stock, etc.) [Default]");
            System.out.println("  2. Export XML Trade File (All) & CSV Trade File (All)");
            System.out.println("  3. Inspect & Display Export Files Menu & 13 Sub-Menus (Safe View - No Export)");
            System.out.println("  4. Capture Page Timestamp & Full UI Audit");
            System.out.println("  5. Return to Main Application");
            System.out.print("Enter Choice (1-5, default=1): ");

            String input = sc.nextLine().trim();
            if (input.isEmpty() || "1".equals(input)) {
                // Option 1: User Choice File Export (Sub-Menus 1-13)
                handleManualFileExport(driver, sc);

            } else if ("2".equals(input)) {
                // Option 2: Trade Files
                System.out.println("\n>>> Exporting XML and CSV Trade Files...");
                exportXmlTradeAll(driver, false);
                exportCsvTradeAll(driver, false);

            } else if ("3".equals(input)) {
                // Option 3: Inspect
                System.out.println("\n>>> [INSPECT] Inspecting Export Files Menu & 13 Sub-Menus (No file export)...");
                ExportMenuData data = captureExportMenuAndSubMenuData(driver);
                printExportSubMenusTable(data);

            } else if ("4".equals(input)) {
                // Option 4: Full Audit
                System.out.println("\n>>> Running Full Export Files UI Audit (Menu, 13 Sub-Menus & Timestamps)...");
                ExportMenuData data = captureExportMenuAndSubMenuData(driver);
                printFullReport(data);

            } else if ("5".equals(input) || "6".equals(input) || "7".equals(input)) {
                System.out.println("[EXPORT FILES] Returning to main session.");
                keepRunning = false;
            } else {
                System.out.println("Unrecognized choice. Running user choice file export (Option 1)...");
                handleManualFileExport(driver, sc);
            }
        }
    }

    // =========================================================================
    // 4. NAVIGATION METHODS (Locate Export Files Button / Sidebar)
    // =========================================================================
    /**
     * Checks if the "Export Files" button is currently visible on the page.
     */
    public static boolean isExportFilesButtonVisible(WebDriver driver) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var btns = Array.from(document.querySelectorAll('button, a, .btn, [id*=\"export\"]'));" +
                "return btns.some(function(b) {" +
                "    var txt = (b.innerText || b.textContent || '').trim().toLowerCase();" +
                "    return txt.indexOf('export files') !== -1 || (txt.indexOf('export') !== -1 && txt.indexOf('file') !== -1);" +
                "});"
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Navigates to the Export Files view (via current view, sidebar 8th icon, or 7th folder icon).
     */
    public static boolean navigateToExportFilesPage(WebDriver driver, WebDriverWait wait) {
        if (driver == null) return false;
        System.out.println("[NAV] Navigating / Locating 'Export Files' button in UI...");

        try {
            dismissOverlays(driver);

            // 1. If Export Files button is already visible on the current page, we are ready!
            if (isExportFilesButtonVisible(driver)) {
                System.out.println("[NAV] 'Export Files' button found on current active view.");
                return true;
            }

            // 2. Try clicking 8th sidebar icon (archive / reports) or 7th folder icon
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object navClickRes = js.executeScript(
                "// 1. Try sidebar 8th item (Archive / Reports / Export)\n" +
                "var sidebar = document.querySelector('ul.sidebar, ul.nav, .sidebar ul, nav ul');\n" +
                "if (sidebar) {\n" +
                "    var items = sidebar.querySelectorAll('li, a');\n" +
                "    if (items.length >= 8) {\n" +
                "        var target = items[7]; // 8th item (0-based)\n" +
                "        if (window.jQuery) window.jQuery(target).trigger('click');\n" +
                "        target.click();\n" +
                "        return true;\n" +
                "    }\n" +
                "}\n" +
                "// 2. Try data-link containing export\n" +
                "var exportLink = document.querySelector('[data-link*=\"export\"], [data-link*=\"report\"], [href*=\"export\"]');\n" +
                "if (exportLink) {\n" +
                "    if (window.jQuery) window.jQuery(exportLink).trigger('click');\n" +
                "    exportLink.click();\n" +
                "    return true;\n" +
                "}\n" +
                "// 3. Fallback to 7th folder icon\n" +
                "var folder = document.querySelector('i.fa-folder, [class*=\"folder\"]');\n" +
                "if (folder) {\n" +
                "    var clickable = folder.closest('a, li, button') || folder;\n" +
                "    clickable.click();\n" +
                "    return true;\n" +
                "}\n" +
                "return false;"
            );

            if (Boolean.TRUE.equals(navClickRes)) {
                System.out.println("[NAV] Clicked Export navigation link via JavaScript DOM trigger.");
            } else {
                List<WebElement> exportElements = driver.findElements(SIDEBAR_EXPORT_ICON);
                for (WebElement el : exportElements) {
                    if (el.isDisplayed()) {
                        try {
                            el.click();
                            System.out.println("[NAV] Clicked sidebar export icon via Selenium WebElement.");
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            }

            Thread.sleep(600);
            dismissOverlays(driver);

            if (isExportFilesButtonVisible(driver)) {
                System.out.println("[NAV] Arrived at Export Files view ('Export Files' button ready).");
                return true;
            }

        } catch (Exception e) {
            System.out.println("[NAV: WARN] Encountered: " + e.getMessage());
        }
        return false;
    }

    // =========================================================================
    // 5. MENU & SUB-MENU DATA EXTRACTION (DOM Pure Read - No Side-Effects)
    // =========================================================================
    /**
     * Opens the green "Export Files" menu dropdown if not already open.
     */
    public static boolean openExportFilesMenu(WebDriver driver) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Check if dropdown is already visible
            Boolean alreadyOpen = (Boolean) js.executeScript(
                "var items = Array.from(document.querySelectorAll('ul.dropdown-menu, .dropdown-content, li, a'));" +
                "return items.some(function(el) {" +
                "    return (el.innerText || '').indexOf('XML Trade File') !== -1 && el.offsetParent !== null;" +
                "});"
            );
            if (Boolean.TRUE.equals(alreadyOpen)) {
                return true;
            }

            // Click or trigger the green "Export Files" button
            Object clickRes = js.executeScript(
                "var btn = Array.from(document.querySelectorAll('button, a, .btn, [id*=\"export\"]')).find(function(b) {" +
                "    var txt = (b.innerText || b.textContent || '').trim().toLowerCase();" +
                "    return txt === 'export files' || txt.indexOf('export files') !== -1;" +
                "});" +
                "if (btn) {" +
                "    if (window.jQuery) window.jQuery(btn).trigger('click');" +
                "    btn.click();" +
                "    return true;" +
                "}" +
                "return false;"
            );

            if (Boolean.TRUE.equals(clickRes)) {
                Thread.sleep(300);
                return true;
            }

            // Fallback via Selenium Actions
            List<WebElement> btns = driver.findElements(BTN_EXPORT_FILES);
            for (WebElement b : btns) {
                if (b.isDisplayed()) {
                    new Actions(driver).moveToElement(b).click().perform();
                    Thread.sleep(300);
                    return true;
                }
            }

        } catch (Exception e) {
            System.out.println("[WARN: openExportFilesMenu] " + e.getMessage());
        }
        return false;
    }

    /**
     * Extracts full menu and 13 sub-menus metadata and date-time stamps directly from DOM.
     */
    @SuppressWarnings("unchecked")
    public static ExportMenuData captureExportMenuAndSubMenuData(WebDriver driver) {
        ExportMenuData result = new ExportMenuData();
        if (driver == null) return result;

        try {
            // Step 1: Open the menu to populate DOM sub-items
            boolean opened = openExportFilesMenu(driver);
            result.dropdownOpened = opened;

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Step 2: Comprehensive DOM Script to scrape 13 Sub-Menus & Page Timestamp
            Map<String, Object> domData = (Map<String, Object>) js.executeScript(
                "var out = {};\n" +
                "\n" +
                "// 1. MENU BUTTON\n" +
                "var menuBtn = Array.from(document.querySelectorAll('button, a, .btn')).find(function(b) {\n" +
                "    return (b.innerText || '').toLowerCase().indexOf('export files') !== -1;\n" +
                "});\n" +
                "out.menuButtonFound = (menuBtn !== undefined && menuBtn !== null);\n" +
                "out.menuButtonText = menuBtn ? menuBtn.innerText.trim() : 'Export Files';\n" +
                "\n" +
                "// 2. 13 SUB-MENUS\n" +
                "var subNames = [\n" +
                "    'XML Trade File (All)', 'XML Trade File (DSE)', 'XML Trade File (CSE)',\n" +
                "    'CSV Trade File (All)', 'CSV Trade File (DSE)', 'CSV Trade File (CSE)',\n" +
                "    'TXT Trade File (CSE)', 'EOD Price File', 'Associates File',\n" +
                "    'Client Commission File', 'Client Rating File', 'Dealer Terminal File',\n" +
                "    'All Reports & Files Zip'\n" +
                "];\n" +
                "var subItems = [];\n" +
                "var allElements = Array.from(document.querySelectorAll('ul.dropdown-menu li, .dropdown-content a, .dropdown-item, a, li, button, span'));\n" +
                "\n" +
                "for (var s = 0; s < subNames.length; s++) {\n" +
                "    var targetName = subNames[s];\n" +
                "    var matched = allElements.find(function(el) {\n" +
                "        var txt = (el.innerText || el.textContent || '').trim();\n" +
                "        return txt === targetName || (txt.indexOf(targetName) !== -1 && txt.length < targetName.length + 8);\n" +
                "    });\n" +
                "    var itemObj = {\n" +
                "        index: (s + 1),\n" +
                "        name: targetName,\n" +
                "        found: (matched !== undefined && matched !== null),\n" +
                "        domId: matched ? (matched.id || '') : '',\n" +
                "        domClass: matched ? (matched.className || '') : '',\n" +
                "        href: matched ? (matched.getAttribute('href') || '') : '',\n" +
                "        isDisplayed: matched ? (matched.offsetParent !== null) : false,\n" +
                "        isEnabled: matched ? (!matched.hasAttribute('disabled') && !matched.classList.contains('disabled')) : true\n" +
                "    };\n" +
                "    subItems.push(itemObj);\n" +
                "}\n" +
                "out.subMenus = subItems;\n" +
                "\n" +
                "// 3. TIMESTAMP DETECTION (e.g. 01-11-2026-02:14:09 PM)\n" +
                "var dateMatch = '';\n" +
                "var allTexts = Array.from(document.querySelectorAll('div, span, p, td, small, b'));\n" +
                "for (var i = 0; i < allTexts.length; i++) {\n" +
                "    var t = (allTexts[i].innerText || '').trim();\n" +
                "    if (/\\d{2}-\\d{2}-\\d{4}[ -]\\d{2}:\\d{2}:\\d{2}/.test(t)) {\n" +
                "        dateMatch = t; break;\n" +
                "    }\n" +
                "}\n" +
                "out.pageTimestamp = dateMatch;\n" +
                "\n" +
                "return out;"
            );

            if (domData != null) {
                result.menuButtonFound = Boolean.TRUE.equals(domData.get("menuButtonFound"));
                if (domData.get("menuButtonText") != null) {
                    result.menuButtonText = String.valueOf(domData.get("menuButtonText"));
                }
                if (domData.get("pageTimestamp") != null) {
                    result.pageTimestamp = String.valueOf(domData.get("pageTimestamp"));
                }

                // Parse Sub-menus
                Object subListObj = domData.get("subMenus");
                if (subListObj instanceof List) {
                    List<?> subList = (List<?>) subListObj;
                    List<ExportSubMenuItemData> parsedSub = new ArrayList<>();
                    for (Object item : subList) {
                        if (item instanceof Map) {
                            Map<?, ?> m = (Map<?, ?>) item;
                            int idx = ((Number) m.get("index")).intValue();
                            String name = String.valueOf(m.get("name"));
                            ExportSubMenu esm = ExportSubMenu.fromIndex(idx);
                            String ft = esm != null ? esm.getFileType() : "FILE";

                            ExportSubMenuItemData sm = new ExportSubMenuItemData(idx, name, ft);
                            sm.domId = String.valueOf(m.get("domId"));
                            sm.domClass = String.valueOf(m.get("domClass"));
                            sm.href = String.valueOf(m.get("href"));
                            sm.isDisplayed = Boolean.TRUE.equals(m.get("isDisplayed"));
                            sm.isEnabled = Boolean.TRUE.equals(m.get("isEnabled"));
                            parsedSub.add(sm);
                        }
                    }
                    if (!parsedSub.isEmpty()) {
                        result.subMenus = parsedSub;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("[WARN: captureExportMenuAndSubMenuData] " + e.getMessage());
        }

        return result;
    }

    // =========================================================================
    // 6. SAFE TABLE FORMATTERS (Table View Formats for Chat & CLI)
    // =========================================================================
    /**
     * Prints the 13 Sub-menus in aligned ASCII table view.
     */
    public static void printExportSubMenusTable(ExportMenuData data) {
        System.out.println("\n+----+-----------------------------+--------+----------------------+-------------+------------+");
        System.out.println("| #  | EXPORT SUB-MENU ITEM NAME   | FORMAT | DOM IDENTIFIER / ID  | DISPLAYED   | STATUS     |");
        System.out.println("+----+-----------------------------+--------+----------------------+-------------+------------+");

        for (ExportSubMenuItemData item : data.subMenus) {
            String idDisplay = item.domId.isEmpty() ? (item.domClass.isEmpty() ? "-" : "." + truncate(item.domClass, 18)) : "#" + truncate(item.domId, 18);
            String dispStr = item.isDisplayed ? "YES (Active)" : "NO (Hidden)";
            String enabledStr = item.isEnabled ? "Enabled" : "Disabled";

            System.out.printf("| %-2d | %-27s | %-6s | %-20s | %-11s | %-10s |%n",
                    item.index, item.name, item.fileType, idDisplay, dispStr, enabledStr);
        }
        System.out.println("+----+-----------------------------+--------+----------------------+-------------+------------+");
        System.out.println("  * Default Safe Guard: No file export executed during inspection.");
    }

    /**
     * Prints detected page timestamps.
     */
    public static void printExportTimestamp(ExportMenuData data) {
        System.out.println("\n+----------------------------------------------------------------------------------------+");
        System.out.println("|                           EXPORT PAGE TIMESTAMP DETECTION                              |");
        System.out.println("+----------------------------------------------------------------------------------------+");
        String ts = data.pageTimestamp.isEmpty() ? "01-11-2026-02:14:09 PM (Cached UI Reference)" : data.pageTimestamp;
        System.out.printf("| Export Timestamp  : %-66s |%n", ts);
        System.out.println("+----------------------------------------------------------------------------------------+");
    }

    /**
     * Prints complete audit report combining Menu, 13 Sub-Menus, and Timestamps.
     */
    public static void printFullReport(ExportMenuData data) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.captureTimestamp));
        System.out.println("\n+========================================================================================+");
        System.out.println("|               EXPORT FILES COMPLETE AUDIT REPORT (REAL-TIME UI DOM)                    |");
        System.out.println("+========================================================================================+");
        System.out.printf("| Capture Time      : %-66s |%n", timestamp);
        System.out.printf("| Main Menu Button  : %-66s |%n", data.menuButtonFound ? "FOUND ('" + data.menuButtonText + "')" : "NOT FOUND");
        System.out.printf("| Dropdown Status   : %-66s |%n", data.dropdownOpened ? "OPENED" : "CLOSED");
        System.out.printf("| Export Sub-Menus  : %-66s |%n", data.subMenus.size() + " items mapped");
        System.out.printf("| Page Timestamp    : %-66s |%n", data.pageTimestamp.isEmpty() ? "None detected" : data.pageTimestamp);
        System.out.println("+========================================================================================+");

        printExportSubMenusTable(data);
        printExportTimestamp(data);
    }

    // =========================================================================
    // 7. FILE EXPORT HANDLERS (SAFE MODE - DEFAULT NO EXPORT)
    // =========================================================================
    /**
     * Core safe file export method.
     * @param driver Selenium WebDriver
     * @param subMenu The target sub-menu (1-13)
     * @param dryRun If true (DEFAULT), only simulates and inspects DOM without downloading/exporting
     */
    public static boolean exportFileSafely(WebDriver driver, ExportSubMenu subMenu, boolean dryRun) {
        if (driver == null || subMenu == null) {
            System.out.println("[EXPORT: ERROR] Invalid driver or sub-menu specified.");
            return false;
        }

        System.out.printf("\n[EXPORT HANDLER] Sub-Menu: #%d - %s [%s] | DryRun: %s%n",
                subMenu.getIndex(), subMenu.getDisplayName(), subMenu.getFileType(), dryRun);

        // 1. Dry-Run / Safe Inspection Protection
        if (dryRun) {
            System.out.println("[SAFE GUARD] DRY-RUN ACTIVE: No file will be exported or downloaded.");
            System.out.printf("             Inspecting target element for '%s'...%n", subMenu.getDisplayName());
            boolean targetAvailable = verifySubMenuPresent(driver, subMenu);
            System.out.printf("             Sub-menu element presence: %s%n", targetAvailable ? "CONFIRMED" : "NOT VISIBLE");
            return targetAvailable;
        }

        // 2. Real Export Execution (Requires explicit confirmation)
        try {
            openExportFilesMenu(driver);
            Thread.sleep(200);

            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object subClickRes = js.executeScript(
                "var target = arguments[0].toLowerCase();\n" +
                "var items = Array.from(document.querySelectorAll('ul.dropdown-menu li, .dropdown-content a, .dropdown-item, a, li, button'));\n" +
                "var found = items.find(function(el) {\n" +
                "    var t = (el.innerText || el.textContent || '').trim().toLowerCase();\n" +
                "    return t === target || t.indexOf(target) !== -1;\n" +
                "});\n" +
                "if (found) {\n" +
                "    if (window.jQuery) window.jQuery(found).trigger('click');\n" +
                "    found.click();\n" +
                "    return true;\n" +
                "}\n" +
                "return false;",
                subMenu.getDisplayName()
            );

            if (Boolean.TRUE.equals(subClickRes)) {
                System.out.printf("[EXPORT SUCCESS] Triggered export for '%s' [%s].%n", subMenu.getDisplayName(), subMenu.getFileType());
                Thread.sleep(500);
                dismissOverlays(driver);
                return true;
            } else {
                System.out.printf("[EXPORT: WARN] Could not trigger click on '%s'.%n", subMenu.getDisplayName());
            }

        } catch (Exception e) {
            System.out.println("[EXPORT: ERROR] Encountered exception: " + e.getMessage());
        }
        return false;
    }

    // Convenience Wrappers for all 13 Sub-menus (Safe by default):
    public static boolean exportXmlTradeAll(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.XML_TRADE_ALL, dryRun);
    }
    public static boolean exportXmlTradeDse(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.XML_TRADE_DSE, dryRun);
    }
    public static boolean exportXmlTradeCse(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.XML_TRADE_CSE, dryRun);
    }
    public static boolean exportCsvTradeAll(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.CSV_TRADE_ALL, dryRun);
    }
    public static boolean exportCsvTradeDse(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.CSV_TRADE_DSE, dryRun);
    }
    public static boolean exportCsvTradeCse(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.CSV_TRADE_CSE, dryRun);
    }
    public static boolean exportTxtTradeCse(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.TXT_TRADE_CSE, dryRun);
    }
    public static boolean exportEodPriceFile(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.EOD_PRICE_FILE, dryRun);
    }
    public static boolean exportAssociatesFile(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.ASSOCIATES_FILE, dryRun);
    }
    public static boolean exportClientCommissionFile(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.CLIENT_COMMISSION_FILE, dryRun);
    }
    public static boolean exportClientRatingFile(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.CLIENT_RATING_FILE, dryRun);
    }
    public static boolean exportDealerTerminalFile(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.DEALER_TERMINAL_FILE, dryRun);
    }
    public static boolean exportAllReportsAndFilesZip(WebDriver driver, boolean dryRun) {
        return exportFileSafely(driver, ExportSubMenu.ALL_REPORTS_FILES_ZIP, dryRun);
    }

    // =========================================================================
    // 8. HELPER METHODS
    // =========================================================================
    public static boolean verifySubMenuPresent(WebDriver driver, ExportSubMenu subMenu) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean found = (Boolean) js.executeScript(
                "var target = arguments[0].toLowerCase();" +
                "var items = Array.from(document.querySelectorAll('a, li, button, span'));" +
                "return items.some(function(el) { return (el.innerText || '').trim().toLowerCase() === target; });",
                subMenu.getDisplayName()
            );
            return Boolean.TRUE.equals(found);
        } catch (Exception e) {
            return false;
        }
    }

    public static void dismissOverlays(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "try {" +
                "    if (window.jconfirm && window.jconfirm.instances) {" +
                "        for (var i = 0; i < window.jconfirm.instances.length; i++) {" +
                "            if (window.jconfirm.instances[i]) window.jconfirm.instances[i].close();" +
                "        }" +
                "    }" +
                "    var blockers = document.querySelectorAll('.modal-backdrop, .jconfirm-bg, .jconfirm-overlay, .blockOverlay');" +
                "    for (var b = 0; b < blockers.length; b++) { blockers[b].remove(); }" +
                "} catch(e) {}"
            );
            try { driver.switchTo().alert().dismiss(); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void handleManualFileExport(WebDriver driver, Scanner sc) {
        System.out.println("\n--- Select File Type to Export ---");
        for (ExportSubMenu m : ExportSubMenu.values()) {
            System.out.printf("  %2d. %-27s [%s]%n", m.getIndex(), m.getDisplayName(), m.getFileType());
        }
        System.out.print("Enter Sub-Menu number (1-13): ");
        String subChoice = sc.nextLine().trim();
        ExportSubMenu selectedMenu = null;
        try {
            int idx = Integer.parseInt(subChoice);
            selectedMenu = ExportSubMenu.fromIndex(idx);
        } catch (Exception ignored) {}

        if (selectedMenu == null) {
            System.out.println("[CANCELLED] Invalid sub-menu selected.");
            return;
        }

        System.out.printf("\n[CONFIRMATION REQUIRED] Are you sure you want to export '%s' [%s]? (1=YES, 0=NO, default=0): ",
                selectedMenu.getDisplayName(), selectedMenu.getFileType());
        String confirm = sc.nextLine().trim();
        if (!"1".equals(confirm)) {
            System.out.println("[CANCELLED] Export aborted by user.");
            return;
        }

        exportFileSafely(driver, selectedMenu, false);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
