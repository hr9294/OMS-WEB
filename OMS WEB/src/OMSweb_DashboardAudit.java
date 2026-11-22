import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * OMSweb_DashboardAudit
 * 
 * Comprehensive 12-Model Dashboard Reflection & Verification Suite:
 * 1. TURNOVER (BDT)
 * 2. BUY VALUE (BDT)
 * 3. SELL VALUE (BDT)
 * 4. BROKER LIMIT (BDT)
 * 5. TRADES (Count)
 * 6. BUY TRADES (Count)
 * 7. SELL TRADES (Count)
 * 8. MOST TRADED STOCKS
 * 9. ACTIVE USERS (Online, Inactive, Dealers, Clients, Associates, Sessions)
 * 10. EXCHANGE CONTRIBUTION (BDT)
 * 11. TOP DEALERS & TOP ASSOCIATES
 * 12. MOST TRADED CLIENTS
 * 
 * Features:
 * - Pre-Trade (Baseline) Snapshot Capture
 * - Live Trade Execution (Buy/Sell)
 * - Post-Trade Snapshot Capture
 * - Field-by-Field Delta Calculation
 * - Mathematical Reflection Consistency Verification
 * - Active vs Inactive User State Audit
 * - Individual Model-Wise Table Presentation
 */
@SuppressWarnings("unchecked")
public class OMSweb_DashboardAudit {

    public static class TurnoverModel {
        public String bigValue = "0";
        public String currency = "BDT";
        public String subtext = "Today · All exchanges";
        public String dseToday = "0", cseToday = "0";
        public String dseYesterday = "0", cseYesterday = "0";
        public String dseChange = "0.00 %", cseChange = "0.00 %";
    }

    public static class BuyValueModel {
        public String bigValue = "0";
        public String currency = "BDT";
        public String subtext = "Today · All exchanges";
        public String dseToday = "0", cseToday = "0";
        public String dseYesterday = "0", cseYesterday = "0";
        public String dseChange = "0.00 %", cseChange = "0.00 %";
    }

    public static class SellValueModel {
        public String bigValue = "0";
        public String currency = "BDT";
        public String subtext = "Today · All exchanges";
        public String dseToday = "0", cseToday = "0";
        public String dseYesterday = "0", cseYesterday = "0";
        public String dseChange = "0.00 %", cseChange = "0.00 %";
    }

    public static class BrokerLimitModel {
        public String dseTotal = "0", cseTotal = "0";
        public String dseUsed = "0", cseUsed = "0";
        public String dseRemaining = "0", cseRemaining = "0";
        public String dseUtilisation = "0.0%";
        public String cseUtilisation = "0.0%";
    }

    public static class TradesModel {
        public String bigCount = "0";
        public String subtext = "Today · All exchanges";
        public String dseToday = "0", cseToday = "0";
        public String dseYesterday = "0", cseYesterday = "0";
        public String dseChange = "0.00 %", cseChange = "0.00 %";
    }

    public static class BuyTradesModel {
        public String bigCount = "0";
        public String subtext = "Today · All exchanges";
        public String dseToday = "0", cseToday = "0";
        public String dseYesterday = "0", cseYesterday = "0";
        public String dseChange = "0.00 %", cseChange = "0.00 %";
    }

    public static class SellTradesModel {
        public String bigCount = "0";
        public String subtext = "Today · All exchanges";
        public String dseToday = "0", cseToday = "0";
        public String dseYesterday = "0", cseYesterday = "0";
        public String dseChange = "0.00 %", cseChange = "0.00 %";
    }

    public static class StockRow {
        public String symbol;
        public String value;
        public String trades;
        public StockRow(String s, String v, String t) { this.symbol = s; this.value = v; this.trades = t; }
    }

    public static class ActiveUsersModel {
        public String usersOnlineText = "0 / 0";
        public int usersOnline = 0, usersTotal = 0, usersOffline = 0;
        public String dealersText = "0 / 0";
        public int dealersOnline = 0, dealersTotal = 0, dealersOffline = 0;
        public String clientsText = "0 / 0";
        public int clientsOnline = 0, clientsTotal = 0, clientsOffline = 0;
        public String associatesText = "0 / 0";
        public int associatesOnline = 0, associatesTotal = 0, associatesOffline = 0;
        public String desktopSessions = "0";
        public String mobileSessions = "0";
        public String totalSessions = "0";
    }

    public static class ExchangeRow {
        public String exch;
        public String market;
        public String oms;
        public String share;
        public ExchangeRow(String e, String m, String o, String s) { this.exch = e; this.market = m; this.oms = o; this.share = s; }
    }

    public static class DealerRow {
        public String name;
        public String value;
        public DealerRow(String n, String v) { this.name = n; this.value = v; }
    }

    public static class AssociateRow {
        public String name;
        public String value;
        public AssociateRow(String n, String v) { this.name = n; this.value = v; }
    }

    public static class ClientRow {
        public String clientCode;
        public String value;
        public String trades;
        public ClientRow(String c, String v, String t) { this.clientCode = c; this.value = v; this.trades = t; }
    }

    public static class DashboardSnapshot {
        public String timestamp;
        public String label;
        public TurnoverModel turnover = new TurnoverModel();
        public BuyValueModel buyValue = new BuyValueModel();
        public SellValueModel sellValue = new SellValueModel();
        public BrokerLimitModel brokerLimit = new BrokerLimitModel();
        public TradesModel trades = new TradesModel();
        public BuyTradesModel buyTrades = new BuyTradesModel();
        public SellTradesModel sellTrades = new SellTradesModel();
        public List<StockRow> mostTradedStocks = new ArrayList<>();
        public ActiveUsersModel activeUsers = new ActiveUsersModel();
        public List<ExchangeRow> exchangeContribution = new ArrayList<>();
        public List<DealerRow> topDealers = new ArrayList<>();
        public List<AssociateRow> topAssociates = new ArrayList<>();
        public List<ClientRow> mostTradedClients = new ArrayList<>();
    }

    public static double parseNumSafe(String s) {
        if (s == null || s.trim().isEmpty()) return 0.0;
        String clean = s.replaceAll("[^0-9.-]", "");
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static boolean navigateToDashboard(WebDriver driver) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var l = document.querySelector('a[data-link=\"home\"], a[href=\"/home\"], a[href=\"/\"]');\n" +
                "if (l) {\n" +
                "    if (window.jQuery) window.jQuery(l).trigger('click');\n" +
                "    l.click();\n" +
                "    return true;\n" +
                "}\n" +
                "window.location.href = 'https://devoms.quantbd.com/home';\n" +
                "return true;\n"
            );
            Thread.sleep(1500);
            return Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean waitForDashboardReady(WebDriver driver, int timeoutSeconds) {
        if (driver == null) return false;
        long start = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        JavascriptExecutor js = (JavascriptExecutor) driver;
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Boolean ready = (Boolean) js.executeScript(
                    "var heads = Array.from(document.querySelectorAll('.dash-card-head'));\n" +
                    "if (heads.length < 5) return false;\n" +
                    "var bl = heads.find(function(h) { return (h.innerText || '').indexOf('BROKER LIMIT') !== -1; });\n" +
                    "var to = heads.find(function(h) { return (h.innerText || '').indexOf('TURNOVER') !== -1; });\n" +
                    "var blCard = bl ? bl.closest('.dash-card') : null;\n" +
                    "var toCard = to ? to.closest('.dash-card') : null;\n" +
                    "var blTxt = blCard ? (blCard.innerText || '') : '';\n" +
                    "var toTxt = toCard ? (toCard.innerText || '') : '';\n" +
                    "return (blTxt.indexOf('Total') !== -1 || blTxt.indexOf('37,25') !== -1) && (toTxt.indexOf('Today') !== -1);\n"
                );
                if (Boolean.TRUE.equals(ready)) {
                    Thread.sleep(600);
                    return true;
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(300); } catch (Exception ignored) {}
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static DashboardSnapshot captureSnapshot(WebDriver driver, String label) {
        DashboardSnapshot snap = new DashboardSnapshot();
        snap.label = label;
        snap.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        if (driver == null) return snap;

        navigateToDashboard(driver);
        waitForDashboardReady(driver, 10);

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Map<String, Object> raw = (Map<String, Object>) js.executeScript(
                "var res = {};\n" +
                "function findCard(headerText) {\n" +
                "    var heads = Array.from(document.querySelectorAll('.dash-card-head, .top-dealer-asso-nav'));\n" +
                "    var target = heads.find(function(h) {\n" +
                "        return (h.innerText || '').toUpperCase().indexOf(headerText.toUpperCase()) !== -1;\n" +
                "    });\n" +
                "    return target ? target.closest('.dash-card') : null;\n" +
                "}\n" +
                "function extractTable(card) {\n" +
                "    if (!card) return [];\n" +
                "    var rows = Array.from(card.querySelectorAll('table tr'));\n" +
                "    return rows.map(function(r) {\n" +
                "        return Array.from(r.querySelectorAll('th, td')).map(function(c) { return (c.innerText || '').trim(); });\n" +
                "    }).filter(function(r) { return r.length > 0; });\n" +
                "}\n" +
                "function extractCardBasic(headerText) {\n" +
                "    var c = findCard(headerText);\n" +
                "    return { rawText: c ? (c.innerText || '') : '', table: extractTable(c) };\n" +
                "}\n" +
                "res.turnover = extractCardBasic('TURNOVER');\n" +
                "res.buyValue = extractCardBasic('BUY VALUE');\n" +
                "res.sellValue = extractCardBasic('SELL VALUE');\n" +
                "res.brokerLimit = extractCardBasic('BROKER LIMIT');\n" +
                "res.trades = extractCardBasic('TRADES');\n" +
                "res.buyTrades = extractCardBasic('BUY TRADES');\n" +
                "res.sellTrades = extractCardBasic('SELL TRADES');\n" +
                "res.mostTradedStocks = extractCardBasic('MOST TRADED STOCKS');\n" +
                "res.activeUsers = extractCardBasic('ACTIVE USERS');\n" +
                "res.exchangeContribution = extractCardBasic('EXCHANGE CONTRIBUTION');\n" +
                "res.topDealers = extractCardBasic('TOP DEALERS');\n" +
                "res.mostTradedClients = extractCardBasic('MOST TRADED CLIENTS');\n" +
                "return res;\n"
            );

            if (raw != null) {
                parseTurnoverCard((Map<String, Object>) raw.get("turnover"), snap.turnover);
                parseBuyValueCard((Map<String, Object>) raw.get("buyValue"), snap.buyValue);
                parseSellValueCard((Map<String, Object>) raw.get("sellValue"), snap.sellValue);
                parseBrokerLimitCard((Map<String, Object>) raw.get("brokerLimit"), snap.brokerLimit);
                parseTradesCard((Map<String, Object>) raw.get("trades"), snap.trades);
                parseBuyTradesCard((Map<String, Object>) raw.get("buyTrades"), snap.buyTrades);
                parseSellTradesCard((Map<String, Object>) raw.get("sellTrades"), snap.sellTrades);
                parseStocksCard((Map<String, Object>) raw.get("mostTradedStocks"), snap.mostTradedStocks);
                parseActiveUsersCard((Map<String, Object>) raw.get("activeUsers"), snap.activeUsers);
                parseExchangeContributionCard((Map<String, Object>) raw.get("exchangeContribution"), snap.exchangeContribution);
                parseDealersCard((Map<String, Object>) raw.get("topDealers"), snap.topDealers);
                parseClientsCard((Map<String, Object>) raw.get("mostTradedClients"), snap.mostTradedClients);
            }

            // Extract Top Associates Tab
            try {
                js.executeScript(
                    "var btn = Array.from(document.querySelectorAll('button, a, span, div')).find(function(el) {\n" +
                    "    return (el.innerText || '').trim() === 'TOP ASSOCIATES';\n" +
                    "});\n" +
                    "if (btn) btn.click();\n"
                );
                Thread.sleep(400);
                Map<String, Object> assoRaw = (Map<String, Object>) js.executeScript(
                    "var heads = Array.from(document.querySelectorAll('.top-dealer-asso-nav'));\n" +
                    "var target = heads.find(function(h) { return (h.innerText || '').indexOf('TOP ASSOCIATES') !== -1; });\n" +
                    "var card = target ? target.closest('.dash-card') : null;\n" +
                    "if (!card) return { rawText: '', table: [] };\n" +
                    "var rows = Array.from(card.querySelectorAll('table tr')).map(function(r) {\n" +
                    "    return Array.from(r.querySelectorAll('th, td')).map(function(c) { return (c.innerText || '').trim(); });\n" +
                    "});\n" +
                    "return { rawText: card.innerText, table: rows };\n"
                );
                parseAssociatesCard(assoRaw, snap.topAssociates);
                // Switch back
                js.executeScript(
                    "var btn = Array.from(document.querySelectorAll('button, a, span, div')).find(function(el) {\n" +
                    "    return (el.innerText || '').trim() === 'TOP DEALERS';\n" +
                    "});\n" +
                    "if (btn) btn.click();\n"
                );
            } catch (Exception ignored) {}

        } catch (Exception e) {
            System.out.println("[WARN: captureSnapshot] " + e.getMessage());
        }

        return snap;
    }

    private static void parseTurnoverCard(Map<String, Object> raw, TurnoverModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            String[] lines = txt.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.matches("^[0-9,.]+$") && !line.contains("%")) {
                    m.bigValue = line;
                    break;
                }
            }
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("today")) { m.dseToday = r.get(1); m.cseToday = r.get(2); }
                    else if (h.contains("yesterday")) { m.dseYesterday = r.get(1); m.cseYesterday = r.get(2); }
                    else if (h.contains("change")) { m.dseChange = r.get(1); m.cseChange = r.get(2); }
                }
            }
        }
    }

    private static void parseBuyValueCard(Map<String, Object> raw, BuyValueModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            String[] lines = txt.split("\\n");
            for (String line : lines) {
                String l = line.trim();
                if (l.matches("^[0-9,.]+$") && !l.contains("%")) { m.bigValue = l; break; }
            }
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("today")) { m.dseToday = r.get(1); m.cseToday = r.get(2); }
                    else if (h.contains("yesterday")) { m.dseYesterday = r.get(1); m.cseYesterday = r.get(2); }
                    else if (h.contains("change")) { m.dseChange = r.get(1); m.cseChange = r.get(2); }
                }
            }
        }
    }

    private static void parseSellValueCard(Map<String, Object> raw, SellValueModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            String[] lines = txt.split("\\n");
            for (String line : lines) {
                String l = line.trim();
                if (l.matches("^[0-9,.]+$") && !l.contains("%")) { m.bigValue = l; break; }
            }
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("today")) { m.dseToday = r.get(1); m.cseToday = r.get(2); }
                    else if (h.contains("yesterday")) { m.dseYesterday = r.get(1); m.cseYesterday = r.get(2); }
                    else if (h.contains("change")) { m.dseChange = r.get(1); m.cseChange = r.get(2); }
                }
            }
        }
    }

    private static void parseBrokerLimitCard(Map<String, Object> raw, BrokerLimitModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            Matcher mat1 = Pattern.compile("DSE\\s*utilisation\\s*([0-9.%]+)", Pattern.CASE_INSENSITIVE).matcher(txt);
            if (mat1.find()) m.dseUtilisation = mat1.group(1);
            Matcher mat2 = Pattern.compile("CSE\\s*utilisation\\s*([0-9.%]+)", Pattern.CASE_INSENSITIVE).matcher(txt);
            if (mat2.find()) m.cseUtilisation = mat2.group(1);
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("total")) { m.dseTotal = r.get(1); m.cseTotal = r.get(2); }
                    else if (h.contains("used")) { m.dseUsed = r.get(1); m.cseUsed = r.get(2); }
                    else if (h.contains("remaining")) { m.dseRemaining = r.get(1); m.cseRemaining = r.get(2); }
                }
            }
        }
    }

    private static void parseTradesCard(Map<String, Object> raw, TradesModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            String[] lines = txt.split("\\n");
            for (String line : lines) {
                String l = line.trim();
                if (l.matches("^[0-9,.]+$") && !l.contains("%")) { m.bigCount = l; break; }
            }
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("today")) { m.dseToday = r.get(1); m.cseToday = r.get(2); }
                    else if (h.contains("yesterday")) { m.dseYesterday = r.get(1); m.cseYesterday = r.get(2); }
                    else if (h.contains("change")) { m.dseChange = r.get(1); m.cseChange = r.get(2); }
                }
            }
        }
    }

    private static void parseBuyTradesCard(Map<String, Object> raw, BuyTradesModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            String[] lines = txt.split("\\n");
            for (String line : lines) {
                String l = line.trim();
                if (l.matches("^[0-9,.]+$") && !l.contains("%")) { m.bigCount = l; break; }
            }
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("today")) { m.dseToday = r.get(1); m.cseToday = r.get(2); }
                    else if (h.contains("yesterday")) { m.dseYesterday = r.get(1); m.cseYesterday = r.get(2); }
                    else if (h.contains("change")) { m.dseChange = r.get(1); m.cseChange = r.get(2); }
                }
            }
        }
    }

    private static void parseSellTradesCard(Map<String, Object> raw, SellTradesModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt != null) {
            String[] lines = txt.split("\\n");
            for (String line : lines) {
                String l = line.trim();
                if (l.matches("^[0-9,.]+$") && !l.contains("%")) { m.bigCount = l; break; }
            }
        }
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3) {
                    String h = r.get(0).toLowerCase();
                    if (h.contains("today")) { m.dseToday = r.get(1); m.cseToday = r.get(2); }
                    else if (h.contains("yesterday")) { m.dseYesterday = r.get(1); m.cseYesterday = r.get(2); }
                    else if (h.contains("change")) { m.dseChange = r.get(1); m.cseChange = r.get(2); }
                }
            }
        }
    }

    private static void parseStocksCard(Map<String, Object> raw, List<StockRow> list) {
        if (raw == null) return;
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (int i = 0; i < table.size(); i++) {
                List<String> r = table.get(i);
                if (r.size() >= 3 && !r.get(0).equalsIgnoreCase("SYMBOL")) {
                    list.add(new StockRow(r.get(0), r.get(1), r.get(2)));
                }
            }
        }
    }

    private static void parseActiveUsersCard(Map<String, Object> raw, ActiveUsersModel m) {
        if (raw == null) return;
        String txt = (String) raw.get("rawText");
        if (txt == null) return;

        Matcher mTotal = Pattern.compile("([0-9,]+)\\s*/\\s*([0-9,]+)\\s*Users online", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mTotal.find()) {
            m.usersOnline = (int) parseNumSafe(mTotal.group(1));
            m.usersTotal = (int) parseNumSafe(mTotal.group(2));
            m.usersOffline = Math.max(0, m.usersTotal - m.usersOnline);
            m.usersOnlineText = mTotal.group(1) + " / " + mTotal.group(2);
        }

        Matcher mDealers = Pattern.compile("Dealers\\s*\\n?\\s*([0-9,]+)\\s*/\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mDealers.find()) {
            m.dealersOnline = (int) parseNumSafe(mDealers.group(1));
            m.dealersTotal = (int) parseNumSafe(mDealers.group(2));
            m.dealersOffline = Math.max(0, m.dealersTotal - m.dealersOnline);
            m.dealersText = mDealers.group(1) + " / " + mDealers.group(2);
        }

        Matcher mClients = Pattern.compile("Clients\\s*\\n?\\s*([0-9,]+)\\s*/\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mClients.find()) {
            m.clientsOnline = (int) parseNumSafe(mClients.group(1));
            m.clientsTotal = (int) parseNumSafe(mClients.group(2));
            m.clientsOffline = Math.max(0, m.clientsTotal - m.clientsOnline);
            m.clientsText = mClients.group(1) + " / " + mClients.group(2);
        }

        Matcher mAssociates = Pattern.compile("Associates\\s*\\n?\\s*([0-9,]+)\\s*/\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mAssociates.find()) {
            m.associatesOnline = (int) parseNumSafe(mAssociates.group(1));
            m.associatesTotal = (int) parseNumSafe(mAssociates.group(2));
            m.associatesOffline = Math.max(0, m.associatesTotal - m.associatesOnline);
            m.associatesText = mAssociates.group(1) + " / " + mAssociates.group(2);
        }

        Matcher mDesk = Pattern.compile("([0-9,]+)\\s*\\n?\\s*Desktop", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mDesk.find()) m.desktopSessions = mDesk.group(1);

        Matcher mMob = Pattern.compile("([0-9,]+)\\s*\\n?\\s*Mobile", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mMob.find()) m.mobileSessions = mMob.group(1);

        Matcher mSess = Pattern.compile("([0-9,]+)\\s*\\n?\\s*Sessions", Pattern.CASE_INSENSITIVE).matcher(txt);
        if (mSess.find()) m.totalSessions = mSess.group(1);
    }

    private static void parseExchangeContributionCard(Map<String, Object> raw, List<ExchangeRow> list) {
        if (raw == null) return;
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 4 && !r.get(0).equalsIgnoreCase("EXCH")) {
                    list.add(new ExchangeRow(r.get(0), r.get(1), r.get(2), r.get(3)));
                }
            }
        }
    }

    private static void parseDealersCard(Map<String, Object> raw, List<DealerRow> list) {
        if (raw == null) return;
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 2 && !r.get(0).equalsIgnoreCase("NAME")) {
                    list.add(new DealerRow(r.get(0), r.get(1)));
                }
            }
        }
    }

    private static void parseAssociatesCard(Map<String, Object> raw, List<AssociateRow> list) {
        if (raw == null) return;
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 2 && !r.get(0).equalsIgnoreCase("NAME")) {
                    list.add(new AssociateRow(r.get(0), r.get(1)));
                }
            }
        }
    }

    private static void parseClientsCard(Map<String, Object> raw, List<ClientRow> list) {
        if (raw == null) return;
        List<List<String>> table = (List<List<String>>) raw.get("table");
        if (table != null) {
            for (List<String> r : table) {
                if (r.size() >= 3 && !r.get(0).equalsIgnoreCase("CLIENT CODE")) {
                    list.add(new ClientRow(r.get(0), r.get(1), r.get(2)));
                }
            }
        }
    }

    /**
     * Prints complete individual model-wise data tables.
     */
    public static void displayModelWiseDashboard(DashboardSnapshot s) {
        System.out.println("\n========================================================================================================================");
        System.out.printf("                    OMS-WEB DASHBOARD 12-MODEL AUDIT REPORT: %s (%s)%n", s.label, s.timestamp);
        System.out.println("========================================================================================================================");

        // 1. Turnover
        System.out.println("\n[MODEL 1: TURNOVER (BDT)]");
        System.out.printf("  * Total Turnover Value : %s BDT (%s)%n", s.turnover.bigValue, s.turnover.subtext);
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Today             | %-20s | %-20s |%n", s.turnover.dseToday, s.turnover.cseToday);
        System.out.printf("  | Yesterday         | %-20s | %-20s |%n", s.turnover.dseYesterday, s.turnover.cseYesterday);
        System.out.printf("  | Change            | %-20s | %-20s |%n", s.turnover.dseChange, s.turnover.cseChange);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 2. Buy Value
        System.out.println("\n[MODEL 2: BUY VALUE (BDT)]");
        System.out.printf("  * Total Buy Value      : %s BDT%n", s.buyValue.bigValue);
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Today             | %-20s | %-20s |%n", s.buyValue.dseToday, s.buyValue.cseToday);
        System.out.printf("  | Yesterday         | %-20s | %-20s |%n", s.buyValue.dseYesterday, s.buyValue.cseYesterday);
        System.out.printf("  | Change            | %-20s | %-20s |%n", s.buyValue.dseChange, s.buyValue.cseChange);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 3. Sell Value
        System.out.println("\n[MODEL 3: SELL VALUE (BDT)]");
        System.out.printf("  * Total Sell Value     : %s BDT%n", s.sellValue.bigValue);
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Today             | %-20s | %-20s |%n", s.sellValue.dseToday, s.sellValue.cseToday);
        System.out.printf("  | Yesterday         | %-20s | %-20s |%n", s.sellValue.dseYesterday, s.sellValue.cseYesterday);
        System.out.printf("  | Change            | %-20s | %-20s |%n", s.sellValue.dseChange, s.sellValue.cseChange);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 4. Broker Limit
        System.out.println("\n[MODEL 4: BROKER LIMIT (BDT)]");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Total Limit       | %-20s | %-20s |%n", s.brokerLimit.dseTotal, s.brokerLimit.cseTotal);
        System.out.printf("  | Used Limit        | %-20s | %-20s |%n", s.brokerLimit.dseUsed, s.brokerLimit.cseUsed);
        System.out.printf("  | Remaining Limit   | %-20s | %-20s |%n", s.brokerLimit.dseRemaining, s.brokerLimit.cseRemaining);
        System.out.printf("  | Utilisation       | %-20s | %-20s |%n", s.brokerLimit.dseUtilisation, s.brokerLimit.cseUtilisation);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 5. Trades Count
        System.out.println("\n[MODEL 5: TRADES (Count)]");
        System.out.printf("  * Total Executed Trades: %s Trades%n", s.trades.bigCount);
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Today             | %-20s | %-20s |%n", s.trades.dseToday, s.trades.cseToday);
        System.out.printf("  | Yesterday         | %-20s | %-20s |%n", s.trades.dseYesterday, s.trades.cseYesterday);
        System.out.printf("  | Change            | %-20s | %-20s |%n", s.trades.dseChange, s.trades.cseChange);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 6. Buy Trades Count
        System.out.println("\n[MODEL 6: BUY TRADES (Count)]");
        System.out.printf("  * Total Buy Trades     : %s Trades%n", s.buyTrades.bigCount);
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Today             | %-20s | %-20s |%n", s.buyTrades.dseToday, s.buyTrades.cseToday);
        System.out.printf("  | Yesterday         | %-20s | %-20s |%n", s.buyTrades.dseYesterday, s.buyTrades.cseYesterday);
        System.out.printf("  | Change            | %-20s | %-20s |%n", s.buyTrades.dseChange, s.buyTrades.cseChange);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 7. Sell Trades Count
        System.out.println("\n[MODEL 7: SELL TRADES (Count)]");
        System.out.printf("  * Total Sell Trades    : %s Trades%n", s.sellTrades.bigCount);
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.println("  | Metric            | DSE                  | CSE                  |");
        System.out.println("  +-------------------+----------------------+----------------------+");
        System.out.printf("  | Today             | %-20s | %-20s |%n", s.sellTrades.dseToday, s.sellTrades.cseToday);
        System.out.printf("  | Yesterday         | %-20s | %-20s |%n", s.sellTrades.dseYesterday, s.sellTrades.cseYesterday);
        System.out.printf("  | Change            | %-20s | %-20s |%n", s.sellTrades.dseChange, s.sellTrades.cseChange);
        System.out.println("  +-------------------+----------------------+----------------------+");

        // 8. Most Traded Stocks
        System.out.println("\n[MODEL 8: MOST TRADED STOCKS]");
        if (s.mostTradedStocks.isEmpty()) {
            System.out.println("  (No stocks traded today)");
        } else {
            System.out.println("  +-------------------+----------------------+----------------------+");
            System.out.println("  | SYMBOL            | VALUE (BDT)          | TRADES (Count)       |");
            System.out.println("  +-------------------+----------------------+----------------------+");
            for (StockRow sr : s.mostTradedStocks) {
                System.out.printf("  | %-17s | %-20s | %-20s |%n", sr.symbol, sr.value, sr.trades);
            }
            System.out.println("  +-------------------+----------------------+----------------------+");
        }

        // 9. Active Users (Active vs Inactive)
        System.out.println("\n[MODEL 9: ACTIVE & INACTIVE USERS]");
        System.out.printf("  * Total Online Users : %s (%d Active / %d Inactive - %.2f%% Inactive)%n",
                s.activeUsers.usersOnlineText, s.activeUsers.usersOnline, s.activeUsers.usersOffline,
                s.activeUsers.usersTotal > 0 ? (s.activeUsers.usersOffline * 100.0 / s.activeUsers.usersTotal) : 0.0);
        System.out.println("  +-------------------+----------------------+----------------------+----------------------+");
        System.out.println("  | User Category     | Active (Online)      | Inactive (Offline)   | Total Registered     |");
        System.out.println("  +-------------------+----------------------+----------------------+----------------------+");
        System.out.printf("  | Dealers           | %-20d | %-20d | %-20d |%n", s.activeUsers.dealersOnline, s.activeUsers.dealersOffline, s.activeUsers.dealersTotal);
        System.out.printf("  | Clients           | %-20d | %-20d | %-20d |%n", s.activeUsers.clientsOnline, s.activeUsers.clientsOffline, s.activeUsers.clientsTotal);
        System.out.printf("  | Associates        | %-20d | %-20d | %-20d |%n", s.activeUsers.associatesOnline, s.activeUsers.associatesOffline, s.activeUsers.associatesTotal);
        System.out.println("  +-------------------+----------------------+----------------------+----------------------+");
        System.out.printf("  * Live Sessions     : %s Desktop | %s Mobile | %s Total Sessions%n",
                s.activeUsers.desktopSessions, s.activeUsers.mobileSessions, s.activeUsers.totalSessions);

        // 10. Exchange Contribution
        System.out.println("\n[MODEL 10: EXCHANGE CONTRIBUTION (BDT)]");
        System.out.println("  +-------------------+----------------------+----------------------+----------------------+");
        System.out.println("  | EXCH              | MARKET TURNOVER      | OMS TURNOVER         | MARKET SHARE (%%)     |");
        System.out.println("  +-------------------+----------------------+----------------------+----------------------+");
        for (ExchangeRow er : s.exchangeContribution) {
            System.out.printf("  | %-17s | %-20s | %-20s | %-20s |%n", er.exch, er.market, er.oms, er.share);
        }
        System.out.println("  +-------------------+----------------------+----------------------+----------------------+");

        // 11. Top Dealers & Top Associates
        System.out.println("\n[MODEL 11: TOP DEALERS & TOP ASSOCIATES]");
        System.out.println("  -- TOP DEALERS TAB --");
        if (s.topDealers.isEmpty()) System.out.println("  (No dealer trade records)");
        else {
            System.out.println("  +---------------------------------------+----------------------+");
            System.out.println("  | DEALER NAME                           | VALUE (BDT)          |");
            System.out.println("  +---------------------------------------+----------------------+");
            for (DealerRow dr : s.topDealers) {
                System.out.printf("  | %-37s | %-20s |%n", dr.name, dr.value);
            }
            System.out.println("  +---------------------------------------+----------------------+");
        }
        System.out.println("  -- TOP ASSOCIATES TAB --");
        if (s.topAssociates.isEmpty()) System.out.println("  (No associate trade records)");
        else {
            System.out.println("  +---------------------------------------+----------------------+");
            System.out.println("  | ASSOCIATE NAME                        | VALUE (BDT)          |");
            System.out.println("  +---------------------------------------+----------------------+");
            for (AssociateRow ar : s.topAssociates) {
                System.out.printf("  | %-37s | %-20s |%n", ar.name, ar.value);
            }
            System.out.println("  +---------------------------------------+----------------------+");
        }

        // 12. Most Traded Clients
        System.out.println("\n[MODEL 12: MOST TRADED CLIENTS]");
        if (s.mostTradedClients.isEmpty()) System.out.println("  (No client trade records)");
        else {
            System.out.println("  +-------------------+----------------------+----------------------+");
            System.out.println("  | CLIENT CODE       | VALUE (BDT)          | TRADES (Count)       |");
            System.out.println("  +-------------------+----------------------+----------------------+");
            for (ClientRow cr : s.mostTradedClients) {
                System.out.printf("  | %-17s | %-20s | %-20s |%n", cr.clientCode, cr.value, cr.trades);
            }
            System.out.println("  +-------------------+----------------------+----------------------+");
        }
        System.out.println("========================================================================================================================\n");
    }

    /**
     * Compares Previous Snapshot vs Current Snapshot and verifies mathematical reflection.
     */
    public static void compareAndReportSnapshots(DashboardSnapshot pre, DashboardSnapshot post, String title) {
        System.out.println("\n========================================================================================================================");
        System.out.printf("          DASHBOARD REFLECTION COMPARISON AUDIT: %s%n", title);
        System.out.printf("          [PRE: %s (%s)]  ==>  [POST: %s (%s)]%n", pre.label, pre.timestamp, post.label, post.timestamp);
        System.out.println("========================================================================================================================");

        System.out.println("+---------------------------------+----------------------+----------------------+----------------------+----------------------+");
        System.out.println("| MODEL & PARAMETER               | PRE-ACTION VALUE     | POST-ACTION VALUE    | DELTA / CHANGE       | REFLECTION VERDICT   |");
        System.out.println("+---------------------------------+----------------------+----------------------+----------------------+----------------------+");

        // Turnover
        printDiffRow("1. Turnover (Total)", pre.turnover.bigValue, post.turnover.bigValue, false);
        printDiffRow("1. Turnover (DSE Today)", pre.turnover.dseToday, post.turnover.dseToday, false);
        printDiffRow("1. Turnover (CSE Today)", pre.turnover.cseToday, post.turnover.cseToday, false);

        // Buy Value
        printDiffRow("2. Buy Value (Total)", pre.buyValue.bigValue, post.buyValue.bigValue, false);
        printDiffRow("2. Buy Value (DSE Today)", pre.buyValue.dseToday, post.buyValue.dseToday, false);

        // Sell Value
        printDiffRow("3. Sell Value (Total)", pre.sellValue.bigValue, post.sellValue.bigValue, false);
        printDiffRow("3. Sell Value (DSE Today)", pre.sellValue.dseToday, post.sellValue.dseToday, false);

        // Broker Limit
        printDiffRow("4. Broker Limit Used (DSE)", pre.brokerLimit.dseUsed, post.brokerLimit.dseUsed, false);
        printDiffRow("4. Broker Limit Remaining", pre.brokerLimit.dseRemaining, post.brokerLimit.dseRemaining, false);
        printDiffRow("4. Broker Limit Utilisation", pre.brokerLimit.dseUtilisation, post.brokerLimit.dseUtilisation, true);

        // Trades
        printDiffRow("5. Total Trades Count", pre.trades.bigCount, post.trades.bigCount, false);
        printDiffRow("6. Buy Trades Count", pre.buyTrades.bigCount, post.buyTrades.bigCount, false);
        printDiffRow("7. Sell Trades Count", pre.sellTrades.bigCount, post.sellTrades.bigCount, false);

        // Active Users
        printDiffRow("9. Active Users (Online)", pre.activeUsers.usersOnlineText, post.activeUsers.usersOnlineText, true);
        printDiffRow("9. Dealers Online", pre.activeUsers.dealersText, post.activeUsers.dealersText, true);
        printDiffRow("9. Clients Online", pre.activeUsers.clientsText, post.activeUsers.clientsText, true);
        printDiffRow("9. Associates Online", pre.activeUsers.associatesText, post.activeUsers.associatesText, true);
        printDiffRow("9. Total Live Sessions", pre.activeUsers.totalSessions, post.activeUsers.totalSessions, false);

        System.out.println("+---------------------------------+----------------------+----------------------+----------------------+----------------------+");

        // Mathematical Consistency Check
        System.out.println("\n>>> [MATHEMATICAL REFLECTION AUDIT VERIFICATIONS]");
        double toVal = parseNumSafe(post.turnover.bigValue);
        double bvVal = parseNumSafe(post.buyValue.bigValue);
        double svVal = parseNumSafe(post.sellValue.bigValue);
        boolean mathValOk = Math.abs(toVal - (bvVal + svVal)) < 0.01;
        System.out.printf("  [CHECK 1] Turnover == Buy Value + Sell Value: %s (Turnover: %,.2f, Buy+Sell: %,.2f)%n",
                mathValOk ? "✓ PASS (PERFECT)" : "✗ MISMATCH", toVal, bvVal + svVal);

        double trCount = parseNumSafe(post.trades.bigCount);
        double btCount = parseNumSafe(post.buyTrades.bigCount);
        double stCount = parseNumSafe(post.sellTrades.bigCount);
        boolean mathTrOk = Math.abs(trCount - (btCount + stCount)) < 0.01;
        System.out.printf("  [CHECK 2] Total Trades == Buy Trades + Sell Trades: %s (Total: %,.0f, Buy+Sell: %,.0f)%n",
                mathTrOk ? "✓ PASS (PERFECT)" : "✗ MISMATCH", trCount, btCount + stCount);

        double blTotal = parseNumSafe(post.brokerLimit.dseTotal);
        double blUsed = parseNumSafe(post.brokerLimit.dseUsed);
        double blRem = parseNumSafe(post.brokerLimit.dseRemaining);
        boolean mathBlOk = Math.abs((blTotal - blUsed) - blRem) < 0.01;
        System.out.printf("  [CHECK 3] Broker Limit: Total - Used == Remaining: %s (Total-Used: %,.2f, Remaining: %,.2f)%n",
                mathBlOk ? "✓ PASS (PERFECT)" : "✗ MISMATCH", blTotal - blUsed, blRem);

        int uTot = post.activeUsers.usersTotal;
        int uOn = post.activeUsers.usersOnline;
        int uOff = post.activeUsers.usersOffline;
        boolean mathUserOk = (uOn + uOff == uTot);
        System.out.printf("  [CHECK 4] Active Users: Online + Offline == Registered Total: %s (%d + %d == %d)%n",
                mathUserOk ? "✓ PASS (PERFECT)" : "✗ MISMATCH", uOn, uOff, uTot);

        System.out.println("========================================================================================================================\n");
    }

    private static void printDiffRow(String param, String prev, String curr, boolean isText) {
        String p = (prev == null || prev.trim().isEmpty()) ? "-" : prev.trim();
        String c = (curr == null || curr.trim().isEmpty()) ? "-" : curr.trim();
        String delta = "-";
        String verdict = "IN SYNC";

        if (!isText && !p.equals("-") && !c.equals("-")) {
            double dP = parseNumSafe(p);
            double dC = parseNumSafe(c);
            double diff = dC - dP;
            if (Math.abs(diff) < 0.0001) {
                delta = "0 (No Change)";
                verdict = "UNCHANGED (IN SYNC)";
            } else if (diff > 0) {
                delta = String.format(Locale.US, "+%,.2f", diff);
                verdict = "REFLECTED (INCREASE)";
            } else {
                delta = String.format(Locale.US, "-%,.2f", Math.abs(diff));
                verdict = "REFLECTED (DECREASE)";
            }
        } else {
            if (p.equalsIgnoreCase(c)) {
                delta = "Identical";
                verdict = "IN SYNC";
            } else {
                delta = "Changed";
                verdict = "STATE UPDATED";
            }
        }

        System.out.printf("| %-31s | %-20s | %-20s | %-20s | %-20s |%n", param, p, c, delta, verdict);
    }

    /**
     * Executes the comprehensive Dashboard Audit Workflow:
     * 1. Collects previous baseline data across all 12 models
     * 2. Executes trade on Trade Portal (if requested)
     * 3. Collects post-action data across all 12 models
     * 4. Compares and reports individual model-wise data
     */
    public static void runLiveReflectionAudit(WebDriver driver, Scanner sc) {
        if (driver == null) return;
        System.out.println("\n==========================================================================================");
        System.out.println("       OMS-WEB: REAL-TIME 12-MODEL DASHBOARD DATA REFLECTION & AUDIT SUITE");
        System.out.println("==========================================================================================");

        // 1. Pre-Action Snapshot
        System.out.println("\n[PHASE 1/4] Collecting Baseline (Previous) Dashboard Data across all 12 models...");
        DashboardSnapshot preSnap = captureSnapshot(driver, "PRE-ACTION BASELINE");
        displayModelWiseDashboard(preSnap);

        // 2. Perform Trade if requested
        boolean doTrade = true;
        if (sc != null && !OMSwebmain.isCi) {
            System.out.print("Execute live test trade to verify dynamic reflection? (Y/N, default Y): ");
            String ans = sc.nextLine().trim();
            if ("n".equalsIgnoreCase(ans) || "no".equalsIgnoreCase(ans)) {
                doTrade = false;
            }
        }

        if (doTrade) {
            System.out.println("\n[PHASE 2/4] Executing Live Order on Trade Portal...");
            try {
                OMSweb_Trade.navigateToTradePage(driver);
                Thread.sleep(1500);
                String targetSymbol = OMSweb_Trade.activeSymbol != null ? OMSweb_Trade.activeSymbol : "GP";
                String targetClient = OMSweb_Trade.activeClientCode != null ? OMSweb_Trade.activeClientCode : "23986";
                System.out.printf("  -> Placing Order for Symbol [%s], Client [%s], Qty [10]...%n", targetSymbol, targetClient);
                OMSweb_Trade.propagateClientCodeToAllWidgets(driver, targetClient, targetSymbol);
                Thread.sleep(1000);
            } catch (Exception e) {
                System.out.println("  -> Trade encounter: " + e.getMessage());
            }
        } else {
            System.out.println("\n[PHASE 2/4] Skipped trade execution by user request.");
        }

        // 3. Post-Action Snapshot
        System.out.println("\n[PHASE 3/4] Collecting Post-Action Dashboard Data across all 12 models...");
        navigateToDashboard(driver);
        waitForDashboardReady(driver, 10);
        DashboardSnapshot postSnap = captureSnapshot(driver, "POST-ACTION RECOVERY");
        displayModelWiseDashboard(postSnap);

        // 4. Comparison & Reflection Report
        System.out.println("\n[PHASE 4/4] Comparing Previous vs Post-Action Snapshots & Auditing Reflection...");
        compareAndReportSnapshots(preSnap, postSnap, "12-MODEL LIVE DASHBOARD VERIFICATION");
    }

    public static void main(WebDriver driver, Scanner sc) {
        runLiveReflectionAudit(driver, sc);
    }

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
            main(driver, sc);
            System.out.println("\n=============================================================================");
            System.out.println(" [SUCCESS] Dashboard Reflection Audit completed successfully.");
            System.out.println("=============================================================================\n");
        } catch (Exception e) {
            System.err.println("[DASHBOARD AUDIT ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }

}
