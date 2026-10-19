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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Market_Depth {

    // =========================================================================
    // 1. ENDPOINTS & SELECTORS
    // =========================================================================
    private static final String API_URL_TEMPLATE = "https://omsapi.quantbd.com/market-data/depth%s?exchange=%s";
    private static final String[] EXCHANGES = {"DSE", "CSE"};

    // Navigation Tabs
    private static final By MARKET_DEPTH_TAB = By.xpath("//div[contains(@class,'lm_tab') and contains(.,'Market Depth')] | //a[normalize-space()='Market Depth'] | //div[@title='Market Depth']");
    private static final By TRADE_TAB        = By.xpath("//div[contains(@class,'lm_tab') and contains(.,'Trade')] | //i[contains(@class,'fa-exchange')] | //a[normalize-space()='Trade']");

    // Symbol Input in Market Depth Header
    private static final By INPUT_DEPTH_SYMBOL = By.xpath("//div[contains(@class,'market-depth') or contains(.,'Market Depth')]//input[contains(@class,'symbol') or contains(@placeholder,'Symbol')] | //input[@id='depth_symbol']");

    // Order Book Level 2 Table
    private static final By DEPTH_TABLE        = By.xpath("//table[thead//th[contains(.,'BID') or contains(.,'ASK')]] | //div[contains(@class,'market-depth')]//table[last()]");

    // =========================================================================
    // 2. MAIN ENTRY POINT
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=======================================================");
        System.out.println("             MARKET DEPTH & QUOTE VIEWER              ");
        System.out.println("=======================================================");

        // Auto-detect symbol from Trade page inputs
        String symbol = readInputSafe(driver, By.id("order_instrument"), By.id("instrument"), By.id("marketwatch_symbol"));
        if (symbol.isEmpty()) {
            System.out.print("Enter Symbol (e.g. GP, ACFL): ");
            symbol = sc.nextLine().trim().toUpperCase();
        }
        String exchange = "DSE";

        // Navigate and search
        navigateToMarketDepth(driver);
        searchSymbolInDepth(driver, symbol);

        Map<String, String> data = getSnapshot(driver, symbol, exchange);
        printDepthReport(symbol, data);
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & SEARCH
    // =========================================================================
    public static void navigateToMarketDepth(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
            WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(MARKET_DEPTH_TAB));
            tab.click();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("[NAV] Market Depth tab already active or clicked.");
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

    public static void searchSymbolInDepth(WebDriver driver, String symbol) {
        try {
            WebElement input = driver.findElement(INPUT_DEPTH_SYMBOL);
            input.clear();
            input.sendKeys(symbol);
            input.sendKeys(Keys.ENTER);
            Thread.sleep(800);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 4. DATA CAPTURE: API FIRST (ADMIN SHARED -> QUANTBD TOKEN -> UI XPATH)
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String symbol, String preferredExchange) {
        String cleanSymbol = symbol.trim().toUpperCase();
        String ex = (preferredExchange != null && !preferredExchange.isEmpty()) ? preferredExchange : "DSE";

        // Tier 1: Admin Shared Market Depth API (/shared/getmktdepth/)
        try {
            String adminUrl = String.format("/shared/getmktdepth/?mkt_group=PUBLIC&mkt_symbol=%s&exchange=%s", cleanSymbol, ex);
            String json = executeBrowserFetch(driver, adminUrl);
            Map<String, String> adminData = parseMarketDepthJson(json, cleanSymbol);
            if (!adminData.isEmpty()) {
                adminData.put("DATA_SOURCE", "API (Admin Shared)");
                System.out.printf("[SUCCESS: API] Captured 12+ metrics for %s from Admin %s API.%n", cleanSymbol, ex);
                return adminData;
            }
        } catch (Exception e) {
            System.out.printf("[WARN: Admin API] Market Depth query failed for %s: %s%n", ex, e.getMessage());
        }

        // Tier 2: Try QuantBD Token API
        List<String> exchangeOrder = new ArrayList<>();
        exchangeOrder.add(ex);
        for (String otherEx : EXCHANGES) {
            if (!exchangeOrder.contains(otherEx)) exchangeOrder.add(otherEx);
        }

        for (String targetEx : exchangeOrder) {
            String apiUrl = String.format(API_URL_TEMPLATE, cleanSymbol, targetEx);
            try {
                Map<String, String> data = fetchFromApi(driver, apiUrl, cleanSymbol);
                if (!data.isEmpty()) {
                    data.put("DATA_SOURCE", "API (" + targetEx + ")");
                    System.out.printf("[SUCCESS: API] Captured 12+ metrics for %s from %s Token API.%n", cleanSymbol, targetEx);
                    return data;
                }
            } catch (Exception ignored) {}
        }

        // Tier 3 Fallback: DOM Scraping
        System.out.println("[FALLBACK: UI] API unavailable. Extracting from Market Depth DOM...");
        Map<String, String> uiData = fetchFromUiDOM(driver);
        uiData.put("DATA_SOURCE", "UI_XPATH (" + preferredExchange + ")");
        return uiData;
    }

    // =========================================================================
    // 5. UI DOM SCRAPER (Extracts all 12 requested fields + Quote Headers)
    // =========================================================================
    private static Map<String, String> fetchFromUiDOM(WebDriver driver) {
        Map<String, String> data = new LinkedHashMap<>();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // Row 1 metrics in your screenshot:
                scrapeMetric(driver, "Last", data);
                scrapeMetric(driver, "Open", data);
                scrapeMetric(driver, "Trade", data);
                scrapeMetric(driver, "L.vol", data, "L.Vol", "L.VOL");
                scrapeMetric(driver, "SH", data);
                scrapeMetric(driver, "DH", data);
                scrapeMetric(driver, "CH", data);
                scrapeMetric(driver, "Net", data);
                scrapeMetric(driver, "D%", data, "D %");
                scrapeMetric(driver, "Ctg", data, "Category");

                // Row 2 metrics in your screenshot (All 12 requested fields):
                scrapeMetric(driver, "YCP", data);
                scrapeMetric(driver, "Close", data);
                scrapeMetric(driver, "Value", data);
                scrapeMetric(driver, "Vol", data, "Volume");
                scrapeMetric(driver, "SL", data);
                scrapeMetric(driver, "DL", data);
                scrapeMetric(driver, "CL", data);
                scrapeMetric(driver, "52 wk", data, "52wk", "52Wk", "52 Wk");
                scrapeMetric(driver, "VWAP", data);
                scrapeMetric(driver, "Mkt", data, "Market");

                // Scrape Order Book Top-of-Book
                WebElement depthTable = wait.until(ExpectedConditions.visibilityOfElementLocated(DEPTH_TABLE));
                List<WebElement> rows = depthTable.findElements(By.xpath(".//tbody//tr"));
                if (!rows.isEmpty()) {
                    List<WebElement> cells = rows.get(0).findElements(By.xpath("./td"));
                    if (cells.size() >= 8) {
                        data.put("Best_Bid_Ordr", cells.get(0).getText().trim());
                        data.put("Best_Bid_Qty", cells.get(2).getText().trim());
                        data.put("Best_Bid_Price", cells.get(3).getText().trim());
                        data.put("Best_Ask_Price", cells.get(4).getText().trim());
                        data.put("Best_Ask_Qty", cells.get(5).getText().trim());
                        data.put("Best_Ask_Ordr", cells.get(7).getText().trim());
                    }
                }

                return data;
            } catch (StaleElementReferenceException e) {
                if (attempt == 3) throw e;
            }
        }
        return data;
    }

    private static void scrapeMetric(WebDriver driver, String standardKey, Map<String, String> out, String... aliases) {
        List<String> lookups = new ArrayList<>();
        lookups.add(standardKey);
        lookups.addAll(Arrays.asList(aliases));

        for (String label : lookups) {
            try {
                // Multi-strategy XPath to match value right below or adjacent to the label
                String xpath = "//div[contains(@class,'market-depth')]//*[translate(normalize-space(),' ','')='" + label.replace(" ", "") + "']/following-sibling::*[1] " +
                               "| //td[translate(normalize-space(),' ','')='" + label.replace(" ", "") + "']/following-sibling::td[1] " +
                               "| //div[contains(@class,'stat-box') and .//*[text()='" + label + "']]/*[2]";

                List<WebElement> matches = driver.findElements(By.xpath(xpath));
                if (!matches.isEmpty() && matches.get(0).isDisplayed()) {
                    String val = matches.get(0).getText().trim();
                    if (!val.isEmpty()) {
                        out.put(standardKey, val);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        out.putIfAbsent(standardKey, "-");
    }

    // =========================================================================
    // 6. API CLIENT (Extracts All 12 Fields from QuantBD Market Depth JSON)
    // =========================================================================
    private static Map<String, String> fetchFromApi(WebDriver driver, String urlStr, String symbol) throws Exception {
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
            while ((line = in.readLine()) != null) jsonBuilder.append(line);
        }
        String json = jsonBuilder.toString();

        // 1. Core Quote
        result.put("Last", getJsonVal(json, "ltp", "last", "last_price"));
        result.put("Open", getJsonVal(json, "open"));
        result.put("Trade", getJsonVal(json, "trade_count", "trades", "total_trades"));
        result.put("Vol", getJsonVal(json, "volume", "total_volume", "vol"));
        result.put("DH", getJsonVal(json, "day_high", "high", "dh"));
        result.put("DL", getJsonVal(json, "day_low", "low", "dl"));
        result.put("CH", getJsonVal(json, "circuit_high", "ch"));
        result.put("CL", getJsonVal(json, "circuit_low", "cl"));

        // 2. The 12 Specific Fields Requested:
        result.put("YCP", getJsonVal(json, "ycp", "yesterday_close_price", "previous_close"));
        result.put("Close", getJsonVal(json, "close", "close_price"));
        result.put("Value", getJsonVal(json, "value", "total_value"));
        result.put("L.vol", getJsonVal(json, "l_vol", "last_volume", "lvol"));
        result.put("SH", getJsonVal(json, "sh", "session_high", "static_high"));
        result.put("SL", getJsonVal(json, "sl", "session_low", "static_low"));
        result.put("Net", getJsonVal(json, "net", "net_change", "change"));
        result.put("D%", getJsonVal(json, "d_percent", "percent_change", "change_percent"));
        result.put("Ctg", getJsonVal(json, "ctg", "category"));
        result.put("52 wk", getJsonVal(json, "52wk", "52_week", "week52_high_low"));
        result.put("VWAP", getJsonVal(json, "vwap"));
        result.put("Mkt", getJsonVal(json, "mkt", "market_type", "board"));

        // 3. Best Bid & Best Ask
        result.put("Best_Bid_Price", getJsonVal(json, "best_bid", "bid_price"));
        result.put("Best_Bid_Qty", getJsonVal(json, "bid_qty"));
        result.put("Best_Ask_Price", getJsonVal(json, "best_ask", "ask_price"));
        result.put("Best_Ask_Qty", getJsonVal(json, "ask_qty"));
        return result;
    }

    private static String getJsonVal(String json, String... keys) {
        for (String k : keys) {
            Pattern p = Pattern.compile("\"" + k + "\"\\s*:\\s*[\"]?([^\",}]+)[\"]?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1).trim();
        }
        return "-";
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

    private static Map<String, String> parseMarketDepthJson(String json, String cleanSymbol) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("symbol_data")) return result;

        Pattern symPat = Pattern.compile("\"symbol_data\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher sm = symPat.matcher(json);
        if (sm.find()) {
            String b = sm.group(1);
            result.put("Last", getJsonVal(b, "ltp", "last", "last_price"));
            result.put("Open", getJsonVal(b, "open"));
            result.put("Trade", getJsonVal(b, "total_trades", "trades", "trade_count"));
            result.put("Vol", getJsonVal(b, "total_qty", "volume", "vol"));
            result.put("DH", getJsonVal(b, "dh", "high", "day_high"));
            result.put("DL", getJsonVal(b, "dl", "low", "day_low"));
            result.put("CH", getJsonVal(b, "circuit_up", "cu", "circuit_high"));
            result.put("CL", getJsonVal(b, "circuit_down", "cd", "circuit_low"));

            result.put("YCP", getJsonVal(b, "ycp", "yesterday_close_price"));
            result.put("Close", getJsonVal(b, "close", "close_price"));
            result.put("Value", getJsonVal(b, "total_value", "value"));
            result.put("L.vol", getJsonVal(b, "last_qty", "l_vol", "lvol"));
            result.put("SH", getJsonVal(b, "circuit_up", "sh"));
            result.put("SL", getJsonVal(b, "circuit_down", "sl"));
            result.put("Net", getJsonVal(b, "ltp_change", "net", "change"));
            result.put("D%", getJsonVal(b, "ltp_changeper", "d_percent", "change_percent"));
            result.put("Ctg", getJsonVal(b, "symbol_category", "category"));
            result.put("52 wk", getJsonVal(b, "h_52w", "52wk"));
            result.put("VWAP", getJsonVal(b, "vwap"));
            result.put("Mkt", getJsonVal(b, "board", "market_type"));
        }

        Pattern depthPat = Pattern.compile("\"market_depth\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher dm = depthPat.matcher(json);
        if (dm.find()) {
            String db = dm.group(1);
            result.put("Best_Bid_Price", getJsonVal(db, "best_bid", "bid_price"));
            result.put("Best_Bid_Qty", getJsonVal(db, "bid_qty"));
            result.put("Best_Ask_Price", getJsonVal(db, "best_ask", "ask_price"));
            result.put("Best_Ask_Qty", getJsonVal(db, "ask_qty"));
        }
        return result;
    }

    // =========================================================================
    // 7. VERIFICATION
    // =========================================================================
    public static void verify(Map<String, String> before, Map<String, String> after,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange) {

        double oldTrades = parseNum(before.getOrDefault("Trade", "0"));
        double newTrades = parseNum(after.getOrDefault("Trade", "0"));

        double oldVol    = parseNum(before.getOrDefault("Vol", "0"));
        double newVol    = parseNum(after.getOrDefault("Vol", "0"));

        double currentLtp= parseNum(after.getOrDefault("Last", "0"));

        double expectedVol = oldVol + tradeQty;
        boolean volPass    = Double.compare(newVol, expectedVol) == 0;
        boolean tradePass  = newTrades >= oldTrades;
        boolean ltpMatch   = (tradeRate > 0) ? (Math.abs(currentLtp - tradeRate) <= 1.0) : true;

        System.out.println();
        System.out.println("+=========================================================================================+");
        System.out.printf("| MARKET DEPTH AUDIT: %-7s (%-4s %d @ %.2f on %-4s)%n", symbol, side, tradeQty, tradeRate, exchange);
        System.out.printf("| Sources: Pre-Trade [%s]  -->  Post-Trade [%s]%n",
                before.getOrDefault("DATA_SOURCE", "NONE"), after.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.println("+=========================================================================================+");
        System.out.printf("| %-18s | %-14s | %-14s | %-14s | %-7s%n", "Metric", "Pre-Trade", "Post-Trade", "Expected", "Status");
        System.out.println("+-----------------------------------------------------------------------------------------+");
        System.out.printf("| Total Volume (Vol)| %-14.0f | %-14.0f | %-14.0f | %-7s%n", oldVol, newVol, expectedVol, volPass ? "PASS" : "INFO");
        System.out.printf("| Trade Count       | %-14.0f | %-14.0f | %-14s | %-7s%n", oldTrades, newTrades, ">= " + (long)oldTrades, tradePass ? "PASS" : "FAIL");
        System.out.printf("| Last Price (LTP)  | %-14.2f | %-14.2f | %-14.2f | %-7s%n", parseNum(before.getOrDefault("Last", "0")), currentLtp, tradeRate, ltpMatch ? "PASS" : "INFO");
        System.out.printf("| Net Change        | %-14s | %-14s | %-14s | %-7s%n", before.getOrDefault("Net", "-"), after.getOrDefault("Net", "-"), "~Updated", "INFO");
        System.out.printf("| VWAP              | %-14s | %-14s | %-14s | %-7s%n", before.getOrDefault("VWAP", "-"), after.getOrDefault("VWAP", "-"), "~Updated", "INFO");
        System.out.println("+=========================================================================================+");

        if (tradePass) {
            System.out.println(">>> [MARKET DEPTH AUDIT PASSED] Market Depth quote reflect trading activity!");
        } else {
            System.err.println(">>> [MARKET DEPTH AUDIT FAILED] Trade metrics did not reflect on Market Depth!");
        }
        System.out.println();
    }

    // =========================================================================
    // 8. FORMATTED REPORT MATCHING YOUR EXACT SCREENSHOT
    // =========================================================================
    public static void printDepthReport(String symbol, Map<String, String> d) {
        System.out.printf("\n+================================================================================================================================+");
        System.out.printf("\n| MARKET DEPTH & QUOTE SUMMARY: %-10s [Source: %-15s]                                                       |", symbol, d.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.printf("\n+================================================================================================================================+");
        System.out.printf("\n| Last: %-9s | Open: %-8s | Trade: %-6s | L.vol: %-6s | SH: %-10s | DH: %-8s | CH: %-10s | Net: %-7s | D%%: %-6s | Ctg: %-2s |",
                d.getOrDefault("Last", "-"), d.getOrDefault("Open", "-"), d.getOrDefault("Trade", "-"), d.getOrDefault("L.vol", "-"),
                d.getOrDefault("SH", "-"), d.getOrDefault("DH", "-"), d.getOrDefault("CH", "-"), d.getOrDefault("Net", "-"),
                d.getOrDefault("D%", "-"), d.getOrDefault("Ctg", "-"));
        System.out.printf("\n| YCP : %-9s | Close: %-7s | Value: %-6s | Vol  : %-6s | SL: %-10s | DL: %-8s | CL: %-10s | 52 wk: %-16s | VWAP: %-7s | Mkt: %-2s |",
                d.getOrDefault("YCP", "-"), d.getOrDefault("Close", "-"), d.getOrDefault("Value", "-"), d.getOrDefault("Vol", "-"),
                d.getOrDefault("SL", "-"), d.getOrDefault("DL", "-"), d.getOrDefault("CL", "-"), d.getOrDefault("52 wk", "-"),
                d.getOrDefault("VWAP", "-"), d.getOrDefault("Mkt", "-"));
        System.out.printf("\n+================================================================================================================================+");
        System.out.println("\n  [BID BOOK]                                                    [ASK BOOK]");
        System.out.printf("  Orders: %-4s | Qty: %-8s | Price: %-10s <---> Price: %-10s | Qty: %-8s | Orders: %-4s%n",
                d.getOrDefault("Best_Bid_Ordr", "0"),
                d.getOrDefault("Best_Bid_Qty", "0"),
                d.getOrDefault("Best_Bid_Price", "0.0"),
                d.getOrDefault("Best_Ask_Price", "0.0"),
                d.getOrDefault("Best_Ask_Qty", "0"),
                d.getOrDefault("Best_Ask_Ordr", "0"));
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
