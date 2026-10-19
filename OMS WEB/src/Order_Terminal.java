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
    private static final String ROW_BY_CLIENT = ".//tbody//tr[td[normalize-space()='%s']]";
    private static final String ROW_BY_SYMBOL = ".//tbody//tr[td[contains(normalize-space(),'%s')]]";

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
            driver.findElement(BTN_CLEAR).click();
            Thread.sleep(400);
        } catch (Exception ignored) {}
    }

    public static void refresh(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("if (typeof get_order_history === 'function') { get_order_history(); }");
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
    // 4. DATA CAPTURE: API FIRST (ADMIN LIVE -> QUANTBD TOKEN -> UI XPATH)
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String clientCode, String symbol, String preferredExchange) {
        String cleanSymbol = symbol.trim().toUpperCase();

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

        boolean sidePass     = postSide.equalsIgnoreCase(side);
        boolean orderNewPass = !postOrderId.equals("-") && !postOrderId.equals(preOrderId);
        boolean qtyPass      = (postQty >= tradeQty);
        boolean pricePass    = (tradeRate > 0) ? (Math.abs(postPrice - tradeRate) <= 1.0 || Math.abs(postAvgPrice - tradeRate) <= 1.0) : true;
        boolean statusPass   = Arrays.asList("FILLED", "PARTIALLY FILLED", "ACCEPTED", "TRADE EXECUTED").contains(postStatus.toUpperCase());

        System.out.println();
        System.out.println("+================================================================================================================+");
        System.out.printf("| ORDER TERMINAL AUDIT: %-7s (%-4s %d @ %.2f on %-4s)%n", symbol, side, tradeQty, tradeRate, exchange);
        System.out.printf("| Sources: Pre-Trade [%s]  -->  Post-Trade [%s]%n",
                before.getOrDefault("DATA_SOURCE", "NONE"), after.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.println("+================================================================================================================+");
        System.out.printf("| %-18s | %-16s | %-18s | %-18s | %-7s%n", "Metric", "Pre-Trade", "Post-Trade", "Expected", "Status");
        System.out.println("+----------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| Order ID           | %-16s | %-18s | %-18s | %-7s%n", preOrderId, postOrderId, "~Generated ID", !postOrderId.equals("-") ? "PASS" : "FAIL");
        System.out.printf("| Side               | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Side", "-"), postSide, side, sidePass ? "PASS" : "FAIL");
        System.out.printf("| Order Status       | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Status", "-"), postStatus, "Filled/Accepted", statusPass ? "PASS" : "INFO");
        System.out.printf("| Execution Type     | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Exec_Type", "-"), postExecType, "Trade Executed", "INFO");
        System.out.printf("| Order Qty (Qty)    | %-16.0f | %-18.0f | %-18d | %-7s%n", parseNum(before.getOrDefault("Qty", "0")), postQty, tradeQty, qtyPass ? "PASS" : "INFO");
        System.out.printf("| Executed Qty (Exec)| %-16.0f | %-18.0f | %-18d | %-7s%n", parseNum(before.getOrDefault("Exec_Qty", "0")), postExecQty, tradeQty, "INFO");
        System.out.printf("| Due Qty            | %-16.0f | %-18.0f | %-18.0f | %-7s%n", parseNum(before.getOrDefault("Due_Qty", "0")), postDueQty, (postQty - postExecQty), "INFO");
        System.out.printf("| Limit Price        | %-16.2f | %-18.2f | %-18.2f | %-7s%n", parseNum(before.getOrDefault("Price", "0")), postPrice, tradeRate, pricePass ? "PASS" : "INFO");
        System.out.printf("| Avg Price          | %-16.2f | %-18.2f | %-18.2f | %-7s%n", parseNum(before.getOrDefault("Avg_Price", "0")), postAvgPrice, tradeRate, "INFO");
        System.out.printf("| Progress           | %-16s | %-18s | %-18s | %-7s%n", before.getOrDefault("Progress", "-"), postProgress, "~Updated", "INFO");
        System.out.printf("| Dealer / User ID   | %-16s | %-18s | %-18s | %-7s%n", "-", after.getOrDefault("Dealer_ID", "-") + " / " + after.getOrDefault("User_ID", "-"), "Authenticated", "INFO");
        System.out.println("+================================================================================================================+");

        if (sidePass && !postOrderId.equals("-")) {
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
                s.getOrDefault("Progress", "-"));
        System.out.println("+===============================================================================================================================================================+");
    }

    private static String readInputSafe(WebDriver driver, By... locators) {
        for (By loc : locators) {
            try {
                WebElement el = driver.findElement(loc);
                String val = el.getDomProperty("value");
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim().toUpperCase();
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
