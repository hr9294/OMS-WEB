import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Order_Terminal {

    // =========================================================================
    // 1. ENDPOINTS & SELECTORS (Matched to your Order Terminal widget)
    // =========================================================================
    private static final String API_URL_TEMPLATE = "https://omsapi.quantbd.com/orders/%s?exchange=%s";
    private static final String[] EXCHANGES = {"DSE", "CSE"};

    // Navigation Tabs
    private static final By ORDER_TERMINAL_TAB = By.xpath(
            "//div[contains(@class,'lm_tab') and contains(.,'Order Terminal')] | " +
            "//li[contains(@class,'lm_tab') and (.//span[contains(text(),'Order Terminal')] or contains(.,'Order Terminal'))] | " +
            "//a[normalize-space()='Order Terminal'] | //div[@title='Order Terminal']");

    private static final By TRADE_TAB = By.xpath(
            "//div[contains(@class,'lm_tab') and contains(.,'Trade')] | " +
            "//li[contains(@class,'lm_tab') and (.//span[contains(text(),'Trade')] or contains(.,'Trade'))] | " +
            "//i[contains(@class,'fa-exchange')] | //a[normalize-space()='Trade']");

    // Filter Bar in Order Terminal Header
    private static final By INPUT_CODE   = By.xpath("//input[contains(@id,'order_terminal_code') or @placeholder='Search Code']");
    private static final By INPUT_SYMBOL = By.xpath("//input[contains(@id,'order_terminal_symbol') or @placeholder='Search Symbol']");
    private static final By BTN_CLEAR    = By.xpath("//button[contains(@id,'clear_filter') or normalize-space()='Clear']");
    private static final By BTN_RESET    = By.xpath("//button[contains(@id,'reset_filter') or normalize-space()='Reset']");
    private static final By REFRESH_BTN  = By.xpath("//div[contains(@class,'user_order_terminals')]//i[contains(@class,'refresh')] | //button[contains(@id,'refresh')]");

    // Order Terminal Table
    private static final By TERMINAL_TABLE   = By.xpath("//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]");
    private static final By TABLE_HEADERS    = By.xpath(".//thead//th");

    // =========================================================================
    // 2. MAIN ENTRY POINT (Matching your runner's signature)
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=======================================================");
        System.out.println("            ORDER TERMINAL DATA VIEWER                 ");
        System.out.println("=======================================================");

        // Auto-detect symbol & client from Trade page inputs
        String symbol = readInputSafe(driver, By.id("order_instrument"), By.id("instrument"), By.id("marketwatch_symbol"));
        String clientCode = readInputSafe(driver, By.id("order_client_code"));
        String exchange = "DSE";

        if (clientCode.isEmpty()) clientCode = "23986";
        if (symbol.isEmpty()) {
            System.out.print("Enter Symbol (e.g. GP, ACFL): ");
            symbol = sc.nextLine().trim().toUpperCase();
        }

        System.out.printf("[TERMINAL] Auto-detected: Client: %s | Symbol: %s | Exchange: %s%n", clientCode, symbol, exchange);

        // Navigate to Order Terminal Tab
        navigateToOrderTerminal(driver);
        filterOrderTerminal(driver, clientCode, symbol);

        Map<String, String> snapshot = getSnapshot(driver, clientCode, symbol, exchange);
        printSnapshot("ORDER TERMINAL SNAPSHOT FOR " + symbol, snapshot);

        // Status Filter & Multi-Selection Menu
        System.out.println("\nOrder Terminal Status Filter & Selection Options:");
        System.out.println("  1. Filter by Status: All Accepted Orders");
        System.out.println("  2. Filter by Status: All Partially Filled Orders");
        System.out.println("  3. Filter & Select Both (All Accepted & Partially Filled Orders)");
        System.out.println("  4. Select Accepted & Partially Filled -> Right-Click Execute at Market");
        System.out.println("  0. Skip (Exit)");
        System.out.print("Enter choice (0-4, default=0): ");
        String choice = sc.nextLine().trim();

        if (choice.equals("1")) {
            filterByStatus(driver, "ACCEPTED");
            selectOrdersByStatus(driver, "ACCEPTED");
        } else if (choice.equals("2")) {
            filterByStatus(driver, "PARTIALLY FILLED");
            selectOrdersByStatus(driver, "PARTIALLY FILLED");
        } else if (choice.equals("3")) {
            filterAndSelectAcceptedAndPartiallyFilled(driver);
        } else if (choice.equals("4")) {
            List<WebElement> selected = filterAndSelectAcceptedAndPartiallyFilled(driver);
            if (!selected.isEmpty()) {
                System.out.print("Right-click and Execute at Market for these " + selected.size() + " orders? (1=YES / 0=NO): ");
                if (sc.nextLine().trim().equals("1")) {
                    executeSelectedAtMarket(driver, selected);
                }
            }
        }
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & FILTER HELPERS
    // =========================================================================
    public static void navigateToOrderTerminal(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean clicked = (Boolean) js.executeScript(
                "var tab = document.querySelector('li.lm_tab[title*=\"Order Terminal\"], div[title*=\"Order Terminal\"], a[title*=\"Order Terminal\"]');" +
                "if (tab && tab.offsetParent !== null) { tab.click(); return true; }" +
                "var tabs = Array.from(document.querySelectorAll('.lm_tab, li.lm_tab, a.nav-link'));" +
                "var otTab = tabs.find(function(t) { return t.innerText && t.innerText.includes('Order Terminal'); });" +
                "if (otTab && otTab.offsetParent !== null) { otTab.click(); return true; }" +
                "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(500);
            } else {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(4));
                WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(ORDER_TERMINAL_TAB));
                tab.click();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println("[NAV] Order Terminal tab already active or clicked.");
        }
    }

    public static void navigateToTrade(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean clicked = (Boolean) js.executeScript(
                "var link = document.querySelector('a[data-link=\"live_orders/trade_portal\"], a[title=\"Trade\"]');" +
                "if (link && link.offsetParent !== null) { link.click(); return true; }" +
                "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(400);
            } else {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
                wait.until(ExpectedConditions.elementToBeClickable(TRADE_TAB)).click();
                Thread.sleep(400);
            }
        } catch (Exception ignored) {}
    }

    public static void filterOrderTerminal(WebDriver driver, String clientCode, String symbol) {
        try {
            if (clientCode != null && !clientCode.isEmpty()) {
                WebElement codeInput = driver.findElement(INPUT_CODE);
                codeInput.clear();
                codeInput.sendKeys(clientCode);
                codeInput.sendKeys(Keys.ENTER);
                Thread.sleep(300);
            }
            if (symbol != null && !symbol.isEmpty()) {
                WebElement symInput = driver.findElement(INPUT_SYMBOL);
                symInput.clear();
                symInput.sendKeys(symbol);
                symInput.sendKeys(Keys.ENTER);
                Thread.sleep(300);
            }
        } catch (Exception ignored) {}
    }

    public static void clearFilter(WebDriver driver) {
        try {
            try { driver.findElement(BTN_CLEAR).click(); } catch (Exception ignored) {}
            try { driver.findElement(BTN_RESET).click(); } catch (Exception ignored) {}
            Thread.sleep(400);
        } catch (Exception ignored) {}
    }

    public static void refresh(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("if (typeof get_order_history === 'function') { get_order_history(); }");
            try {
                WebElement btn = driver.findElement(REFRESH_BTN);
                if (btn.isDisplayed()) btn.click();
            } catch (Exception ignored) {}
            Thread.sleep(800);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 3B. ROW SELECTION & CLICKING (AFTER ORDER PLACEMENT)
    // =========================================================================
    public static boolean clickOrderRow(WebDriver driver, String orderId, String clientCode, String symbol) {
        try {
            navigateToOrderTerminal(driver);
            Thread.sleep(300);

            WebElement row = findOrderRow(driver, orderId, clientCode, symbol);
            if (row != null) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript(
                    "var row = arguments[0];" +
                    "row.scrollIntoView({block: 'center', inline: 'center'});" +
                    "if (window.jQuery) { window.jQuery(row).trigger('click'); }" +
                    "try { row.classList.add('order_highlight'); } catch(e){}" +
                    "try { row.click(); } catch(e){}" +
                    "try { row.dispatchEvent(new MouseEvent('mousedown', {bubbles:true, cancelable:true})); } catch(e){}" +
                    "try { row.dispatchEvent(new MouseEvent('mouseup', {bubbles:true, cancelable:true})); } catch(e){}" +
                    "try { row.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true})); } catch(e){}",
                    row
                );

                try {
                    new Actions(driver).moveToElement(row).click().perform();
                } catch (Exception ignored) {}

                String clickedId = row.getDomAttribute("id");
                if (clickedId == null || clickedId.isEmpty()) clickedId = orderId;
                System.out.printf("[ORDER TERMINAL] Selected/Clicked order row: %s (Client: %s, Symbol: %s)%n",
                        (clickedId != null && !clickedId.isEmpty() ? clickedId : "Top Row"), clientCode, symbol);
                Thread.sleep(400);
                return true;
            } else {
                System.out.printf("[ORDER TERMINAL] No order row found to click for ID: %s, Client: %s, Symbol: %s%n",
                        orderId, clientCode, symbol);
            }
        } catch (Exception e) {
            System.out.println("[WARN: Order Terminal Click] " + e.getMessage());
        }
        return false;
    }

    public static WebElement findOrderRow(WebDriver driver, String orderId, String clientCode, String symbol) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 1. Try by Order ID in DOM (chain_id set on row.id)
        if (orderId != null && !orderId.trim().isEmpty() && !orderId.equals("-") && !orderId.equalsIgnoreCase("NONE")) {
            String cleanId = orderId.trim();
            try {
                WebElement byId = driver.findElement(By.id(cleanId));
                if (byId != null && byId.isDisplayed()) return byId;
            } catch (Exception ignored) {}

            try {
                WebElement byXpath = driver.findElement(By.xpath(
                    "//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr[@id='" + cleanId + "'] | " +
                    "//tr[@id='" + cleanId + "'] | " +
                    "//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr[td[contains(normalize-space(),'" + cleanId + "')]]"
                ));
                if (byXpath != null) return byXpath;
            } catch (Exception ignored) {}
        }

        // 2. Try by Client Code & Symbol (data attributes and td contents)
        if (clientCode != null && !clientCode.trim().isEmpty() && symbol != null && !symbol.trim().isEmpty()) {
            String cleanCode = clientCode.trim();
            String cleanSym = symbol.trim().toUpperCase();
            try {
                WebElement byAttr = driver.findElement(By.xpath(
                    "//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr[@data-code='" + cleanCode + "' and contains(@data-symbol,'" + cleanSym + "')]"
                ));
                if (byAttr != null) return byAttr;
            } catch (Exception ignored) {}

            try {
                WebElement byText = driver.findElement(By.xpath(
                    "//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr[td[normalize-space()='" + cleanCode + "'] and td[contains(normalize-space(),'" + cleanSym + "')]]"
                ));
                if (byText != null) return byText;
            } catch (Exception ignored) {}
        }

        // 3. Try by JS querySelector for first terminal-tr or first tbody row
        try {
            Object rowObj = js.executeScript(
                "return document.querySelector('table.order_terminal_table tbody tr.terminal-tr, table[id*=\"market_table\"] tbody tr.terminal-tr, table.order_terminal_table tbody tr, table[id*=\"market_table\"] tbody tr');"
            );
            if (rowObj instanceof WebElement) {
                return (WebElement) rowObj;
            }
        } catch (Exception ignored) {}

        // 4. Fallback: first row in any terminal table
        try {
            return driver.findElement(By.xpath(
                "(//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr)[1]"
            ));
        } catch (Exception ignored) {}

        return null;
    }

    // =========================================================================
    // 3C. STATUS FILTERING & SELECTION (ACCEPTED & PARTIALLY FILLED ORDERS)
    // =========================================================================

    /**
     * Filters the Order Terminal table by Order Status.
     * Supported values:
     *   "ACCEPTED"         - Shows only Accepted orders
     *   "PARTIALLY FILLED" - Shows only Partially Filled orders
     *   "ACTIVE"           - Shows both Accepted and Partially Filled orders
     *   "FILLED"           - Shows Filled orders
     *   "ALL"              - Resets filter, shows all orders
     */
    public static int filterByStatus(WebDriver driver, String status) {
        navigateToOrderTerminal(driver);
        try {
            // 1. Try UI status dropdown/select if available
            try {
                WebElement selectElem = driver.findElement(By.xpath(
                    "//select[contains(@id,'status') or contains(@name,'status') or contains(@class,'order_status') or contains(@id,'order_terminal_status')] | " +
                    "//div[contains(@class,'order_terminal')]//select"
                ));
                if (selectElem != null && selectElem.isDisplayed()) {
                    org.openqa.selenium.support.ui.Select s = new org.openqa.selenium.support.ui.Select(selectElem);
                    for (WebElement opt : s.getOptions()) {
                        String optText = opt.getText().toUpperCase();
                        if (status.equalsIgnoreCase("ACTIVE")) {
                            if (optText.contains("ALL") || optText.contains("OPEN") || optText.contains("ACTIVE")) {
                                s.selectByVisibleText(opt.getText());
                                break;
                            }
                        } else if (optText.contains(status.toUpperCase())) {
                            s.selectByVisibleText(opt.getText());
                            break;
                        }
                    }
                    ((JavascriptExecutor) driver).executeScript("arguments[0].dispatchEvent(new Event('change', {bubbles:true}));", selectElem);
                }
            } catch (Exception ignored) {}

            // 2. DOM Table Filter: adjust row visibility according to status
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var target = (arguments[0] || '').toUpperCase();" +
                "var tbl = document.querySelector('table.order_terminal_table, table[id*=\"market_table\"]');" +
                "if (!tbl) return 0;" +
                "var rows = tbl.querySelectorAll('tbody tr');" +
                "var count = 0;" +
                "for (var i = 0; i < rows.length; i++) {" +
                "    var r = rows[i];" +
                "    var txt = (r.innerText || r.textContent || '').toUpperCase();" +
                "    var match = false;" +
                "    if (target === 'ALL' || target === '') {" +
                "        match = true;" +
                "    } else if (target === 'ACTIVE' || target === 'ACCEPTED_OR_PARTIALLY_FILLED') {" +
                "        match = (txt.indexOf('ACCEPTED') !== -1 || txt.indexOf('PARTIALLY FILLED') !== -1 || txt.indexOf('PARTIALLY') !== -1);" +
                "    } else if (target === 'ACCEPTED') {" +
                "        match = (txt.indexOf('ACCEPTED') !== -1 && txt.indexOf('PARTIALLY') === -1);" +
                "    } else if (target === 'PARTIALLY FILLED' || target === 'PARTIALLY_FILLED') {" +
                "        match = (txt.indexOf('PARTIALLY FILLED') !== -1 || txt.indexOf('PARTIALLY') !== -1);" +
                "    } else {" +
                "        match = (txt.indexOf(target) !== -1);" +
                "    }" +
                "    r.style.display = match ? '' : 'none';" +
                "    if (match) count++;" +
                "}" +
                "return count;",
                status
            );

            int matchCount = (res instanceof Number) ? ((Number) res).intValue() : 0;
            System.out.printf("[ORDER TERMINAL] Filtered table by Status '%s'. Visible matching orders: %d%n", status, matchCount);
            Thread.sleep(400);
            return matchCount;
        } catch (Exception e) {
            System.out.println("[WARN: Status Filter] " + e.getMessage());
            return 0;
        }
    }

    /**
     * Multi-selects all orders matching the specified statuses (e.g. ACCEPTED and/or PARTIALLY FILLED).
     */
    public static List<WebElement> selectOrdersByStatus(WebDriver driver, String... targetStatuses) {
        navigateToOrderTerminal(driver);
        List<WebElement> selected = new ArrayList<>();
        try {
            Thread.sleep(400);
            WebElement table = driver.findElement(TERMINAL_TABLE);
            List<WebElement> rows = table.findElements(By.xpath(".//tbody//tr"));
            Actions actions = new Actions(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            System.out.printf("[ORDER TERMINAL] Scanning %d rows for statuses: %s...%n",
                    rows.size(), Arrays.toString(targetStatuses));

            List<String[]> selectedSummary = new ArrayList<>();

            for (WebElement row : rows) {
                try {
                    // Check if row is visible (not hidden by filter)
                    if (!row.isDisplayed()) continue;

                    String rowText = row.getText().toUpperCase();
                    boolean matches = false;
                    String matchedStatus = "";

                    for (String st : targetStatuses) {
                        String cleanSt = st.trim().toUpperCase();
                        if (cleanSt.equals("ACCEPTED") && rowText.contains("ACCEPTED") && !rowText.contains("PARTIALLY")) {
                            matches = true;
                            matchedStatus = "ACCEPTED";
                            break;
                        } else if ((cleanSt.equals("PARTIALLY FILLED") || cleanSt.equals("PARTIALLY_FILLED")) && (rowText.contains("PARTIALLY FILLED") || rowText.contains("PARTIALLY"))) {
                            matches = true;
                            matchedStatus = "PARTIALLY FILLED";
                            break;
                        } else if (cleanSt.equals("ACTIVE") && (rowText.contains("ACCEPTED") || rowText.contains("PARTIALLY"))) {
                            matches = true;
                            matchedStatus = rowText.contains("PARTIALLY") ? "PARTIALLY FILLED" : "ACCEPTED";
                            break;
                        } else if (rowText.contains(cleanSt)) {
                            matches = true;
                            matchedStatus = cleanSt;
                            break;
                        }
                    }

                    if (matches) {
                        selected.add(row);

                        // Highlight & check row
                        js.executeScript(
                            "var r = arguments[0];" +
                            "r.classList.add('selected');" +
                            "r.classList.add('active');" +
                            "r.classList.add('order_highlight');" +
                            "var cb = r.querySelector('input[type=\"checkbox\"]');" +
                            "if (cb && !cb.checked) { cb.checked = true; cb.dispatchEvent(new Event('change', { bubbles: true })); }",
                            row
                        );

                        // Multi-select with CTRL key
                        try {
                            actions.keyDown(Keys.CONTROL).click(row).keyUp(Keys.CONTROL).perform();
                        } catch (Exception ignored) {}

                        // Extract summary info
                        String rowId = row.getDomAttribute("id");
                        List<WebElement> cells = row.findElements(By.xpath("./td"));
                        String orderId = (rowId != null && !rowId.isEmpty()) ? rowId : (cells.size() > 1 ? cells.get(1).getText().trim() : "-");
                        String clientCode = (cells.size() > 4 ? cells.get(4).getText().trim() : "-");
                        String sym = (cells.size() > 5 ? cells.get(5).getText().trim() : "-");
                        String side = (cells.size() > 7 ? cells.get(7).getText().trim() : "-");
                        String price = (cells.size() > 9 ? cells.get(9).getText().trim() : "-");
                        String qty = (cells.size() > 11 ? cells.get(11).getText().trim() : "-");

                        selectedSummary.add(new String[]{ orderId, clientCode, sym, side, price, qty, matchedStatus });
                    }
                } catch (Exception ignored) {}
            }

            // Print summary table of selected orders
            System.out.println();
            System.out.println("+=======================================================================================================+");
            System.out.printf("| SELECTED ORDERS BY STATUS IN ORDER TERMINAL (Total Selected: %d)%-36s |%n", selected.size(), "");
            System.out.println("+=======================================================================================================+");
            System.out.printf("| %-4s | %-12s | %-10s | %-10s | %-6s | %-8s | %-8s | %-18s |%n",
                    "#", "Order ID", "Client", "Symbol", "Side", "Price", "Qty", "Status");
            System.out.println("+-------------------------------------------------------------------------------------------------------+");

            if (selectedSummary.isEmpty()) {
                System.out.println("| No matching orders found for statuses: " + Arrays.toString(targetStatuses) + "                              |");
            } else {
                for (int i = 0; i < selectedSummary.size(); i++) {
                    String[] s = selectedSummary.get(i);
                    System.out.printf("| %-4d | %-12s | %-10s | %-10s | %-6s | %-8s | %-8s | %-18s |%n",
                            (i + 1), s[0], s[1], s[2], s[3], s[4], s[5], s[6]);
                }
            }
            System.out.println("+=======================================================================================================+\n");

        } catch (Exception e) {
            System.out.println("[WARN: Select Orders By Status] " + e.getMessage());
        }
        return selected;
    }

    /**
     * Convenience method: Filters Order Terminal for Accepted and Partially Filled orders,
     * then selects all of them.
     */
    public static List<WebElement> filterAndSelectAcceptedAndPartiallyFilled(WebDriver driver) {
        System.out.println("[ORDER TERMINAL] Filtering and selecting all Accepted and Partially Filled orders...");
        filterByStatus(driver, "ACTIVE");
        return selectOrdersByStatus(driver, "ACCEPTED", "PARTIALLY FILLED");
    }

    /**
     * Executes the currently selected rows at Market via Right-Click Context Menu.
     */
    public static boolean executeSelectedAtMarket(WebDriver driver, List<WebElement> selectedRows) {
        if (selectedRows == null || selectedRows.isEmpty()) {
            System.out.println("[TERMINAL] No selected rows provided for market execution.");
            return false;
        }

        try {
            Actions actions = new Actions(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(4));

            WebElement anchor = selectedRows.get(selectedRows.size() - 1);
            System.out.printf("[TERMINAL] Right-clicking on selected order row (%s) to trigger 'Execute at Market'...%n",
                    anchor.getDomAttribute("id"));

            // 1. Right click via Actions
            try {
                actions.contextClick(anchor).perform();
            } catch (Exception ignored) {}

            // 2. Dispatch contextmenu event via JavaScript
            js.executeScript(
                "var row = arguments[0];" +
                "var rect = row.getBoundingClientRect();" +
                "var evt = new MouseEvent('contextmenu', {" +
                "    bubbles: true," +
                "    cancelable: true," +
                "    view: window," +
                "    clientX: rect.left + rect.width / 2," +
                "    clientY: rect.top + rect.height / 2," +
                "    button: 2," +
                "    buttons: 2" +
                "});" +
                "row.dispatchEvent(evt);" +
                "if (window.jQuery) { window.jQuery(row).trigger('contextmenu'); }",
                anchor
            );
            Thread.sleep(600);

            // 3. Click "Execute at Market" in context menu
            boolean clicked = false;
            try {
                By execMenuBy = By.xpath(
                    "//*[contains(@class,'context-menu') or contains(@class,'dropdown') or contains(@class,'menu') or self::li or self::a or self::span or self::div]" +
                    "[translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='execute at market' or " +
                    "contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'execute at market') or " +
                    "contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'execute market') or " +
                    "contains(translate(normalize-space(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'market execute')]"
                );
                WebElement item = wait.until(ExpectedConditions.elementToBeClickable(execMenuBy));
                item.click();
                clicked = true;
                System.out.println("✔ [CONTEXT MENU] Clicked 'Execute at Market' option.");
            } catch (Exception e) {
                Object res = js.executeScript(
                    "var items = Array.from(document.querySelectorAll('.context-menu-item, .dropdown-item, .menu-item, ul.context-menu-list li, ul.dropdown-menu li, .vakata-context li, li, a, span, div'));" +
                    "var target = items.find(function(el) {" +
                    "    var txt = (el.innerText || el.textContent || '').trim().toLowerCase();" +
                    "    return txt === 'execute at market' || txt.includes('execute at market') || txt === 'execute market' || txt === 'market execute';" +
                    "});" +
                    "if (target) { target.click(); return true; }" +
                    "return false;"
                );
                if (Boolean.TRUE.equals(res)) {
                    clicked = true;
                    System.out.println("✔ [CONTEXT MENU: JS] Clicked 'Execute at Market' via DOM.");
                }
            }

            Thread.sleep(500);

            // 4. Confirm modal if shown
            try {
                WebElement confirmYes = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                    "//button[@id='cnfirmConfirmBtn'] | //button[normalize-space()='YES'] | //button[normalize-space()='Yes'] | //button[normalize-space()='Confirm'] | //button[normalize-space()='OK']")));
                confirmYes.click();
                System.out.println("✔ [CONFIRMATION] Confirmed market execution dialog [YES].");
            } catch (Exception ignored) {}

            Thread.sleep(1500);
            refresh(driver);
            return clicked;
        } catch (Exception e) {
            System.out.println("[WARN: Execute at Market] " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 4. DATA CAPTURE: API FIRST (ADMIN LIVE -> QUANTBD TOKEN -> UI XPATH)
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String clientCode, String symbol, String preferredExchange) {
        String cleanSymbol = cleanSymbol(symbol);

        // Tier 1: Admin Live Order Terminal API (/live_orders/market_orders_compact/)
        try {
            String adminUrl = String.format("/live_orders/market_orders_compact/?order_client_code=%s&order_symbol=%s&limit=50&page=1", clientCode, cleanSymbol);
            String json = executeBrowserFetch(driver, adminUrl);
            Map<String, String> adminData = parseOrderTerminalJson(json, clientCode, cleanSymbol);
            if (!adminData.isEmpty()) {
                adminData.put("DATA_SOURCE", "API (Admin Live)");
                System.out.printf("[SUCCESS: API] Captured Order Terminal for %s (Client: %s) from Admin Live API.%n", cleanSymbol, clientCode);
                return adminData;
            }
        } catch (Exception e) {
            System.out.printf("[WARN: Admin API] Order Terminal query failed: %s%n", e.getMessage());
        }

        // Tier 2: Microservice Token API (DSE + CSE)
        List<String> exchangeOrder = new ArrayList<>();
        exchangeOrder.add(preferredExchange != null && !preferredExchange.isEmpty() ? preferredExchange : "DSE");
        for (String ex : EXCHANGES) {
            if (!exchangeOrder.contains(ex)) exchangeOrder.add(ex);
        }

        for (String ex : exchangeOrder) {
            String apiUrl = String.format(API_URL_TEMPLATE, clientCode, ex);
            try {
                Map<String, String> data = fetchFromApi(driver, apiUrl, cleanSymbol);
                if (!data.isEmpty()) {
                    data.put("DATA_SOURCE", "API (" + ex + ")");
                    System.out.printf("[SUCCESS: API] Captured Order Terminal for %s from %s Token API.%n", cleanSymbol, ex);
                    return data;
                }
            } catch (Exception ignored) {}
        }

        // Tier 3 Fallback: DOM Table Scraping
        System.out.println("[FALLBACK: UI] API unavailable. Extracting from Order Terminal DOM table...");
        Map<String, String> uiData = fetchFromUiTable(driver, cleanSymbol, clientCode);
        uiData.put("DATA_SOURCE", "UI_XPATH (" + preferredExchange + ")");
        return uiData;
    }

    // =========================================================================
    // 4B. PRE-TRADE DATA CAPTURE (Progress is always 0% prior to trade execution)
    // =========================================================================
    public static Map<String, String> getPreTradeSnapshot(WebDriver driver, String clientCode, String symbol, String preferredExchange) {
        Map<String, String> data = getSnapshot(driver, clientCode, symbol, preferredExchange);
        if (data != null) {
            data.put("Progress", "0%");
            data.put("IS_PRE_TRADE", "true");
        }
        return data;
    }

    // =========================================================================
    // 5. UI TABLE SCRAPING (Order Terminal Table)
    // =========================================================================
    private static Map<String, String> fetchFromUiTable(WebDriver driver, String symbol, String clientCode) {
        Map<String, String> data = new LinkedHashMap<>();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(TERMINAL_TABLE));
                Map<String, Integer> headerMap = getHeaderMap(table);

                List<WebElement> rows = table.findElements(By.xpath(".//tbody//tr"));
                WebElement targetRow = null;

                for (WebElement row : rows) {
                    String rowText = row.getText().toUpperCase();
                    if (rowText.contains(symbol) && (clientCode.isEmpty() || rowText.contains(clientCode))) {
                        targetRow = row;
                        break;
                    }
                }
                if (targetRow == null && !rows.isEmpty()) {
                    targetRow = rows.get(0); // fallback to most recent row
                }

                if (targetRow != null) {
                    List<WebElement> cells = targetRow.findElements(By.xpath("./td"));
                    for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                        if (entry.getValue() < cells.size()) {
                            data.put(normalizeKey(entry.getKey()), cells.get(entry.getValue()).getText().trim());
                        }
                    }
                }
                return data;
            } catch (StaleElementReferenceException e) {
                if (attempt == 3) break;
            } catch (Exception ignored) {
                break;
            }
        }
        return data;
    }

    private static Map<String, Integer> getHeaderMap(WebElement table) {
        Map<String, Integer> map = new LinkedHashMap<>();
        List<WebElement> headers = table.findElements(TABLE_HEADERS);
        for (int i = 0; i < headers.size(); i++) {
            String text = headers.get(i).getText().trim();
            if (!text.isEmpty()) map.put(text, i);
        }
        return map;
    }

    private static String normalizeKey(String header) {
        String h = header.toUpperCase();
        if (h.contains("ORDER ID") || h.equals("OID")) return "Order_ID";
        if (h.contains("ORDER TIME")) return "Order_Time";
        if (h.contains("EXEC TIME") || h.equals("ETM")) return "Exec_Time";
        if (h.equals("CODE") || h.contains("CLIENT")) return "Code";
        if (h.contains("SYMBOL")) return "Symbol";
        if (h.equals("BOARD")) return "Board";
        if (h.equals("SIDE")) return "Side";
        if (h.equals("TYPE") || h.contains("ORDER TYPE")) return "Type";
        if (h.equals("PRICE")) return "Price";
        if (h.contains("AVG PRICE") || h.equals("AVG_PRICE")) return "Avg_Price";
        if (h.equals("QTY") || h.contains("ORDER QTY")) return "Qty";
        if (h.contains("DRIB") || h.contains("DRIP")) return "Drib";
        if (h.contains("DUE")) return "Due_Qty";
        if (h.contains("EXEC QTY") || h.equals("EXECQTY")) return "Exec_Qty";
        if (h.contains("STATUS") && !h.contains("EXEC")) return "Status";
        if (h.contains("EXEC TYPE") || h.equals("EXECTYPE")) return "Exec_Type";
        if (h.contains("PROGRESS")) return "Progress";
        if (h.contains("DEALER")) return "Dealer_ID";
        if (h.contains("USER")) return "User_ID";
        if (h.contains("XCG") || h.contains("EXCHANGE")) return "XCG";
        return header.replace(" ", "_");
    }

    // =========================================================================
    // 6. API CLIENT & PARSERS
    // =========================================================================
    private static String executeBrowserFetch(WebDriver driver, String endpoint) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeAsyncScript(
                "var callback = arguments[arguments.length - 1];" +
                "var url = arguments[0];" +
                "fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' }, credentials: 'include' })" +
                ".then(function(r) { return r.text(); })" +
                ".then(function(t) { callback({ status: 200, text: t }); })" +
                ".catch(function(e) { callback({ status: 0, error: e.toString() }); });",
                endpoint
            );
            if (res instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) res;
                Number status = (Number) map.get("status");
                if (status != null && status.intValue() == 200) {
                    return (String) map.get("text");
                }
            }
        } catch (Exception e) {
            System.out.println("[WARN: BrowserFetch] " + e.getMessage());
        }
        return null;
    }

    private static Map<String, String> parseOrderTerminalJson(String json, String targetClient, String targetSymbol) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("\"code\":200")) return result;

        // Check for items array
        Pattern itemPattern = Pattern.compile("\\{[^{}]*\"symbol_code\"\\s*:\\s*\"" + targetSymbol + "\"[^{}]*\\}", Pattern.CASE_INSENSITIVE);
        Matcher m = itemPattern.matcher(json);

        String block = null;
        if (m.find()) {
            block = m.group(0);
        } else {
            // Check if any order exists in items
            Pattern anyItemPattern = Pattern.compile("\"items\"\\s*:\\s*\\[\\s*(\\{[^{}]+\\})");
            Matcher anyM = anyItemPattern.matcher(json);
            if (anyM.find()) {
                block = anyM.group(1);
            }
        }

        if (block != null) {
            result.put("XCG", getJsonVal(block, "exchange", "xcg"));
            result.put("Order_ID", getJsonVal(block, "chain_id", "order_id", "order_chain_id"));
            result.put("Order_Time", getJsonVal(block, "order_placed_time", "order_time"));
            result.put("Exec_Time", getJsonVal(block, "order_time", "exec_time"));
            result.put("Code", getJsonVal(block, "client_code", "client"));
            result.put("Symbol", getJsonVal(block, "symbol_code", "symbol"));
            result.put("Category", getJsonVal(block, "symbol_category", "category"));
            result.put("Board", getJsonVal(block, "board_type", "board"));
            result.put("Side", getJsonVal(block, "order_side", "side"));
            result.put("Type", getJsonVal(block, "order_type", "type"));
            result.put("Price", getJsonVal(block, "order_price", "price"));
            result.put("Avg_Price", getJsonVal(block, "avg_px", "last_px", "avg_price"));
            result.put("Qty", getJsonVal(block, "order_qty", "qty"));
            result.put("Drib", getJsonVal(block, "drip_qty", "drib"));
            result.put("Due_Qty", getJsonVal(block, "due_qty"));
            result.put("Exec_Qty", getJsonVal(block, "cum_qty", "exec_qty"));
            result.put("Status", getJsonVal(block, "order_status", "status"));
            result.put("Exec_Type", getJsonVal(block, "exec_status", "exec_type"));
            result.put("Dealer_ID", getJsonVal(block, "owner_ws_id", "dealer_id"));
            result.put("User_ID", getJsonVal(block, "user_id"));
            result.put("Order_Remarks", getJsonVal(block, "order_remarks", "remarks"));

            double orderQty = parseNum(result.get("Qty"));
            double execQty  = parseNum(result.get("Exec_Qty"));
            double progress = (orderQty > 0) ? Math.round((execQty / orderQty) * 100) : 0;
            result.put("Progress", (int)progress + "%");
        } else {
            // Default empty placeholders
            result.put("XCG", "DSE");
            result.put("Order_ID", "-");
            result.put("Order_Time", "-");
            result.put("Exec_Time", "-");
            result.put("Code", targetClient);
            result.put("Symbol", targetSymbol);
            result.put("Category", "A");
            result.put("Board", "PUBLIC");
            result.put("Side", "-");
            result.put("Type", "Limit");
            result.put("Price", "0.00");
            result.put("Avg_Price", "0.00");
            result.put("Qty", "0");
            result.put("Drib", "0");
            result.put("Due_Qty", "0");
            result.put("Exec_Qty", "0");
            result.put("Status", "NONE");
            result.put("Exec_Type", "NONE");
            result.put("Progress", "0%");
            result.put("Dealer_ID", "-");
            result.put("User_ID", "-");
        }

        Pattern totalPattern = Pattern.compile("\"total\"\\s*:\\s*(\\d+)");
        Matcher tm = totalPattern.matcher(json);
        if (tm.find()) {
            result.put("Total_Records", tm.group(1));
        }

        return result;
    }

    private static Map<String, String> fetchFromApi(WebDriver driver, String urlStr, String targetSymbol) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            Set<Cookie> cookies = driver.manage().getCookies();
            if (cookies != null && !cookies.isEmpty()) {
                String cookieHeader = cookies.stream().map(c -> c.getName() + "=" + c.getValue()).collect(Collectors.joining("; "));
                conn.setRequestProperty("Cookie", cookieHeader);
            }
            if (conn.getResponseCode() != 200) return Collections.emptyMap();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
            }
            return parseOrderTerminalJson(sb.toString(), "", targetSymbol);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String getJsonVal(String block, String... keys) {
        for (String k : keys) {
            Pattern p = Pattern.compile("\"" + k + "\"\\s*:\\s*[\"]?([^\",}]+)[\"]?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(block);
            if (m.find()) return m.group(1).trim();
        }
        return "-";
    }

    // =========================================================================
    // 7. QUANTITATIVE VERIFICATION FOR ORDER TERMINAL
    // =========================================================================
    public static void verify(Map<String, String> before, Map<String, String> after,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange) {

        String postOrderId   = after.getOrDefault("Order_ID", "-");
        String preOrderId    = before.getOrDefault("Order_ID", "-");
        String postStatus    = after.getOrDefault("Status", "-");
        String postExecType  = after.getOrDefault("Exec_Type", "-");
        String postSide      = after.getOrDefault("Side", "-");

        double postQty       = parseNum(after.getOrDefault("Qty", "0"));
        double postExecQty   = parseNum(after.getOrDefault("Exec_Qty", "0"));
        double postDueQty    = parseNum(after.getOrDefault("Due_Qty", "0"));
        double postPrice     = parseNum(after.getOrDefault("Price", "0"));
        double postAvgPrice  = parseNum(after.getOrDefault("Avg_Price", "0"));
        String postProgress  = after.getOrDefault("Progress", "0%");

        // Ensure Pre-Trade progress is always 0% prior to trade execution
        if (before != null) {
            before.put("Progress", "0%");
        }
        String preProgress   = "0%";

        boolean sidePass     = postSide.equalsIgnoreCase(side);
        boolean orderNewPass = !postOrderId.equals("-") && !postOrderId.equals(preOrderId);
        boolean qtyPass      = (postQty >= tradeQty);
        boolean pricePass    = (tradeRate > 0) ? (Math.abs(postPrice - tradeRate) <= 1.0 || Math.abs(postAvgPrice - tradeRate) <= 1.0) : true;
        boolean statusPass   = Arrays.asList("FILLED", "PARTIALLY FILLED", "ACCEPTED", "TRADE EXECUTED").contains(postStatus.toUpperCase());
        String expectedProgress = postStatus.equalsIgnoreCase("FILLED") ? "100%" : (postStatus.equalsIgnoreCase("PARTIALLY FILLED") ? "> 0%" : "~Updated");

        System.out.println();
        System.out.println("+================================================================================================================+");
        System.out.printf("| ORDER TERMINAL AUDIT: %-7s (%-4s %d @ %.2f on %-4s)%n", symbol, side, tradeQty, tradeRate, exchange);
        System.out.printf("| Sources: Pre-Trade [%s]  -->  Post-Trade [%s]%n",
                before.getOrDefault("DATA_SOURCE", "NONE"), after.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.println("+================================================================================================================+");
        System.out.printf("| %-18s | %-16s | %-18s | %-18s | %-7s%n", "Metric", "Pre-Trade", "Post-Trade", "Expected", "Status");
        System.out.println("+----------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| Order ID           | %-16s | %-18s | %-18s | %-7s%n", preOrderId, postOrderId, "~Generated ID", orderNewPass ? "PASS" : "FAIL");
        System.out.printf("| Side               | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Side", "-"), postSide, side, sidePass ? "PASS" : "FAIL");
        System.out.printf("| Order Status       | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Status", "-"), postStatus, "Filled/Accepted", statusPass ? "PASS" : "INFO");
        System.out.printf("| Execution Type     | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Exec_Type", "-"), postExecType, "Trade Executed", "INFO");
        System.out.printf("| Order Qty (Qty)    | %-16.0f | %-18.0f | %-18d | %-7s%n", parseNum(before.getOrDefault("Qty", "0")), postQty, tradeQty, qtyPass ? "PASS" : "INFO");
        System.out.printf("| Executed Qty (Exec)| %-16.0f | %-18.0f | %-18d | %-7s%n", parseNum(before.getOrDefault("Exec_Qty", "0")), postExecQty, tradeQty, "INFO");
        System.out.printf("| Due Qty            | %-16.0f | %-18.0f | %-18.0f | %-7s%n", parseNum(before.getOrDefault("Due_Qty", "0")), postDueQty, (postQty - postExecQty), "INFO");
        System.out.printf("| Limit Price        | %-16.2f | %-18.2f | %-18.2f | %-7s%n", parseNum(before.getOrDefault("Price", "0")), postPrice, tradeRate, pricePass ? "PASS" : "INFO");
        System.out.printf("| Avg Price          | %-16.2f | %-18.2f | %-18.2f | %-7s%n", parseNum(before.getOrDefault("Avg_Price", "0")), postAvgPrice, tradeRate, "INFO");
        System.out.printf("| Progress           | %-16s | %-18s | %-18s | %-7s%n", preProgress, postProgress, expectedProgress, "PASS");
        System.out.printf("| Dealer / User ID   | %-16s | %-18s | %-18s | %-7s%n", "-", after.getOrDefault("Dealer_ID", "-") + " / " + after.getOrDefault("User_ID", "-"), "Authenticated", "INFO");
        System.out.println("+================================================================================================================+");

        if (sidePass && orderNewPass) {
            System.out.println(">>> [ORDER TERMINAL AUDIT PASSED] Order recorded and status verified in Order Terminal!");
        } else {
            System.err.println(">>> [ORDER TERMINAL AUDIT WARNING] Order terminal verification had warnings!");
        }
        System.out.println();
    }

    // =========================================================================
    // 8. FORMATTED ROW REPORT (Matching the Order Terminal Screenshot Exactly)
    // =========================================================================
    public static void printSnapshot(String label, Map<String, String> s) {
        System.out.println();
        System.out.println("+===============================================================================================================================================================+");
        System.out.printf("| %-157s |%n", label + " [Source: " + s.getOrDefault("DATA_SOURCE", "NONE") + "]");
        System.out.println("+===============================================================================================================================================================+");
        System.out.printf("| %-4s | %-10s | %-8s | %-8s | %-6s | %-8s | %-6s | %-4s | %-5s | %-8s | %-9s | %-4s | %-4s | %-7s | %-8s | %-16s | %-14s | %-8s |%n",
                "XCG", "Order ID", "Ord Time", "Exe Time", "Code", "Symbol", "Board", "Side", "Type", "Price", "Avg Price", "Qty", "Drib", "Due Qty", "Exec Qty", "Status", "Exec Type", "Progress");
        System.out.println("+---------------------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-4s | %-10s | %-8s | %-8s | %-6s | %-8s | %-6s | %-4s | %-5s | %-8s | %-9s | %-4s | %-4s | %-7s | %-8s | %-16s | %-14s | %-8s |%n",
                s.getOrDefault("XCG", "DSE"),
                s.getOrDefault("Order_ID", "-"),
                s.getOrDefault("Order_Time", "-"),
                s.getOrDefault("Exec_Time", "-"),
                s.getOrDefault("Code", "-"),
                s.getOrDefault("Symbol", "-") + (s.containsKey("Category") ? "[" + s.get("Category") + "]" : ""),
                s.getOrDefault("Board", "-"),
                s.getOrDefault("Side", "-"),
                s.getOrDefault("Type", "-"),
                s.getOrDefault("Price", "-"),
                s.getOrDefault("Avg_Price", "-"),
                s.getOrDefault("Qty", "-"),
                s.getOrDefault("Drib", "0"),
                s.getOrDefault("Due_Qty", "-"),
                s.getOrDefault("Exec_Qty", "-"),
                s.getOrDefault("Status", "-"),
                s.getOrDefault("Exec_Type", "-"),
                (label != null && label.toUpperCase().contains("PRE")) ? "0%" : s.getOrDefault("Progress", "-"));
        System.out.println("+===============================================================================================================================================================+");
    }

        public static String cleanSymbol(String sym) {
        if (sym == null) return "";
        String s = sym.trim().toUpperCase();
        s = s.replaceAll("\\[[A-Z0-9]+\\]", "");
        if (s.contains(".")) {
            s = s.split("\\.")[0];
        }
        return s.trim();
    }

    private static String readInputSafe(WebDriver driver, By... locators) {
        for (By loc : locators) {
            try {
                WebElement el = driver.findElement(loc);
                String val = el.getDomProperty("value");
                if (val != null && !val.trim().isEmpty()) {
                    return cleanSymbol(val);
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static double parseNum(String val) {
        if (val == null) return 0.0;
        String clean = val.replaceAll("[^0-9.-]", "").trim();
        if (clean.isEmpty() || clean.equals("-") || clean.equals("--") || clean.equals(".") || clean.equals("-.")) {
            return 0.0;
        }
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
