import org.openqa.selenium.WebDriver;
import java.util.Scanner;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Market_Watch {
    // =========================================================================
    // 1. SELECTORS & COLUMN DEFINITIONS
    // =========================================================================

    // Tab Selectors
    public static final By MARKET_WATCH_TAB = By.xpath(
            "//div[contains(@class,'lm_tab') and contains(.,'Market Watch')] | " +
            "//li[contains(@class,'lm_tab') and (.//span[contains(text(),'Market Watch')] or contains(.,'Market Watch'))] | " +
            "//a[normalize-space()='Market Watch'] | //div[@title='Market Watch']");

    public static final By TRADE_TAB = By.xpath(
            "//div[contains(@class,'lm_tab') and (contains(.,'Trade') or @title='Trade')] | " +
            "//li[contains(@class,'lm_tab') and (.//span[contains(text(),'Trade')] or contains(.,'Trade') or @title='Trade')]");

    // Controls Toolbar in Market Watch Widget Header
    public static final By INPUT_SEARCH_SYMBOL = By.xpath(
            "//input[@id='marketwatch_symbol'] | " +
            "//div[contains(@class,'market-watch') or contains(@class,'marketwatch')]//input[contains(@placeholder,'Search Symbol')] | " +
            "//input[@placeholder='Search Symbol']");

    public static final By BTN_CLEAR = By.xpath(
            "//button[normalize-space()='Clear' or contains(@class,'btn-danger')] | " +
            "//button[@id='marketwatch_clear'] | " +
            "//div[contains(@class,'marketwatch') or contains(@class,'market-watch')]//button[contains(.,'Clear')]");

    // 20 Exact UI Columns as displayed in the Market Watch Table:
    // Symbol | Sector | Category | LTP | CP | YCP | Vol | Chg | % Chg | B.Qty | BID | ASK | A.Qty | Trades | Turnover | Open | High | Low | L.VOL | Y.VOL
    public static final String[] MARKET_WATCH_COLUMNS = {
        "Symbol", "Sector", "Category", "LTP", "CP", "YCP", "Vol", "Chg", "% Chg",
        "B.Qty", "BID", "ASK", "A.Qty", "Trades", "Turnover", "Open", "High", "Low", "L.VOL", "Y.VOL"
    };

    // =========================================================================
    // 2. MAIN ENTRY POINT (Interactive Standalone Viewer)
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n=======================================================");
        System.out.println("             MARKET WATCH DATA VIEWER                  ");
        System.out.println("=======================================================");
        String symbol = readInputSafe(driver, By.id("order_instrument"), By.id("instrument"), By.id("marketwatch_symbol"));
        if (symbol.isEmpty()) {
            System.out.print("Enter Symbol to Search (or press ENTER for all rows, e.g. GP, ACFL): ");
            symbol = sc.nextLine().trim().toUpperCase();
        }

        connectWidget(driver, symbol, "DSE");

        if (!symbol.isEmpty()) {
            Map<String, String> row = getUiSnapshot(driver, symbol);
            printMarketWatchTable(symbol, row);
        } else {
            List<Map<String, String>> rows = getAllUiRows(driver, "", 25);
            printMarketWatchRows(rows, "TOP 25 MARKET WATCH LIVE ROWS");
        }
        System.out.println("=======================================================\n");
    }

    // =========================================================================
    // 3. NAVIGATION & CONNECTIVITY HELPERS
    // =========================================================================

    /**
     * Focuses or brings the Market Watch widget into visibility.
     */
    public static void navigateToMarketWatch(WebDriver driver) {
        if (driver == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean clicked = (Boolean) js.executeScript(
                "var tabs = Array.from(document.querySelectorAll('.lm_tab, li.lm_tab, div.lm_tab'));" +
                "var mwTab = tabs.find(function(t) {" +
                "    var title = (t.getAttribute('title') || t.innerText || t.textContent || '').trim().toLowerCase();" +
                "    return title === 'market watch' || title.indexOf('market watch') !== -1;" +
                "});" +
                "if (mwTab && mwTab.offsetParent !== null) {" +
                "    mwTab.click();" +
                "    return true;" +
                "}" +
                "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(250);
            } else {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
                WebElement tab = wait.until(ExpectedConditions.elementToBeClickable(MARKET_WATCH_TAB));
                tab.click();
                Thread.sleep(250);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Navigates safely back to Trade tab if not already visible.
     */
    public static void navigateToTrade(WebDriver driver) {
        if (driver == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean alreadyVisible = (Boolean) js.executeScript(
                "var inst = document.getElementById('order_instrument');" +
                "return inst && inst.offsetParent !== null;"
            );
            if (Boolean.TRUE.equals(alreadyVisible)) return;

            Boolean clicked = (Boolean) js.executeScript(
                "var tabs = Array.from(document.querySelectorAll('.lm_tab, li.lm_tab, div.lm_tab'));" +
                "var tradeTab = tabs.find(function(t) {" +
                "    var title = (t.getAttribute('title') || t.innerText || t.textContent || '').trim().toLowerCase();" +
                "    return title === 'trade' || title.indexOf('trade') !== -1;" +
                "});" +
                "if (tradeTab && tradeTab.offsetParent !== null) {" +
                "    tradeTab.click();" +
                "    return true;" +
                "}" +
                "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                Thread.sleep(200);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Checks if Market Watch is currently rendered in the DOM.
     */
    public static boolean isMarketWatchVisible(WebDriver driver) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var mw = document.getElementById('marketwatch_symbol');" +
                "if (mw && (mw.offsetParent !== null || mw.offsetWidth > 0)) return true;" +
                "var tbl = document.getElementById('marketwatch_table') || document.querySelector('table.marketwatch') || document.querySelector('div[id*=\"marketwatch\"] table');" +
                "return !!(tbl && (tbl.offsetParent !== null || tbl.offsetWidth > 0));"
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Establishes connectivity between Trade actions and Market Watch:
     * 1. Synchronizes the target symbol in Market Watch filter
     * 2. Sets the exchange (DSE/CSE)
     * 3. Ensures widget color linking
     */
    public static void connectWidget(WebDriver driver, String symbol, String exchange) {
        if (driver == null) return;
        String cleanSym = cleanSymbol(symbol);
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. Set exchange toggle if present
            js.executeScript(
                "var ex = arguments[0];" +
                "var exBtns = Array.from(document.querySelectorAll('.marketwatch button, .marketwatch select, [id*=\"marketwatch\"] button, [id*=\"marketwatch\"] select'));" +
                "for (var i = 0; i < exBtns.length; i++) {" +
                "    var btn = exBtns[i];" +
                "    if (btn.tagName === 'SELECT') {" +
                "        for (var o = 0; o < btn.options.length; o++) {" +
                "            if (btn.options[o].text.trim().toUpperCase() === ex) { btn.selectedIndex = o; $(btn).trigger('change'); break; }" +
                "        }" +
                "    } else if (btn.innerText && btn.innerText.trim().toUpperCase() === ex) {" +
                "        btn.click();" +
                "        break;" +
                "    }" +
                "}",
                ex
            );

            // 2. Synchronize target symbol into #marketwatch_symbol
            if (!cleanSym.isEmpty()) {
                WebElement mwInput = null;
                try {
                    mwInput = driver.findElement(By.id("marketwatch_symbol"));
                } catch (Exception e) {
                    try {
                        mwInput = driver.findElement(INPUT_SEARCH_SYMBOL);
                    } catch (Exception ignored) {}
                }

                if (mwInput != null && mwInput.isDisplayed()) {
                    try {
                        mwInput.clear();
                        mwInput.sendKeys(cleanSym);
                        mwInput.sendKeys(Keys.ARROW_DOWN);
                        mwInput.sendKeys(Keys.ENTER);
                    } catch (Exception e) {
                        js.executeScript(
                            "arguments[0].value = arguments[1];" +
                            "if (window.jQuery) { $(arguments[0]).trigger('input').trigger('change'); }" +
                            "var e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, keyCode: 13 });" +
                            "arguments[0].dispatchEvent(e);",
                            mwInput, cleanSym
                        );
                    }
                } else {
                    // Fallback purely via JS
                    js.executeScript(
                        "var sym = arguments[0];" +
                        "var inp = document.getElementById('marketwatch_symbol') || document.querySelector('input[placeholder*=\"Search Symbol\"]');" +
                        "if (inp) {" +
                        "    inp.value = sym;" +
                        "    if (window.jQuery) { $(inp).trigger('input').trigger('change'); }" +
                        "    var e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, keyCode: 13 });" +
                        "    inp.dispatchEvent(e);" +
                        "}",
                        cleanSym
                    );
                }
            }

            Thread.sleep(200);
        } catch (Exception ignored) {}
    }

    /**
     * Filters the Market Watch table by symbol.
     */
    public static void filterBySymbol(WebDriver driver, String symbol) {
        connectWidget(driver, symbol, "DSE");
    }

    /**
     * Clears filter in Market Watch to restore all symbol rows.
     */
    public static void clearFilter(WebDriver driver) {
        if (driver == null) return;
        try {
            WebElement clearBtn = driver.findElement(BTN_CLEAR);
            if (clearBtn.isDisplayed() && clearBtn.isEnabled()) {
                clearBtn.click();
            } else {
                ((JavascriptExecutor) driver).executeScript(
                    "var btn = document.querySelector('button[id*=\"clear\"], button.btn-danger, [id*=\"marketwatch\"] button');" +
                    "if (btn) btn.click();" +
                    "var inp = document.getElementById('marketwatch_symbol');" +
                    "if (inp) { inp.value = ''; if (window.jQuery) $(inp).trigger('input').trigger('change'); }"
                );
            }
            Thread.sleep(300);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 4. REAL-TIME UI DOM DATA EXTRACTION (ALL 20 COLUMNS)
    // =========================================================================

    /**
     * Extracts the real-time UI DOM snapshot of the Market Watch row for the specified symbol.
     * Captures all 20 columns:
     * Symbol, Sector, Category, LTP, CP, YCP, Vol, Chg, % Chg, B.Qty, BID, ASK, A.Qty, Trades, Turnover, Open, High, Low, L.VOL, Y.VOL
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getUiSnapshot(WebDriver driver, String symbol) {
        Map<String, String> data = new LinkedHashMap<>();
        if (driver == null) return data;
        String cleanSym = cleanSymbol(symbol);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            Object res = js.executeScript(
                "var targetSym = (arguments[0] || '').trim().toUpperCase();" +
                "var cleanTarget = targetSym.replace(/\\[.*?\\]/g, '').split('.')[0].trim();" +

                // 1. Locate Market Watch table
                "var tbl = document.getElementById('marketwatch_table') || " +
                "          document.querySelector('table.marketwatch') || " +
                "          document.querySelector('div[id*=\"marketwatch\"] table') || " +
                "          document.querySelector('table[id*=\"marketwatch\"]');" +

                // Fallback: search any table with Market Watch headers
                "if (!tbl) {" +
                "    var allTables = document.querySelectorAll('table');" +
                "    for (var t = 0; t < allTables.length; t++) {" +
                "        var text = (allTables[t].innerText || '').toUpperCase();" +
                "        if (text.indexOf('SYMBOL') !== -1 && text.indexOf('LTP') !== -1 && text.indexOf('BID') !== -1) {" +
                "            tbl = allTables[t];" +
                "            break;" +
                "        }" +
                "    }" +
                "}" +

                "if (!tbl) return {};" +

                // 2. Read headers
                "var headers = [];" +
                "var ths = tbl.querySelectorAll('thead th, tr:first-child th');" +
                "for (var h = 0; h < ths.length; h++) {" +
                "    headers.push(ths[h].innerText.trim());" +
                "}" +

                // 3. Locate matching row for symbol
                "var rows = tbl.querySelectorAll('tbody tr');" +
                "if (rows.length === 0) rows = tbl.querySelectorAll('tr:not(:first-child)');" +
                "var matchedRow = null;" +

                "for (var r = 0; r < rows.length; r++) {" +
                "    var row = rows[r];" +
                "    var cells = row.querySelectorAll('td');" +
                "    if (cells.length === 0) continue;" +

                "    var symCell = row.querySelector('td.mw_table_symbol, td[class*=\"symbol\"]') || cells[0];" +
                "    var cellText = (symCell ? symCell.innerText.trim() : '').toUpperCase();" +
                "    var cleanCell = cellText.replace(/\\[.*?\\]/g, '').split('.')[0].trim();" +
                "    var rowClass = (row.className || '').toUpperCase();" +

                "    if (cleanTarget.length > 0) {" +
                "        if (cleanCell === cleanTarget || cellText.indexOf(cleanTarget) !== -1 || rowClass.indexOf(cleanTarget) !== -1) {" +
                "            matchedRow = row;" +
                "            break;" +
                "        }" +
                "    } else {" +
                "        matchedRow = row;" +
                "        break;" +
                "    }" +
                "}" +

                "if (!matchedRow && rows.length > 0 && cleanTarget.length === 0) {" +
                "    matchedRow = rows[0];" +
                "}" +

                "if (!matchedRow) return {};" +

                "var cells = matchedRow.querySelectorAll('td');" +
                "var result = {};" +

                // Map header index to cell value
                "for (var c = 0; c < cells.length; c++) {" +
                "    var rawHeader = (c < headers.length && headers[c] && headers[c].length > 0) ? headers[c] : ('col_' + c);" +
                "    result[rawHeader] = cells[c].innerText.trim();" +
                "}" +

                // Normalize known 20 Market Watch columns
                "for (var k = 0; k < headers.length && k < cells.length; k++) {" +
                "    var ht = headers[k].toUpperCase().trim();" +
                "    var v = cells[k].innerText.trim();" +
                "    if (ht === 'SYMBOL' || ht.indexOf('SYMBOL') !== -1) result['Symbol'] = v;" +
                "    else if (ht === 'SECTOR') result['Sector'] = v;" +
                "    else if (ht === 'CATEGORY' || ht === 'CAT') result['Category'] = v;" +
                "    else if (ht === 'LTP') result['LTP'] = v;" +
                "    else if (ht === 'CP' || ht === 'CLOSE') result['CP'] = v;" +
                "    else if (ht === 'YCP') result['YCP'] = v;" +
                "    else if (ht === 'VOL' || ht === 'VOLUME') result['Vol'] = v;" +
                "    else if (ht === 'CHG' || ht === 'CHANGE') result['Chg'] = v;" +
                "    else if (ht === '% CHG' || ht === '%CHG' || ht === 'PER CHG' || ht.indexOf('%') !== -1) result['% Chg'] = v;" +
                "    else if (ht === 'B.QTY' || ht === 'BQTY' || ht.indexOf('B.QTY') !== -1) result['B.Qty'] = v;" +
                "    else if (ht === 'BID') result['BID'] = v;" +
                "    else if (ht === 'ASK') result['ASK'] = v;" +
                "    else if (ht === 'A.QTY' || ht === 'AQTY' || ht.indexOf('A.QTY') !== -1) result['A.Qty'] = v;" +
                "    else if (ht === 'TRADES' || ht === 'TRADE') result['Trades'] = v;" +
                "    else if (ht === 'TURNOVER') result['Turnover'] = v;" +
                "    else if (ht === 'OPEN') result['Open'] = v;" +
                "    else if (ht === 'HIGH') result['High'] = v;" +
                "    else if (ht === 'LOW') result['Low'] = v;" +
                "    else if (ht === 'L.VOL' || ht === 'LVOL') result['L.VOL'] = v;" +
                "    else if (ht === 'Y.VOL' || ht === 'YVOL') result['Y.VOL'] = v;" +
                "}" +

                // Class-based fallback if any specific column was missed
                "var classMap = {" +
                "    'Symbol': 'td.mw_table_symbol, td[class*=\"symbol\"]'," +
                "    'Sector': 'td.mw_table_sector, td[class*=\"sector\"]'," +
                "    'Category': 'td.mw_table_category, td[class*=\"category\"]'," +
                "    'LTP': 'td.mw_table_ltp, td[class*=\"ltp\"]'," +
                "    'CP': 'td.mw_table_cp, td.mw_table_close, td[class*=\"close\"]'," +
                "    'YCP': 'td.mw_table_ycp, td[class*=\"ycp\"]'," +
                "    'Vol': 'td.mw_table_vol, td[class*=\"volume\"], td[class*=\"vol\"]'," +
                "    'Chg': 'td.mw_table_chg, td[class*=\"chg\"]'," +
                "    '% Chg': 'td.mw_table_per_chg, td.mw_table_chg_percent, td[class*=\"percent\"]'," +
                "    'B.Qty': 'td.mw_table_b_qty, td.mw_table_bqty, td[class*=\"b_qty\"]'," +
                "    'BID': 'td.mw_table_bid, td[class*=\"bid\"]'," +
                "    'ASK': 'td.mw_table_ask, td[class*=\"ask\"]'," +
                "    'A.Qty': 'td.mw_table_a_qty, td.mw_table_aqty, td[class*=\"a_qty\"]'," +
                "    'Trades': 'td.mw_table_trades, td[class*=\"trades\"]'," +
                "    'Turnover': 'td.mw_table_turnover, td[class*=\"turnover\"]'," +
                "    'Open': 'td.mw_table_open, td[class*=\"open\"]'," +
                "    'High': 'td.mw_table_high, td[class*=\"high\"]'," +
                "    'Low': 'td.mw_table_low, td[class*=\"low\"]'," +
                "    'L.VOL': 'td.mw_table_l_vol, td.mw_table_lvol'," +
                "    'Y.VOL': 'td.mw_table_y_vol, td.mw_table_yvol'" +
                "};" +
                "for (var key in classMap) {" +
                "    if (!result[key] || result[key] === '') {" +
                "        var el = matchedRow.querySelector(classMap[key]);" +
                "        if (el && el.innerText.trim()) result[key] = el.innerText.trim();" +
                "    }" +
                "}" +

                // Positional mapping fallback if 20 columns exist
                "var order = ['Symbol', 'Sector', 'Category', 'LTP', 'CP', 'YCP', 'Vol', 'Chg', '% Chg', 'B.Qty', 'BID', 'ASK', 'A.Qty', 'Trades', 'Turnover', 'Open', 'High', 'Low', 'L.VOL', 'Y.VOL'];" +
                "if (cells.length >= 20) {" +
                "    for (var i = 0; i < order.length && i < cells.length; i++) {" +
                "        if (!result[order[i]] || result[order[i]] === '') {" +
                "            result[order[i]] = cells[i].innerText.trim();" +
                "        }" +
                "    }" +
                "}" +

                "return result;",
                cleanSym
            );

            if (res instanceof Map) {
                Map<String, Object> raw = (Map<String, Object>) res;
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    data.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        } catch (Exception ignored) {}

        // Guarantee all 20 standard columns exist with sensible defaults
        if (!data.containsKey("Symbol")) data.put("Symbol", cleanSym.isEmpty() ? "-" : cleanSym);
        for (String col : MARKET_WATCH_COLUMNS) {
            data.putIfAbsent(col, "-");
        }
        data.put("DATA_SOURCE", "Market Watch UI DOM");
        return data;
    }

    /**
     * Extracts all rendered rows from the Market Watch table up to maxRows.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> getAllUiRows(WebDriver driver, String symbolFilter, int maxRows) {
        List<Map<String, String>> list = new ArrayList<>();
        if (driver == null) return list;

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var target = (arguments[0] || '').trim().toUpperCase();" +
                "var max = arguments[1] || 100;" +
                "var tbl = document.getElementById('marketwatch_table') || " +
                "          document.querySelector('table.marketwatch') || " +
                "          document.querySelector('div[id*=\"marketwatch\"] table') || " +
                "          document.querySelector('table[id*=\"marketwatch\"]');" +
                "if (!tbl) return [];" +

                "var headers = [];" +
                "var ths = tbl.querySelectorAll('thead th, tr:first-child th');" +
                "for (var h = 0; h < ths.length; h++) headers.push(ths[h].innerText.trim());" +

                "var rows = tbl.querySelectorAll('tbody tr');" +
                "if (rows.length === 0) rows = tbl.querySelectorAll('tr:not(:first-child)');" +
                "var out = [];" +
                "var order = ['Symbol', 'Sector', 'Category', 'LTP', 'CP', 'YCP', 'Vol', 'Chg', '% Chg', 'B.Qty', 'BID', 'ASK', 'A.Qty', 'Trades', 'Turnover', 'Open', 'High', 'Low', 'L.VOL', 'Y.VOL'];" +

                "for (var r = 0; r < rows.length && out.length < max; r++) {" +
                "    var cells = rows[r].querySelectorAll('td');" +
                "    if (cells.length === 0) continue;" +

                "    var symCell = rows[r].querySelector('td.mw_table_symbol, td[class*=\"symbol\"]') || cells[0];" +
                "    var symText = (symCell ? symCell.innerText.trim() : '').toUpperCase();" +
                "    if (target.length > 0 && symText.indexOf(target) === -1) continue;" +

                "    var rowObj = {};" +
                "    for (var c = 0; c < cells.length; c++) {" +
                "        var hdr = (c < headers.length && headers[c]) ? headers[c] : ('col_' + c);" +
                "        rowObj[hdr] = cells[c].innerText.trim();" +
                "    }" +
                "    if (cells.length >= 20) {" +
                "        for (var i = 0; i < order.length && i < cells.length; i++) {" +
                "            rowObj[order[i]] = cells[i].innerText.trim();" +
                "        }" +
                "    }" +
                "    out.push(rowObj);" +
                "}" +
                "return out;",
                symbolFilter != null ? symbolFilter.trim().toUpperCase() : "",
                maxRows > 0 ? maxRows : 100
            );

            if (res instanceof List) {
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) res;
                for (Map<String, Object> r : rawList) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : r.entrySet()) {
                        row.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                    list.add(row);
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    // =========================================================================
    // 5. PRICE & FIELD EXTRACTION SHORTCUTS
    // =========================================================================

    public static double getMarketWatchLtp(WebDriver driver, String symbol) {
        Map<String, String> snap = getUiSnapshot(driver, symbol);
        String ltpStr = snap.getOrDefault("LTP", "0");
        return parseNumSafe(ltpStr);
    }

    public static double getMarketWatchYcp(WebDriver driver, String symbol) {
        Map<String, String> snap = getUiSnapshot(driver, symbol);
        String ycpStr = snap.getOrDefault("YCP", "0");
        return parseNumSafe(ycpStr);
    }

    // =========================================================================
    // 6. FORMATTED TABLE VIEW REPORTERS
    // =========================================================================

    /**
     * Prints a single Market Watch symbol row in full table view format matching the screenshot.
     */
    public static void printMarketWatchTable(String symbol, Map<String, String> row) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (row != null && !row.isEmpty()) {
            rows.add(row);
        }
        printMarketWatchRows(rows, "MARKET WATCH LIVE UI DOM TABLE (" + symbol + ")");
    }

    /**
     * Prints multiple Market Watch rows in formatted ASCII table view with all 20 columns.
     */
    public static void printMarketWatchRows(List<Map<String, String>> rows, String title) {
        System.out.println("\n+==========================================================================================================================================================================================================================+");
        System.out.printf("| %-248s |%n", title);
        System.out.println("+==============+============+==========+==========+==========+==========+==============+==========+==========+==========+============+============+==========+==========+==============+==========+==========+==========+==========+==============+");
        System.out.printf("| %-12s | %-10s | %-8s | %-8s | %-8s | %-8s | %-12s | %-8s | %-8s | %-8s | %-10s | %-10s | %-8s | %-8s | %-12s | %-8s | %-8s | %-8s | %-8s | %-12s |%n",
                "Symbol", "Sector", "Category", "LTP", "CP", "YCP", "Vol", "Chg", "% Chg", "B.Qty", "BID", "ASK", "A.Qty", "Trades", "Turnover", "Open", "High", "Low", "L.VOL", "Y.VOL");
        System.out.println("+==============+============+==========+==========+==========+==========+==============+==========+==========+==========+============+============+==========+==========+==============+==========+==========+==========+==========+==============+");

        if (rows == null || rows.isEmpty()) {
            System.out.printf("| %-248s |%n", "No Market Watch row data rendered in DOM.");
        } else {
            for (Map<String, String> r : rows) {
                System.out.printf("| %-12s | %-10s | %-8s | %-8s | %-8s | %-8s | %-12s | %-8s | %-8s | %-8s | %-10s | %-10s | %-8s | %-8s | %-12s | %-8s | %-8s | %-8s | %-8s | %-12s |%n",
                        truncate(r.getOrDefault("Symbol", "-"), 12),
                        truncate(r.getOrDefault("Sector", "-"), 10),
                        truncate(r.getOrDefault("Category", "-"), 8),
                        truncate(r.getOrDefault("LTP", "-"), 8),
                        truncate(r.getOrDefault("CP", "-"), 8),
                        truncate(r.getOrDefault("YCP", "-"), 8),
                        truncate(r.getOrDefault("Vol", "-"), 12),
                        truncate(r.getOrDefault("Chg", "-"), 8),
                        truncate(r.getOrDefault("% Chg", "-"), 8),
                        truncate(r.getOrDefault("B.Qty", "-"), 8),
                        truncate(r.getOrDefault("BID", "-"), 10),
                        truncate(r.getOrDefault("ASK", "-"), 10),
                        truncate(r.getOrDefault("A.Qty", "-"), 8),
                        truncate(r.getOrDefault("Trades", "-"), 8),
                        truncate(r.getOrDefault("Turnover", "-"), 12),
                        truncate(r.getOrDefault("Open", "-"), 8),
                        truncate(r.getOrDefault("High", "-"), 8),
                        truncate(r.getOrDefault("Low", "-"), 8),
                        truncate(r.getOrDefault("L.VOL", "-"), 8),
                        truncate(r.getOrDefault("Y.VOL", "-"), 12));
            }
        }
        System.out.println("+==============+============+==========+==========+==========+==========+==============+==========+==========+==========+============+============+==========+==========+==============+==========+==========+==========+==========+==============+\n");
    }

    // =========================================================================
    // 7. INTERNAL UTILITY HELPERS
    // =========================================================================

    public static String cleanSymbol(String sym) {
        if (sym == null) return "";
        return sym.replace("[", "").replace("]", "").split("\\.")[0].trim().toUpperCase();
    }

    private static double parseNumSafe(String val) {
        if (val == null) return 0.0;
        try {
            String clean = val.replaceAll("[^0-9.-]", "");
            return clean.isEmpty() ? 0.0 : Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "-";
        s = s.trim();
        if (s.isEmpty()) return "-";
        return (s.length() <= max) ? s : s.substring(0, max - 1) + "…";
    }

    private static String readInputSafe(WebDriver driver, By... locators) {
        for (By b : locators) {
            try {
                WebElement el = driver.findElement(b);
                String v = el.getDomAttribute("value");
                if (v != null && !v.trim().isEmpty()) return v.trim();
                String t = el.getText();
                if (t != null && !t.trim().isEmpty()) return t.trim();
            } catch (Exception ignored) {}
        }
        return "";
    }

    // Standalone launcher for Eclipse & command-line execution
    public static void main(String[] args) {
        WebDriver driver = OMSwebmain.createDriver();
        Scanner sc = new Scanner(System.in);
        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(800);
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            );
            OMSweb_Login.main(driver, sc);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            OMSweb_Trade.navigateToTradePage(driver, wait, true);
            main(driver, sc);
            System.out.println("\n=============================================================================");
            System.out.println(" [SUCCESS] Market Watch session completed successfully.");
            System.out.println("=============================================================================\n");
        } catch (Exception e) {
            System.err.println("[MARKET WATCH LAUNCH ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }
}
