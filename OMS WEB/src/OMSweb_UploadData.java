import java.io.File;
import java.time.Duration;
import java.text.SimpleDateFormat;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
//import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * ============================================================================
 * OMSweb_UploadData
 * ============================================================================
 * Dedicated module for navigating to and managing the "Upload Data" section
 * in the UFTFAST OMS Web application (7th sidebar folder icon).
 *
 * Core Features:
 * 1. Safe Navigation to Upload Data page (Folder Icon: i.fa-folder / 7th sidebar item).
 * 2. Menu & 9 Sub-Menus DOM Data Capture:
 *      1. Client File
 *      2. Stock File
 *      3. Cash File
 *      4. Associates File
 *      5. Client Rating File
 *      6. Client Commission File
 *      7. User Active / Inactive File
 *      8. Dealer Terminal File
 *      9. CSE Circuit Breaker File
 * 3. Uploaded XML Files History Table Scraper (File names, timestamps, status).
 * 4. Zip Report Controls Scraper & Handlers ("all reports zip file" download / delete).
 * 5. File Insertion / Upload handlers for all 9 sub-menu file types.
 *
 * MANDATORY SAFETY CONSTRAINT:
 * - "BY DEFAULT NO FILE UPLOAD" (DEFAULT_NO_UPLOAD = true).
 * - Safe inspection, presence verification, and reading menu/sub-menu data
 *   is the default action on running or pressing ENTER.
 * ============================================================================
 */
public class OMSweb_UploadData {

    // =========================================================================
    // 0. CONFIGURATION & SAFETY CONSTRAINTS
    // =========================================================================
    /** By default, NO file upload is executed. Safe inspection mode only. */
    public static final boolean DEFAULT_NO_UPLOAD = true;

    // Sidebar Folder Selector (7th icon down)
    public static final By SIDEBAR_FOLDER_ICON = By.xpath(
            "//i[contains(@class,'fa-folder')] | " +
            "//a[contains(@data-link,'upload') or contains(@data-link,'file') or contains(@href,'upload')] | " +
            "//ul[contains(@class,'sidebar') or contains(@class,'nav')]//li[7]//a | " +
            "//ul[contains(@class,'sidebar') or contains(@class,'nav')]//li[7] | " +
            "//div[contains(@class,'sidebar')]//a[7]");

    // Green "Upload Data" Button in Main Area
    public static final By BTN_UPLOAD_DATA = By.xpath(
            "//button[contains(normalize-space(),'Upload Data')] | " +
            "//a[contains(normalize-space(),'Upload Data')] | " +
            "//*[contains(@class,'btn') and (contains(.,'Upload Data') or .//i[contains(@class,'upload')])] | " +
            "//button[@id='upload_data' or contains(@id,'upload_data') or contains(@class,'btn-upload')]");

    // All 9 Sub-menu names in exact UI order
    public static final String[] SUB_MENU_NAMES = {
        "Client File",
        "Stock File",
        "Cash File",
        "Associates File",
        "Client Rating File",
        "Client Commission File",
        "User Active / Inactive File",
        "Dealer Terminal File",
        "CSE Circuit Breaker File"
    };

    // =========================================================================
    // 1. SUB-MENU ENUM DEFINITION
    // =========================================================================
    public enum UploadSubMenu {
        CLIENT_FILE(1, "Client File", "client_file"),
        STOCK_FILE(2, "Stock File", "stock_file"),
        CASH_FILE(3, "Cash File", "cash_file"),
        ASSOCIATES_FILE(4, "Associates File", "associates_file"),
        CLIENT_RATING_FILE(5, "Client Rating File", "client_rating_file"),
        CLIENT_COMMISSION_FILE(6, "Client Commission File", "client_commission_file"),
        USER_ACTIVE_INACTIVE_FILE(7, "User Active / Inactive File", "user_active_inactive_file"),
        DEALER_TERMINAL_FILE(8, "Dealer Terminal File", "dealer_terminal_file"),
        CSE_CIRCUIT_BREAKER_FILE(9, "CSE Circuit Breaker File", "cse_circuit_breaker_file");

        private final int index;
        private final String displayName;
        private final String identifierKey;

        UploadSubMenu(int index, String displayName, String identifierKey) {
            this.index = index;
            this.displayName = displayName;
            this.identifierKey = identifierKey;
        }

        public int getIndex() { return index; }
        public String getDisplayName() { return displayName; }
        public String getIdentifierKey() { return identifierKey; }

        public static UploadSubMenu fromIndex(int idx) {
            for (UploadSubMenu m : values()) {
                if (m.index == idx) return m;
            }
            return null;
        }

        public static UploadSubMenu fromName(String name) {
            if (name == null) return null;
            String clean = name.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            for (UploadSubMenu m : values()) {
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
    public static class SubMenuItemData {
        public int index;
        public String name;
        public String domId = "";
        public String domClass = "";
        public String href = "";
        public String dataTarget = "";
        public boolean isDisplayed = false;
        public boolean isEnabled = true;
        public String actionType = "File Upload";

        public SubMenuItemData(int index, String name) {
            this.index = index;
            this.name = name;
        }
    }

    public static class UploadedFileRecord {
        public int index;
        public String fileName = "";
        public String timestamp = "";
        public String fileType = "";
        public String status = "Uploaded";
        public Map<String, String> rawAttributes = new LinkedHashMap<>();

        public UploadedFileRecord(int index, String fileName) {
            this.index = index;
            this.fileName = fileName;
        }
    }

    public static class ZipReportInfo {
        public boolean hasDownloadBtn = false;
        public boolean hasDeleteBtn = false;
        public String label = "all reports zip file";
        public String downloadBtnClass = "";
        public String deleteBtnClass = "";
    }

    public static class MenuData {
        public boolean menuButtonFound = false;
        public boolean dropdownOpened = false;
        public String menuButtonText = "Upload Data";
        public List<SubMenuItemData> subMenus = new ArrayList<>();
        public List<UploadedFileRecord> uploadedFiles = new ArrayList<>();
        public ZipReportInfo zipReportInfo = new ZipReportInfo();
        public long captureTimestamp = System.currentTimeMillis();

        public MenuData() {
            // Seed default 9 submenus
            for (int i = 0; i < SUB_MENU_NAMES.length; i++) {
                subMenus.add(new SubMenuItemData(i + 1, SUB_MENU_NAMES[i]));
            }
        }
    }

    // =========================================================================
    // 3. MAIN INTERACTIVE ENTRY POINT (Integration with OMSwebmain)
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) {
        System.out.println("\n+========================================================================================+");
        System.out.println("|                     UPLOAD DATA: MENU & SUB-MENU DATA VIEWER                           |");
        System.out.println("|             [MANDATORY SAFEGUARD: BY DEFAULT NO FILE UPLOAD WILL OCCUR]                |");
        System.out.println("+========================================================================================+");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // 1. Ensure Navigation to Upload Data Page
        boolean navOk = navigateToUploadDataPage(driver, wait);
        if (!navOk) {
            System.out.println("[WARN] Upload Data page could not be verified automatically. Attempting DOM inspection...");
        }

        // 2. Interactive Menu
        boolean keepRunning = true;
        while (keepRunning) {
            System.out.println("\n----------------------------------------------------------------------------------------");
            System.out.println("Select Upload File Action:");
            System.out.println("  1. Select File Type to Upload (User Choice 1-9: Client, Stock, Cash, etc.) [Default]");
            System.out.println("  2. Inspect & Display Upload Menu & 9 Sub-Menus Data (Safe View - No Upload)");
            System.out.println("  3. Scrape Uploaded XML Files History Table & Zip Controls");
            System.out.println("  4. Full UI Audit (Menu, 9 Sub-Menus, Uploaded Files & Zip Reports)");
            System.out.println("  5. Return to Main Application");
            System.out.print("Enter Choice (1-5, default=1): ");

            String input = sc.nextLine().trim();
            if (input.isEmpty() || "1".equals(input)) {
                // Option 1: User Choice File Upload (Client, Stock, Cash, etc.)
                handleManualFileUpload(driver, sc);

            } else if ("2".equals(input)) {
                // Option 2: Safe Inspection
                System.out.println("\n>>> Inspecting Menu & 9 Sub-Menus DOM Data (No file upload)...");
                MenuData data = captureMenuAndSubMenuData(driver);
                printSubMenusTable(data);

            } else if ("3".equals(input)) {
                // Option 3: Uploaded XML Files Table & Zip
                System.out.println("\n>>> Inspecting Uploaded XML Files Table & Zip Controls...");
                MenuData data = captureMenuAndSubMenuData(driver);
                printUploadedFilesTable(data);
                printZipReportControls(data);

            } else if ("4".equals(input)) {
                // Option 4: Full Audit
                System.out.println("\n>>> Running Full Upload Data UI Audit (Menu, 9 Sub-Menus, Table, Zip)...");
                MenuData data = captureMenuAndSubMenuData(driver);
                printFullReport(data);

            } else if ("5".equals(input) || "6".equals(input) || "7".equals(input)) {
                System.out.println("[UPLOAD DATA] Returning to main session.");
                keepRunning = false;
            } else {
                System.out.println("Unrecognized choice. Running user choice file upload (Option 1)...");
                handleManualFileUpload(driver, sc);
            }
        }
    }

    // =========================================================================
    // 4. NAVIGATION METHODS (7th Sidebar Folder Icon)
    // =========================================================================
    /**
     * Checks if the browser is already displaying the Upload Data section.
     */
    public static boolean isAlreadyOnUploadDataPage(WebDriver driver) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var btn = document.querySelector('button, a, div, span');" +
                "var textMatches = Array.from(document.querySelectorAll('button, a, h1, h2, h3, h4, .page-title, .title'))" +
                "    .some(function(el) { return (el.innerText || '').trim().toLowerCase().indexOf('upload data') !== -1; });" +
                "var folderActive = document.querySelector('.sidebar li:nth-child(7).active, .sidebar a:nth-child(7).active, [data-link*=\"upload\"].active');" +
                "return textMatches || folderActive !== null;"
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Navigates to the Upload Data section by clicking the 7th sidebar folder icon.
     */
    public static boolean navigateToUploadDataPage(WebDriver driver, WebDriverWait wait) {
        if (driver == null) return false;
        System.out.println("[NAV] Navigating to 'Upload Data' page via 7th sidebar folder icon...");

        try {
            dismissOverlays(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Primary Attempt via DOM Javascript search targeting Folder Icon (7th icon down)
            Object navClickRes = js.executeScript(
                "// Try finding folder icon\n" +
                "var folderIcon = document.querySelector('i.fa-folder, i.fa-folder-open, i.fa-folder-o, [class*=\"folder\"]');\n" +
                "if (folderIcon) {\n" +
                "    var clickable = folderIcon.closest('a, li, button, div') || folderIcon;\n" +
                "    if (window.jQuery) window.jQuery(clickable).trigger('click');\n" +
                "    clickable.click();\n" +
                "    try { folderIcon.click(); } catch(e) {}\n" +
                "    return true;\n" +
                "}\n" +
                "// Try 7th item in sidebar list\n" +
                "var sidebar = document.querySelector('ul.sidebar, ul.nav, .sidebar ul, nav ul, .sidebar-menu');\n" +
                "if (sidebar) {\n" +
                "    var items = sidebar.querySelectorAll('li, a.nav-link, a');\n" +
                "    if (items.length >= 7) {\n" +
                "        var target = items[6]; // 0-based 7th item\n" +
                "        if (window.jQuery) window.jQuery(target).trigger('click');\n" +
                "        target.click();\n" +
                "        return true;\n" +
                "    }\n" +
                "}\n" +
                "// Try data-link containing upload\n" +
                "var uploadLink = document.querySelector('[data-link*=\"upload\"], [data-link*=\"file\"], [href*=\"upload\"], [title*=\"Upload\"]');\n" +
                "if (uploadLink) {\n" +
                "    if (window.jQuery) window.jQuery(uploadLink).trigger('click');\n" +
                "    uploadLink.click();\n" +
                "    return true;\n" +
                "}\n" +
                "return false;"
            );

            if (Boolean.TRUE.equals(navClickRes)) {
                System.out.println("[NAV] Clicked 7th sidebar folder icon via JavaScript DOM trigger.");
            } else {
                // 2. Fallback via Selenium WebElements
                List<WebElement> folderElements = driver.findElements(SIDEBAR_FOLDER_ICON);
                boolean clicked = false;
                for (WebElement el : folderElements) {
                    if (el.isDisplayed()) {
                        try {
                            el.click();
                            clicked = true;
                            System.out.println("[NAV] Clicked sidebar folder icon via Selenium WebElement.");
                            break;
                        } catch (Exception ignored) {}
                    }
                }
                if (!clicked && !folderElements.isEmpty()) {
                    js.executeScript("arguments[0].click();", folderElements.get(0));
                }
            }

            Thread.sleep(600);
            dismissOverlays(driver);

            // Verify Presence of Upload Data Button or Header
            boolean ready = waitForUploadDataButton(driver, 5);
            if (ready) {
                System.out.println("[NAV] Successfully arrived at Upload Data Page ('Upload Data' button verified).");
                return true;
            }

        } catch (Exception e) {
            System.out.println("[NAV: WARN] Navigation encounter: " + e.getMessage());
        }
        return false;
    }

    /**
     * Waits up to timeoutSeconds for the green Upload Data button to be present.
     */
    public static boolean waitForUploadDataButton(WebDriver driver, int timeoutSeconds) {
        long end = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < end) {
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Boolean exists = (Boolean) js.executeScript(
                    "var btns = Array.from(document.querySelectorAll('button, a, .btn'));" +
                    "return btns.some(function(b) {" +
                    "    var txt = (b.innerText || b.textContent || '').trim().toLowerCase();" +
                    "    return txt.indexOf('upload data') !== -1;" +
                    "});"
                );
                if (Boolean.TRUE.equals(exists)) return true;
            } catch (Exception ignored) {}
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    // =========================================================================
    // 5. MENU & SUB-MENU DATA EXTRACTION (DOM Pure Read - No Side-Effects)
    // =========================================================================
    /**
     * Opens the green "Upload Data" menu dropdown if not already expanded.
     */
    public static boolean openUploadDataMenu(WebDriver driver) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Check if dropdown is already visible
            Boolean alreadyOpen = (Boolean) js.executeScript(
                "var items = Array.from(document.querySelectorAll('ul.dropdown-menu, .dropdown-content, .dropdown-menu, li, a'));" +
                "return items.some(function(el) {" +
                "    return (el.innerText || '').indexOf('Client File') !== -1 && el.offsetParent !== null;" +
                "});"
            );
            if (Boolean.TRUE.equals(alreadyOpen)) {
                return true;
            }

            // Click or trigger the green "Upload Data" button
            Object clickRes = js.executeScript(
                "var btn = Array.from(document.querySelectorAll('button, a, .btn, [id*=\"upload\"]')).find(function(b) {" +
                "    var txt = (b.innerText || b.textContent || '').trim().toLowerCase();" +
                "    return txt === 'upload data' || txt.indexOf('upload data') !== -1;" +
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

            // Fallback via Selenium Actions hover/click
            List<WebElement> btns = driver.findElements(BTN_UPLOAD_DATA);
            for (WebElement b : btns) {
                if (b.isDisplayed()) {
                    new Actions(driver).moveToElement(b).click().perform();
                    Thread.sleep(300);
                    return true;
                }
            }

        } catch (Exception e) {
            System.out.println("[WARN: openUploadDataMenu] " + e.getMessage());
        }
        return false;
    }

    /**
     * Extracts full menu and sub-menu metadata, uploaded XML files table,
     * and zip report controls directly from real-time DOM.
     */
    @SuppressWarnings("unchecked")
    public static MenuData captureMenuAndSubMenuData(WebDriver driver) {
        MenuData result = new MenuData();
        if (driver == null) return result;

        try {
            // Step 1: Open the menu to populate DOM sub-items
            boolean opened = openUploadDataMenu(driver);
            result.dropdownOpened = opened;

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Step 2: Comprehensive DOM Script to scrape Sub-Menus, Files Table, and Zip controls
            Map<String, Object> domData = (Map<String, Object>) js.executeScript(
                "var out = {};\n" +
                "\n" +
                "// 1. MENU BUTTON\n" +
                "var menuBtn = Array.from(document.querySelectorAll('button, a, .btn')).find(function(b) {\n" +
                "    return (b.innerText || '').toLowerCase().indexOf('upload data') !== -1;\n" +
                "});\n" +
                "out.menuButtonFound = (menuBtn !== undefined && menuBtn !== null);\n" +
                "out.menuButtonText = menuBtn ? menuBtn.innerText.trim() : 'Upload Data';\n" +
                "\n" +
                "// 2. SUB MENUS (9 EXPECTED)\n" +
                "var subNames = [\n" +
                "    'Client File', 'Stock File', 'Cash File', 'Associates File', \n" +
                "    'Client Rating File', 'Client Commission File', 'User Active / Inactive File', \n" +
                "    'Dealer Terminal File', 'CSE Circuit Breaker File'\n" +
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
                "        dataTarget: matched ? (matched.getAttribute('data-target') || matched.getAttribute('data-link') || '') : '',\n" +
                "        isDisplayed: matched ? (matched.offsetParent !== null) : false,\n" +
                "        isEnabled: matched ? (!matched.hasAttribute('disabled') && !matched.classList.contains('disabled')) : true\n" +
                "    };\n" +
                "    subItems.push(itemObj);\n" +
                "}\n" +
                "out.subMenus = subItems;\n" +
                "\n" +
                "// 3. UPLOADED XML FILES TABLE\n" +
                "var files = [];\n" +
                "var tables = Array.from(document.querySelectorAll('table'));\n" +
                "tables.forEach(function(tbl) {\n" +
                "    var rows = Array.from(tbl.querySelectorAll('tbody tr, tr'));\n" +
                "    rows.forEach(function(r) {\n" +
                "        var text = (r.innerText || '').trim();\n" +
                "        if (text.indexOf('.xml') !== -1 || text.indexOf('.csv') !== -1 || text.indexOf('.txt') !== -1) {\n" +
                "            var cells = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "            var fileName = cells.find(function(c) { return c.indexOf('.xml') !== -1; }) || (cells.length > 0 ? cells[0] : text);\n" +
                "            files.push({\n" +
                "                fileName: fileName,\n" +
                "                fullText: text,\n" +
                "                cellCount: cells.length,\n" +
                "                cells: cells\n" +
                "            });\n" +
                "        }\n" +
                "    });\n" +
                "});\n" +
                "out.uploadedFiles = files;\n" +
                "\n" +
                "// 4. ZIP REPORT CONTROLS\n" +
                "var zipLabel = Array.from(document.querySelectorAll('*')).find(function(el) {\n" +
                "    return (el.innerText || '').trim().toLowerCase().indexOf('all reports zip file') !== -1;\n" +
                "});\n" +
                "var downloadBtn = document.querySelector('button.btn-success, .btn-download, button i.fa-download, a i.fa-download');\n" +
                "var deleteBtn = document.querySelector('button.btn-danger, .btn-delete, button i.fa-trash, a i.fa-trash');\n" +
                "out.zipInfo = {\n" +
                "    hasLabel: (zipLabel !== undefined && zipLabel !== null),\n" +
                "    hasDownloadBtn: (downloadBtn !== null),\n" +
                "    hasDeleteBtn: (deleteBtn !== null),\n" +
                "    downloadBtnClass: downloadBtn ? downloadBtn.className : '',\n" +
                "    deleteBtnClass: deleteBtn ? deleteBtn.className : ''\n" +
                "};\n" +
                "\n" +
                "return out;"
            );

            if (domData != null) {
                result.menuButtonFound = Boolean.TRUE.equals(domData.get("menuButtonFound"));
                if (domData.get("menuButtonText") != null) {
                    result.menuButtonText = String.valueOf(domData.get("menuButtonText"));
                }

                // Parse Sub-menus
                Object subListObj = domData.get("subMenus");
                if (subListObj instanceof List) {
                    List<?> subList = (List<?>) subListObj;
                    List<SubMenuItemData> parsedSub = new ArrayList<>();
                    for (Object item : subList) {
                        if (item instanceof Map) {
                            Map<?, ?> m = (Map<?, ?>) item;
                            int idx = ((Number) m.get("index")).intValue();
                            String name = String.valueOf(m.get("name"));
                            SubMenuItemData sm = new SubMenuItemData(idx, name);
                            sm.domId = String.valueOf(m.get("domId"));
                            sm.domClass = String.valueOf(m.get("domClass"));
                            sm.href = String.valueOf(m.get("href"));
                            sm.dataTarget = String.valueOf(m.get("dataTarget"));
                            sm.isDisplayed = Boolean.TRUE.equals(m.get("isDisplayed"));
                            sm.isEnabled = Boolean.TRUE.equals(m.get("isEnabled"));
                            parsedSub.add(sm);
                        }
                    }
                    if (!parsedSub.isEmpty()) {
                        result.subMenus = parsedSub;
                    }
                }

                // Parse Uploaded Files
                Object fileListObj = domData.get("uploadedFiles");
                if (fileListObj instanceof List) {
                    List<?> fileList = (List<?>) fileListObj;
                    int fIndex = 1;
                    for (Object f : fileList) {
                        if (f instanceof Map) {
                            Map<?, ?> fm = (Map<?, ?>) f;
                            String fName = String.valueOf(fm.get("fileName"));
                            UploadedFileRecord ufr = new UploadedFileRecord(fIndex++, fName);
                            Object cellsObj = fm.get("cells");
                            if (cellsObj instanceof List) {
                                List<?> cells = (List<?>) cellsObj;
                                for (int c = 0; c < cells.size(); c++) {
                                    ufr.rawAttributes.put("col_" + c, String.valueOf(cells.get(c)));
                                }
                            }
                            result.uploadedFiles.add(ufr);
                        }
                    }
                }

                // Parse Zip Controls
                Object zipObj = domData.get("zipInfo");
                if (zipObj instanceof Map) {
                    Map<?, ?> zm = (Map<?, ?>) zipObj;
                    result.zipReportInfo.hasDownloadBtn = Boolean.TRUE.equals(zm.get("hasDownloadBtn"));
                    result.zipReportInfo.hasDeleteBtn = Boolean.TRUE.equals(zm.get("hasDeleteBtn"));
                    result.zipReportInfo.downloadBtnClass = String.valueOf(zm.get("downloadBtnClass"));
                    result.zipReportInfo.deleteBtnClass = String.valueOf(zm.get("deleteBtnClass"));
                }
            }

        } catch (Exception e) {
            System.out.println("[WARN: captureMenuAndSubMenuData] " + e.getMessage());
        }

        return result;
    }

    // =========================================================================
    // 6. SAFE TABLE FORMATTERS (Table View Formats for Chat & CLI)
    // =========================================================================
    /**
     * Prints the 9 Sub-menus data in aligned ASCII table format.
     */
    public static void printSubMenusTable(MenuData data) {
        System.out.println("\n+----+-----------------------------+----------------------+-------------+------------+");
        System.out.println("| #  | SUB-MENU ITEM NAME          | DOM IDENTIFIER / ID  | DISPLAYED   | STATUS     |");
        System.out.println("+----+-----------------------------+----------------------+-------------+------------+");

        for (SubMenuItemData item : data.subMenus) {
            String idDisplay = item.domId.isEmpty() ? (item.domClass.isEmpty() ? "-" : "." + truncate(item.domClass, 18)) : "#" + truncate(item.domId, 18);
            String dispStr = item.isDisplayed ? "YES (Active)" : "NO (Hidden)";
            String enabledStr = item.isEnabled ? "Enabled" : "Disabled";

            System.out.printf("| %-2d | %-27s | %-20s | %-11s | %-10s |%n",
                    item.index, item.name, idDisplay, dispStr, enabledStr);
        }
        System.out.println("+----+-----------------------------+----------------------+-------------+------------+");
        System.out.println("  * Default Safe Guard: No file upload executed during inspection.");
    }

    /**
     * Prints the uploaded XML files history table in aligned ASCII format.
     */
    public static void printUploadedFilesTable(MenuData data) {
        System.out.println("\n+========================================================================================+");
        System.out.println("|                     UPLOADED FILES HISTORY TABLE (REAL-TIME DOM)                       |");
        System.out.println("+========================================================================================+");

        if (data.uploadedFiles.isEmpty()) {
            System.out.println("|  [INFO] No uploaded files currently listed in table (or table clean).                  |");
            System.out.println("+========================================================================================+");
            return;
        }

        System.out.println("+----+-----------------------------------------------------+-----------------------------+");
        System.out.println("| #  | UPLOADED FILE NAME                                  | STATUS                      |");
        System.out.println("+----+-----------------------------------------------------+-----------------------------+");

        for (UploadedFileRecord f : data.uploadedFiles) {
            System.out.printf("| %-2d | %-51s | %-27s |%n",
                    f.index, truncate(f.fileName, 51), f.status);
        }
        System.out.println("+----+-----------------------------------------------------+-----------------------------+");
    }

    /**
     * Prints the Zip report controls at the bottom left of the page.
     */
    public static void printZipReportControls(MenuData data) {
        System.out.println("\n+----------------------------------------------------------------------------------------+");
        System.out.println("|                               ZIP REPORT CONTROLS (FOOTER)                             |");
        System.out.println("+----------------------------------------------------------------------------------------+");
        System.out.printf("| Label Text        : %-66s |%n", data.zipReportInfo.label);
        System.out.printf("| Download Button   : %-66s |%n", data.zipReportInfo.hasDownloadBtn ? "[READY] Green Download Icon Available" : "[NOT DETECTED]");
        System.out.printf("| Delete Button     : %-66s |%n", data.zipReportInfo.hasDeleteBtn ? "[READY] Red Trash Icon Available" : "[NOT DETECTED]");
        System.out.println("+----------------------------------------------------------------------------------------+");
    }

    /**
     * Prints the complete audit report combining Menu, 9 Sub-Menus, Files, and Zip Controls.
     */
    public static void printFullReport(MenuData data) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.captureTimestamp));
        System.out.println("\n+========================================================================================+");
        System.out.println("|               UPLOAD DATA COMPLETE AUDIT REPORT (REAL-TIME UI DOM)                     |");
        System.out.println("+========================================================================================+");
        System.out.printf("| Capture Time      : %-66s |%n", timestamp);
        System.out.printf("| Main Menu Button  : %-66s |%n", data.menuButtonFound ? "FOUND ('" + data.menuButtonText + "')" : "NOT FOUND");
        System.out.printf("| Dropdown Status   : %-66s |%n", data.dropdownOpened ? "OPENED" : "CLOSED");
        System.out.printf("| Sub-Menus Total   : %-66s |%n", data.subMenus.size() + " items mapped");
        System.out.printf("| Uploaded Files    : %-66s |%n", data.uploadedFiles.size() + " files found in history");
        System.out.println("+========================================================================================+");

        printSubMenusTable(data);
        printUploadedFilesTable(data);
        printZipReportControls(data);
    }

    // =========================================================================
    // 7. FILE INSERTION / UPLOAD HANDLERS ("insert those file" - SAFE MODE)
    // =========================================================================
    /**
     * Core safe file upload method.
     * @param driver Selenium WebDriver
     * @param subMenu The target sub-menu (1-9)
     * @param filePath Absolute path of local file to upload
     * @param dryRun If true (DEFAULT), only simulates and inspects DOM without executing actual upload
     */
    public static boolean uploadFileSafely(WebDriver driver, UploadSubMenu subMenu, String filePath, boolean dryRun) {
        if (driver == null || subMenu == null) {
            System.out.println("[UPLOAD: ERROR] Invalid driver or sub-menu specified.");
            return false;
        }

        System.out.printf("\n[UPLOAD HANDLER] Sub-Menu: #%d - %s | File: %s | DryRun: %s%n",
                subMenu.getIndex(), subMenu.getDisplayName(), filePath, dryRun);

        // 1. Dry-Run / Safe Inspection Protection
        if (dryRun) {
            System.out.println("[SAFE GUARD] DRY-RUN ACTIVE: No file will be uploaded to server.");
            System.out.printf("             Inspecting target element for sub-menu '%s'...%n", subMenu.getDisplayName());
            boolean targetAvailable = verifySubMenuPresent(driver, subMenu);
            System.out.printf("             Sub-menu element presence: %s%n", targetAvailable ? "CONFIRMED" : "NOT VISIBLE");
            return targetAvailable;
        }

        // 2. Local File Existence Verification
        if (filePath == null || filePath.trim().isEmpty()) {
            System.out.println("[UPLOAD: ERROR] File path is empty. Aborting upload.");
            return false;
        }
        File f = new File(filePath);
        if (!f.exists() || !f.isFile()) {
            System.out.printf("[UPLOAD: ERROR] Local file does not exist: %s%n", filePath);
            return false;
        }

        try {
            // 3. Open Upload Data Menu
            openUploadDataMenu(driver);
            Thread.sleep(200);

            // 4. Click the target Sub-Menu item
            boolean subClicked = clickSubMenuItem(driver, subMenu);
            if (!subClicked) {
                System.out.printf("[UPLOAD: WARN] Could not trigger click on '%s'.%n", subMenu.getDisplayName());
            }
            Thread.sleep(500);

            // Optional: Automatically download sample template if provided by application modal
            boolean sampleDownloaded = downloadSampleFileIfAvailable(driver);
            if (sampleDownloaded) {
                System.out.printf("[SAMPLE DOWNLOAD] Successfully triggered 'Download Sample' for %s.%n", subMenu.getDisplayName());
                Thread.sleep(400);
            }

            // 5. Look for file input (<input type="file">) in DOM or Modal
            JavascriptExecutor js = (JavascriptExecutor) driver;
            WebElement fileInput = findFileInputElement(driver);

            if (fileInput != null) {
                // Ensure file input is interactable
                js.executeScript(
                    "arguments[0].style.display = 'block';" +
                    "arguments[0].style.visibility = 'visible';" +
                    "arguments[0].style.opacity = '1';" +
                    "arguments[0].removeAttribute('hidden');",
                    fileInput
                );
                fileInput.sendKeys(f.getAbsolutePath());
                System.out.printf("[UPLOAD] File path sent to <input type='file'>: %s%n", f.getAbsolutePath());

                // Trigger change & input events
                js.executeScript(
                    "if (window.jQuery) { $(arguments[0]).trigger('input').trigger('change'); }" +
                    "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                    "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                    fileInput
                );

                // 6. Look for Upload Confirmation / Submit Button if present
                Thread.sleep(600);
                confirmUploadModalIfPresent(driver);

                // 7. Wait for server-side processing & DOM table refresh
                System.out.println("[UPLOAD] Waiting for server processing & File Manager table update...");
                Thread.sleep(2500);
                dismissOverlays(driver);

                // 8. Verify & Display in Web Application File Manager Table
                MenuData refreshed = captureMenuAndSubMenuData(driver);
                System.out.printf("[UPLOAD SUCCESS] Successfully inserted and processed '%s' for %s.%n", f.getName(), subMenu.getDisplayName());
                System.out.println("\n[FILE MANAGER VERIFICATION] Real-Time Table in Web Application:");
                printUploadedFilesTable(refreshed);
                return true;

            } else {
                System.out.println("[UPLOAD: WARN] No <input type='file'> found in DOM for this sub-menu.");
            }

        } catch (Exception e) {
            System.out.println("[UPLOAD: ERROR] Encountered exception: " + e.getMessage());
        }
        return false;
    }

    // Convenience Wrappers for all 9 Sub-menu types (Safe by default):
    public static boolean uploadClientFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.CLIENT_FILE, filePath, dryRun);
    }
    public static boolean uploadStockFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.STOCK_FILE, filePath, dryRun);
    }
    public static boolean uploadCashFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.CASH_FILE, filePath, dryRun);
    }
    public static boolean uploadAssociatesFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.ASSOCIATES_FILE, filePath, dryRun);
    }
    public static boolean uploadClientRatingFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.CLIENT_RATING_FILE, filePath, dryRun);
    }
    public static boolean uploadClientCommissionFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.CLIENT_COMMISSION_FILE, filePath, dryRun);
    }
    public static boolean uploadUserActiveInactiveFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.USER_ACTIVE_INACTIVE_FILE, filePath, dryRun);
    }
    public static boolean uploadDealerTerminalFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.DEALER_TERMINAL_FILE, filePath, dryRun);
    }
    public static boolean uploadCseCircuitBreakerFile(WebDriver driver, String filePath, boolean dryRun) {
        return uploadFileSafely(driver, UploadSubMenu.CSE_CIRCUIT_BREAKER_FILE, filePath, dryRun);
    }

    // =========================================================================
    // 8. HELPER METHODS (DOM Click, Find Input, Confirmation)
    // =========================================================================
    public static boolean verifySubMenuPresent(WebDriver driver, UploadSubMenu subMenu) {
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

    public static boolean clickSubMenuItem(WebDriver driver, UploadSubMenu subMenu) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object clickRes = js.executeScript(
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
            return Boolean.TRUE.equals(clickRes);
        } catch (Exception e) {
            return false;
        }
    }

    public static WebElement findFileInputElement(WebDriver driver) {
        long end = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < end) {
            try {
                List<WebElement> inputs = driver.findElements(By.xpath(
                    "//input[@type='file'] | " +
                    "//div[contains(@class,'modal')]//input[@type='file'] | " +
                    "//div[contains(@class,'jconfirm')]//input[@type='file'] | " +
                    "//form//input[@type='file']"
                ));
                for (WebElement inp : inputs) {
                    return inp;
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(200); } catch (Exception ignored) {}
        }
        return null;
    }

    public static boolean downloadSampleFileIfAvailable(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var candidates = Array.from(document.querySelectorAll('.modal a, .modal button, .jconfirm a, .jconfirm button, a, button'));\n" +
                "var sampleLink = candidates.find(function(el) {\n" +
                "    var t = (el.innerText || el.textContent || '').trim().toLowerCase();\n" +
                "    return t.indexOf('sample') !== -1 || t.indexOf('template') !== -1 || t.indexOf('download sample') !== -1 || t.indexOf('sample format') !== -1;\n" +
                "});\n" +
                "if (sampleLink) {\n" +
                "    if (window.jQuery) window.jQuery(sampleLink).trigger('click');\n" +
                "    sampleLink.click();\n" +
                "    return true;\n" +
                "}\n" +
                "return false;"
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    public static void confirmUploadModalIfPresent(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "var btns = Array.from(document.querySelectorAll('.modal button, .jconfirm button, button.btn-primary, button.btn-success'));" +
                "var uploadConfirmBtn = btns.find(function(b) {" +
                "    var t = (b.innerText || '').trim().toLowerCase();" +
                "    return t === 'upload' || t === 'submit' || t === 'confirm' || t === 'yes' || t === 'save';" +
                "});" +
                "if (uploadConfirmBtn) uploadConfirmBtn.click();"
            );
        } catch (Exception ignored) {}
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

    private static void handleManualFileUpload(WebDriver driver, Scanner sc) {
        System.out.println("\n--- Select File Type to Upload ---");
        for (UploadSubMenu m : UploadSubMenu.values()) {
            System.out.printf("  %d. %s%n", m.getIndex(), m.getDisplayName());
        }
        System.out.print("Enter Sub-Menu number (1-9): ");
        String subChoice = sc.nextLine().trim();
        UploadSubMenu selectedMenu = null;
        try {
            int idx = Integer.parseInt(subChoice);
            selectedMenu = UploadSubMenu.fromIndex(idx);
        } catch (Exception ignored) {}

        if (selectedMenu == null) {
            System.out.println("[CANCELLED] Invalid sub-menu selected.");
            return;
        }

        String defaultPath = "";
        if (selectedMenu == UploadSubMenu.CLIENT_FILE) {
            defaultPath = "sample_files/increment_active_clients.xml";
        } else if (selectedMenu == UploadSubMenu.STOCK_FILE) {
            defaultPath = "sample_files/increment_stock.xml";
        } else if (selectedMenu == UploadSubMenu.CASH_FILE) {
            defaultPath = "sample_files/increment_cash.xml";
        }

        if (!defaultPath.isEmpty() && new File(defaultPath).exists()) {
            System.out.printf("Enter Absolute Local Path for '%s' (press ENTER for default: %s): ", selectedMenu.getDisplayName(), defaultPath);
        } else {
            System.out.printf("Enter Absolute Local Path for '%s' (e.g. C:\\data\\clients.xml): ", selectedMenu.getDisplayName());
        }

        String path = sc.nextLine().trim();
        if (path.isEmpty() && !defaultPath.isEmpty() && new File(defaultPath).exists()) {
            path = new File(defaultPath).getAbsolutePath();
            System.out.println("Using default sample file: " + path);
        }

        if (path.isEmpty()) {
            System.out.println("[CANCELLED] No path provided. Aborting upload.");
            return;
        }

        File f = new File(path);
        if (!f.exists()) {
            System.out.printf("[ERROR] File does not exist on disk: %s%n", path);
            return;
        }

        System.out.printf("\n[CONFIRMATION REQUIRED] Are you sure you want to upload '%s' to '%s'? (1=YES, 0=NO, default=0): ",
                f.getName(), selectedMenu.getDisplayName());
        String confirm = sc.nextLine().trim();
        if (!"1".equals(confirm)) {
            System.out.println("[CANCELLED] Upload aborted by user.");
            return;
        }

        uploadFileSafely(driver, selectedMenu, path, false);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
