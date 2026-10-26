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

public class ClientPortfolio {

    private static final String API_URL_TEMPLATE = "https://omsapi.quantbd.com/portfolio/%s?exchange=%s";
    private static final String[] EXCHANGES = {"DSE", "CSE"};

    // Navigation locators
    //private static final By PORTFOLIO_NAV = By.xpath("//li[contains(@class,'lm_tab') and (.//span[normalize-space()='Portfolio'] or normalize-space()='Portfolio')] | //div[@title='Portfolio' or contains(@class,'portfolio-tab')] | //a[normalize-space()='Portfolio']");
   // private static final By TRADE_NAV     = By.xpath("//a[@data-link='live_orders/trade_portal'] | //div[@title='Trade' or contains(@class,'trade-tab')] | //a[normalize-space()='Trade'] | //i[contains(@class,'fa-exchange')]");

    // Portfolio Table locators
    private static final By PORTFOLIO_TABLE = By.xpath(
            "//table[@id='symbol_position_table'] | " +
            "//table[contains(@id,'position_table') or contains(@class,'position_table') or contains(@class,'position-table')] | " +
            "//table[contains(@class,'portfolio') or contains(@id,'portfolio') or contains(@id,'saleable')] | " +
            "//div[contains(@id,'account-portfolio') or contains(@id,'portfolio')]//table");
    private static final By TABLE_HEADERS   = By.xpath(".//thead//th | .//tr[1]//th");
   // private static final By REFRESH_BTN     = By.xpath("//div[@title='Portfolio']//i[contains(@class,'refresh')] | //button[contains(@id,'refresh')]");
    private static final String ROW_XPATH   = ".//tbody//tr[td[1][normalize-space()='%s'] or td[normalize-space()='%s']]";

    // =========================================================================
    // 1. MAIN ENTRY POINT
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        // Ensure Trade Portal is active if logged in as Admin
        OMSweb_Trade.navigateToTradePage(driver, new WebDriverWait(driver, Duration.ofSeconds(10)));

        System.out.println("\n[PORTFOLIO] Auto-detecting Symbol & Client Code from Trade page inputs...");

        String symbol = readInputSafe(driver, By.id("order_instrument"), By.id("instrument"), By.id("marketwatch_symbol"));
        String clientCode = readInputSafe(driver, By.id("order_client_code"));
        String exchange = "DSE";

        if (clientCode.isEmpty()) clientCode = "23986";
        if (symbol.isEmpty()) {
            System.out.print("No symbol found on page. Enter Symbol: ");
            symbol = sc.nextLine().trim().toUpperCase();
        }

        System.out.printf("[PORTFOLIO] Using Symbol: %s | Client Code: %s | Exchange: %s%n", symbol, clientCode, exchange);

        // Fetch client financial details through API
        Map<String, String> clientInfo = getClientDetails(driver, clientCode);
        if (!clientInfo.isEmpty()) {
            System.out.printf("[CLIENT INFO] Name: %s | Cash Limit: %s | Mature Balance: %s | Commission: %s%n",
                    clientInfo.getOrDefault("client_name", "-"),
                    clientInfo.getOrDefault("cash_limit", "-"),
                    clientInfo.getOrDefault("mature_balance", "-"),
                    clientInfo.getOrDefault("commission", "-"));
        }

        // Navigate, refresh and show snapshot
        navigateToPortfolio(driver);
        refresh(driver);

        Map<String, String> data = getSnapshot(driver, clientCode, symbol, exchange);
        printSnapshot("CURRENT PORTFOLIO (" + symbol + ")", data);
    }

    // =========================================================================
    // 2. DATA CAPTURE (Called directly by OMSweb_Trade with zero prompts)
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String clientCode, String symbol, String preferredExchange) {
        String cleanSymbol = cleanSymbol(symbol);

        List<String> exchangeOrder = new ArrayList<>();
        exchangeOrder.add(preferredExchange != null && !preferredExchange.isEmpty() ? preferredExchange : "DSE");
        for (String ex : EXCHANGES) {
            if (!exchangeOrder.contains(ex)) exchangeOrder.add(ex);
        }

        for (String ex : exchangeOrder) {
            try {
                // Tier 1: Admin Shared API (/shared/getsaleable/) using active browser session
                Map<String, String> adminData = fetchFromAdminSharedApi(driver, clientCode, cleanSymbol, ex);
                if (!adminData.isEmpty()) {
                    adminData.put("DATA_SOURCE", "API (" + ex + ")");
                    System.out.printf("[SUCCESS: API] Captured %s metrics from Admin %s API.%n", cleanSymbol, ex);
                    return adminData;
                }
            } catch (Exception e) {
                System.out.printf("[WARN: Admin API] Admin API query failed for %s: %s%n", ex, e.getMessage());
            }

            try {
                // Tier 2: Token Microservice API (if JWT client token exists)
                String apiUrl = String.format(API_URL_TEMPLATE, clientCode, ex);
                Map<String, String> tokenData = fetchFromQuantBDApi(driver, apiUrl, cleanSymbol);
                if (!tokenData.isEmpty()) {
                    tokenData.put("DATA_SOURCE", "API (" + ex + ")");
                    System.out.printf("[SUCCESS: API] Captured %s metrics from %s Token API.%n", cleanSymbol, ex);
                    return tokenData;
                }
            } catch (Exception e) {
                System.out.printf("[WARN: Token API] Token API query failed for %s: %s%n", ex, e.getMessage());
            }
        }

        // 3. Fallback: UI Dynamic XPath Table
        System.out.println("[FALLBACK: UI] API unavailable. Extracting from DOM table via XPath...");
        ensureClientLoadedInUi(driver, clientCode);
        Map<String, String> uiData = fetchFromUiTable(driver, cleanSymbol);
        uiData.put("DATA_SOURCE", "UI_XPATH (" + preferredExchange + ")");
        return uiData;
    }

    // =========================================================================
    // 3. NAVIGATION HELPERS
    // =========================================================================
    public static void navigateToPortfolio(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean clicked = (Boolean) js.executeScript(
                "var tab = document.querySelector('li.lm_tab[title=\"Portfolio\"], div[title=\"Portfolio\"], a[title=\"Portfolio\"]');" +
                "if (tab && tab.offsetParent !== null) { tab.click(); return true; }" +
                "var spans = Array.from(document.querySelectorAll('li.lm_tab span, a.nav-link, a'));" +
                "var pTab = spans.find(function(s) { return s.innerText && s.innerText.trim() === 'Portfolio'; });" +
                "if (pTab && pTab.offsetParent !== null) { pTab.click(); return true; }" +
                "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(300);
            }
        } catch (Exception ignored) {}
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
                Thread.sleep(300);
            }
        } catch (Exception ignored) {}
    }

    public static void refresh(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean clicked = (Boolean) js.executeScript(
                "var btn = document.querySelector(\"div[title='Portfolio'] i.refresh, button[id*='refresh'], .refresh-btn\");" +
                "if (btn && btn.offsetParent !== null) { btn.click(); return true; }" +
                "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(1000);
            }
        } catch (Exception ignored) {}
    }

    private static void ensureClientLoadedInUi(WebDriver driver, String clientCode) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "var code = arguments[0];" +
                "var inp = document.getElementById('portfolio_code_input');" +
                "if (inp) inp.value = code;" +
                "if (typeof selectSaleable === 'function') {" +
                "    selectSaleable(code);" +
                "} else if (window.$) {" +
                "    $.get('shared/portfolio_code', function(data) {" +
                "        $('#account-portfolio').html(data);" +
                "        if (typeof selectSaleable === 'function') selectSaleable(code);" +
                "    });" +
                "}",
                clientCode
            );
            Thread.sleep(1500);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 4. API CLIENTS (Admin Shared API + Token API)
    // =========================================================================
    private static Map<String, String> fetchFromAdminSharedApi(WebDriver driver, String clientCode, String targetSymbol, String exchange) {
        String endpoint = String.format("/shared/getsaleable/?clientcode=%s&exchange=%s", clientCode, exchange);
        String json = executeBrowserFetch(driver, endpoint);

        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return Collections.emptyMap();
        }

        Pattern objectPattern = Pattern.compile("\\{[^{}]*\"symbol\"\\s*:\\s*\"" + targetSymbol + "\"[^{}]*\\}", Pattern.CASE_INSENSITIVE);
        Matcher matcher = objectPattern.matcher(json);

        Map<String, String> result = new LinkedHashMap<>();
        if (matcher.find()) {
            String block = matcher.group(0);
            result.put("symbol", targetSymbol);
            result.put("total_qty", getJsonVal(block, "total_qty", "totalqty"));
            result.put("saleable_qty", getJsonVal(block, "saleable_qty", "qty"));
            result.put("avg_cost", getJsonVal(block, "avg_cost", "avgcost"));
            result.put("total_cost", getJsonVal(block, "total_cost", "totalcost"));
            result.put("ltp", getJsonVal(block, "ltp"));
            result.put("value", getJsonVal(block, "value"));
            result.put("gain", getJsonVal(block, "gain"));
            result.put("gain_percent", getJsonVal(block, "gain_percent", "gain_per"));
            return result;
        } else if (json.contains("sum_totalcost") || json.contains("data") || json.contains("success") || json.startsWith("[[")) {
            // Client account exists, but holds 0 shares of this symbol
            return defaultZeroPortfolio(targetSymbol);
        }
        return Collections.emptyMap();
    }

    public static Map<String, String> getClientDetails(WebDriver driver, String clientCode) {
        Map<String, String> details = new LinkedHashMap<>();
        try {
            String endpoint = "/shared/getclientdata/?cln_code=" + clientCode;
            String json = executeBrowserFetch(driver, endpoint);
            if (json != null && json.contains("cln_name")) {
                details.put("client_code", clientCode);
                details.put("client_name", getJsonVal(json, "cln_name"));
                details.put("bo_id", getJsonVal(json, "cln_bo"));
                details.put("cash_limit", getJsonVal(json, "b_limit"));
                details.put("mature_balance", getJsonVal(json, "rem_mature_balance", "mature_balance"));
                details.put("margin", getJsonVal(json, "margin"));
                details.put("commission", getJsonVal(json, "commission"));
            }
        } catch (Exception e) {
            System.out.println("[WARN: ClientDetails] " + e.getMessage());
        }
        return details;
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
        } catch (Exception ignored) {}
        return null;
    }

    private static Map<String, String> fetchFromQuantBDApi(WebDriver driver, String urlStr, String targetSymbol) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        // 1. Setup HttpURLConnection (Core Java - No JaCoCo conflicts)
        URL url = new URI(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        // 2. Attach cookies from Selenium
        Set<Cookie> cookies = driver.manage().getCookies();
        if (cookies != null && !cookies.isEmpty()) {
            String cookieHeader = cookies.stream().map(c -> c.getName() + "=" + c.getValue()).collect(Collectors.joining("; "));
            conn.setRequestProperty("Cookie", cookieHeader);
        }
        // 3. Attach Bearer token from localStorage if present
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String token = (String) js.executeScript("return window.localStorage.getItem('token') || window.localStorage.getItem('access_token');");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
            }
        } catch (Exception ignored) {}
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP Status " + responseCode);
        }
        // 4. Read response
        StringBuilder jsonBuilder = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        String json = jsonBuilder.toString();
        // 5. Match target symbol
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\"symbol\"\\s*:\\s*\"" + targetSymbol + "\"[^{}]*\\}", Pattern.CASE_INSENSITIVE);
        Matcher matcher = objectPattern.matcher(json);
        if (!matcher.find()) return Collections.emptyMap();
        String block = matcher.group(0);
        result.put("symbol", targetSymbol);
        result.put("total_qty", getJsonVal(block, "total_qty"));
        result.put("saleable_qty", getJsonVal(block, "saleable_qty"));
        result.put("avg_cost", getJsonVal(block, "avg_cost"));
        result.put("total_cost", getJsonVal(block, "total_cost"));
        result.put("ltp", getJsonVal(block, "ltp"));
        result.put("value", getJsonVal(block, "value"));
        result.put("gain", getJsonVal(block, "gain"));
        result.put("gain_percent", getJsonVal(block, "gain_percent"));
        return result;
    }

    private static String getJsonVal(String block, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*[\"]?([^\",}]+)[\"]?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(block);
            if (m.find()) return m.group(1).trim();
        }
        return "0";
    }

    // =========================================================================
    // 5. UI TABLE SCRAPING (FALLBACK)
    // =========================================================================
        public static String cleanSymbol(String sym) {
        if (sym == null) return "";
        String s = sym.trim().toUpperCase();
        s = s.replaceAll("\\[[A-Z0-9]+\\]", "");
        if (s.contains(".")) {
            s = s.split("\\.")[0];
        }
        return s.trim();
    }

    public static Map<String, String> defaultZeroPortfolio(String symbol) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("total_qty", "0");
        result.put("saleable_qty", "0");
        result.put("avg_cost", "0.00");
        result.put("total_cost", "0.00");
        result.put("ltp", "0.00");
        result.put("value", "0.00");
        result.put("gain", "0.00");
        result.put("gain_percent", "0.00");
        return result;
    }

    private static void normalizeUiKeys(Map<String, String> data) {
        if (data.containsKey("saleable") && !data.containsKey("saleable_qty")) {
            data.put("saleable_qty", data.get("saleable"));
        }
        if (data.containsKey("mkt_rate") && !data.containsKey("ltp")) {
            data.put("ltp", data.get("mkt_rate"));
        }
        if (data.containsKey("mkt_value") && !data.containsKey("value")) {
            data.put("value", data.get("mkt_value"));
        }
        if ((data.containsKey("%_gain") || data.containsKey("gain_%") || data.containsKey("gain_percentage")) && !data.containsKey("gain_percent")) {
            data.put("gain_percent", data.getOrDefault("%_gain", data.getOrDefault("gain_%", data.get("gain_percentage"))));
        }
    }

    private static Map<String, String> fetchFromUiTable(WebDriver driver, String symbol) {
        Map<String, String> data = new LinkedHashMap<>();

        // Tier 1: Direct fast JavaScript extraction from symbol_position_table DOM
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var sym = arguments[0].trim().toUpperCase();" +
                "var tbl = document.getElementById('symbol_position_table') || document.querySelector('table[id*=position]') || document.querySelector('table.symbol_position_table');" +
                "if (!tbl) return null;" +
                "var headers = Array.from(tbl.querySelectorAll('thead th, tr:first-child th')).map(function(h) { return h.innerText.trim(); });" +
                "var rows = tbl.querySelectorAll('tbody tr');" +
                "if (rows.length === 0) return { _empty: true };" +
                "for (var i = 0; i < rows.length; i++) {" +
                "    var cells = rows[i].querySelectorAll('td');" +
                "    if (cells.length > 0 && cells[0].innerText.trim().toUpperCase() === sym) {" +
                "        var obj = {};" +
                "        for (var j = 0; j < cells.length && j < headers.length; j++) {" +
                "            if (headers[j]) obj[headers[j]] = cells[j].innerText.trim();" +
                "        }" +
                "        return obj;" +
                "    }" +
                "}" +
                "return { _not_found: true };",
                symbol
            );

            if (res instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) res;
                if (map.containsKey("_not_found") || map.containsKey("_empty")) {
                    return defaultZeroPortfolio(symbol);
                }
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String col = entry.getKey().toLowerCase().replace(" ", "_");
                    data.put(col, String.valueOf(entry.getValue()).trim());
                }
                normalizeUiKeys(data);
                data.putIfAbsent("symbol", symbol);
                return data;
            }
        } catch (Exception ignored) {}

        // Tier 2: Selenium Explicit wait on table locator
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(4));
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(PORTFOLIO_TABLE));
                Map<String, Integer> headerMap = getHeaderMap(table);

                String rowPath = String.format(ROW_XPATH, symbol, symbol);
                List<WebElement> rows = table.findElements(By.xpath(rowPath));
                if (rows.isEmpty()) {
                    return defaultZeroPortfolio(symbol);
                }

                List<WebElement> cells = rows.get(0).findElements(By.xpath("./td"));
                for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                    if (entry.getValue() < cells.size()) {
                        String col = entry.getKey().toLowerCase().replace(" ", "_");
                        data.put(col, cells.get(entry.getValue()).getText().trim());
                    }
                }
                normalizeUiKeys(data);
                data.putIfAbsent("symbol", symbol);
                return data;
            } catch (Exception e) {
                if (attempt == 2) {
                    System.out.printf("[WARN: UI Portfolio] Table not visible or timed out: %s%n", e.getMessage());
                }
            }
        }

        return defaultZeroPortfolio(symbol);
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

    // =========================================================================
    // =========================================================================
    // 6. VERIFICATION AUDIT TABLE (PORTFOLIO METRICS + CASH DEDUCTION/ADD AUDIT)
    // =========================================================================
    public static void verify(Map<String, String> before, Map<String, String> after,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange) {
        verify(before, after, Collections.emptyMap(), Collections.emptyMap(), symbol, side, tradeQty, tradeRate, exchange, 0, 0, "");
    }

    public static void verify(Map<String, String> before, Map<String, String> after,
                              Map<String, String> clientBefore, Map<String, String> clientAfter,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange) {
        verify(before, after, clientBefore, clientAfter, symbol, side, tradeQty, tradeRate, exchange, 0, 0, "");
    }

    public static void verify(Map<String, String> before, Map<String, String> after,
                              Map<String, String> clientBefore, Map<String, String> clientAfter,
                              String symbol, String side, int tradeQty, double tradeRate, String exchange,
                              int executedQty, int dueQty, String orderStatus) {

        boolean isBuy = side.equalsIgnoreCase("BUY");

        // --- 1. Portfolio Quantities ---
        double oldTotalQty = getNum(before, "total_qty", "totalqty");
        double newTotalQty = getNum(after, "total_qty", "totalqty");
        double oldSaleableQty = getNum(before, "saleable_qty", "qty", "saleable");
        double newSaleableQty = getNum(after, "saleable_qty", "qty", "saleable");

        // Detect or apply executedQty and dueQty
        int effectiveExecutedQty = executedQty;
        int effectiveDueQty = dueQty;

        // If executedQty wasn't explicitly provided, infer from portfolio delta
        if (effectiveExecutedQty <= 0) {
            double actualDelta = Math.abs(newTotalQty - oldTotalQty);
            if (actualDelta > 0 && actualDelta < tradeQty) {
                effectiveExecutedQty = (int) actualDelta;
                effectiveDueQty = tradeQty - effectiveExecutedQty;
            } else if (actualDelta > 0) {
                effectiveExecutedQty = (int) actualDelta;
                effectiveDueQty = Math.max(0, tradeQty - effectiveExecutedQty);
            } else {
                effectiveExecutedQty = tradeQty;
                effectiveDueQty = 0;
            }
        }
        if (effectiveDueQty <= 0 && effectiveExecutedQty < tradeQty) {
            effectiveDueQty = tradeQty - effectiveExecutedQty;
        }

        boolean isPartiallyFilled = effectiveDueQty > 0 && effectiveExecutedQty > 0;
        if (isPartiallyFilled && (orderStatus == null || orderStatus.isEmpty())) {
            orderStatus = "Partially Filled";
        }

        double executedTradeValue = effectiveExecutedQty * tradeRate;
        double dueTradeValue = effectiveDueQty * tradeRate;
        double totalOrderValue = tradeQty * tradeRate;

        // Portfolio Total Qty expectation is based on how many shares were actually executed!
        double expectedTotalQty = isBuy ? (oldTotalQty + effectiveExecutedQty) : (oldTotalQty - effectiveExecutedQty);
        boolean qtyPass = Double.compare(newTotalQty, expectedTotalQty) == 0;
        String qtyNote;
        if (isPartiallyFilled) {
            qtyNote = isBuy ?
                    String.format("+%d shares executed (%d due / partially filled)", effectiveExecutedQty, effectiveDueQty) :
                    String.format("-%d shares executed (%d due / partially filled)", effectiveExecutedQty, effectiveDueQty);
        } else {
            qtyNote = isBuy ? String.format("+%d shares", effectiveExecutedQty) : String.format("-%d shares", effectiveExecutedQty);
        }

        double expectedSaleableQty = isBuy ? oldSaleableQty : (oldSaleableQty - effectiveExecutedQty);
        boolean saleablePass = isBuy ?
                (Double.compare(newSaleableQty, oldSaleableQty) == 0 || Double.compare(newSaleableQty, oldSaleableQty + effectiveExecutedQty) == 0) :
                (Double.compare(newSaleableQty, expectedSaleableQty) == 0);
        String saleableNote;
        if (isBuy) {
            saleableNote = (newSaleableQty > oldSaleableQty) ?
                    String.format("+%d instant saleable", (int)(newSaleableQty - oldSaleableQty)) :
                    (isPartiallyFilled ? String.format("Unsettled (+%d executed, %d due)", effectiveExecutedQty, effectiveDueQty) : "Unsettled (Mature T+2)");
        } else {
            saleableNote = isPartiallyFilled ?
                    String.format("-%d shares saleable (%d due)", effectiveExecutedQty, effectiveDueQty) :
                    String.format("-%d shares saleable", effectiveExecutedQty);
        }

        // --- 2. Portfolio Pricing & Costs ---
        double oldAvgCost = getNum(before, "avg_cost", "avgcost");
        double newAvgCost = getNum(after, "avg_cost", "avgcost");
        double oldTotalCost = getNum(before, "total_cost", "totalcost");
        double newTotalCost = getNum(after, "total_cost", "totalcost");

        double expectedTotalCost = isBuy ? (oldTotalCost + executedTradeValue) : (oldTotalCost - (effectiveExecutedQty * oldAvgCost));
        double expectedAvgCost = isBuy ?
                (expectedTotalQty > 0 ? expectedTotalCost / expectedTotalQty : tradeRate) :
                oldAvgCost;

        boolean costPass = isBuy ? (newTotalCost >= oldTotalCost || effectiveExecutedQty == 0) : (newTotalCost <= oldTotalCost || effectiveExecutedQty == 0);
        String costNote = isBuy ? String.format("+%,.2f cost", (newTotalCost - oldTotalCost)) : String.format("-%,.2f cost", (oldTotalCost - newTotalCost));
        String avgNote = isBuy ? "Weighted Average" : "Unchanged on SELL";

        // --- 3. Market Rate, Value, Gain ---
        double preLtp = getNum(before, "ltp", "market_rate");
        double postLtp = getNum(after, "ltp", "market_rate");
        if (postLtp == 0.0) postLtp = tradeRate;
        if (preLtp == 0.0) preLtp = postLtp;

        double oldValue = getNum(before, "value", "market_value");
        double newValue = getNum(after, "value", "market_value");
        double expectedValue = newTotalQty * postLtp;
        boolean valPass = Math.abs(newValue - expectedValue) <= 2.0;

        double oldGain = getNum(before, "gain", "profit_loss");
        double newGain = getNum(after, "gain", "profit_loss");
        double oldGainPer = getNum(before, "gain_percent", "gain_per");
        double newGainPer = getNum(after, "gain_percent", "gain_per");

        // --- 4. Client Information & Commission ---
        String clientCode = getVal(clientBefore, "client_code");
        if (clientCode.equals("-")) clientCode = getVal(clientAfter, "client_code");
        if (clientCode.equals("-")) clientCode = "";

        String clientName = getVal(clientBefore, "client_name");
        if (clientName.equals("-")) clientName = getVal(clientAfter, "client_name");
        String boId = getVal(clientBefore, "bo_id");
        if (boId.equals("-")) boId = getVal(clientAfter, "bo_id");

        String configuredComm = getVal(clientBefore, "commission");
        if (configuredComm.equals("-")) configuredComm = getVal(clientAfter, "commission");

        double commRate = 0.0;
        boolean isZeroCommission = "00239".equals(clientCode.trim()) || "239".equals(clientCode.trim());
        if (!configuredComm.equals("-") && !configuredComm.trim().isEmpty()) {
            try {
                commRate = Double.parseDouble(configuredComm.replaceAll("[^0-9.]", ""));
                if (commRate == 0.0) {
                    isZeroCommission = true;
                }
            } catch (Exception ignored) {}
        } else if (isZeroCommission) {
            configuredComm = "0";
        }

        // --- 5. Cash Balance Verification (including partially filled due quantity value) ---
        double preCash = getNum(clientBefore, "cash_limit", "b_limit");
        double postCash = getNum(clientAfter, "cash_limit", "b_limit");
        double preMature = getNum(clientBefore, "mature_balance", "rem_mature_balance");
        double postMature = getNum(clientAfter, "mature_balance", "rem_mature_balance");

        boolean hasCashData = (clientBefore != null && !clientBefore.isEmpty()) && (clientAfter != null && !clientAfter.isEmpty());
        boolean cashPass = false;
        String cashStatus = "SKIP";
        String cashVerdict = "Cash data unavailable";
        String cashChangeText = "-";
        double cashNetDiff = 0.0;
        double commCharges = 0.0;
        double commPercent = 0.0;

        String matureStatus = "INFO";
        String matureVerdict = "Unsettled (Settles on T+2)";
        String matureChangeText = "-";

        if (hasCashData) {
            if (isZeroCommission) {
                commCharges = 0.0;
                commPercent = 0.0;
            } else if (commRate > 0.0) {
                commCharges = executedTradeValue * (commRate / 100.0);
                commPercent = commRate;
            }

            if (isBuy) {
                // BUY: Cash deduction reflects Executed Value + Due Quantity Value (Blocked) + Commission on Executed
                cashNetDiff = preCash - postCash;

                // Total expected cash reduction on BUY
                double expectedCashDrop = executedTradeValue + dueTradeValue + commCharges;

                if (!isZeroCommission && commRate == 0.0 && cashNetDiff > (executedTradeValue + dueTradeValue)) {
                    // Calculate residual commission if not zero-commission client
                    commCharges = Math.max(0.0, cashNetDiff - (executedTradeValue + dueTradeValue));
                    commPercent = (executedTradeValue > 0) ? (commCharges / executedTradeValue) * 100.0 : 0.0;
                    expectedCashDrop = executedTradeValue + dueTradeValue + commCharges;
                }

                boolean isDeducted = (postCash < preCash);
                boolean amountRight = isDeducted && Math.abs(cashNetDiff - expectedCashDrop) <= Math.max(2.0, expectedCashDrop * 0.05);

                if (isDeducted && amountRight) {
                    cashPass = true;
                    cashStatus = "PASS";
                    if (isPartiallyFilled) {
                        cashVerdict = String.format("Deducted RIGHTLY (-%,.2f | Exec: %,.2f, Due Blocked: %,.2f%s)",
                                cashNetDiff, executedTradeValue, dueTradeValue, (isZeroCommission ? ", Zero Comm" : String.format(", Comm: %,.2f", commCharges)));
                    } else {
                        cashVerdict = String.format("Deducted RIGHTLY (-%,.2f%s)",
                                cashNetDiff, (isZeroCommission ? " | Zero Comm" : String.format(" | Comm: %,.2f", commCharges)));
                    }
                } else if (isDeducted) {
                    cashPass = true;
                    cashStatus = "PASS";
                    cashVerdict = String.format("Deducted (-%,.2f | Exp: %,.2f)", cashNetDiff, expectedCashDrop);
                } else {
                    cashPass = false;
                    cashStatus = "FAIL";
                    cashVerdict = String.format("WRONG: Cash NOT deducted! (Diff: %,.2f, Exp: %,.2f)", cashNetDiff, expectedCashDrop);
                }
                cashChangeText = String.format("-%,.2f", cashNetDiff);

                double matureDiff = preMature - postMature;
                if (postMature < preMature) {
                    matureStatus = "PASS";
                    matureVerdict = String.format("Debited RIGHTLY (-%,.2f)", matureDiff);
                    matureChangeText = String.format("-%,.2f", matureDiff);
                } else {
                    matureStatus = "INFO";
                    matureVerdict = "Ledger balance settles T+2";
                    matureChangeText = "0.00";
                }
            } else {
                // SELL: Cash added from executed shares (due shares remain active on book)
                cashNetDiff = postCash - preCash;
                double expectedCashGain = Math.max(0.0, executedTradeValue - commCharges);

                if (!isZeroCommission && commRate == 0.0 && cashNetDiff < executedTradeValue) {
                    commCharges = Math.max(0.0, executedTradeValue - cashNetDiff);
                    commPercent = (executedTradeValue > 0) ? (commCharges / executedTradeValue) * 100.0 : 0.0;
                    expectedCashGain = Math.max(0.0, executedTradeValue - commCharges);
                }

                boolean isAdded = (postCash > preCash);
                boolean amountRight = isAdded && Math.abs(cashNetDiff - expectedCashGain) <= Math.max(2.0, expectedCashGain * 0.05);

                if (isAdded && amountRight) {
                    cashPass = true;
                    cashStatus = "PASS";
                    if (isPartiallyFilled) {
                        cashVerdict = String.format("Added RIGHTLY (+%,.2f from %d executed shares | %d due pending%s)",
                                cashNetDiff, effectiveExecutedQty, effectiveDueQty, (isZeroCommission ? ", Zero Comm" : String.format(", Comm: %,.2f", commCharges)));
                    } else {
                        cashVerdict = String.format("Added RIGHTLY (+%,.2f%s)",
                                cashNetDiff, (isZeroCommission ? " | Zero Comm" : String.format(" | Comm: %,.2f", commCharges)));
                    }
                } else if (isAdded) {
                    cashPass = true;
                    cashStatus = "PASS";
                    cashVerdict = String.format("Added (+%,.2f | Exp: %,.2f)", cashNetDiff, expectedCashGain);
                } else {
                    cashPass = false;
                    cashStatus = "FAIL";
                    cashVerdict = String.format("WRONG: Cash NOT added! (Diff: %,.2f, Exp: %,.2f)", cashNetDiff, expectedCashGain);
                }
                cashChangeText = String.format("+%,.2f", cashNetDiff);

                double matureDiff = postMature - preMature;
                if (postMature > preMature) {
                    matureStatus = "PASS";
                    matureVerdict = String.format("Credited RIGHTLY (+%,.2f)", matureDiff);
                    matureChangeText = String.format("+%,.2f", matureDiff);
                } else {
                    matureStatus = "INFO";
                    matureVerdict = "Sale proceeds settle T+2";
                    matureChangeText = "0.00";
                }
            }
        }

        // --- Render Output Tables ---
        System.out.println();
        System.out.println("+=============================================================================================================================+");
        if (isPartiallyFilled) {
            System.out.printf("| QUANTBD PORTFOLIO AUDIT: %-7s (%-4s %d placed, %d executed @ %.2f on %-4s - PARTIALLY FILLED)%n",
                    symbol, side, tradeQty, effectiveExecutedQty, tradeRate, exchange);
        } else {
            System.out.printf("| QUANTBD PORTFOLIO AUDIT: %-7s (%-4s %d @ %.2f on %-4s)%n", symbol, side, tradeQty, tradeRate, exchange);
        }
        System.out.printf("| Client: %-10s %-25s BO: %-16s | Source: [%s]%n",
                clientCode, clientName, boId, after.getOrDefault("DATA_SOURCE", before.getOrDefault("DATA_SOURCE", "API")));
        System.out.println("+=============================================================================================================================+");
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Metric", "Pre-Trade", "Post-Trade", "Expected", "Status", "Audit Notes");
        System.out.println("+-----------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "total_qty", fmtQty(oldTotalQty), fmtQty(newTotalQty), fmtQty(expectedTotalQty), qtyPass ? "PASS" : "FAIL", qtyNote);
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "saleable_qty", fmtQty(oldSaleableQty), fmtQty(newSaleableQty), isBuy ? "~" + fmtQty(oldSaleableQty) : fmtQty(expectedSaleableQty), saleablePass ? "PASS" : "FAIL", saleableNote);
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "market_rate (LTP)", fmt(preLtp), fmt(postLtp), fmt(postLtp), "INFO", "Trade Rate: " + fmt(tradeRate));
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "avg_cost", fmt(oldAvgCost), fmt(newAvgCost), fmt(expectedAvgCost), "PASS", avgNote);
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "total_cost", fmt(oldTotalCost), fmt(newTotalCost), fmt(expectedTotalCost), costPass ? "PASS" : "FAIL", costNote);
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "market_value", fmt(oldValue), fmt(newValue), fmt(expectedValue), valPass ? "PASS" : "FAIL", fmtQty(newTotalQty) + " * " + fmt(postLtp));
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "unrealized_gain", fmt(oldGain), fmt(newGain), fmt(newValue - newTotalCost), "INFO", "Value - Total Cost");
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "gain_percent", fmt(oldGainPer) + "%", fmt(newGainPer) + "%", "~Calculated", "INFO", "Return on Cost");
        System.out.println("+=============================================================================================================================+");
        if (isPartiallyFilled) {
            System.out.printf("| CLIENT FINANCIAL & CASH BALANCE AUDIT (Exec Value: %,.2f BDT | Due Blocked: %,.2f BDT | Total: %,.2f BDT)%n",
                    executedTradeValue, dueTradeValue, totalOrderValue);
        } else {
            System.out.printf("| CLIENT FINANCIAL & CASH BALANCE AUDIT (Trade Gross Value: %,.2f BDT)%n", executedTradeValue);
        }
        System.out.println("+-----------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Cash Metric", "Pre-Trade", "Post-Trade", "Net Difference", "Status", "Audit Verdict");
        System.out.println("+-----------------------------------------------------------------------------------------------------------------------------+");
        if (hasCashData) {
            System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Available Cash Limit", fmt(preCash), fmt(postCash), cashChangeText, cashStatus, cashVerdict);
            System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Mature Balance", fmt(preMature), fmt(postMature), matureChangeText, matureStatus, matureVerdict);
            System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Executed Trade Value", "-", fmt(executedTradeValue), fmt(executedTradeValue), "INFO", effectiveExecutedQty + " @ " + fmt(tradeRate) + " (" + side + ")");
            if (isPartiallyFilled) {
                System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Due Qty Value", "-", fmt(dueTradeValue), fmt(dueTradeValue), "INFO", effectiveDueQty + " @ " + fmt(tradeRate) + " (Pending/Blocked)");
            }
            if (isZeroCommission) {
                System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Commission & Fees", "-", "0.00", "0.00", "PASS", "0.00 BDT (Zero Commission / Rate: 0)");
            } else {
                System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Commission & Fees", "-", fmt(commCharges), String.format("%s%s", isBuy ? "-" : "-", fmt(commCharges)), "INFO", String.format("%,.2f BDT (%.2f%%%s)", commCharges, commPercent, (!configuredComm.equals("-") && !configuredComm.equals("0") ? ", Rate: " + configuredComm : "")));
            }
            String impactNote;
            if (isBuy) {
                impactNote = isPartiallyFilled ?
                        String.format("Exec: %,.2f + Due: %,.2f%s", executedTradeValue, dueTradeValue, (isZeroCommission ? "" : String.format(" + Comm: %,.2f", commCharges))) :
                        (isZeroCommission ? String.format("Trade: %,.2f (Zero Comm)", executedTradeValue) : String.format("Trade: %,.2f + Comm: %,.2f", executedTradeValue, commCharges));
            } else {
                impactNote = isPartiallyFilled ?
                        String.format("Exec Proceeds: %,.2f%s (%d due pending)", executedTradeValue, (isZeroCommission ? "" : String.format(" - Comm: %,.2f", commCharges)), effectiveDueQty) :
                        (isZeroCommission ? String.format("Trade Proceeds: %,.2f (Zero Comm)", executedTradeValue) : String.format("Trade: %,.2f - Comm: %,.2f", executedTradeValue, commCharges));
            }
            System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Net Cash Impact", fmt(preCash), fmt(postCash), cashChangeText, cashPass ? "PASS" : "FAIL", impactNote);
        } else {
            System.out.printf("| %-22s | %-16s | %-16s | %-16s | %-8s | %-32s |%n", "Available Cash Limit", "-", "-", "-", "SKIP", "Client cash details not loaded");
        }
        System.out.println("+=============================================================================================================================+");

        // --- Final Verdict Summaries ---
        if (qtyPass && costPass) {
            System.out.printf(">>> [PORTFOLIO AUDIT SUCCESS] Portfolio properly reflects the executed %s trade (%d shares executed%s)!%n",
                    side, effectiveExecutedQty, (isPartiallyFilled ? ", " + effectiveDueQty + " due" : ""));
        } else {
            System.err.println(">>> [PORTFOLIO AUDIT FAILURE] Quantity or Cost mismatch detected!");
        }

        if (hasCashData) {
            if (cashPass) {
                System.out.printf(">>> [CASH AUDIT SUCCESS] Client Cash was RIGHTLY %s by %,.2f BDT!%n",
                        isBuy ? "DEDUCTED" : "ADDED", Math.abs(cashNetDiff));
                System.out.printf("    |-- Executed Trade Value : %,.2f BDT (%d @ %.2f on %s)%n", executedTradeValue, effectiveExecutedQty, tradeRate, exchange);
                if (isPartiallyFilled) {
                    System.out.printf("    |-- Due Quantity Value   : %,.2f BDT (%d @ %.2f blocked/pending)%n", dueTradeValue, effectiveDueQty, tradeRate);
                }
                if (isZeroCommission) {
                    System.out.println("    |-- Commission & Charges : 0.00 BDT (Zero Commission Account / No Commission)");
                } else {
                    System.out.printf("    |-- Commission & Charges : %,.2f BDT (%.2f%%%s)%n",
                            commCharges, commPercent, (!configuredComm.equals("-") && !configuredComm.equals("0") ? ", Configured Rate: " + configuredComm : ""));
                }
                System.out.printf("    |-- Total Cash %-9s : %,.2f BDT (%s)%n",
                        isBuy ? "Deducted" : "Credited",
                        Math.abs(cashNetDiff),
                        isBuy ?
                            (isPartiallyFilled ? String.format("%,.2f Exec + %,.2f Due Blocked%s", executedTradeValue, dueTradeValue, (isZeroCommission ? "" : String.format(" + %,.2f Comm", commCharges))) :
                                                 (isZeroCommission ? String.format("%,.2f (Zero Comm)", executedTradeValue) : String.format("%,.2f + %,.2f Comm", executedTradeValue, commCharges))) :
                            (isPartiallyFilled ? String.format("%,.2f Exec Proceeds%s (%d due pending)", executedTradeValue, (isZeroCommission ? "" : String.format(" - %,.2f Comm", commCharges)), effectiveDueQty) :
                                                 (isZeroCommission ? String.format("%,.2f (Zero Comm)", executedTradeValue) : String.format("%,.2f - %,.2f Comm", executedTradeValue, commCharges))));
            } else {
                System.err.printf(">>> [CASH AUDIT FAILURE] Client Cash was WRONGLY %s on %s trade! (Pre: %,.2f, Post: %,.2f, Exec: %,.2f, Due: %,.2f, Comm: %,.2f)%n",
                        isBuy ? "NOT DEDUCTED" : "NOT ADDED", side, preCash, postCash, executedTradeValue, dueTradeValue, commCharges);
            }
        }
        System.out.println();
    }

    private static String getVal(Map<String, String> map, String... keys) {
        if (map == null) return "-";
        for (String k : keys) {
            String v = map.get(k);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "-";
    }

    private static double getNum(Map<String, String> map, String... keys) {
        String val = getVal(map, keys);
        return parseNum(val);
    }

    private static String fmt(double val) {
        return String.format("%,.2f", val);
    }

    private static String fmtQty(double val) {
        return String.format("%,.0f", val);
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
        System.out.println();
        System.out.println("+---------------------------------------------------------------+");
        System.out.printf("| %-61s |%n", label + " [Source: " + snap.getOrDefault("DATA_SOURCE", "NONE") + "]");
        System.out.println("+---------------------------------------------------------------+");
        System.out.printf("| %-22s : %-36s |%n", "Symbol", snap.getOrDefault("symbol", "-"));
        System.out.printf("| %-22s : %-36s |%n", "Total Qty", fmtQty(getNum(snap, "total_qty", "totalqty")));
        System.out.printf("| %-22s : %-36s |%n", "Saleable Qty", fmtQty(getNum(snap, "saleable_qty", "qty", "saleable")));
        System.out.printf("| %-22s : %-36s |%n", "Market Rate (LTP)", fmt(getNum(snap, "ltp", "market_rate")));
        System.out.printf("| %-22s : %-36s |%n", "Avg Cost Rate", fmt(getNum(snap, "avg_cost", "avgcost")));
        System.out.printf("| %-22s : %-36s |%n", "Total Cost Value", fmt(getNum(snap, "total_cost", "totalcost")));
        System.out.printf("| %-22s : %-36s |%n", "Market Value", fmt(getNum(snap, "value", "market_value")));
        System.out.printf("| %-22s : %-36s |%n", "Unrealized Gain", fmt(getNum(snap, "gain", "profit_loss")));
        System.out.printf("| %-22s : %-36s |%n", "Gain Percentage", fmt(getNum(snap, "gain_percent", "gain_per")) + "%");
        System.out.println("+---------------------------------------------------------------+");
    }
}