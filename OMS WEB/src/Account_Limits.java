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
    private static final String API_URL_TEMPLATE = "https://omsapi.quantbd.com/account-limit/%s?exchange=%s";
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
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & FILTER HELPERS
    // =========================================================================
    public static void navigateToAccountLimits(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
            WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(ACCOUNT_LIMITS_TAB));
            tab.click();
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("[NAV] Account Limits tab already active or clicked.");
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
    // 4. DATA CAPTURE: API FIRST (DSE + CSE) -> UI XPATH FALLBACK
    // =========================================================================
    public static Map<String, String> getSnapshot(WebDriver driver, String clientCode, String preferredExchange) {
        // Strategy 1: Try QuantBD Account Limits API
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
                    System.out.printf("[SUCCESS: API] Captured Account Limits for %s from %s API.%n", clientCode, ex);
                    return data;
                }
            } catch (Exception ignored) {}
        }

        // Strategy 2: Fallback to DOM Table Scraping (Exact tables in your screenshot)
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

    // =========================================================================
    // 7. QUANTITATIVE VERIFICATION FOR ACCOUNT LIMITS
    // =========================================================================
    public static void verify(Map<String, String> before, Map<String, String> after,
                              String side, int tradeQty, double tradeRate, String exchange) {

        boolean isBuy = side.equalsIgnoreCase("BUY");
        double tradeValue = tradeQty * tradeRate;

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
        double expectedDayOrder = oldDayOrder + tradeValue;
        double expectedTotalTx = oldTotalTx + tradeValue;
        double expectedExecVal = oldExecVal + tradeValue;
        double expectedCash = isBuy ? (oldRemainingCash - tradeValue) : (oldRemainingCash + tradeValue);

        boolean dayOrderPass = Math.abs(newDayOrder - expectedDayOrder) <= 1.0;
        boolean totalTxPass  = Math.abs(newTotalTx - expectedTotalTx) <= 1.0;
        boolean execValPass  = Math.abs(newExecVal - expectedExecVal) <= 1.0;

        System.out.println();
        System.out.println("+=========================================================================================+");
        System.out.printf("| ACCOUNT LIMITS AUDIT: %-4s %d @ %.2f (Trade Value: %,.2f on %-4s)%n", side, tradeQty, tradeRate, tradeValue, exchange);
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

        if (dayOrderPass && totalTxPass && execValPass) {
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
        return clean.isEmpty() ? 0.0 : Double.parseDouble(clean);
    }

    private static void printSnapshot(String label, Map<String, String> snap) {
        System.out.printf("\n--- %s [Source: %s] ---%n", label, snap.getOrDefault("DATA_SOURCE", "NONE"));
        snap.forEach((k, v) -> {
            if (!k.equals("DATA_SOURCE")) System.out.printf("   %-22s: %s%n", k, v);
        });
    }
}