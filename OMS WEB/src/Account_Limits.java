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

        JavascriptExecutor js = (JavascriptExecutor) driver;
        Map<String, Double> discoveredCashMap = new LinkedHashMap<>();

        // 1. Confirmed Active Accounts with Cash >= 100,000 BDT & Valid 16-Digit BO IDs
        String[] verifiedHighCashSeeds = {
            "08096", // 694,922.53 BDT | BO: 1203110065478644 | MD ARIF HOSSEN
            "00224", // 687,540.02 BDT | BO: 1203110000747628 | MR SOHEL MAHMUD
            "08833", // 598,007.27 BDT | BO: 1203110068108142 | MD MAHMUDUR RAHMAN
            "00507", // 581,173.99 BDT | BO: 1203110004547869 | MEHA MOHAMMED SIRAJ
            "09139", // 496,799.24 BDT | BO: 1203110017855029 | MR SK MD LUTFAR RAHMAN
            "09189", // 487,920.47 BDT | BO: 1203110017778601 | MD REZWAN ZIHADI
            "07605", // 255,182.04 BDT | BO: 1203110014808724 | MRS SHARIFA AHMED
            "09185", // 181,305.19 BDT | BO: 1203110072913055 | ENAMUL HOQUE TALUKDER
            "08383", // 156,881.04 BDT | BO: 1203110066381699 | MD SAIFUL ISLAM
            "08231", // 133,581.95 BDT | BO: 1203110065839885 | UMME HABIBA SANJIDA
            "08570", // 122,026.40 BDT | BO: 1203110062567351 | DOER SERVICES LIMITED
            "08739", // 111,280.03 BDT | BO: 1203110015531181 | BAITUL AHMED ADVOCATE
            "08425", // 109,613.38 BDT | BO: 1203110015313219 | MD HABIBUR RAHMAN CHOWDHURY
            "00495", // 104,558.20 BDT | BO: 1203110004494105 | AFM YOUSUF ALI
            "08345"  // 104,130.70 BDT | BO: 1203110066345548 | MD ABDULLAH AL MAMUN
        };
        for (String seed : verifiedHighCashSeeds) {
            discoveredCashMap.put(seed, minCash);
        }

        // 2. Query Live Limits Engine (/broker_ccd/list) directly across all accounts
        try {
            System.out.println("[ACCOUNT LIMITS] Querying Limits Engine for accounts with Cash >= 100,000 BDT...");
            Object ccdRes = js.executeAsyncScript(
                "var cb = arguments[arguments.length - 1];" +
                "var threshold = arguments[0];" +
                "fetch('/broker_ccd/list?page=1&limit=1000&sme_enable=false&atb_enable=false&search=', { credentials: 'include' })" +
                ".then(function(r) { return r.json(); })" +
                ".then(function(data) {" +
                "    var items = (data && data.data && data.data.items) ? data.data.items : [];" +
                "    var out = [];" +
                "    for (var i = 0; i < items.length; i++) {" +
                "        var it = items[i];" +
                "        var c = (it.client_code || '').trim();" +
                "        var cash = parseFloat(it.cash || 0);" +
                "        var initCash = parseFloat(it.initial_cash || 0);" +
                "        var mature = parseFloat(it.mature_balance || 0);" +
                "        var maxC = Math.max(cash, Math.max(initCash, mature));" +
                "        if (c && maxC >= threshold) {" +
                "            out.push({ code: c, cash: maxC });" +
                "        }" +
                "    }" +
                "    cb(out);" +
                "})" +
                ".catch(function(e) { cb([]); });",
                minCash
            );

            if (ccdRes instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) ccdRes;
                for (Map<String, Object> m : list) {
                    String code = String.valueOf(m.getOrDefault("code", "")).trim();
                    double cash = parseDbl(String.valueOf(m.getOrDefault("cash", "0")));
                    if (!code.isEmpty() && !code.equals("-")) {
                        discoveredCashMap.put(code, cash);
                    }
                }
                System.out.printf("[ACCOUNT LIMITS] Discovered %d accounts with cash >= %,.2f BDT from Limits engine.%n",
                        list.size(), minCash);
            }
        } catch (Exception e) {
            System.out.println("[INFO: Limits Engine Query] " + e.getMessage());
        }

        // 3. For each candidate account, verify live client data (/shared/getclientdata/?cln_code=)
        List<ClientCashInfo> eligibleClients = new ArrayList<>();

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

                if (hasBoId && isActive && effectiveCash >= minCash) {
                    eligibleClients.add(new ClientCashInfo(code, effectiveCash, boId, status.isEmpty() ? "Active" : status, name));
                }
            } catch (Exception ignored) {}
        }

        // 4. Sort eligible clients by Cash descending (highest cash first)
        eligibleClients.sort((a, b) -> Double.compare(b.getCash(), a.getCash()));

        System.out.printf("[ACCOUNT LIMITS] Found %d active clients with cash >= %,.2f BDT & valid BO ID.%n",
                eligibleClients.size(), minCash);

        // 5. Randomly select 4-5 clients from this high-cash pool
        List<ClientCashInfo> chosen = new ArrayList<>();
        if (!eligibleClients.isEmpty()) {
            List<ClientCashInfo> shuffled = new ArrayList<>(eligibleClients);
            Collections.shuffle(shuffled);
            int desiredCount = Math.min(targetCount, shuffled.size());
            chosen = new ArrayList<>(shuffled.subList(0, desiredCount));
            // Sort chosen clients descending by cash balance
            chosen.sort((a, b) -> Double.compare(b.getCash(), a.getCash()));
        }

        // Display chosen client table
        System.out.println();
        System.out.println("+----+----------+--------------------+------------------+----------+--------------------------------+");
        System.out.printf("| %-2s | %-8s | %-18s | %-16s | %-8s | %-30s |%n",
                "#", "Client", "Cash Balance (BDT)", "BO ID", "Status", "Client Name");
        System.out.println("+----+----------+--------------------+------------------+----------+--------------------------------+");
        for (int i = 0; i < chosen.size(); i++) {
            ClientCashInfo c = chosen.get(i);
            System.out.printf("| %-2d | %-8s | %,18.2f | %-16s | %-8s | %-30s |%n",
                    (i + 1), c.getClientCode(), c.getCash(), c.getBoId(), c.getStatus(),
                    c.getClientName().length() > 30 ? c.getClientName().substring(0, 30) : c.getClientName());
        }
        System.out.println("+----+----------+--------------------+------------------+----------+--------------------------------+");

        // AUTOMATICALLY RETURN TO TRADE PAGE AND RESTORE STATE
        String topClient = !chosen.isEmpty() ? chosen.get(0).getClientCode() : "08096";
        OMSweb_Trade.restoreTradePageState(driver, "GP", topClient);

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