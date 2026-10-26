
import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Order_Summary {

    // =========================================================================
    // 1. ENDPOINTS & SELECTORS (Matched to your screenshot)
    // =========================================================================
    private static final String API_URL_TEMPLATE = "https://omsapi.quantbd.com/portfolio/order-summary/%s?exchange=%s";
    private static final String[] EXCHANGES = {"DSE", "CSE"};

    // Navigation Tabs
    private static final By ORDER_SUMMARY_TAB = By.xpath("//div[contains(@class,'lm_tab') and contains(.,'Order Summary')] | //a[normalize-space()='Order Summary'] | //div[@title='Order Summary']");
    private static final By TRADE_TAB         = By.xpath("//div[contains(@class,'lm_tab') and contains(.,'Trade')] | //i[contains(@class,'fa-exchange')] | //a[normalize-space()='Trade']");

    // Filter Inputs (Top bar in your screenshot)
    private static final By INPUT_CLIENT_FILTER = By.xpath("//div[contains(@class,'order-summary') or contains(.,'Order Summary')]//input[1] | //input[@id='summary_client_code']");
    private static final By INPUT_SEARCH_SYMBOL = By.xpath("//input[@placeholder='Search Symbol' or contains(@id,'search_symbol')]");
    private static final By BTN_CLEAR           = By.xpath("//button[normalize-space()='Clear' or contains(@class,'btn-danger')]");
    private static final By REFRESH_BTN         = By.xpath("//div[contains(@class,'order-summary')]//i[contains(@class,'refresh')] | //button[contains(@id,'refresh')]");

    // Tables
    private static final By DETAIL_TABLE        = By.xpath("//table[thead//th[contains(.,'B Qty') or contains(.,'Total Ordr')]] | //div[contains(@class,'order-summary')]//table[last()]");
    private static final By SUMMARY_SIDE_TABLE  = By.xpath("//table[thead//th[contains(.,'Ord Qty') or contains(.,'Exec Value')]] | //div[contains(@class,'order-summary')]//table[1]");
    private static final By DETAIL_TABLE_HEADERS= By.xpath(".//thead//th");
    private static final String SYMBOL_ROW_XPATH = ".//tbody//tr[td[normalize-space()='%s']]";

    // =========================================================================
    // 2. MAIN ENTRY POINT (Matching your runner's signature)
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=======================================================");
        System.out.println("            ORDER SUMMARY DATA VIEWER                  ");
        System.out.println("=======================================================");

        // Auto-detect symbol & client from Trade page inputs so you don't re-type
        String symbol = readInputSafe(driver, By.id("order_instrument"), By.id("instrument"), By.id("marketwatch_symbol"));
        String clientCode = readInputSafe(driver, By.id("order_client_code"));
        String exchange = "DSE";

        if (clientCode.isEmpty()) clientCode = "23986";
        if (symbol.isEmpty()) {
            System.out.print("Enter Symbol (e.g. GP, ACFL): ");
            symbol = sc.nextLine().trim().toUpperCase();
        }

        System.out.printf("[SUMMARY] Auto-detected: Client: %s | Symbol: %s | Exchange: %s%n", clientCode, symbol, exchange);

        // Navigate to Order Summary Tab
        navigateToOrderSummary(driver);
        filterBySymbol(driver, symbol);
        filterOrderSummary(driver, clientCode, symbol);

        Map<String, String> snapshot = getSnapshot(driver, clientCode, symbol, exchange);
        printSnapshot("ORDER SUMMARY FOR " + symbol, snapshot);
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & FILTER HELPERS
    // =========================================================================
    public static void navigateToOrderSummary(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
            WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(ORDER_SUMMARY_TAB));
            tab.click();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("[NAV] Order Summary tab already active or clicked.");
        }
    }

    public static void navigateToTrade(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
            WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(TRADE_TAB));
            tab.click();
            Thread.sleep(1000);
        } catch (Exception ignored) {}
    }

    public static void filterBySymbol(WebDriver driver, String symbol) {
        try {
            WebElement searchBox = driver.findElement(INPUT_SEARCH_SYMBOL);
            searchBox.clear();
            searchBox.sendKeys(symbol);
            searchBox.sendKeys(Keys.ENTER);
            Thread.sleep(600);
        } catch (Exception ignored) {}
    }
    
    public static void filterOrderSummary(WebDriver driver, String clientCode, String symbol) {
        try {
            // 1. Enter Client Code in the left box (if provided)
            if (clientCode != null && !clientCode.trim().isEmpty()) {
                WebElement clientBox = driver.findElement(INPUT_CLIENT_FILTER);
                clientBox.clear();
                clientBox.sendKeys(clientCode);
                clientBox.sendKeys(Keys.ENTER);
            }
            // 2. Enter Symbol in the middle Search box
            if (symbol != null && !symbol.trim().isEmpty()) {
                WebElement searchBox = driver.findElement(INPUT_SEARCH_SYMBOL);
                searchBox.clear();
                searchBox.sendKeys(symbol);
                searchBox.sendKeys(Keys.ENTER);
            }
            Thread.sleep(600);
        } catch (Exception ignored) {}
    }

    public static void clearFilter(WebDriver driver) {
        try {
            driver.findElement(BTN_CLEAR).click();
            Thread.sleep(500);
        } catch (Exception ignored) {}
    }

    public static void refresh(WebDriver driver) {
        try {
            driver.findElement(REFRESH_BTN).click();
            Thread.sleep(1500);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 4. DATA CAPTURE: API FIRST (ADMIN LIVE -> QUANTBD TOKEN -> UI XPATH)
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String clientCode, String symbol, String preferredExchange) {
        String cleanSymbol = cleanSymbol(symbol);

        // Tier 1: Admin Live Orders API (/live_orders/order_summary_view/)
        try {
            String adminUrl = String.format("/live_orders/order_summary_view/?page=1&per_page=50&client_code=%s&symbol_code=%s", clientCode, cleanSymbol);
            String json = executeBrowserFetch(driver, adminUrl);
            Map<String, String> adminData = parseOrderSummaryJson(json, cleanSymbol);
            if (!adminData.isEmpty()) {
                adminData.put("DATA_SOURCE", "API (Admin Live)");
                System.out.printf("[SUCCESS: API] Captured Order Summary for %s from Admin Live API.%n", cleanSymbol);
                return adminData;
            }
        } catch (Exception e) {
            System.out.printf("[WARN: Admin API] Order Summary query failed: %s%n", e.getMessage());
        }

        // Tier 2: QuantBD Microservice Token API (DSE + CSE)
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
                    System.out.printf("[SUCCESS: API] Captured Order Summary for %s from %s Token API.%n", cleanSymbol, ex);
                    return data;
                }
            } catch (Exception ignored) {}
        }

        // Tier 3 Fallback: DOM Table Scraping (Exact tables in your screenshot)
        System.out.println("[FALLBACK: UI] API unavailable. Extracting from Order Summary DOM tables...");
        Map<String, String> uiData = fetchFromUiTables(driver, cleanSymbol);
        uiData.put("DATA_SOURCE", "UI_XPATH (" + preferredExchange + ")");
        return uiData;
    }

    // =========================================================================
    // 5. UI TABLE SCRAPING (Top Summary + Bottom Detailed Rows)
    // =========================================================================
    private static Map<String, String> fetchFromUiTables(WebDriver driver, String symbol) {
        Map<String, String> data = new LinkedHashMap<>();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // 1. Scrape Bottom Detail Table (Client, Symbol, B Qty, S Qty, B Ordr, S Ordr, Total Ordr, etc.)
                WebElement detailTable = wait.until(ExpectedConditions.visibilityOfElementLocated(DETAIL_TABLE));
                Map<String, Integer> headerMap = getHeaderMap(detailTable);

                String rowPath = String.format(SYMBOL_ROW_XPATH, symbol);
                List<WebElement> rows = detailTable.findElements(By.xpath(rowPath));

                if (!rows.isEmpty()) {
                    List<WebElement> cells = rows.get(0).findElements(By.xpath("./td"));
                    for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                        if (entry.getValue() < cells.size()) {
                            data.put(entry.getKey(), cells.get(entry.getValue()).getText().trim());
                        }
                    }
                }

                // 2. Scrape Top Side Breakdown (Buy, Sell, Total, Net)
                try {
                    WebElement sideTable = driver.findElement(SUMMARY_SIDE_TABLE);
                    scrapeSideRow(sideTable, "Buy", data);
                    scrapeSideRow(sideTable, "Sell", data);
                    scrapeSideRow(sideTable, "Total", data);
                } catch (Exception ignored) {}

                return data;
            } catch (StaleElementReferenceException e) {
                if (attempt == 3) throw e;
            }
        }
        return data;
    }

    private static void scrapeSideRow(WebElement table, String sideName, Map<String, String> out) {
        try {
            WebElement row = table.findElement(By.xpath(".//tr[td[normalize-space()='" + sideName + "']]"));
            List<WebElement> cells = row.findElements(By.xpath("./td"));
            if (cells.size() >= 5) {
                out.put(sideName + "_Orders", cells.get(1).getText().trim());
                out.put(sideName + "_Ord_Qty", cells.get(2).getText().trim());
                out.put(sideName + "_Exec_Qty", cells.get(3).getText().trim());
                out.put(sideName + "_Exec_Value", cells.get(4).getText().trim());
            }
        } catch (Exception ignored) {}
    }

    private static Map<String, Integer> getHeaderMap(WebElement table) {
        Map<String, Integer> map = new LinkedHashMap<>();
        List<WebElement> headers = table.findElements(DETAIL_TABLE_HEADERS);
        for (int i = 0; i < headers.size(); i++) {
            String text = headers.get(i).getText().trim();
            if (!text.isEmpty()) map.put(text, i);
        }
        return map;
    }

    // =========================================================================
    // 6. API CLIENT (Fallback / Direct API)
    // =========================================================================
    private static Map<String, String> fetchFromApi(WebDriver driver, String urlStr, String targetSymbol) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
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
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String token = (String) js.executeScript("return window.localStorage.getItem('token') || window.localStorage.getItem('access_token');");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
            }
        } catch (Exception ignored) {}
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) return Collections.emptyMap();
        StringBuilder jsonBuilder = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        String json = jsonBuilder.toString();
        Pattern p = Pattern.compile("\\{[^{}]*\"symbol\"\\s*:\\s*\"" + targetSymbol + "\"[^{}]*\\}", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (!m.find()) return Collections.emptyMap();
        String block = m.group(0);
        result.put("Symbol", targetSymbol);
        result.put("B Qty", getJsonVal(block, "b_qty", "buy_qty", "bQty"));
        result.put("S Qty", getJsonVal(block, "s_qty", "sell_qty", "sQty"));
        result.put("B Ordr", getJsonVal(block, "b_ordr", "buy_orders", "bOrdr"));
        result.put("S Ordr", getJsonVal(block, "s_ordr", "sell_orders", "sOrdr"));
        result.put("Total Ordr", getJsonVal(block, "total_ordr", "total_orders"));
        result.put("B Amount", getJsonVal(block, "b_amount", "buy_amount"));
        result.put("S Amount", getJsonVal(block, "s_amount", "sell_amount"));
        result.put("Net Amount", getJsonVal(block, "net_amount", "netAmount"));
        result.put("Avg Price", getJsonVal(block, "avg_price", "average_price"));
        return result;
    }

    private static String getJsonVal(String block, String... keys) {
        for (String k : keys) {
            Pattern p = Pattern.compile("\"" + k + "\"\\s*:\\s*[\"]?([^\",}]+)[\"]?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(block);
            if (m.find()) return m.group(1).trim();
        }
        return "0";
    }

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

    private static Map<String, String> parseOrderSummaryJson(String json, String targetSymbol) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("\"code\":200")) return result;

        result.put("Symbol", targetSymbol);

        Pattern symRowPattern = Pattern.compile("\\{[^{}]*\"symbol\"\\s*:\\s*\"" + targetSymbol + "\"[^{}]*\\}", Pattern.CASE_INSENSITIVE);
        Matcher m = symRowPattern.matcher(json);
        if (m.find()) {
            String block = m.group(0);
            result.put("B Qty", getJsonVal(block, "buy_order_qty", "buy_qty", "b_qty"));
            result.put("S Qty", getJsonVal(block, "sell_order_qty", "sell_qty", "s_qty"));
            result.put("B Ordr", getJsonVal(block, "buy_order", "buy_orders", "b_ordr"));
            result.put("S Ordr", getJsonVal(block, "sell_order", "sell_orders", "s_ordr"));
            result.put("Total Ordr", getJsonVal(block, "total_order", "total_orders", "total_ordr"));
            result.put("B Amount", getJsonVal(block, "buy_amount", "b_amount"));
            result.put("S Amount", getJsonVal(block, "sell_amount", "s_amount"));
            result.put("Net Amount", getJsonVal(block, "net_amount"));
            result.put("Avg Price", getJsonVal(block, "total_avg_price", "avg_price", "buy_avg_price"));
            result.put("Buy_Exec_Qty", getJsonVal(block, "buy_exec_qty"));
            result.put("Sell_Exec_Qty", getJsonVal(block, "sell_exec_qty"));
            result.put("Total_Exec_Qty", getJsonVal(block, "total_exec_qty"));
        } else {
            result.put("B Qty", "0");
            result.put("S Qty", "0");
            result.put("B Ordr", "0");
            result.put("S Ordr", "0");
            result.put("Total Ordr", "0");
            result.put("B Amount", "0.00");
            result.put("S Amount", "0.00");
            result.put("Net Amount", "0.00");
            result.put("Avg Price", "0.00");
            result.put("Buy_Exec_Qty", "0");
            result.put("Sell_Exec_Qty", "0");
            result.put("Total_Exec_Qty", "0");
        }

        Pattern totalPattern = Pattern.compile("\"total_summary_data\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher tm = totalPattern.matcher(json);
        if (tm.find()) {
            String totalBlock = tm.group(1);
            result.put("Buy_Orders", getJsonVal(totalBlock, "buy_orders"));
            result.put("Buy_Ord_Qty", getJsonVal(totalBlock, "buy_order_qty"));
            result.put("Buy_Exec_Qty_Total", getJsonVal(totalBlock, "buy_exec_qty"));
            result.put("Buy_Exec_Value", getJsonVal(totalBlock, "buy_value"));
            result.put("Sell_Orders", getJsonVal(totalBlock, "sell_orders"));
            result.put("Sell_Ord_Qty", getJsonVal(totalBlock, "sell_order_qty"));
            result.put("Sell_Exec_Qty_Total", getJsonVal(totalBlock, "sell_exec_qty"));
            result.put("Sell_Exec_Value", getJsonVal(totalBlock, "sell_value"));
            result.put("Total_Orders", getJsonVal(totalBlock, "total_orders"));
            result.put("Total_Ord_Qty", getJsonVal(totalBlock, "total_order_qty"));
            result.put("Total_Exec_Qty_Total", getJsonVal(totalBlock, "total_exec_qty"));
            result.put("Total_Exec_Value", getJsonVal(totalBlock, "total_value"));
        }
        return result;
    }

    // =========================================================================
    // 7. QUANTITATIVE VERIFICATION FOR ORDER SUMMARY
    // =========================================================================
    public static void verify(Map<String, String> before, Map<String, String> after,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange) {
        verify(before, after, symbol, side, tradeQty, tradeRate, exchange, tradeQty, 0);
    }

    public static void verify(Map<String, String> before, Map<String, String> after,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange,
                              int executedQty, int dueQty) {

        boolean isBuy = side.equalsIgnoreCase("BUY");
        int effExecQty = executedQty > 0 ? executedQty : tradeQty;
        int effDueQty = dueQty >= 0 && executedQty > 0 ? dueQty : Math.max(0, tradeQty - effExecQty);

        String qtyKey = isBuy ? "B Qty" : "S Qty";
        String ordrKey = isBuy ? "B Ordr" : "S Ordr";
        String amountKey = isBuy ? "B Amount" : "S Amount";

        double oldQty = parseNum(before.getOrDefault(qtyKey, "0"));
        double newQty = parseNum(after.getOrDefault(qtyKey, "0"));

        double oldOrdr = parseNum(before.getOrDefault(ordrKey, "0"));
        double newOrdr = parseNum(after.getOrDefault(ordrKey, "0"));

        double oldAmount = parseNum(before.getOrDefault(amountKey, "0"));
        double newAmount = parseNum(after.getOrDefault(amountKey, "0"));

        double expectedQty = oldQty + tradeQty;
        double expectedOrdr = oldOrdr + 1;
        double executedValue = effExecQty * tradeRate;
        double expectedAmount = oldAmount + executedValue;

        boolean qtyPass = Double.compare(newQty, expectedQty) == 0;
        boolean ordrPass = Double.compare(newOrdr, expectedOrdr) == 0;
        boolean amountPass = Math.abs(newAmount - expectedAmount) <= 1.0 || effExecQty == 0;

        System.out.println();
        System.out.println("+=========================================================================================+");
        if (effDueQty > 0) {
            System.out.printf("| ORDER SUMMARY AUDIT: %-7s (%-4s %d placed, %d executed @ %.2f on %-4s)%n",
                    symbol, side, tradeQty, effExecQty, tradeRate, exchange);
        } else {
            System.out.printf("| ORDER SUMMARY AUDIT: %-7s (%-4s %d @ %.2f on %-4s)%n", symbol, side, tradeQty, tradeRate, exchange);
        }
        System.out.printf("| Sources: Pre-Trade [%s]  -->  Post-Trade [%s]%n",
                before.getOrDefault("DATA_SOURCE", "NONE"), after.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.println("+=========================================================================================+");
        System.out.printf("| %-15s | %-14s | %-14s | %-14s | %-7s%n", "Metric", "Pre-Trade", "Post-Trade", "Expected", "Status");
        System.out.println("+-----------------------------------------------------------------------------------------+");
        System.out.printf("| %-15s | %-14.0f | %-14.0f | %-14.0f | %-7s%n", qtyKey, oldQty, newQty, expectedQty, qtyPass ? "PASS" : "FAIL");
        System.out.printf("| %-15s | %-14.0f | %-14.0f | %-14.0f | %-7s%n", ordrKey, oldOrdr, newOrdr, expectedOrdr, ordrPass ? "PASS" : "FAIL");
        System.out.printf("| %-15s | %-14.2f | %-14.2f | %-14.2f | %-7s%n", amountKey, oldAmount, newAmount, expectedAmount, amountPass ? "PASS" : "FAIL");
        System.out.printf("| Total Ordr      | %-14s | %-14s | %-14s | %-7s%n", before.getOrDefault("Total Ordr", "-"), after.getOrDefault("Total Ordr", "-"), "~Incremented", "INFO");
        System.out.printf("| Net Amount      | %-14s | %-14s | %-14s | %-7s%n", before.getOrDefault("Net Amount", "-"), after.getOrDefault("Net Amount", "-"), "~Updated", "INFO");
        System.out.println("+=========================================================================================+");

        if (qtyPass && ordrPass) {
            System.out.println(">>> [ORDER SUMMARY AUDIT PASSED] Order Summary correctly updated!");
        } else {
            System.err.println(">>> [ORDER SUMMARY AUDIT FAILED] Order counts or quantities mismatch!");
        }
        System.out.println();
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

    private static void printSnapshot(String label, Map<String, String> snap) {
        String sym = snap.getOrDefault("Symbol", "-");
        System.out.printf("\n--- %s [Source: %s] ---%n", label, snap.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.println("   [INSTRUMENT SUMMARY FOR " + sym + "]");
        System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Buy Orders", snap.getOrDefault("B Ordr", "0"), "Sell Orders", snap.getOrDefault("S Ordr", "0"));
        System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Buy Order Qty", snap.getOrDefault("B Qty", "0"), "Sell Order Qty", snap.getOrDefault("S Qty", "0"));
        System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Buy Exec Qty", snap.getOrDefault("Buy_Exec_Qty", "0"), "Sell Exec Qty", snap.getOrDefault("Sell_Exec_Qty", "0"));
        System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Buy Exec Value", snap.getOrDefault("B Amount", "0.00"), "Sell Exec Value", snap.getOrDefault("S Amount", "0.00"));
        System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Total Orders", snap.getOrDefault("Total Ordr", "0"), "Total Exec Qty", snap.getOrDefault("Total_Exec_Qty", "0"));
        System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Net Exec Value", snap.getOrDefault("Net Amount", "0.00"), "Avg Exec Price", snap.getOrDefault("Avg Price", "0.00"));

        if (snap.containsKey("Buy_Orders") || snap.containsKey("Total_Orders")) {
            System.out.println("   [CLIENT PORTFOLIO-WIDE TOTALS (ALL SYMBOLS)]");
            System.out.printf("   %-20s: %-10s | %-20s: %s%n", "All Buy Orders", snap.getOrDefault("Buy_Orders", "-"), "All Sell Orders", snap.getOrDefault("Sell_Orders", "-"));
            System.out.printf("   %-20s: %-10s | %-20s: %s%n", "All Buy Ord Qty", snap.getOrDefault("Buy_Ord_Qty", "-"), "All Sell Ord Qty", snap.getOrDefault("Sell_Ord_Qty", "-"));
            System.out.printf("   %-20s: %-10s | %-20s: %s%n", "All Buy Exec Qty", snap.getOrDefault("Buy_Exec_Qty_Total", "-"), "All Sell Exec Qty", snap.getOrDefault("Sell_Exec_Qty_Total", "-"));
            System.out.printf("   %-20s: %-10s | %-20s: %s%n", "All Buy Exec Value", snap.getOrDefault("Buy_Exec_Value", "-"), "All Sell Exec Value", snap.getOrDefault("Sell_Exec_Value", "-"));
            System.out.printf("   %-20s: %-10s | %-20s: %s%n", "Total Client Orders", snap.getOrDefault("Total_Orders", "-"), "Total Client Value", snap.getOrDefault("Total_Exec_Value", "-"));
        }
    }
}
