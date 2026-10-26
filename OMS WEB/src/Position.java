import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
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

    public static ClientPosition promptOrSelectClientForSell(WebDriver driver, Scanner sc, String symbol, String exchange, String fallbackClient) {
        String cleanSym = cleanSymbol(symbol);
        searchSymbolInPosition(driver, cleanSym, exchange);
        List<ClientPosition> saleableClients = getClientsWithSaleable(driver, cleanSym, exchange);

        System.out.printf("[POSITION] Found %d client(s) with saleable shares for %s.%n", saleableClients.size(), cleanSym);

        if (saleableClients.isEmpty()) {
            System.out.println("[WARN: POSITION] No clients currently hold saleable shares for " + cleanSym + " in Position table.");
            System.out.printf("Press Enter to keep current client [%s], or enter another client code: ", fallbackClient);
            String manual = sc.nextLine().trim();
            String chosen = manual.isEmpty() ? fallbackClient : manual;
            return new ClientPosition(cleanSym, chosen, 0, 0, 0, 0, 0, 0, 0, 0);
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

        System.out.print("\nEnter specific Client Code for SELL, or press ENTER to pick a random holding client: ");
        String entered = sc.nextLine().trim();

        if (entered.isEmpty()) {
            int randomIdx = ThreadLocalRandom.current().nextInt(saleableClients.size());
            ClientPosition randomClient = saleableClients.get(randomIdx);
            System.out.printf(">>> [POSITION SELECTED] Random Client: %s | Saleable Balance: %,.0f shares of %s%n",
                    randomClient.getClientCode(), randomClient.getSaleableQty(), cleanSym);
            return randomClient;
        } else {
            for (ClientPosition cp : saleableClients) {
                if (cp.getClientCode().equalsIgnoreCase(entered)) {
                    System.out.printf(">>> [POSITION CONFIRMED] Client: %s has %,.0f saleable shares of %s%n",
                            cp.getClientCode(), cp.getSaleableQty(), cleanSym);
                    return cp;
                }
            }
            System.out.printf("[INFO: POSITION] Client %s not found in table preview. Using client %s for trade.%n", entered, entered);
            return new ClientPosition(cleanSym, entered, 0, 0, 0, 0, 0, 0, 0, 0);
        }
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

            // Fill Client Code
            WebElement clientInp = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("order_client_code")));
            clientInp.click();
            clientInp.clear();
            clientInp.sendKeys(clientCode);

            // Fill Symbol
            WebElement symInp = driver.findElement(By.id("order_instrument"));
            symInp.clear();
            symInp.sendKeys(cleanSymbol(symbol));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
            symInp.sendKeys(Keys.ARROW_DOWN);
            symInp.sendKeys(Keys.ENTER);

            // Fill Qty
            WebElement qtyInp = driver.findElement(By.id("order_qty"));
            qtyInp.clear();
            qtyInp.sendKeys(String.valueOf(qty));

            // Fill Rate
            if (rate > 0) {
                try {
                    WebElement rateInp = driver.findElement(By.id("limit_order_rate"));
                    rateInp.clear();
                    rateInp.sendKeys(String.valueOf(rate));
                } catch (Exception ignored) {}
            }

            // Click Submit SELL Button
            driver.findElement(By.xpath(
                    "//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']"))
                    .click();
            System.out.println("[TRADE] Clicked SELL submit button.");

            // Confirm
            WebElement confirmBtn = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='YES']")));
            confirmBtn.click();
            System.out.println("[TRADE] Order confirmed successfully!");
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
}
