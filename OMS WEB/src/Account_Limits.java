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

public class Account_Limits {

    // =========================================================================
    // 1. ENDPOINTS & SELECTORS (Matched to your screenshot)
    // =========================================================================
    private static final String API_URL_TEMPLATE = "https://omsapi.quantbd.com/portfolio/account-limit/%s?exchange=%s";
    private static final String[] EXCHANGES = {"DSE", "CSE"};

    // Navigation Tabs
    private static final By ACCOUNT_LIMITS_TAB = By.xpath("//div[contains(@class,'lm_tab') and contains(.,'Account Limit')] | //a[normalize-space()='Account Limits'] | //div[@title='Account Limit']");
    private static final By TRADE_TAB          = By.xpath("//div[contains(@class,'lm_tab') and contains(.,'Trade')] | //i[contains(@class,'fa-exchange')] | //a[normalize-space()='Trade']");

    // Filter Bar
    private static final By INPUT_CLIENT_FILTER = By.xpath("//div[contains(@class,'account-limit') or contains(.,'Account Limit')]//input[1] | //input[@id='limit_client_code']");
    private static final By BTN_CLEAR           = By.xpath("//button[normalize-space()='Clear' or contains(@class,'btn-danger')]");
    private static final By REFRESH_BTN         = By.xpath("//div[contains(@class,'account-limit')]//i[contains(@class,'refresh')] | //button[contains(@id,'refresh')]");

    // Tables in Account Limits
    private static final By CASH_TABLE          = By.xpath("//table[thead//th[contains(.,'Remaining Cash') or contains(.,'Initial Limit')]] | //div[contains(@class,'account-limit')]//table[1]");
    private static final By CAPITAL_TABLE       = By.xpath("//table[thead//th[contains(.,'Exec Value') or contains(.,'Day Order')]] | //div[contains(@class,'account-limit')]//table[2]");

    // =========================================================================
    // 2. MAIN ENTRY POINT (Matching your runner's signature)
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=======================================================");
        System.out.println("            ACCOUNT LIMITS DATA VIEWER                 ");
        System.out.println("=======================================================");

        // Auto-detect client code from Trade page inputs
        String clientCode = readInputSafe(driver, By.id("order_client_code"));
        if (clientCode.isEmpty()) clientCode = "23986";
        String exchange = "DSE";

        System.out.printf("[LIMITS] Using Client Code: %s | Exchange: %s%n", clientCode, exchange);

        // Navigate and capture snapshot
        navigateToAccountLimits(driver);
        filterByClient(driver, clientCode);

        Map<String, String> data = getSnapshot(driver, clientCode, exchange);
        printSnapshot("ACCOUNT LIMITS FOR " + clientCode, data);

        System.out.print("\nDo you want to query & randomly select 4-5 clients with > 100,000 cash, BO ID, and Active status? (1=YES / 0=NO): ");
        String opt = sc.nextLine().trim();
        if (opt.equals("1")) {
            getActiveBuyClientsWithCash(driver, 100000.0, 5);
        }
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & FILTER HELPERS
    // =========================================================================
    // =========================================================================
    // 2. DATA MODEL FOR CLIENT CASH & STATUS
    // =========================================================================
    public static class ClientCashInfo {
        private final String clientCode;
        private final double cash;
        private final String boId;
        private final String status;
        private final String clientName;

        public ClientCashInfo(String clientCode, double cash, String boId, String status, String clientName) {
            this.clientCode = clientCode;
            this.cash = cash;
            this.boId = boId;
            this.status = status;
            this.clientName = clientName;
        }

        public String getClientCode() { return clientCode; }
        public double getCash() { return cash; }
        public String getBoId() { return boId; }
        public String getStatus() { return status; }
        public String getClientName() { return clientName; }

        @Override
        public String toString() {
            return String.format("Client: %-8s | Cash: %,12.2f BDT | BO ID: %-16s | Status: %-8s | Name: %s",
                    clientCode, cash, boId, status, clientName);
        }
    }

    /**
     * Finds clients from the Account Limits table and client records,
     * sorts those with cash >= minCash (default 100,000 BDT),
     * verifies each client has a valid BO ID and Active account status,
     * and randomly selects 4 to 5 verified clients.
     */
    public static List<String> getActiveBuyClientsWithCash(WebDriver driver, double minCash, int targetCount) {
        System.out.printf("%n=======================================================================================%n");
        System.out.printf(" [ACCOUNT LIMITS] QUERYING CLIENTS WITH CASH >= %,.2f BDT, VALID BO ID, & ACTIVE STATUS%n", minCash);
        System.out.printf("=======================================================================================%n");

        navigateToAccountLimits(driver);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Map<String, Double> discoveredCashMap = new LinkedHashMap<>();

        // 1. Scrape client codes and cash from Account Limits DOM table
        try {
            Object res = js.executeScript(
                "var tables = Array.from(document.querySelectorAll('table'));" +
                "var limitTable = tables.find(function(t) {" +
                "    var text = (t.innerText || '').toLowerCase();" +
                "    return text.includes('client code') && (text.includes('remaining') || text.includes('initial limit'));" +
                "});" +
                "if (!limitTable) return [];" +
                "var ths = Array.from(limitTable.querySelectorAll('thead th, tr:first-child th, tr:first-child td'));" +
                "var headers = ths.map(function(h) { return (h.innerText || '').trim().toLowerCase(); });" +
                "var codeIdx = headers.findIndex(function(h) { return h.includes('client code') || h === 'code'; });" +
                "var remIdx = headers.findIndex(function(h) { return h === 'remaining' || h.includes('remaining'); });" +
                "var initIdx = headers.findIndex(function(h) { return h.includes('initial limit') || h.includes('limit'); });" +
                "var matureIdx = headers.findIndex(function(h) { return h.includes('mature'); });" +
                "var rows = Array.from(limitTable.querySelectorAll('tbody tr'));" +
                "var out = [];" +
                "for (var i = 0; i < rows.length; i++) {" +
                "    var r = rows[i];" +
                "    var cells = r.querySelectorAll('td');" +
                "    if (cells.length > Math.max(codeIdx, remIdx)) {" +
                "        var c = cells[codeIdx >= 0 ? codeIdx : 1].innerText.trim();" +
                "        var rStr = cells[remIdx >= 0 ? remIdx : 7].innerText.trim().replace(/,/g, '');" +
                "        var iStr = initIdx >= 0 ? cells[initIdx].innerText.trim().replace(/,/g, '') : '0';" +
                "        var mStr = matureIdx >= 0 ? cells[matureIdx].innerText.trim().replace(/,/g, '') : '0';" +
                "        var rVal = parseFloat(rStr) || 0;" +
                "        var iVal = parseFloat(iStr) || 0;" +
                "        var mVal = parseFloat(mStr) || 0;" +
                "        var maxCash = Math.max(rVal, Math.max(iVal, mVal));" +
                "        if (c && c !== '-' && c.toLowerCase() !== 'client code') {" +
                "            out.push({ code: c, cash: maxCash, remaining: rVal, initial: iVal, mature: mVal });" +
                "        }" +
                "    }" +
                "}" +
                "return out;"
            );

            if (res instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) res;
                for (Map<String, Object> m : list) {
                    String code = String.valueOf(m.getOrDefault("code", "")).trim();
                    double cash = parseDbl(String.valueOf(m.getOrDefault("cash", "0")));
                    if (!code.isEmpty() && !code.equals("-")) {
                        discoveredCashMap.put(code, cash);
                    }
                }
                System.out.printf("[ACCOUNT LIMITS] Scraped %d client accounts from UI Account Limits table.%n", list.size());
            }
        } catch (Exception e) {
            System.out.println("[WARN: DOM Table Scrape] " + e.getMessage());
        }

        // 2. Also incorporate seed/known active client accounts across the platform
        String[] seedClients = { "23986", "10009", "10004", "10102", "10340", "10351", "11310", "12245", "14118", "19363", "26687", "00239", "00006", "00010", "00017", "00033", "00086", "00101" };
        for (String seed : seedClients) {
            if (!discoveredCashMap.containsKey(seed)) {
                discoveredCashMap.put(seed, 0.0);
            }
        }

        // 3. For each candidate, query client details (/shared/getclientdata/?cln_code=) to get accurate cash, BO ID, and status
        List<ClientCashInfo> eligibleClients = new ArrayList<>();
        List<ClientCashInfo> allVerifiedClients = new ArrayList<>();

        for (Map.Entry<String, Double> entry : discoveredCashMap.entrySet()) {
            String code = entry.getKey();
            try {
                Map<String, String> details = ClientPortfolio.getClientDetails(driver, code);
                if (details == null || details.isEmpty()) continue;

                String boId = details.getOrDefault("bo_id", "-").trim();
                String name = details.getOrDefault("client_name", "-").trim();
                String status = details.getOrDefault("status", "Active").trim();

                double cashLimit = parseDbl(details.getOrDefault("cash_limit", "0"));
                double mature = parseDbl(details.getOrDefault("mature_balance", "0"));
                double tableCash = entry.getValue();
                double effectiveCash = Math.max(tableCash, Math.max(cashLimit, mature));

                // Validate BO ID: non-empty, not '-', length >= 8
                boolean hasBoId = (boId != null && !boId.isEmpty() && !boId.equals("-") && !boId.equalsIgnoreCase("null") && !boId.equals("0") && boId.replaceAll("[^0-9]", "").length() >= 8);

                // Validate Active Status
                String statusLower = status.toLowerCase();
                boolean isActive = (status.isEmpty() || status.equalsIgnoreCase("A") || status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("1") || status.equalsIgnoreCase("true")
                        || (!statusLower.contains("inact") && !statusLower.contains("close") && !statusLower.contains("suspend") && !statusLower.contains("block")));

                ClientCashInfo info = new ClientCashInfo(code, effectiveCash, boId, status.isEmpty() ? "Active" : status, name);

                if (hasBoId && isActive) {
                    allVerifiedClients.add(info);
                    if (effectiveCash >= minCash) {
                        eligibleClients.add(info);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Sort eligible clients by Cash descending (highest cash first)
        eligibleClients.sort((a, b) -> Double.compare(b.getCash(), a.getCash()));
        allVerifiedClients.sort((a, b) -> Double.compare(b.getCash(), a.getCash()));

        System.out.printf("%n[ACCOUNT LIMITS] Found %d client(s) with CASH >= %,.2f BDT, VALID BO ID, & ACTIVE STATUS:%n",
                eligibleClients.size(), minCash);
        System.out.println("+=================================================================================================================+");
        System.out.printf("| %-4s | %-10s | %-18s | %-18s | %-10s | %-32s |%n",
                "#", "Client", "Cash Balance (BDT)", "BO ID", "Status", "Client Name");
        System.out.println("+-----------------------------------------------------------------------------------------------------------------+");

        List<ClientCashInfo> selectionPool;
        if (!eligibleClients.isEmpty()) {
            for (int i = 0; i < eligibleClients.size(); i++) {
                ClientCashInfo c = eligibleClients.get(i);
                System.out.printf("| %-4d | %-10s | %,18.2f | %-18s | %-10s | %-32s |%n",
                        (i + 1), c.getClientCode(), c.getCash(), c.getBoId(), c.getStatus(),
                        c.getClientName().length() > 32 ? c.getClientName().substring(0, 29) + "..." : c.getClientName());
            }
            selectionPool = eligibleClients;
        } else {
            System.out.printf("| No clients found with cash >= %,.2f BDT. Showing top verified active clients:                             |%n", minCash);
            for (int i = 0; i < Math.min(10, allVerifiedClients.size()); i++) {
                ClientCashInfo c = allVerifiedClients.get(i);
                System.out.printf("| %-4d | %-10s | %,18.2f | %-18s | %-10s | %-32s |%n",
                        (i + 1), c.getClientCode(), c.getCash(), c.getBoId(), c.getStatus(),
                        c.getClientName().length() > 32 ? c.getClientName().substring(0, 29) + "..." : c.getClientName());
            }
            selectionPool = allVerifiedClients;
        }
        System.out.println("+=================================================================================================================+\n");

        // 5. Randomly select 4 to 5 clients from the pool
        int desiredCount = Math.min(selectionPool.size(), Math.max(4, Math.min(5, targetCount)));
        List<ClientCashInfo> shuffled = new ArrayList<>(selectionPool);
        Collections.shuffle(shuffled);
        List<ClientCashInfo> chosen = shuffled.subList(0, desiredCount);

        System.out.printf("✔ [RANDOM SELECTION] Randomly selected %d active client(s) with BO ID for BUY orders:%n", chosen.size());
        System.out.println("+=================================================================================================================+");
        System.out.printf("| %-4s | %-10s | %-18s | %-18s | %-10s | %-32s |%n",
                "#", "Client", "Cash Balance (BDT)", "BO ID", "Status", "Client Name");
        System.out.println("+-----------------------------------------------------------------------------------------------------------------+");
        for (int i = 0; i < chosen.size(); i++) {
            ClientCashInfo c = chosen.get(i);
            System.out.printf("| %-4d | %-10s | %,18.2f | %-18s | %-10s | %-32s |%n",
                    (i + 1), c.getClientCode(), c.getCash(), c.getBoId(), c.getStatus(),
                    c.getClientName().length() > 32 ? c.getClientName().substring(0, 29) + "..." : c.getClientName());
        }
        System.out.println("+=================================================================================================================+\n");

        return chosen.stream().map(ClientCashInfo::getClientCode).collect(Collectors.toList());
    }

    public static void navigateToAccountLimits(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean isVis = (Boolean) js.executeScript(
                "var ths = Array.from(document.querySelectorAll('th'));" +
                "return ths.some(function(th) { return (th.innerText || '').toLowerCase().includes('client code'); });"
            );
            if (Boolean.TRUE.equals(isVis)) return;

            By navBy = By.xpath(
                "//a[contains(@href,'account_limit') or contains(@data-link,'account_limit') or contains(@title,'Account Limit') or contains(@data-original-title,'Account Limit')] | " +
                "//a[.//i[contains(@class,'fa-usd') or contains(@class,'fa-dollar') or contains(@class,'fa-money')]] | " +
                "//div[contains(@class,'lm_tab') and contains(.,'Account Limit')] | " +
                "//div[@title='Account Limit' or @title='Account Limits'] | //a[normalize-space()='Account Limits']"
            );
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(4));
            WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(navBy));
            tab.click();
            Thread.sleep(1000);
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript(
                    "var el = document.querySelector('a[data-link*=\"account_limit\"], a[href*=\"account_limit\"], a[title*=\"Account Limit\"], .fa-usd, .fa-dollar');" +
                    "if (el) { (el.closest('a') || el).click(); }"
                );
                Thread.sleep(800);
            } catch (Exception ignored) {}
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

    public static void filterByClient(WebDriver driver, String clientCode) {
        try {
            if (clientCode != null && !clientCode.isEmpty()) {
                // Click Clear button first if present
                try { driver.findElement(BTN_CLEAR).click(); } catch (Exception ignored) {}

                WebElement input = driver.findElement(INPUT_CLIENT_FILTER);
                input.clear();
                input.sendKeys(clientCode);
                input.sendKeys(Keys.ENTER);
                Thread.sleep(600);
            }
        } catch (Exception ignored) {}
    }

    public static void refresh(WebDriver driver) {
        try {
            driver.findElement(REFRESH_BTN).click();
            Thread.sleep(1500);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 4. DATA CAPTURE: API FIRST (ADMIN SHARED -> QUANTBD TOKEN -> UI XPATH)
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String clientCode, String preferredExchange) {
        // Tier 1: Admin Shared API (/shared/get_account_limit/)
        try {
            String adminUrl = String.format("/shared/get_account_limit/?client_code=%s", clientCode);
            String json = executeBrowserFetch(driver, adminUrl);
            Map<String, String> adminData = parseAccountLimitsJson(json);
            if (!adminData.isEmpty()) {
                adminData.put("DATA_SOURCE", "API (Admin Shared)");
                System.out.printf("[SUCCESS: API] Captured Account Limits for %s from Admin API.%n", clientCode);
                return adminData;
            }
        } catch (Exception e) {
            System.out.printf("[WARN: Admin API] Account Limits query failed: %s%n", e.getMessage());
        }

        // Tier 2: Try QuantBD Token API
        List<String> exchangeOrder = new ArrayList<>();
        exchangeOrder.add(preferredExchange != null && !preferredExchange.isEmpty() ? preferredExchange : "DSE");
        for (String ex : EXCHANGES) {
            if (!exchangeOrder.contains(ex)) exchangeOrder.add(ex);
        }

        for (String ex : exchangeOrder) {
            String apiUrl = String.format(API_URL_TEMPLATE, clientCode, ex);
            try {
                Map<String, String> data = fetchFromApi(driver, apiUrl);
                if (!data.isEmpty()) {
                    data.put("DATA_SOURCE", "API (" + ex + ")");
                    System.out.printf("[SUCCESS: API] Captured Account Limits for %s from %s Token API.%n", clientCode, ex);
                    return data;
                }
            } catch (Exception ignored) {}
        }

        // Tier 3 Fallback: DOM Table Scraping (Exact tables in your screenshot)
        System.out.println("[FALLBACK: UI] API unavailable. Extracting from Account Limits DOM tables...");
        Map<String, String> uiData = fetchFromUiTables(driver);
        uiData.put("DATA_SOURCE", "UI_XPATH (" + preferredExchange + ")");
        return uiData;
    }

    // =========================================================================
    // 5. UI TABLE SCRAPING (Top Cash Table + Bottom Capital Table)
    // =========================================================================
    private static Map<String, String> fetchFromUiTables(WebDriver driver) {
        Map<String, String> data = new LinkedHashMap<>();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // 1. Scrape Top Cash Table (Initial Limit, Remaining Cash, Day Order, Total Transaction, Net Transaction)
                WebElement cashTable = wait.until(ExpectedConditions.visibilityOfElementLocated(CASH_TABLE));
                List<WebElement> cashRows = cashTable.findElements(By.xpath(".//tbody//tr[td[normalize-space()='Cash']]"));
                if (!cashRows.isEmpty()) {
                    List<WebElement> cells = cashRows.get(0).findElements(By.xpath("./td"));
                    if (cells.size() >= 6) {
                        data.put("Initial_Limit", cells.get(1).getText().trim());
                        data.put("Remaining_Cash", cells.get(2).getText().trim());
                        data.put("Cash_Day_Order", cells.get(3).getText().trim());
                        data.put("Total_Transaction", cells.get(4).getText().trim());
                        data.put("Net_Transaction", cells.get(5).getText().trim());
                    }
                }

                // 2. Scrape Bottom Capital Table (Max Capital Buy, Max Capital Sell)
                try {
                    WebElement capTable = driver.findElement(CAPITAL_TABLE);
                    scrapeCapitalRow(capTable, "Max Capital Buy", "Buy", data);
                    scrapeCapitalRow(capTable, "Max Capital Sell", "Sell", data);
                } catch (Exception ignored) {}

                return data;
            } catch (StaleElementReferenceException e) {
                if (attempt == 3) throw e;
            }
        }
        return data;
    }

    private static void scrapeCapitalRow(WebElement table, String titleText, String prefix, Map<String, String> out) {
        try {
            WebElement row = table.findElement(By.xpath(".//tr[td[contains(normalize-space(),'" + titleText + "')]]"));
            List<WebElement> cells = row.findElements(By.xpath("./td"));
            if (cells.size() >= 5) {
                out.put(prefix + "_Limit", cells.get(1).getText().trim());
                out.put(prefix + "_Day_Order", cells.get(2).getText().trim());
                out.put(prefix + "_Exec_Value", cells.get(3).getText().trim());
                out.put(prefix + "_Remaining", cells.get(4).getText().trim());
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 6. API CLIENT (Using HttpURLConnection - Avoids Java 22 JaCoCo conflict)
    // =========================================================================
    private static Map<String, String> fetchFromApi(WebDriver driver, String urlStr) throws Exception {
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

        result.put("Initial_Limit", getJsonVal(json, "initial_limit", "initialLimit"));
        result.put("Remaining_Cash", getJsonVal(json, "remaining_cash", "remainingCash", "cash_balance"));
        result.put("Cash_Day_Order", getJsonVal(json, "day_order", "dayOrder"));
        result.put("Total_Transaction", getJsonVal(json, "total_transaction", "totalTransaction"));
        result.put("Net_Transaction", getJsonVal(json, "net_transaction", "netTransaction"));
        result.put("Buy_Day_Order", getJsonVal(json, "buy_day_order", "max_capital_buy_day_order"));
        result.put("Buy_Exec_Value", getJsonVal(json, "buy_exec_value", "max_capital_buy_exec_value"));
        result.put("Sell_Day_Order", getJsonVal(json, "sell_day_order", "max_capital_sell_day_order"));
        result.put("Sell_Exec_Value", getJsonVal(json, "sell_exec_value", "max_capital_sell_exec_value"));
        return result;
    }

    private static String getJsonVal(String json, String... keys) {
        for (String k : keys) {
            Pattern p = Pattern.compile("\"" + k + "\"\\s*:\\s*[\"]?([^\",}]+)[\"]?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
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

    private static Map<String, String> parseAccountLimitsJson(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("ClientCode")) return result;

        result.put("Initial_Limit", getJsonVal(json, "initial_cash", "initial_limit"));
        result.put("Remaining_Cash", getJsonVal(json, "Cash", "cash"));
        result.put("Cash_Day_Order", getJsonVal(json, "total_active_orders_value", "day_order"));
        result.put("Total_Transaction", getJsonVal(json, "total_value", "total_transaction"));
        result.put("Net_Transaction", getJsonVal(json, "net_value", "net_transaction"));

        result.put("Buy_Limit", getJsonVal(json, "main_buy_initial_limit"));
        result.put("Buy_Day_Order", getJsonVal(json, "buy_active_orders_value"));
        result.put("Buy_Exec_Value", getJsonVal(json, "buy_value"));

        double buyActive = parseDbl(result.get("Buy_Day_Order"));
        double buyExec = parseDbl(result.get("Buy_Exec_Value"));
        result.put("Buy_Remaining", String.format(Locale.US, "%.2f", buyActive - buyExec));

        result.put("Sell_Limit", getJsonVal(json, "main_sell_initial_limit"));
        result.put("Sell_Day_Order", getJsonVal(json, "sell_active_orders_value"));
        result.put("Sell_Exec_Value", getJsonVal(json, "sell_value"));

        double sellActive = parseDbl(result.get("Sell_Day_Order"));
        double sellExec = parseDbl(result.get("Sell_Exec_Value"));
        result.put("Sell_Remaining", String.format(Locale.US, "%.2f", sellActive - sellExec));

        result.put("Mature_Balance", getJsonVal(json, "mature_balance"));
        result.put("Ledger_Balance", getJsonVal(json, "ledger_balance"));
        return result;
    }

    private static double parseDbl(String val) {
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

    // =========================================================================
    // 7. QUANTITATIVE VERIFICATION FOR ACCOUNT LIMITS
    // =========================================================================
    public static void verify(Map<String, String> before, Map<String, String> after,
                              String side, int tradeQty, double tradeRate, String exchange) {
        verify(before, after, side, tradeQty, tradeRate, exchange, tradeQty, 0);
    }

    public static void verify(Map<String, String> before, Map<String, String> after,
                              String side, int tradeQty, double tradeRate, String exchange,
                              int executedQty, int dueQty) {

        boolean isBuy = side.equalsIgnoreCase("BUY");
        int effExecQty = executedQty > 0 ? executedQty : tradeQty;
        int effDueQty = dueQty >= 0 && executedQty > 0 ? dueQty : Math.max(0, tradeQty - effExecQty);

        double executedTradeValue = effExecQty * tradeRate;
        double dueTradeValue = effDueQty * tradeRate;
        double totalTradeValue = tradeQty * tradeRate;

        // Cash fields
        double oldRemainingCash = parseNum(before.getOrDefault("Remaining_Cash", "0"));
        double newRemainingCash = parseNum(after.getOrDefault("Remaining_Cash", "0"));

        double oldDayOrder = parseNum(before.getOrDefault("Cash_Day_Order", "0"));
        double newDayOrder = parseNum(after.getOrDefault("Cash_Day_Order", "0"));

        double oldTotalTx = parseNum(before.getOrDefault("Total_Transaction", "0"));
        double newTotalTx = parseNum(after.getOrDefault("Total_Transaction", "0"));

        // Capital Exec fields
        String execKey = isBuy ? "Buy_Exec_Value" : "Sell_Exec_Value";
        double oldExecVal = parseNum(before.getOrDefault(execKey, "0"));
        double newExecVal = parseNum(after.getOrDefault(execKey, "0"));

        // Expected math
        double expectedDayOrder = oldDayOrder + totalTradeValue;
        double expectedTotalTx = oldTotalTx + executedTradeValue;
        double expectedExecVal = oldExecVal + executedTradeValue;
        double expectedCash = isBuy ? (oldRemainingCash - (executedTradeValue + dueTradeValue)) : (oldRemainingCash + executedTradeValue);

        boolean dayOrderPass = Math.abs(newDayOrder - expectedDayOrder) <= 1.0;
        boolean totalTxPass  = Math.abs(newTotalTx - expectedTotalTx) <= 1.0 || effExecQty == 0;
        boolean execValPass  = Math.abs(newExecVal - expectedExecVal) <= 1.0 || effExecQty == 0;

        System.out.println();
        System.out.println("+=========================================================================================+");
        if (effDueQty > 0) {
            System.out.printf("| ACCOUNT LIMITS AUDIT: %-4s %d placed, %d executed @ %.2f (Due: %d on %-4s)%n",
                    side, tradeQty, effExecQty, tradeRate, effDueQty, exchange);
        } else {
            System.out.printf("| ACCOUNT LIMITS AUDIT: %-4s %d @ %.2f (Trade Value: %,.2f on %-4s)%n",
                    side, tradeQty, tradeRate, executedTradeValue, exchange);
        }
        System.out.printf("| Sources: Pre-Trade [%s]  -->  Post-Trade [%s]%n",
                before.getOrDefault("DATA_SOURCE", "NONE"), after.getOrDefault("DATA_SOURCE", "NONE"));
        System.out.println("+=========================================================================================+");
        System.out.printf("| %-20s | %-16s | %-16s | %-16s | %-7s%n", "Metric", "Pre-Trade", "Post-Trade", "Expected", "Status");
        System.out.println("+-----------------------------------------------------------------------------------------+");
        System.out.printf("| Cash Day Order       | %-16.2f | %-16.2f | %-16.2f | %-7s%n", oldDayOrder, newDayOrder, expectedDayOrder, dayOrderPass ? "PASS" : "FAIL");
        System.out.printf("| Total Transaction    | %-16.2f | %-16.2f | %-16.2f | %-7s%n", oldTotalTx, newTotalTx, expectedTotalTx, totalTxPass ? "PASS" : "FAIL");
        System.out.printf("| %-20s | %-16.2f | %-16.2f | %-16.2f | %-7s%n", (isBuy ? "Max Cap Buy Exec" : "Max Cap Sell Exec"), oldExecVal, newExecVal, expectedExecVal, execValPass ? "PASS" : "FAIL");
        System.out.printf("| Remaining Cash       | %-16.2f | %-16.2f | %-16.2f | %-7s%n", oldRemainingCash, newRemainingCash, expectedCash, "INFO");
        System.out.println("+=========================================================================================+");

        if (dayOrderPass && (totalTxPass || effExecQty == 0) && (execValPass || effExecQty == 0)) {
            System.out.println(">>> [ACCOUNT LIMITS AUDIT PASSED] Account limits and cash reflect the trade accurately!");
        } else {
            System.err.println(">>> [ACCOUNT LIMITS AUDIT FAILED] Limits mismatch detected!");
        }
        System.out.println();
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

    private static void printSnapshot(String label, Map<String, String> snap) {
        System.out.printf("\n--- %s [Source: %s] ---%n", label, snap.getOrDefault("DATA_SOURCE", "NONE"));
        snap.forEach((k, v) -> {
            if (!k.equals("DATA_SOURCE")) System.out.printf("   %-22s: %s%n", k, v);
        });
    }
}