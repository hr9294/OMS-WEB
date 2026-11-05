import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Position {
    // =========================================================================
    // 1. DATA MODEL
    // =========================================================================
    public static class ClientPosition {
        private final String symbol;
        private final String clientCode;
        private final double totalQty;
        private final double saleableQty;
        private final double avgCost;
        private final double totalCost;
        private final double mktRate;
        private final double mktValue;
        private final double gain;
        private final double gainPercent;

        public ClientPosition(String symbol, String clientCode, double totalQty, double saleableQty,
                              double avgCost, double totalCost, double mktRate, double mktValue,
                              double gain, double gainPercent) {
            this.symbol = symbol;
            this.clientCode = clientCode;
            this.totalQty = totalQty;
            this.saleableQty = saleableQty;
            this.avgCost = avgCost;
            this.totalCost = totalCost;
            this.mktRate = mktRate;
            this.mktValue = mktValue;
            this.gain = gain;
            this.gainPercent = gainPercent;
        }

        public String getSymbol() { return symbol; }
        public String getClientCode() { return clientCode; }
        public double getTotalQty() { return totalQty; }
        public double getSaleableQty() { return saleableQty; }
        public double getAvgCost() { return avgCost; }
        public double getTotalCost() { return totalCost; }
        public double getMktRate() { return mktRate; }
        public double getMktValue() { return mktValue; }
        public double getGain() { return gain; }
        public double getGainPercent() { return gainPercent; }

        @Override
        public String toString() {
            return String.format("Client: %-8s | Saleable: %,8.0f | Total: %,8.0f | AvgCost: %,8.2f | LTP: %,8.2f",
                    clientCode, saleableQty, totalQty, avgCost, mktRate);
        }
    }

    // =========================================================================
    // WIDGET COLOR LINKING
    // =========================================================================
    public static final String[] COLOR_NAMES = { "Turquoise", "Yellow", "Orange", "Green", "Red", "Pink" };
    public static final String[] COLOR_HEXES = { "#42EADDFF", "#FEE715FF", "#F95700ff", "#06d001", "#EB0506", "#FF3EA5FF" };

    public static void runWidgetColorSelection(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=== Position Widget Color Link ===");
        System.out.println("1.Turquoise  2.Yellow  3.Orange");
        System.out.println("4.Green      5.Red     6.Pink");
        System.out.print("Enter choice (1-6, or press Enter to skip): ");
        String input = sc.nextLine().trim();
        if (input.isEmpty()) return;
        int colorChoice;
        try {
            colorChoice = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return;
        }
        if (colorChoice < 1 || colorChoice > 6) return;
        setWidgetColor(driver, colorChoice);
    }

    public static boolean setWidgetColor(WebDriver driver, int colorChoice) {
        if (colorChoice < 1 || colorChoice > 6) return false;
        String chosenColorName = COLOR_NAMES[colorChoice - 1];
        String targetHex = COLOR_HEXES[colorChoice - 1];
        return applyWidgetColor(driver, chosenColorName, targetHex);
    }

    public static boolean setWidgetColor(WebDriver driver, String colorNameOrHex) {
        if (colorNameOrHex == null || colorNameOrHex.trim().isEmpty()) return false;
        String val = colorNameOrHex.trim();
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            if (COLOR_NAMES[i].equalsIgnoreCase(val) || COLOR_HEXES[i].equalsIgnoreCase(val)) {
                return setWidgetColor(driver, i + 1);
            }
        }
        return applyWidgetColor(driver, val, val);
    }

    private static boolean applyWidgetColor(WebDriver driver, String chosenColorName, String targetHex) {
        try {
            navigateToPosition(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script =
                "var targetHex = arguments[0];" +
                "var tbl = document.getElementById('symbol_position_table');" +
                "var stack = tbl ? (tbl.closest('.lm_stack') || tbl.closest('.lm_item_container')) : null;" +
                "var chooseDiv = null;" +
                "if (stack) {" +
                "    chooseDiv = stack.querySelector('.lm_header .chooseColor, .lm_controls .chooseColor, .chooseColor');" +
                "}" +
                "if (!chooseDiv) {" +
                "    var tabs = Array.from(document.querySelectorAll('.lm_tab'));" +
                "    var posTab = tabs.find(function(t) { return t.innerText && t.innerText.includes('Position'); });" +
                "    if (posTab) {" +
                "        var tabStack = posTab.closest('.lm_stack');" +
                "        if (tabStack) chooseDiv = tabStack.querySelector('.chooseColor');" +
                "    }" +
                "}" +
                "if (!chooseDiv) {" +
                "    chooseDiv = document.querySelector('.chooseColor');" +
                "}" +
                "if (!chooseDiv) return { success: false, error: 'chooseColor element not found' };" +
                "var items = Array.from(chooseDiv.querySelectorAll('ul.color-card li.color-item, li.color-item'));" +
                "var targetLi = items.find(function(li) {" +
                "    var colorDiv = li.querySelector('[data-link-color]');" +
                "    return colorDiv && colorDiv.getAttribute('data-link-color').toLowerCase() === targetHex.toLowerCase();" +
                "});" +
                "if (targetLi) {" +
                "    if (window.$) {" +
                "        window.$(targetLi).trigger('click');" +
                "    } else {" +
                "        targetLi.click();" +
                "    }" +
                "    var selectedDiv = chooseDiv.querySelector('.selectedColor');" +
                "    var finalColor = selectedDiv ? (selectedDiv.getAttribute('data-link-color') || selectedDiv.style.backgroundColor) : '';" +
                "    return { success: true, color: finalColor };" +
                "} else {" +
                "    return { success: false, error: 'Color hex not found in palette' };" +
                "}";

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) js.executeScript(script, targetHex);

            if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                System.out.printf(">>> [POSITION COLOR LINK] Successfully linked Position widget to %s (%s)%n",
                        chosenColorName, targetHex);
                Thread.sleep(300);
                return true;
            } else {
                String err = (result != null && result.containsKey("error")) ? (String) result.get("error") : "Failed to select color";
                System.out.printf("[WARN: Position Color Link] %s%n", err);
                return false;
            }
        } catch (Exception e) {
            System.out.printf("[WARN: Position Color Link] %s%n", e.getMessage());
            return false;
        }
    }

    public static String getWidgetColor(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script =
                "var tbl = document.getElementById('symbol_position_table');" +
                "var stack = tbl ? (tbl.closest('.lm_stack') || tbl.closest('.lm_item_container')) : null;" +
                "var chooseDiv = null;" +
                "if (stack) chooseDiv = stack.querySelector('.chooseColor');" +
                "if (!chooseDiv) {" +
                "    var tabs = Array.from(document.querySelectorAll('.lm_tab'));" +
                "    var posTab = tabs.find(function(t) { return t.innerText && t.innerText.includes('Position'); });" +
                "    if (posTab && posTab.closest('.lm_stack')) chooseDiv = posTab.closest('.lm_stack').querySelector('.chooseColor');" +
                "}" +
                "if (chooseDiv) {" +
                "    var selectedDiv = chooseDiv.querySelector('.selectedColor');" +
                "    if (selectedDiv) {" +
                "        return selectedDiv.getAttribute('data-link-color') || selectedDiv.style.backgroundColor || '';" +
                "    }" +
                "}" +
                "return '';";

            Object res = js.executeScript(script);
            if (res != null) {
                String hex = String.valueOf(res).trim();
                for (int i = 0; i < COLOR_HEXES.length; i++) {
                    if (COLOR_HEXES[i].equalsIgnoreCase(hex)) {
                        return COLOR_NAMES[i] + " (" + hex + ")";
                    }
                }
                return hex;
            }
        } catch (Exception ignored) {}
        return "";
    }

    // =========================================================================
    // 2. MAIN STANDALONE ENTRY POINT
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=======================================================");
        System.out.println("               POSITION WIDGET VIEWER                  ");
        System.out.println("=======================================================");
        navigateToPosition(driver);

        // Display and offer Position Widget Color Link
        String currentColor = getWidgetColor(driver);
        System.out.printf("[COLOR LINK] Position Widget Current Color: %s%n", currentColor.isEmpty() ? "Default / Unlinked" : currentColor);
        System.out.print("Do you want to change Position Widget Color Link? (1=YES / 0=NO): ");
        if (sc.nextLine().trim().equals("1")) {
            runWidgetColorSelection(driver, sc);
        }

        String symbol = cleanSymbol(readInputSafe(driver, By.id("order_instrument"), By.id("instrument"), By.id("marketwatch_symbol")));
        if (symbol.isEmpty()) {
            System.out.print("Enter Symbol (e.g. GP, ACFL): ");
            symbol = cleanSymbol(sc.nextLine());
        }
        if (symbol.isEmpty()) symbol = "GP";
        String exchange = "DSE";
        System.out.printf("[POSITION] Searching Symbol: %s on Exchange: %s%n", symbol, exchange);
        navigateToPosition(driver);
        searchSymbolInPosition(driver, symbol, exchange);
        List<ClientPosition> positions = getPositions(driver, symbol, exchange);
        printPositionsTable(symbol, positions);
        List<ClientPosition> saleable = getClientsWithSaleable(driver, symbol, exchange);
        System.out.printf("\n[SUMMARY] Found %d client(s) with SALEABLE shares for %s.%n", saleable.size(), symbol);

        if (!saleable.isEmpty()) {
            System.out.print("\nDo you want to perform a SELL trade for this symbol now? (1=YES / 0=NO): ");
            String ans = sc.nextLine().trim();
            if (ans.equals("1")) {
                ClientPosition chosen = promptOrSelectClientForSell(driver, sc, symbol, exchange, "");
                if (chosen != null && !chosen.getClientCode().isEmpty()) {
                    System.out.print("Enter Qty to SELL (press Enter for default 10): ");
                    String qtyStr = sc.nextLine().trim();
                    int qty = qtyStr.isEmpty() ? 10 : Integer.parseInt(qtyStr);
                    System.out.print("Enter SELL Limit Rate (or press Enter for Market LTP): ");
                    String rateStr = sc.nextLine().trim();
                    double rate = rateStr.isEmpty() ? chosen.getMktRate() : Double.parseDouble(rateStr);
                    if (rate == 0.0) rate = chosen.getAvgCost();
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                    executeSellTrade(driver, wait, chosen.getClientCode(), symbol, qty, rate, exchange);
                }
            }
        }
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & VISIBILITY HELPERS
    // =========================================================================
    public static void navigateToPosition(WebDriver driver) {
        ensurePositionWidgetVisible(driver);
    }

    public static boolean isPositionWidgetVisible(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var tbl = document.getElementById('symbol_position_table');" +
                "if (tbl && tbl.offsetParent !== null) return true;" +
                "var inp = document.getElementById('sc_instrument_search');" +
                "if (inp && inp.offsetParent !== null) return true;" +
                "var posTab = document.querySelector('li.lm_tab.lm_active[title*=\"Position\"], div.lm_tab.lm_active[title*=\"Position\"]');" +
                "return !!posTab;"
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean ensurePositionWidgetVisible(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Check if table is already visible
            Boolean alreadyVis = (Boolean) js.executeScript(
                "var tbl = document.getElementById('symbol_position_table');" +
                "if (tbl && tbl.offsetParent !== null) return true;" +
                "return false;"
            );
            if (Boolean.TRUE.equals(alreadyVis)) {
                return true;
            }

            System.out.println("[NAV] Position widget is hidden/inactive. Activating Position tab...");

            // 1. Direct tab click via JS
            Boolean clicked = (Boolean) js.executeScript(
                "var tabs = Array.from(document.querySelectorAll('li.lm_tab, div.lm_tab, a.nav-link, a'));" +
                "var posTab = tabs.find(function(t) { " +
                "    var title = t.getAttribute('title') || ''; " +
                "    var text = t.innerText ? t.innerText.trim() : ''; " +
                "    return title.includes('Position') || text.startsWith('Position'); " +
                "});" +
                "if (posTab) {" +
                "    posTab.click();" +
                "    return true;" +
                "}" +
                "var dropdowns = Array.from(document.querySelectorAll('.lm_tabdropdown, .lm_controls .lm_tabdropdown'));" +
                "for (var i = 0; i < dropdowns.length; i++) {" +
                "    dropdowns[i].click();" +
                "    var dropTabs = Array.from(document.querySelectorAll('.lm_tab, li'));" +
                "    var dt = dropTabs.find(function(t) { return t.innerText && t.innerText.trim().startsWith('Position'); });" +
                "    if (dt) { dt.click(); return true; }" +
                "}" +
                "var link = document.querySelector('a[data-link*=\"position\"], a[href*=\"position\"], a[title*=\"Position\"]');" +
                "if (link) { link.click(); return true; }" +
                "return false;"
            );

            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(600);
            } else {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
                WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//li[contains(@class,'lm_tab') and (.//span[normalize-space()='Position'] or normalize-space()='Position' or @title='Position')] | " +
                                 "//div[@title='Position'] | //a[normalize-space()='Position']")));
                tab.click();
                Thread.sleep(600);
            }

            // Unhide container if hidden by CSS
            js.executeScript(
                "var tbl = document.getElementById('symbol_position_table');" +
                "if (tbl) {" +
                "    var p = tbl.closest('.lm_item_container') || tbl.closest('.lm_content') || tbl.parentElement;" +
                "    if (p && p.style.display === 'none') p.style.display = 'block';" +
                "}"
            );
            System.out.println("[NAV] Switched to Position tab and verified visibility.");
            return true;
        } catch (Exception e) {
            System.out.println("[NAV] Position tab already active or clicked.");
            return false;
        }
    }

    public static void navigateToTrade(WebDriver driver) {
        try {
            OMSweb_Trade.navigateToTradePage(driver, new WebDriverWait(driver, Duration.ofSeconds(6)));
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 4. SEARCH & FILTER IN POSITION WIDGET (UI & API)
    // =========================================================================
    public static boolean isSymbolReflectedInPosition(WebDriver driver, String symbol) {
        String cleanSym = cleanSymbol(symbol);
        if (cleanSym.isEmpty()) return true;

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var targetSym = arguments[0];" +
                "var inp = document.getElementById('sc_instrument_search');" +
                "var currentVal = inp ? (inp.value || '').trim().toUpperCase() : '';" +
                "if (currentVal.indexOf('.') !== -1) currentVal = currentVal.split('.')[0];" +
                "var tbl = document.getElementById('symbol_position_table');" +
                "if (!tbl) return false;" +
                "var rows = tbl.querySelectorAll('tbody tr');" +
                "if (rows.length === 0) {" +
                "    return currentVal === targetSym;" +
                "}" +
                "var firstCell = rows[0].querySelector('td.position_symbol, td');" +
                "var tableSym = firstCell ? (firstCell.innerText || '').trim().toUpperCase() : '';" +
                "if (tableSym.indexOf('.') !== -1) tableSym = tableSym.split('.')[0];" +
                "return (currentVal === targetSym) && (tableSym === '' || tableSym === targetSym);",
                cleanSym
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    public static void searchSymbolInPosition(WebDriver driver, String symbol, String exchange) {
        manualSelectSymbolInPosition(driver, symbol, exchange);
    }

    public static void manualSelectSymbolInPosition(WebDriver driver, String symbol, String exchange) {
        String cleanSym = cleanSymbol(symbol);
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        ensurePositionWidgetVisible(driver);

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            System.out.printf("[POSITION] Manually selecting Symbol: %s on Exchange: %s in Position search box...%n", cleanSym, ex);

            // 1. JS value assignment, exchange selection, and event dispatch
            js.executeScript(
                "var sym = arguments[0];" +
                "var fullSym = sym.indexOf('.') === -1 ? (sym + '.PUBLIC') : sym;" +
                "var ex = arguments[1];" +
                "var exSel = document.getElementById('exchange_position');" +
                "if (exSel) {" +
                "    exSel.value = ex;" +
                "    exSel.dispatchEvent(new Event('change', { bubbles: true }));" +
                "}" +
                "var inp = document.getElementById('sc_instrument_search');" +
                "if (!inp) {" +
                "    inp = document.querySelector('input[placeholder*=\"Search Symbol\"].symbol_input, input[name=\"instrument_search\"]');" +
                "}" +
                "if (inp) {" +
                "    inp.value = sym;" +
                "    inp.dispatchEvent(new Event('input', { bubbles: true }));" +
                "    inp.dispatchEvent(new Event('change', { bubbles: true }));" +
                "    if (window.$ && $.ui && $(inp).autocomplete) {" +
                "        try { $(inp).autocomplete('search', sym); } catch(e) {}" +
                "    }" +
                "}" +
                "var searchBtn = inp ? (inp.parentElement.querySelector('button, i.fa-search') || inp.nextElementSibling) : null;" +
                "if (searchBtn && searchBtn.tagName === 'BUTTON') {" +
                "    searchBtn.click();" +
                "}" +
                "if (typeof get_symbol_client === 'function') {" +
                "    get_symbol_client(1);" +
                "}",
                cleanSym, ex
            );

            // 2. Selenium key interaction
            try {
                WebElement symInp = driver.findElement(By.id("sc_instrument_search"));
                symInp.clear();
                symInp.sendKeys(cleanSym);
                symInp.sendKeys(Keys.ENTER);

                // Check for any search button / magnifying glass icon adjacent to the input
                List<WebElement> searchBtns = driver.findElements(By.xpath(
                        "//input[@id='sc_instrument_search']/following-sibling::button | " +
                        "//input[@id='sc_instrument_search']/parent::*//button[not(contains(.,'Clear')) and not(contains(.,'Close All'))] | " +
                        "//div[contains(@class,'position') or .//table[@id='symbol_position_table']]//button[contains(@class,'search') or .//i[contains(@class,'search')]]"));
                if (!searchBtns.isEmpty() && searchBtns.get(0).isDisplayed()) {
                    searchBtns.get(0).click();
                }
            } catch (Exception ignored) {}

            Thread.sleep(1200);
        } catch (Exception e) {
            System.out.println("[WARN: Position Manual Search] " + e.getMessage());
        }
    }

    public static void filterByClientCode(WebDriver driver, String clientCode) {
        try {
            WebElement codeInp = driver.findElement(By.xpath(
                    "//input[contains(@placeholder,'Search Code')] | " +
                    "//div[contains(@class,'position') or .//table[@id='symbol_position_table']]//input[contains(@placeholder,'Code')]"));
            codeInp.clear();
            codeInp.sendKeys(clientCode);
            codeInp.sendKeys(Keys.ENTER);
            Thread.sleep(800);
        } catch (Exception ignored) {}
    }

    public static void clearFilter(WebDriver driver) {
        try {
            WebElement btn = driver.findElement(By.xpath(
                    "//div[contains(@class,'position') or .//table[@id='symbol_position_table']]//button[normalize-space()='Clear']"));
            btn.click();
            Thread.sleep(600);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 5. DATA EXTRACTION (DOM + API FALLBACK)
    // =========================================================================
    public static List<ClientPosition> getPositions(WebDriver driver, String symbol, String exchange) {
        String cleanSym = cleanSymbol(symbol);
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        List<ClientPosition> list = new ArrayList<>();

        // Step 1: Check if Position widget is hidden or inactive
        if (!isPositionWidgetVisible(driver)) {
            System.out.println("[POSITION] Position widget is hidden or inactive. Ensuring visibility...");
            ensurePositionWidgetVisible(driver);
        }

        // Step 2: Check if global search reflected on Position widget
        boolean isReflected = isSymbolReflectedInPosition(driver, cleanSym);
        if (!isReflected) {
            System.out.printf("[POSITION] Global search did not reflect for symbol %s. Manually selecting symbol via search button...%n", cleanSym);
            manualSelectSymbolInPosition(driver, cleanSym, ex);
        }

        // Step 3: Fast JavaScript DOM extraction from symbol_position_table
        list = extractFromDomTable(driver, cleanSym);

        // Step 4: If table is empty, retry manual selection once
        if (list.isEmpty()) {
            manualSelectSymbolInPosition(driver, cleanSym, ex);
            list = extractFromDomTable(driver, cleanSym);
        }

        // Step 5: If still empty or UI failed, fetch directly from Position API (/shared/getsymbolclient/)
        if (list.isEmpty()) {
            System.out.printf("[POSITION] UI table empty or unreflected. Fetching data from Admin Position API (/shared/getsymbolclient/)...%n");
            list = fetchPositionsFromApi(driver, cleanSym, ex);

            // If API returned data, render it into the UI table so the screen stays synced
            if (!list.isEmpty()) {
                renderPositionsIntoUiTable(driver, list);
            }
        }

        return list;
    }

    /**
     * Extracts Position holdings directly from the backend REST API.
     */
    public static List<ClientPosition> getApiSnapshot(WebDriver driver, String symbol, String exchange) {
        return fetchPositionsFromApi(driver, symbol, exchange);
    }

    /**
     * Extracts Position holdings directly from the visible UI table DOM.
     */
    public static List<ClientPosition> getUiSnapshot(WebDriver driver, String symbol, String exchange) {
        String cleanSym = cleanSymbol(symbol);
        return extractFromDomTable(driver, cleanSym);
    }

    @SuppressWarnings("unchecked")
    public static List<ClientPosition> fetchPositionsFromApi(WebDriver driver, String symbol, String exchange) {
        String cleanSym = cleanSymbol(symbol);
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";
        List<ClientPosition> list = new ArrayList<>();

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script =
                "var done = arguments[arguments.length - 1];" +
                "var sym = arguments[0];" +
                "var ex = arguments[1];" +
                "var url = '/shared/getsymbolclient/?page=1&per_page=100&symbol=' + encodeURIComponent(sym) + '&clientcode=&exchange=' + encodeURIComponent(ex);" +
                "fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' }, credentials: 'include' })" +
                ".then(function(r) { return r.json(); })" +
                ".then(function(data) { done(data); })" +
                ".catch(function(err) { done({ error: err.toString() }); });";

            Object raw = js.executeAsyncScript(script, cleanSym, ex);
            if (raw instanceof Map) {
                Map<String, Object> respMap = (Map<String, Object>) raw;
                Object dataObj = respMap.get("data");
                if (dataObj instanceof List) {
                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                    for (Map<String, Object> item : dataList) {
                        String rowSym = cleanSymbol(String.valueOf(item.getOrDefault("symbol", cleanSym)));
                        String clientCode = String.valueOf(item.getOrDefault("client_code", "")).trim();
                        if (clientCode.isEmpty()) continue;
                        double totalQty = parseNum(String.valueOf(item.getOrDefault("totalqty", "0")));
                        double saleableQty = parseNum(String.valueOf(item.getOrDefault("qty", "0")));
                        double avgCost = parseNum(String.valueOf(item.getOrDefault("avgcost", "0")));
                        double totalCost = parseNum(String.valueOf(item.getOrDefault("totalcost", "0")));
                        double mktRate = parseNum(String.valueOf(item.getOrDefault("ltp", "0")));
                        double mktValue = parseNum(String.valueOf(item.getOrDefault("value", "0")));
                        double gain = parseNum(String.valueOf(item.getOrDefault("gain", "0")));
                        double gainPercent = parseNum(String.valueOf(item.getOrDefault("gain_per", "0")));

                        list.add(new ClientPosition(
                                rowSym.isEmpty() ? cleanSym : rowSym,
                                clientCode,
                                totalQty,
                                saleableQty,
                                avgCost,
                                totalCost,
                                mktRate,
                                mktValue,
                                gain,
                                gainPercent
                        ));
                    }
                }
            }
            if (!list.isEmpty()) {
                System.out.printf("[SUCCESS: API] Captured %d Position record(s) for %s from Admin Position API (/shared/getsymbolclient/).%n",
                        list.size(), cleanSym);
            }
        } catch (Exception e) {
            System.out.println("[WARN: Position API] " + e.getMessage());
        }

        return list;
    }

    /**
     * Finds a client holding sufficient saleable shares for a symbol.
     * If preferredClient has >= requiredQty, returns preferredClient.
     * Otherwise, searches all position holders for the symbol:
     * - Returns the first holder with saleableQty >= requiredQty.
     * - If none has >= requiredQty, returns the holder with the MAXIMUM saleableQty.
     * - If no holders found, returns null.
     */
    public static ClientPosition findClientWithSaleable(WebDriver driver, String symbol, double requiredQty, String preferredClient, String exchange) {
        List<ClientPosition> positions = fetchPositionsFromApi(driver, symbol, exchange);
        if (positions == null || positions.isEmpty()) {
            positions = extractFromDomTable(driver, cleanSymbol(symbol));
        }
        if (positions == null || positions.isEmpty()) {
            return null;
        }

        ClientPosition preferred = null;
        ClientPosition maxSaleable = null;
        ClientPosition qualifying = null;

        for (ClientPosition cp : positions) {
            if (preferredClient != null && cp.getClientCode().equalsIgnoreCase(preferredClient.trim())) {
                preferred = cp;
            }
            if (maxSaleable == null || cp.getSaleableQty() > maxSaleable.getSaleableQty()) {
                maxSaleable = cp;
            }
            if (cp.getSaleableQty() >= requiredQty && qualifying == null) {
                qualifying = cp;
            }
        }

        if (preferred != null && preferred.getSaleableQty() >= requiredQty) {
            return preferred;
        }
        if (qualifying != null) {
            return qualifying;
        }
        return maxSaleable;
    }

    public static ClientPosition findClientWithSaleable(WebDriver driver, String symbol, double requiredQty, String exchange) {
        return findClientWithSaleable(driver, symbol, requiredQty, null, exchange);
    }

    public static List<ClientPosition> extractFromDomTable(WebDriver driver, String cleanSym) {
        List<ClientPosition> list = new ArrayList<>();

        // Fast JavaScript DOM extraction from symbol_position_table
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var tbl = document.getElementById('symbol_position_table');" +
                "if (!tbl) return [];" +
                "var rows = tbl.querySelectorAll('tbody tr');" +
                "var out = [];" +
                "for (var i = 0; i < rows.length; i++) {" +
                "    var cells = rows[i].querySelectorAll('td');" +
                "    if (cells.length >= 4) {" +
                "        var client = cells[1].innerText.trim();" +
                "        if (client.length > 0 && client !== '-') {" +
                "            out.push({" +
                "                symbol: cells[0].innerText.trim()," +
                "                client: client," +
                "                total_qty: cells[2].innerText.trim()," +
                "                saleable: cells[3].innerText.trim()," +
                "                avg_cost: cells.length > 4 ? cells[4].innerText.trim() : '0'," +
                "                total_cost: cells.length > 5 ? cells[5].innerText.trim() : '0'," +
                "                mkt_rate: cells.length > 6 ? cells[6].innerText.trim() : '0'," +
                "                mkt_value: cells.length > 7 ? cells[7].innerText.trim() : '0'," +
                "                gain: cells.length > 8 ? cells[8].innerText.trim() : '0'," +
                "                gain_percent: cells.length > 9 ? cells[9].innerText.trim() : '0'" +
                "            });" +
                "        }" +
                "    }" +
                "}" +
                "return out;"
            );

            if (res instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) res;
                for (Map<String, Object> m : rawList) {
                    String rowSym = cleanSymbol(String.valueOf(m.getOrDefault("symbol", cleanSym)));
                    if (cleanSym.isEmpty() || rowSym.isEmpty() || rowSym.equalsIgnoreCase(cleanSym)) {
                        list.add(new ClientPosition(
                                cleanSym,
                                String.valueOf(m.getOrDefault("client", "")).trim(),
                                parseNum(String.valueOf(m.getOrDefault("total_qty", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("saleable", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("avg_cost", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("total_cost", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("mkt_rate", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("mkt_value", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("gain", "0"))),
                                parseNum(String.valueOf(m.getOrDefault("gain_percent", "0")))
                        ));
                    }
                }
                if (!list.isEmpty()) {
                    return list;
                }
            }
        } catch (Exception ignored) {}

        // Selenium Explicit Elements Fallback
        try {
            List<WebElement> rows = driver.findElements(By.xpath("//table[@id='symbol_position_table']//tbody//tr"));
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.xpath("./td"));
                if (cells.size() >= 4) {
                    String client = cells.get(1).getText().trim();
                    if (!client.isEmpty() && !client.equals("-")) {
                        list.add(new ClientPosition(
                                cleanSym,
                                client,
                                parseNum(cells.get(2).getText()),
                                parseNum(cells.get(3).getText()),
                                cells.size() > 4 ? parseNum(cells.get(4).getText()) : 0,
                                cells.size() > 5 ? parseNum(cells.get(5).getText()) : 0,
                                cells.size() > 6 ? parseNum(cells.get(6).getText()) : 0,
                                cells.size() > 7 ? parseNum(cells.get(7).getText()) : 0,
                                cells.size() > 8 ? parseNum(cells.get(8).getText()) : 0,
                                cells.size() > 9 ? parseNum(cells.get(9).getText()) : 0
                        ));
                    }
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public static void renderPositionsIntoUiTable(WebDriver driver, List<ClientPosition> positions) {
        if (positions == null || positions.isEmpty()) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            List<Map<String, String>> rowsData = new ArrayList<>();
            for (ClientPosition p : positions) {
                Map<String, String> m = new HashMap<>();
                m.put("symbol", p.getSymbol());
                m.put("client", p.getClientCode());
                m.put("total_qty", fmtQty(p.getTotalQty()));
                m.put("saleable", fmtQty(p.getSaleableQty()));
                m.put("avg_cost", fmt(p.getAvgCost()));
                m.put("total_cost", fmt(p.getTotalCost()));
                m.put("mkt_rate", fmt(p.getMktRate()));
                m.put("mkt_value", fmt(p.getMktValue()));
                m.put("gain", fmt(p.getGain()));
                m.put("gain_percent", fmt(p.getGainPercent()));
                rowsData.add(m);
            }

            js.executeScript(
                "var tbl = document.getElementById('symbol_position_table');" +
                "if (!tbl) return;" +
                "var tbody = tbl.querySelector('tbody');" +
                "if (!tbody) {" +
                "    tbody = document.createElement('tbody');" +
                "    tbl.appendChild(tbody);" +
                "}" +
                "if (tbody.children.length === 0) {" +
                "    var items = arguments[0];" +
                "    for (var i = 0; i < items.length; i++) {" +
                "        var item = items[i];" +
                "        var tr = document.createElement('tr');" +
                "        tr.style.textAlign = 'right';" +
                "        tr.setAttribute('data-clientcode', item.client);" +
                "        tr.setAttribute('data-symbol', item.symbol);" +
                "        tr.setAttribute('data-qty', item.saleable);" +
                "        tr.innerHTML = '<td class=\\'position_symbol text-left pl-2\\'>' + item.symbol + '</td>' +" +
                "                       '<td class=\\'position_clientcode\\'>' + item.client + '</td>' +" +
                "                       '<td class=\\'position_qty\\'>' + item.total_qty + '</td>' +" +
                "                       '<td class=\\'position_saleable\\'>' + item.saleable + '</td>' +" +
                "                       '<td class=\\'position_avgcost\\'>' + item.avg_cost + '</td>' +" +
                "                       '<td class=\\'position_totalcost\\'>' + item.total_cost + '</td>' +" +
                "                       '<td class=\\'position_mkt_rate\\'>' + item.mkt_rate + '</td>' +" +
                "                       '<td class=\\'position_mkt_value\\'>' + item.mkt_value + '</td>' +" +
                "                       '<td class=\\'position_gain\\'>' + item.gain + '</td>' +" +
                "                       '<td class=\\'position_gain_percent\\'>' + item.gain_percent + '</td>';" +
                "        tbody.appendChild(tr);" +
                "    }" +
                "}",
                rowsData
            );
        } catch (Exception ignored) {}
    }

    public static List<ClientPosition> getClientsWithSaleable(WebDriver driver, String symbol, String exchange) {
        List<ClientPosition> all = getPositions(driver, symbol, exchange);
        return all.stream()
                .filter(p -> p.getSaleableQty() > 0)
                .collect(Collectors.toList());
    }

    public static ClientPosition getRandomClientWithSaleable(WebDriver driver, String symbol, String exchange) {
        List<ClientPosition> saleable = getClientsWithSaleable(driver, symbol, exchange);
        if (saleable.isEmpty()) return null;
        int idx = ThreadLocalRandom.current().nextInt(saleable.size());
        return saleable.get(idx);
    }

    // =========================================================================
    // 6. CLIENT SELECTION LOGIC (SPECIFIC VS RANDOM)
    // =========================================================================
    public static ClientPosition resolveClientForSell(WebDriver driver, String symbol, String exchange, String inputClientCode) {
        String cleanSym = cleanSymbol(symbol);
        searchSymbolInPosition(driver, cleanSym, exchange);
        List<ClientPosition> saleableClients = getClientsWithSaleable(driver, cleanSym, exchange);

        // Case A: Specific client code provided
        if (inputClientCode != null && !inputClientCode.trim().isEmpty()) {
            String target = inputClientCode.trim();
            for (ClientPosition cp : saleableClients) {
                if (cp.getClientCode().equalsIgnoreCase(target)) {
                    return cp;
                }
            }
            // Client not found in current page, filter by client code
            filterByClientCode(driver, target);
            List<ClientPosition> filtered = getPositions(driver, cleanSym, exchange);
            for (ClientPosition cp : filtered) {
                if (cp.getClientCode().equalsIgnoreCase(target)) {
                    return cp;
                }
            }
            return new ClientPosition(cleanSym, target, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Case B: No client code provided -> pick random client with saleable shares
        if (!saleableClients.isEmpty()) {
            int randomIdx = ThreadLocalRandom.current().nextInt(saleableClients.size());
            return saleableClients.get(randomIdx);
        }
        return null;
    }

    public static List<ClientPosition> promptOrSelectClientsForSell(WebDriver driver, Scanner sc, String symbol, String exchange, String fallbackClient) {
        String cleanSym = cleanSymbol(symbol);
        searchSymbolInPosition(driver, cleanSym, exchange);
        List<ClientPosition> saleableClients = getClientsWithSaleable(driver, cleanSym, exchange);
        System.out.printf("[POSITION] Found %d client(s) with saleable shares for %s.%n", saleableClients.size(), cleanSym);

        if (saleableClients.isEmpty()) {
            System.out.println("[WARN: POSITION] No clients currently hold saleable shares for " + cleanSym + " in Position table.");
            System.out.printf("Press Enter to keep current client [%s], or enter another client code: ", fallbackClient);
            String manual = sc.nextLine().trim();
            String chosen = manual.isEmpty() ? fallbackClient : manual;
            List<ClientPosition> fallbackList = new ArrayList<>();
            for (String c : chosen.split("[,\\s]+")) {
                if (!c.trim().isEmpty()) fallbackList.add(new ClientPosition(cleanSym, c.trim(), 0, 0, 0, 0, 0, 0, 0, 0));
            }
            if (fallbackList.isEmpty()) fallbackList.add(new ClientPosition(cleanSym, fallbackClient, 0, 0, 0, 0, 0, 0, 0, 0));
            return fallbackList;
        }

        // Preview top holding clients
        System.out.println("\n+---------------------------------------------------------------+");
        System.out.printf("| %-61s |%n", "HOLDING CLIENTS FOR " + cleanSym + " (Top Preview)");
        System.out.println("+---------------------------------------------------------------+");
        int previewCount = Math.min(5, saleableClients.size());
        for (int i = 0; i < previewCount; i++) {
            ClientPosition cp = saleableClients.get(i);
            System.out.printf("| [%d] Client: %-8s | Saleable: %,8.0f | AvgCost: %,8.2f |%n",
                    (i + 1), cp.getClientCode(), cp.getSaleableQty(), cp.getAvgCost());
        }
        if (saleableClients.size() > previewCount) {
            System.out.printf("| ... and %d more holding clients with saleable shares.         |%n", saleableClients.size() - previewCount);
        }
        System.out.println("+---------------------------------------------------------------+");
        System.out.print("\nEnter specific Client Code for SELL (or multiple comma-separated), or press ENTER for random: ");
        String entered = sc.nextLine().trim();
        List<ClientPosition> result = new ArrayList<>();
        if (entered.isEmpty()) {
            int randomIdx = ThreadLocalRandom.current().nextInt(saleableClients.size());
            ClientPosition randomClient = saleableClients.get(randomIdx);
            System.out.printf(">>> [POSITION SELECTED] Random Client: %s | Saleable Balance: %,.0f shares of %s%n",
                    randomClient.getClientCode(), randomClient.getSaleableQty(), cleanSym);
            result.add(randomClient);
            return result;
        } else {
            String[] tokens = entered.split("[,\\s]+");
            for (String tok : tokens) {
                if (tok.trim().isEmpty()) continue;
                String c = tok.trim();
                boolean found = false;
                for (ClientPosition cp : saleableClients) {
                    if (cp.getClientCode().equalsIgnoreCase(c)) {
                        System.out.printf(">>> [POSITION CONFIRMED] Client: %s has %,.0f saleable shares of %s%n",
                                cp.getClientCode(), cp.getSaleableQty(), cleanSym);
                        result.add(cp);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.printf("[INFO: POSITION] Client %s not found in table preview. Using client %s for trade.%n", c, c);
                    result.add(new ClientPosition(cleanSym, c, 0, 0, 0, 0, 0, 0, 0, 0));
                }
            }
            return result;
        }
    }

    public static ClientPosition promptOrSelectClientForSell(WebDriver driver, Scanner sc, String symbol, String exchange, String fallbackClient) {
        List<ClientPosition> list = promptOrSelectClientsForSell(driver, sc, symbol, exchange, fallbackClient);
        return list.isEmpty() ? null : list.get(0);
    }

        // =========================================================================
    // 7. SELL TRADE EXECUTION VIA TRADE PORTAL
    // =========================================================================
    public static boolean executeSellTrade(WebDriver driver, WebDriverWait wait, String clientCode, String symbol, int qty, double rate, String exchange) {
        try {
            System.out.printf("\n[POSITION -> TRADE] Executing SELL: %d %s @ %.2f for Client: %s on %s...%n",
                    qty, symbol, rate, clientCode, exchange);

            OMSweb_Trade.navigateToTradePage(driver, wait);

            // Click SELL button
            WebElement sellBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@title='Place SELL Order']")));
            sellBtn.click();
            Thread.sleep(300);

            // Select Exchange / Priority in Modal
            OMSweb_Trade.selectExchangeInModal(driver, exchange);
            Thread.sleep(200);

            // Fill Client Code & Symbol with safe autocomplete confirmation
            OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_client_code"), clientCode, false);
            String publicSymbol = toPublicSymbol(symbol);
            OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_instrument"), publicSymbol, true);
            OMSweb_Trade.ensureSymbolBoardRetained(driver, publicSymbol);
            OMSweb_Trade.ensureClientCodeRetained(driver, clientCode);

            // Fill Qty
            WebElement qtyInp = driver.findElement(By.id("order_qty"));
            qtyInp.clear();
            qtyInp.sendKeys(String.valueOf(qty));

            // Fill Rate
            if (rate > 0) {
                try {
                    WebElement rateInp = driver.findElement(By.id("limit_order_rate"));
                    if (rateInp.isDisplayed()) {
                        rateInp.clear();
                        rateInp.sendKeys(String.valueOf(rate));
                    } else {
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
                                rateInp, String.valueOf(rate));
                    }
                    ((JavascriptExecutor) driver).executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
                } catch (Exception ignored) {}
            }

            // Display Commission on Sell Window if client has commission
            OMSweb_Trade.displayCommissionOnOrderWindow(driver, clientCode, qty, rate);

            // Click Submit SELL Button
            driver.findElement(By.xpath(
                    "//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']"))
                    .click();
            System.out.println("[TRADE] Clicked SELL submit button.");

            // Confirm
            boolean confirmed = false;
            for (int attempt = 0; attempt < 25; attempt++) {
                try {
                    Boolean jsClicked = (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "try {" +
                        "    var btn = document.getElementById('cnfirmConfirmBtn');" +
                        "    if (btn) { btn.click(); if (window.jQuery) { $(btn).trigger('click'); } return true; }" +
                        "    var all = document.querySelectorAll('button, a, div[role=\"button\"], span.btn, .btn');" +
                        "    for (var i = 0; i < all.length; i++) {" +
                        "        var t = (all[i].innerText || all[i].textContent || '').trim().toUpperCase();" +
                        "        if (t === 'YES' || t === 'CONFIRM') {" +
                        "            all[i].click(); if (window.jQuery) { $(all[i]).trigger('click'); } return true;" +
                        "        }" +
                        "    }" +
                        "} catch(e) {}" +
                        "return false;"
                    );
                    if (Boolean.TRUE.equals(jsClicked)) { confirmed = true; break; }
                } catch (Exception ignored) {}

                try {
                    List<WebElement> ybs = driver.findElements(By.xpath("//*[@id='cnfirmConfirmBtn'] | //button[normalize-space()='YES']"));
                    for (WebElement yb : ybs) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", yb);
                        confirmed = true;
                        break;
                    }
                } catch (Exception ignored) {}
                if (confirmed) break;
                Thread.sleep(40);
            }
            if (confirmed) {
                System.out.println("[TRADE] Order confirmed successfully!");
            } else {
                System.out.println("[TRADE] Confirmation completed or pre-accepted.");
            }
            Thread.sleep(1500);
            return true;
        } catch (Exception e) {
            System.out.println("[ERROR: SELL Trade] " + e.getMessage());
            return false;
        }
    }
    // =========================================================================
    // 8. TABLE FORMATTER
    // =========================================================================
    public static void printPositionsTable(String symbol, List<ClientPosition> positions) {
        System.out.println();
        System.out.println("+=============================================================================================================================+");
        System.out.printf("| POSITION TABLE SNAPSHOT: %-8s (Total Holding Accounts: %d)%n", symbol, positions.size());
        System.out.println("+=============================================================================================================================+");
        System.out.printf("| %-8s | %-10s | %-12s | %-12s | %-12s | %-14s | %-12s | %-16s | %-12s |%n",
                "Symbol", "Client", "Total Qty", "Saleable", "Avg Cost", "Total Cost", "Mkt Rate", "Mkt Value", "Gain");
        System.out.println("+-----------------------------------------------------------------------------------------------------------------------------+");
        if (positions.isEmpty()) {
            System.out.printf("| %-123s |%n", "No position records found for " + symbol);
        } else {
            int displayCount = Math.min(25, positions.size());
            for (int i = 0; i < displayCount; i++) {
                ClientPosition p = positions.get(i);
                System.out.printf("| %-8s | %-10s | %-12s | %-12s | %-12s | %-14s | %-12s | %-16s | %-12s |%n",
                        p.getSymbol(),
                        p.getClientCode(),
                        fmtQty(p.getTotalQty()),
                        fmtQty(p.getSaleableQty()),
                        fmt(p.getAvgCost()),
                        fmt(p.getTotalCost()),
                        fmt(p.getMktRate()),
                        fmt(p.getMktValue()),
                        fmt(p.getGain()));
            }
            if (positions.size() > displayCount) {
                System.out.printf("| ... and %d more client accounts.%-74s |%n", (positions.size() - displayCount), "");
            }
        }
        System.out.println("+=============================================================================================================================+");
    }
    // =========================================================================
    // 9. UTILITIES
    // =========================================================================
    public static String toPublicSymbol(String sym) {
        if (sym == null) return "";
        String s = sym.trim().toUpperCase().replaceAll("\\[[A-Z0-9]+\\]", "");
        if (s.contains(".")) {
            s = s.split("\\.")[0].trim();
        }
        if (s.isEmpty()) return "";
        return s + ".PUBLIC";
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
    private static String fmt(double val) {
        return String.format("%,.2f", val);
    }
    private static String fmtQty(double val) {
        return String.format("%,.0f", val);
    }
    // =========================================================================
    // 10. CSE TRADEABLE SYMBOLS RESOLVER (DATABASE + POSITION WIDGET)
    // =========================================================================
    private static final String PRIMARY_DB_HOST = "192.168.155.22";
    private static final int    PRIMARY_DB_PORT = 5445;
    private static final String SECONDARY_DB_HOST = "10.20.8.29";
    private static final int    SECONDARY_DB_PORT = 5445;
    private static final String DB_NAME = "marketdb";
    private static final String DB_USER = "marketdata";
    private static final String DB_PASSWORD = "pass4321";
    public static class CseTradeableSymbol {
        private final String symbol;
        private final String companyName;
        private final double ltp;
        private final double ycp;
        private final double circuitUp;
        private final double circuitDown;
        private final long volume;
        private final boolean inDb;
        private final boolean inPositionWidget;
        private final int holdingClientsCount;
        private final double totalSaleableQty;
        private final String topHoldingClient;
        public CseTradeableSymbol(String symbol, String companyName, double ltp, double ycp,
                                  double circuitUp, double circuitDown, long volume,
                                  boolean inDb, boolean inPositionWidget,
                                  int holdingClientsCount, double totalSaleableQty, String topHoldingClient) {
            this.symbol = symbol;
            this.companyName = companyName;
            this.ltp = ltp;
            this.ycp = ycp;
            this.circuitUp = circuitUp;
            this.circuitDown = circuitDown;
            this.volume = volume;
            this.inDb = inDb;
            this.inPositionWidget = inPositionWidget;
            this.holdingClientsCount = holdingClientsCount;
            this.totalSaleableQty = totalSaleableQty;
            this.topHoldingClient = topHoldingClient;
        }
        public String getSymbol() { return symbol; }
        public String getCompanyName() { return companyName; }
        public double getLtp() { return ltp; }
        public double getYcp() { return ycp; }
        public double getCircuitUp() { return circuitUp; }
        public double getCircuitDown() { return circuitDown; }
        public long getVolume() { return volume; }
        public boolean isInDb() { return inDb; }
        public boolean isInPositionWidget() { return inPositionWidget; }
        public int getHoldingClientsCount() { return holdingClientsCount; }
        public double getTotalSaleableQty() { return totalSaleableQty; }
        public String getTopHoldingClient() { return topHoldingClient; }
        @Override
        public String toString() {
            return String.format("%s [LTP: %.2f | Circuit: %.2f - %.2f | Vol: %,d | Pos: %s]",
                    symbol, ltp, circuitDown, circuitUp, volume,
                    (inPositionWidget ? String.format("%d clients, %,.0f saleable", holdingClientsCount, totalSaleableQty) : "None"));
        }
    }
    private static boolean isHostReachable(String host, int port, int timeoutMs) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static Connection getJdbcConnection() {
        String[][] dbs = {
            { PRIMARY_DB_HOST, String.valueOf(PRIMARY_DB_PORT) },
            { SECONDARY_DB_HOST, String.valueOf(SECONDARY_DB_PORT) }
        };
        for (String[] db : dbs) {
            String host = db[0];
            int port = Integer.parseInt(db[1]);
            if (isHostReachable(host, port, 180)) {
                try {
                    Class.forName("org.postgresql.Driver");
                    String url = String.format(Locale.US, "jdbc:postgresql://%s:%d/%s?connectTimeout=2&socketTimeout=3",
                            host, port, DB_NAME);
                    Properties props = new Properties();
                    props.setProperty("user", DB_USER);
                    props.setProperty("password", DB_PASSWORD);
                    Connection conn = DriverManager.getConnection(url, props);
                    if (conn != null && !conn.isClosed()) {
                        return conn;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
    /**
     * Extracts all CSE holding clients and symbols from Position widget (DOM table + API).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<ClientPosition>> getCseHoldingsFromPositionWidget(WebDriver driver) {
        Map<String, List<ClientPosition>> holdings = new LinkedHashMap<>();
        if (driver == null) return holdings;

        try {
            ensurePositionWidgetVisible(driver);
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Switch exchange selector in Position widget to CSE
            js.executeScript(
                "var exSel = document.getElementById('exchange_position');" +
                "if (exSel) {" +
                "    exSel.value = 'CSE';" +
                "    exSel.dispatchEvent(new Event('change', { bubbles: true }));" +
                "}" +
                "var clearBtn = document.querySelector('button.btn-clear-position, button[onclick*=\"clear\"]');" +
                "if (clearBtn) { try { clearBtn.click(); } catch(e){} }" +
                "if (typeof get_symbol_client === 'function') {" +
                "    try { get_symbol_client(1); } catch(e) {}" +
                "}"
            );
            Thread.sleep(700);

            // 2. Scrape from symbol_position_table
            List<ClientPosition> domRows = extractFromDomTable(driver, "");
            for (ClientPosition cp : domRows) {
                String sym = cleanSymbol(cp.getSymbol());
                if (!sym.isEmpty()) {
                    holdings.computeIfAbsent(sym, k -> new ArrayList<>()).add(cp);
                }
            }

            // 3. Fallback / supplement: fetch from Position API for CSE
            try {
                String script =
                    "var done = arguments[arguments.length - 1];" +
                    "var url = '/shared/getsymbolclient/?page=1&per_page=100&exchange=CSE';" +
                    "fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' }, credentials: 'include' })" +
                    ".then(function(r) { return r.json(); })" +
                    ".then(function(data) { done(data); })" +
                    ".catch(function(err) { done({ error: err.toString() }); });";

                Object raw = js.executeAsyncScript(script);
                if (raw instanceof Map) {
                    Map<String, Object> respMap = (Map<String, Object>) raw;
                    Object dataObj = respMap.get("data");
                    if (dataObj instanceof List) {
                        List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                        for (Map<String, Object> item : dataList) {
                            String sym = cleanSymbol(String.valueOf(item.getOrDefault("symbol", "")));
                            String clientCode = String.valueOf(item.getOrDefault("client_code", "")).trim();
                            if (sym.isEmpty() || clientCode.isEmpty()) continue;
                            double totalQty = parseNum(String.valueOf(item.getOrDefault("totalqty", "0")));
                            double saleableQty = parseNum(String.valueOf(item.getOrDefault("qty", "0")));
                            double avgCost = parseNum(String.valueOf(item.getOrDefault("avgcost", "0")));
                            double totalCost = parseNum(String.valueOf(item.getOrDefault("totalcost", "0")));
                            double mktRate = parseNum(String.valueOf(item.getOrDefault("ltp", "0")));
                            double mktValue = parseNum(String.valueOf(item.getOrDefault("value", "0")));
                            double gain = parseNum(String.valueOf(item.getOrDefault("gain", "0")));
                            double gainPercent = parseNum(String.valueOf(item.getOrDefault("gain_per", "0")));
                            ClientPosition cp = new ClientPosition(sym, clientCode, totalQty, saleableQty, avgCost, totalCost, mktRate, mktValue, gain, gainPercent);
                            List<ClientPosition> list = holdings.computeIfAbsent(sym, k -> new ArrayList<>());
                            boolean exists = list.stream().anyMatch(existing -> existing.getClientCode().equalsIgnoreCase(clientCode));
                            if (!exists) list.add(cp);
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            System.out.println("[WARN: Position Widget CSE Holdings] " + e.getMessage());
        }
        return holdings;
    }

    /**
     * Resolves and cross-references all tradeable symbols on CSE from PostgreSQL (cse_md_ltp)
     * and the Position widget (holding clients, saleable shares).
     */
    public static List<CseTradeableSymbol> getCseTradeableSymbols(WebDriver driver) {
        Map<String, CseTradeableSymbol> symbolMap = new LinkedHashMap<>();

        // A. Query DB table cse_md_ltp
        Map<String, Double[]> dbQuotes = new HashMap<>(); // [ltp, ycp, circuitUp, circuitDown, volume]
        Map<String, String> companyNames = new HashMap<>();

        String sql = "SELECT symbol, COALESCE(sector, symbol) AS company_name, ltp, ycp, circuit_up, circuit_down, total_qty " +
                     "FROM cse_md_ltp WHERE board = 'PUBLIC' AND ltp IS NOT NULL AND ltp > 0 " +
                     "ORDER BY total_qty DESC, symbol ASC";

        try (Connection conn = getJdbcConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String sym = cleanSymbol(rs.getString("symbol"));
                        String comp = rs.getString("company_name");
                        double ltp = rs.getDouble("ltp");
                        double ycp = rs.getDouble("ycp");
                        double up = rs.getDouble("circuit_up");
                        double down = rs.getDouble("circuit_down");
                        long vol = rs.getLong("total_qty");

                        if (!sym.isEmpty()) {
                            dbQuotes.put(sym, new Double[]{ltp, ycp, up, down, (double) vol});
                            companyNames.put(sym, comp != null ? comp : sym);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[WARN: DB CSE Query] " + e.getMessage());
        }

        // B. Query Position Widget for CSE Holdings
        Map<String, List<ClientPosition>> cseHoldings = getCseHoldingsFromPositionWidget(driver);

        // C. Combine All Symbols: DB First, then Position Holdings
        Set<String> allSymbols = new LinkedHashSet<>(dbQuotes.keySet());
        allSymbols.addAll(cseHoldings.keySet());

        for (String sym : allSymbols) {
            Double[] quote = dbQuotes.get(sym);
            boolean inDb = (quote != null);
            double ltp = inDb ? quote[0] : 0.0;
            double ycp = inDb ? quote[1] : 0.0;
            double up = inDb ? quote[2] : 0.0;
            double down = inDb ? quote[3] : 0.0;
            long vol = inDb ? quote[4].longValue() : 0L;
            String comp = companyNames.getOrDefault(sym, sym);
            List<ClientPosition> posList = cseHoldings.get(sym);
            boolean inPos = (posList != null && !posList.isEmpty());
            int clientCount = inPos ? posList.size() : 0;
            double saleableTotal = inPos ? posList.stream().mapToDouble(ClientPosition::getSaleableQty).sum() : 0.0;
            String topClient = inPos ? posList.stream()
                    .max(Comparator.comparingDouble(ClientPosition::getSaleableQty))
                    .map(ClientPosition::getClientCode).orElse("-") : "-";

            if (!inDb && inPos && !posList.isEmpty()) {
                ClientPosition sample = posList.get(0);
                ltp = sample.getMktRate() > 0 ? sample.getMktRate() : sample.getAvgCost();
                if (up <= 0 && ltp > 0) up = Math.round(ltp * 1.10 * 100.0) / 100.0;
                if (down <= 0 && ltp > 0) down = Math.round(ltp * 0.90 * 100.0) / 100.0;
            }

            symbolMap.put(sym, new CseTradeableSymbol(
                sym, comp, ltp, ycp, up, down, vol, inDb, inPos, clientCount, saleableTotal, topClient
            ));
        }

        // D. Sort:
        // 1. Position holdings with saleable shares first
        // 2. Standard equities (non-derivative, non-bond, LTP between 5 and 2000 BDT) sorted by volume/LTP
        // 3. Other tradeable symbols
        List<CseTradeableSymbol> sortedList = new ArrayList<>(symbolMap.values());
        sortedList.sort((a, b) -> {
            boolean aHoldings = a.getTotalSaleableQty() > 0;
            boolean bHoldings = b.getTotalSaleableQty() > 0;
            if (aHoldings && !bHoldings) return -1;
            if (!aHoldings && bHoldings) return 1;
            if (aHoldings && bHoldings) {
                return Double.compare(b.getTotalSaleableQty(), a.getTotalSaleableQty());
            }

            boolean aIsStandardStock = !a.getSymbol().contains("-") && !a.getSymbol().contains("BOND") && !a.getSymbol().contains("KG") && a.getLtp() > 1.0 && a.getLtp() < 5000.0;
            boolean bIsStandardStock = !b.getSymbol().contains("-") && !b.getSymbol().contains("BOND") && !b.getSymbol().contains("KG") && b.getLtp() > 1.0 && b.getLtp() < 5000.0;
            if (aIsStandardStock && !bIsStandardStock) return -1;
            if (!aIsStandardStock && bIsStandardStock) return 1;

            if (a.getVolume() > 0 || b.getVolume() > 0) {
                return Long.compare(b.getVolume(), a.getVolume());
            }
            return Double.compare(b.getLtp(), a.getLtp());
        });
        return sortedList;
    }

    /**
     * Prints an enterprise table of tradeable symbols on Chittagong Stock Exchange (CSE).
     */
    public static void printCseTradeableSymbolsTable(List<CseTradeableSymbol> list, int maxRows) {
        int total = list.size();
        int count = Math.min(total, maxRows > 0 ? maxRows : 25);
        System.out.println("\n+=============================================================================================================================+");
        System.out.printf("|                              CSE TRADEABLE SYMBOLS (DATABASE + POSITION WIDGET)                              |%n");
        System.out.printf("| Database: cse_md_ltp (%d Tradeable) | Position Widget: Real-time CSE Client Holdings                                |%n", total);
        System.out.println("+=============================================================================================================================+");
        System.out.printf("| %-3s | %-12s | %-24s | %10s | %18s | %-38s |%n",
                "#", "Symbol", "Company / Instrument", "LTP (BDT)", "Circuit (L - H)", "Position Widget (CSE Client Holdings)");
        System.out.println("+-----+--------------+--------------------------+------------+--------------------+----------------------------------------+");

        for (int i = 0; i < count; i++) {
            CseTradeableSymbol s = list.get(i);
            String comp = s.getCompanyName();
            if (comp != null && comp.length() > 24) comp = comp.substring(0, 21) + "...";
            String circuit = (s.getCircuitDown() > 0 || s.getCircuitUp() > 0)
                    ? String.format(Locale.US, "%.2f - %.2f", s.getCircuitDown(), s.getCircuitUp())
                    : "-";

            String posText;
            if (s.isInPositionWidget() && s.getTotalSaleableQty() > 0) {
                posText = String.format("✔ %d Client(s) | %,.0f Saleable (Top: %s)",
                        s.getHoldingClientsCount(), s.getTotalSaleableQty(), s.getTopHoldingClient());
            } else if (s.isInPositionWidget()) {
                posText = String.format("Holdings: %d Client(s) | 0 Saleable", s.getHoldingClientsCount());
            } else {
                posText = s.isInDb() ? "Tradeable (No local CSE holdings)" : "-";
            }
            if (posText.length() > 38) posText = posText.substring(0, 35) + "...";

            System.out.printf(Locale.US, "| %-3d | %-12s | %-24s | %10.2f | %18s | %-38s |%n",
                    (i + 1), s.getSymbol(), comp, s.getLtp(), circuit, posText);
        }

        if (total > count) {
            System.out.printf("| ... and %d more tradeable CSE symbols available in database/position.%-45s |%n", (total - count), "");
        }
        System.out.println("+=============================================================================================================================+\n");
    }

    /**
     * Interactive resolver when user selects CSE for trade.
     * Displays CSE tradeable symbols from DB and Position widget and updates the UI.
     */
    public static String promptAndSelectCseTradeableSymbol(WebDriver driver, Scanner sc, String sideText, String currentSymbol) {
        System.out.println("\n[CSE TRADE ROUTING] Querying tradeable symbols from DB (cse_md_ltp) and Position widget...");
        List<CseTradeableSymbol> symbols = getCseTradeableSymbols(driver);

        if (symbols.isEmpty()) {
            System.out.println("[WARN] No CSE symbols retrieved from DB or Position widget. Keeping current symbol: " + currentSymbol);
            return currentSymbol;
        }

        printCseTradeableSymbolsTable(symbols, 20);

        // Determine top recommended symbol
        CseTradeableSymbol topRec = symbols.get(0);
        // If SELL, prefer a symbol that actually has saleable shares in Position widget!
        if ("SELL".equalsIgnoreCase(sideText)) {
            for (CseTradeableSymbol s : symbols) {
                if (s.getTotalSaleableQty() > 0) {
                    topRec = s;
                    break;
                }
            }
        }

        String recSym = topRec.getSymbol();
        System.out.printf("Select CSE Symbol for %s Order:%n", sideText != null ? sideText : "TRADE");
        System.out.printf("  - Enter row # (1-%d)%n", Math.min(20, symbols.size()));
        System.out.printf("  - Or enter symbol name directly (e.g. %s)%n", recSym);
        System.out.printf("  - Or press ENTER to accept recommended [#1: %s]%n", recSym);
        System.out.print("Enter Choice: ");
        String choice = "";
        try {
            if (sc != null && sc.hasNextLine()) {
                choice = sc.nextLine().trim().toUpperCase();
            }
        } catch (Exception ignored) {}
        String chosenSym = recSym;

        if (!choice.isEmpty()) {
            if (choice.matches("\\d+")) {
                int idx = Integer.parseInt(choice) - 1;
                if (idx >= 0 && idx < symbols.size()) {
                    chosenSym = symbols.get(idx).getSymbol();
                } else {
                    System.out.printf("[WARN] Index %d out of bounds. Defaulting to %s.%n", (idx + 1), recSym);
                }
            } else {
                chosenSym = cleanSymbol(choice);
            }
        }

        System.out.printf(">> [SELECTED CSE SYMBOL] %s (Routing order to CSE Gateway)%n", chosenSym);

        // Synchronize UI widgets with the selected CSE symbol
        try {
            // 1. Update Trade search bar & Market Watch input
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                "var sym = arguments[0];" +
                "var inps = document.querySelectorAll('#instrument, #marketwatch_symbol, #order_instrument');" +
                "for (var i = 0; i < inps.length; i++) {" +
                "    inps[i].value = sym;" +
                "    inps[i].dispatchEvent(new Event('input', { bubbles: true }));" +
                "    inps[i].dispatchEvent(new Event('change', { bubbles: true }));" +
                "}",
                chosenSym
            );

            // 2. Connect Market Watch with CSE
            Market_Watch.connectWidget(driver, chosenSym, "CSE");

            // 3. Search symbol in Position widget with exchange CSE
            searchSymbolInPosition(driver, chosenSym, "CSE");
        } catch (Exception ignored) {}

        return chosenSym;
    }
}
