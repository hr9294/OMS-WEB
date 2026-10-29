import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OMSweb_DBMarketData
 * -------------------
 * Collects Market Data directly from PostgreSQL database tables (marketdb)
 * for the PUBLIC Board (board = 'PUBLIC'):
 *
 *   1. LTP & Statistics   -> dse_md_ltp / cse_md_ltp
 *   2. Bid/Ask Price(BBO) -> dse_md_bbo / cse_md_bbo
 *   3. Market Depth       -> dse_md_mktdepth / cse_md_mktdepth
 *
 * STRICT FILTERING:
 *   - ONLY Public Board: board = 'PUBLIC'
 *   - STRICTLY EXCLUDES all other boards: DEBT, BUYIN, YIELDDBT, BUYDBT, ALTDBT,
 *     ATBDEBT, SDEBT, SBUYIN, ATBBUYIN, BLOCK, SBLOCK, ATBPUB, ATBX, SCPX, SPUBLIC,
 *     BULK, ODDLOT, FOREIGN, INDEX, etc.
 *   - STRICTLY EXCLUDES non-equity/derivative instruments: Bonds, Sukuk, Treasury
 *     Bills/Bonds (TB...), Debentures, Options (-CE, -PE), Futures, Multi-leg (-SPD, -XX).
 *
 * Configured for UFTFAST OMS / UFTCL UAT environment:
 *   MARKETDATA_DB_URI = 'postgresql+psycopg2://marketdata:pass4321@10.20.8.29:5445/marketdb'
 */
public class OMSweb_DBMarketData {

    // Database Connection Parameters
    public static final String MARKETDATA_DB_URI = System.getenv("MARKETDATA_DB_URI") != null ?
            System.getenv("MARKETDATA_DB_URI") : "postgresql+psycopg2://marketdata:pass4321@10.20.8.29:5445/marketdb";
    public static final String DB_HOST           = System.getenv("MKT_DB_HOST") != null ?
            System.getenv("MKT_DB_HOST") : "10.20.8.29";
    public static final int    DB_PORT           = System.getenv("MKT_DB_PORT") != null ?
            Integer.parseInt(System.getenv("MKT_DB_PORT")) : 5445;
    public static final String DB_NAME           = System.getenv("MKT_DB_NAME") != null ?
            System.getenv("MKT_DB_NAME") : "marketdb";
    public static final String DB_USER           = System.getenv("MKT_DB_USER") != null ?
            System.getenv("MKT_DB_USER") : "marketdata";
    public static final String DB_PASSWORD       = System.getenv("MKT_DB_PASS") != null ?
            System.getenv("MKT_DB_PASS") : "pass4321";

    // ==========================================
    // 1. DATA MODELS
    // ==========================================
    public static class PublicSymbolLtp {
        public String symbol;
        public String companyName;
        public String board; // "PUBLIC"
        public double ltp;
        public double ycp;
        public double circuitUp;
        public double circuitDown;
        public double floorPrice;
        public double open;
        public double high;
        public double low;
        public double close;
        public long   totalQty;
        public String lastUpdate;

        public PublicSymbolLtp(String symbol, String companyName, double ltp, double ycp,
                               double circuitUp, double circuitDown, double floorPrice,
                               double open, double high, double low, double close,
                               long totalQty, String lastUpdate) {
            this.symbol = symbol;
            this.companyName = companyName;
            this.board = "PUBLIC";
            this.ltp = ltp;
            this.ycp = ycp;
            this.circuitUp = circuitUp;
            this.circuitDown = circuitDown;
            this.floorPrice = floorPrice;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.totalQty = totalQty;
            this.lastUpdate = lastUpdate;
        }

        @Override
        public String toString() {
            return String.format("%s [LTP: %.2f | YCP: %.2f | SH: %.2f | SL: %.2f | Qty: %d]",
                    symbol, ltp, ycp, circuitUp, circuitDown, totalQty);
        }
    }

    public static class BboRecord {
        public String symbol;
        public double bidPrice;
        public long   bidQty;
        public double askPrice;
        public long   askQty;
        public int    priceDecimals;
        public String lastUpdate;

        public BboRecord(String symbol, double bidPrice, long bidQty, double askPrice, long askQty, int priceDecimals, String lastUpdate) {
            this.symbol = symbol;
            this.bidPrice = bidPrice;
            this.bidQty = bidQty;
            this.askPrice = askPrice;
            this.askQty = askQty;
            this.priceDecimals = priceDecimals;
            this.lastUpdate = lastUpdate;
        }
    }

    public static class DepthLevel {
        public int    level;
        public double price;
        public long   qty;
        public int    orders;

        public DepthLevel(int level, double price, long qty, int orders) {
            this.level = level;
            this.price = price;
            this.qty = qty;
            this.orders = orders;
        }
    }

    public static class DepthRecord {
        public String symbol;
        public List<DepthLevel> bidLevels = new ArrayList<>();
        public List<DepthLevel> askLevels = new ArrayList<>();
        public int priceDecimals = 2;
        public String lastUpdate = "";

        public DepthRecord(String symbol, int priceDecimals, String lastUpdate) {
            this.symbol = symbol;
            this.priceDecimals = priceDecimals;
            this.lastUpdate = lastUpdate;
        }
    }

    // In-memory caches
    private static final Map<String, PublicSymbolLtp> DSE_LTP_CACHE = new LinkedHashMap<>();
    private static final Map<String, PublicSymbolLtp> CSE_LTP_CACHE = new LinkedHashMap<>();

    private static final Map<String, BboRecord> DSE_BBO_CACHE = new LinkedHashMap<>();
    private static final Map<String, BboRecord> CSE_BBO_CACHE = new LinkedHashMap<>();

    private static final Map<String, DepthRecord> DSE_DEPTH_CACHE = new LinkedHashMap<>();
    private static final Map<String, DepthRecord> CSE_DEPTH_CACHE = new LinkedHashMap<>();

    // ==========================================
    // 2. STRICT PUBLIC EQUITY VALIDATOR
    // ==========================================
    public static boolean isStrictlyPublicEquity(String symbol, String companyName, String sector, String symbolInstr) {
        if (symbol == null || symbol.trim().isEmpty()) return false;
        String sUpper = symbol.trim().toUpperCase();

        if (sUpper.endsWith("-CE") || sUpper.endsWith("-PE") || sUpper.endsWith("-XX") || sUpper.endsWith("-SPD")) {
            return false;
        }

        if (sUpper.startsWith("TB") && sUpper.length() >= 6) {
            boolean hasDigit = false;
            for (char c : sUpper.toCharArray()) {
                if (Character.isDigit(c)) { hasDigit = true; break; }
            }
            if (hasDigit) return false;
        }
        if (sUpper.contains("BOND") || sUpper.contains("SUKUK")) {
            return false;
        }

        String secUpper = (sector == null) ? "" : sector.toUpperCase();
        if (secUpper.contains("BOND") || secUpper.contains("DEBT") || secUpper.contains("DEBENTURE")) {
            return false;
        }

        String instrUpper = (symbolInstr == null) ? "" : symbolInstr.toUpperCase();
        if (instrUpper.equals("OPT") || instrUpper.equals("FUT") || instrUpper.equals("MLEG") ||
            instrUpper.equals("TBILL") || instrUpper.equals("TBOND") || instrUpper.equals("CORP") ||
            instrUpper.equals("GOVDBT") || instrUpper.equals("CORPDBT") || instrUpper.equals("TB") ||
            instrUpper.equals("CB") || instrUpper.equals("DB")) {
            return false;
        }

        return true;
    }

    // ==========================================
    // 3. LTP & STATS: dse_md_ltp / cse_md_ltp
    // ==========================================
    public static synchronized Map<String, PublicSymbolLtp> fetchPublicBoardLtp(String exchange) {
        String exClean = (exchange == null || exchange.trim().isEmpty()) ? "DSE" : exchange.trim().toUpperCase();
        Map<String, PublicSymbolLtp> targetCache = exClean.equals("CSE") ? CSE_LTP_CACHE : DSE_LTP_CACHE;
        if (!targetCache.isEmpty()) {
            return Collections.unmodifiableMap(targetCache);
        }

        String tableLtp = exClean.equals("CSE") ? "cse_md_ltp" : "dse_md_ltp";
        String tableSym = exClean.equals("CSE") ? "cse_md_symbols" : "dse_md_symbols";

        try {
            String psqlExe = findPsqlExecutable();
            if (psqlExe != null) {
                String query = String.format(
                        "SELECT l.symbol, COALESCE(s.company_name, l.symbol), l.ltp, l.ycp, " +
                        "l.circuit_up, l.circuit_down, l.floor_price, l.open, l.high, l.low, l.close, l.total_qty, l.last_update, " +
                        "COALESCE(s.sector, '') as sector, COALESCE(s.symbol_instr, '') as symbol_instr " +
                        "FROM %s l LEFT JOIN %s s ON l.symbol = s.symbol AND l.board = s.board " +
                        "WHERE l.board = 'PUBLIC' " +
                        "  AND (s.symbol_instr IS NULL OR s.symbol_instr IN ('EQ', 'MF', 'CS')) " +
                        "  AND COALESCE(s.sector, '') NOT ILIKE '%%Bond%%' " +
                        "  AND COALESCE(s.sector, '') NOT ILIKE '%%Debt%%' " +
                        "  AND COALESCE(s.sector, '') NOT ILIKE '%%Debenture%%' " +
                        "  AND l.symbol NOT LIKE '%%-CE' " +
                        "  AND l.symbol NOT LIKE '%%-PE' " +
                        "  AND l.symbol NOT LIKE '%%-XX' " +
                        "  AND l.symbol NOT LIKE '%%-SPD' " +
                        "  AND l.symbol NOT LIKE '%%BOND%%' " +
                        "  AND l.symbol NOT LIKE 'TB%%' " +
                        "  AND l.symbol NOT LIKE '%%SUKUK%%' " +
                        "  AND l.ltp IS NOT NULL AND l.ltp > 0 " +
                        "ORDER BY l.symbol ASC;",
                        tableLtp, tableSym);

                ProcessBuilder pb = new ProcessBuilder(
                        psqlExe,
                        "-h", DB_HOST,
                        "-p", String.valueOf(DB_PORT),
                        "-U", DB_USER,
                        "-d", DB_NAME,
                        "-t", "-A", "-F", "\t",
                        "-c", query
                );
                pb.environment().put("PGPASSWORD", DB_PASSWORD);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] p = line.split("\t", -1);
                        if (p.length >= 3) {
                            String sym = p[0].trim().toUpperCase();
                            String name = p.length > 1 ? p[1].trim() : sym;
                            String sec = p.length > 13 ? p[13].trim() : "";
                            String sinstr = p.length > 14 ? p[14].trim() : "";

                            if (!isStrictlyPublicEquity(sym, name, sec, sinstr)) continue;

                            if (!sym.isEmpty()) {
                                double ltp = parseDouble(p[2]);
                                double ycp = p.length > 3 ? parseDouble(p[3]) : ltp;
                                double up = p.length > 4 ? parseDouble(p[4]) : 0.0;
                                double down = p.length > 5 ? parseDouble(p[5]) : 0.0;
                                double floor = p.length > 6 ? parseDouble(p[6]) : 0.0;
                                double open = p.length > 7 ? parseDouble(p[7]) : 0.0;
                                double high = p.length > 8 ? parseDouble(p[8]) : 0.0;
                                double low = p.length > 9 ? parseDouble(p[9]) : 0.0;
                                double close = p.length > 10 ? parseDouble(p[10]) : 0.0;
                                long qty = p.length > 11 ? parseLong(p[11]) : 0L;
                                String lastUp = p.length > 12 ? p[12].trim() : "";

                                targetCache.put(sym, new PublicSymbolLtp(sym, name, ltp, ycp, up, down, floor, open, high, low, close, qty, lastUp));
                            }
                        }
                    }
                }
                proc.waitFor();
                if (!targetCache.isEmpty()) {
                    saveToJsonCache(targetCache);
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN: OMSweb_DBMarketData LTP psql] " + e.getMessage());
        }

        if (targetCache.isEmpty()) {
            loadFromJsonCache(targetCache);
        }

        return Collections.unmodifiableMap(targetCache);
    }

    public static synchronized Map<String, PublicSymbolLtp> collectLtp(String exchange) {
        String exClean = (exchange == null || exchange.trim().isEmpty()) ? "DSE" : exchange.trim().toUpperCase();
        if (exClean.equals("CSE")) {
            CSE_LTP_CACHE.clear();
        } else {
            DSE_LTP_CACHE.clear();
        }
        Map<String, PublicSymbolLtp> result = fetchPublicBoardLtp(exClean);
        System.out.printf("[SUCCESS: DB LTP] Collected %d Public Board symbols from %s:%d/%s (%s)%n",
                result.size(), DB_HOST, DB_PORT, DB_NAME, exClean);
        return result;
    }

    // ==========================================
    // 4. BID/ASK PRICE (BBO): dse_md_bbo / cse_md_bbo
    // ==========================================
    public static synchronized Map<String, BboRecord> fetchPublicBoardBbo(String exchange) {
        String exClean = (exchange == null || exchange.trim().isEmpty()) ? "DSE" : exchange.trim().toUpperCase();
        Map<String, BboRecord> targetCache = exClean.equals("CSE") ? CSE_BBO_CACHE : DSE_BBO_CACHE;
        if (!targetCache.isEmpty()) {
            return Collections.unmodifiableMap(targetCache);
        }

        String tableBbo = exClean.equals("CSE") ? "cse_md_bbo" : "dse_md_bbo";
        String tableSym = exClean.equals("CSE") ? "cse_md_symbols" : "dse_md_symbols";

        try {
            String psqlExe = findPsqlExecutable();
            if (psqlExe != null) {
                String query = String.format(
                        "SELECT b.symbol, COALESCE(b.bid_price, 0.0), COALESCE(b.bid_qty, 0), " +
                        "COALESCE(b.ask_price, 0.0), COALESCE(b.ask_qty, 0), COALESCE(b.price_decimals, 2), b.last_update, " +
                        "COALESCE(s.company_name, b.symbol), COALESCE(s.sector, ''), COALESCE(s.symbol_instr, '') " +
                        "FROM %s b LEFT JOIN %s s ON b.symbol = s.symbol AND b.board = s.board " +
                        "WHERE b.board = 'PUBLIC' " +
                        "  AND (s.symbol_instr IS NULL OR s.symbol_instr IN ('EQ', 'MF', 'CS')) " +
                        "  AND COALESCE(s.sector, '') NOT ILIKE '%%Bond%%' " +
                        "  AND COALESCE(s.sector, '') NOT ILIKE '%%Debt%%' " +
                        "  AND COALESCE(s.sector, '') NOT ILIKE '%%Debenture%%' " +
                        "  AND b.symbol NOT LIKE '%%-CE' " +
                        "  AND b.symbol NOT LIKE '%%-PE' " +
                        "  AND b.symbol NOT LIKE '%%-XX' " +
                        "  AND b.symbol NOT LIKE '%%-SPD' " +
                        "  AND b.symbol NOT LIKE '%%BOND%%' " +
                        "  AND b.symbol NOT LIKE 'TB%%' " +
                        "  AND b.symbol NOT LIKE '%%SUKUK%%' " +
                        "ORDER BY b.symbol ASC;",
                        tableBbo, tableSym);

                ProcessBuilder pb = new ProcessBuilder(
                        psqlExe,
                        "-h", DB_HOST,
                        "-p", String.valueOf(DB_PORT),
                        "-U", DB_USER,
                        "-d", DB_NAME,
                        "-t", "-A", "-F", "\t",
                        "-c", query
                );
                pb.environment().put("PGPASSWORD", DB_PASSWORD);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] p = line.split("\t", -1);
                        if (p.length >= 5) {
                            String sym = p[0].trim().toUpperCase();
                            String name = p.length > 7 ? p[7].trim() : sym;
                            String sec = p.length > 8 ? p[8].trim() : "";
                            String sinstr = p.length > 9 ? p[9].trim() : "";

                            if (!isStrictlyPublicEquity(sym, name, sec, sinstr)) continue;

                            double bPrice = parseDouble(p[1]);
                            long bQty = parseLong(p[2]);
                            double aPrice = parseDouble(p[3]);
                            long aQty = parseLong(p[4]);
                            int dec = p.length > 5 ? (int) parseLong(p[5]) : 2;
                            String lup = p.length > 6 ? p[6].trim() : "";

                            targetCache.put(sym, new BboRecord(sym, bPrice, bQty, aPrice, aQty, dec, lup));
                        }
                    }
                }
                proc.waitFor();
            }
        } catch (Exception e) {
            System.err.println("[WARN: OMSweb_DBMarketData BBO psql] " + e.getMessage());
        }

        return Collections.unmodifiableMap(targetCache);
    }

    // ==========================================
    // 5. MARKET DEPTH: dse_md_mktdepth / cse_md_mktdepth
    // ==========================================
    public static synchronized DepthRecord fetchSymbolDepth(String exchange, String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return null;
        String symClean = symbol.trim().toUpperCase();
        String exClean = (exchange == null || exchange.trim().isEmpty()) ? "DSE" : exchange.trim().toUpperCase();

        Map<String, DepthRecord> targetCache = exClean.equals("CSE") ? CSE_DEPTH_CACHE : DSE_DEPTH_CACHE;
        if (targetCache.containsKey(symClean)) {
            return targetCache.get(symClean);
        }

        String tableDepth = exClean.equals("CSE") ? "cse_md_mktdepth" : "dse_md_mktdepth";
        String tableSym = exClean.equals("CSE") ? "cse_md_symbols" : "dse_md_symbols";

        try {
            String psqlExe = findPsqlExecutable();
            if (psqlExe != null) {
                String query = String.format(
                        "SELECT m.symbol, m.bid_levels, m.ask_levels, COALESCE(m.price_decimals, 2), m.last_update " +
                        "FROM %s m LEFT JOIN %s s ON m.symbol = s.symbol AND m.board = s.board " +
                        "WHERE m.board = 'PUBLIC' AND m.symbol = '%s' LIMIT 1;",
                        tableDepth, tableSym, symClean);

                ProcessBuilder pb = new ProcessBuilder(
                        psqlExe,
                        "-h", DB_HOST,
                        "-p", String.valueOf(DB_PORT),
                        "-U", DB_USER,
                        "-d", DB_NAME,
                        "-t", "-A", "-F", "\t",
                        "-c", query
                );
                pb.environment().put("PGPASSWORD", DB_PASSWORD);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String[] p = line.split("\t", -1);
                        if (p.length >= 3) {
                            String bRaw = p[1].trim();
                            String aRaw = p[2].trim();
                            int dec = p.length > 3 ? (int) parseLong(p[3]) : 2;
                            String lup = p.length > 4 ? p[4].trim() : "";

                            DepthRecord rec = new DepthRecord(symClean, dec, lup);
                            double factor = Math.pow(10, dec);

                            parseDepthJsonArray(bRaw, rec.bidLevels, factor);
                            parseDepthJsonArray(aRaw, rec.askLevels, factor);

                            targetCache.put(symClean, rec);
                            return rec;
                        }
                    }
                }
                proc.waitFor();
            }
        } catch (Exception e) {
            System.err.println("[WARN: OMSweb_DBMarketData Depth psql] " + e.getMessage());
        }
        return null;
    }

    private static void parseDepthJsonArray(String raw, List<DepthLevel> list, double factor) {
        if (raw == null || raw.isEmpty() || raw.equals("[]")) return;
        Pattern lvlPat = Pattern.compile("\\[([0-9]+)\\s*,\\s*([0-9]+)\\s*,\\s*\\[[^\\]]*\\]\\s*,\\s*([0-9]+)\\]");
        Matcher m = lvlPat.matcher(raw);
        int lvlIdx = 1;
        while (m.find()) {
            double rate = parseDouble(m.group(1)) / factor;
            long qty = parseLong(m.group(2));
            int orders = (int) parseLong(m.group(3));
            list.add(new DepthLevel(lvlIdx++, rate, qty, orders));
        }
    }

    // ==========================================
    // 6. UNIFIED COMPLETE MARKET SNAPSHOT
    // ==========================================
    public static Map<String, String> getCompleteMarketSnapshot(String symbol, String exchange) {
        Map<String, String> data = new LinkedHashMap<>();
        if (symbol == null || symbol.trim().isEmpty()) return data;

        String symClean = symbol.trim().toUpperCase();
        String exClean = (exchange == null || exchange.trim().isEmpty()) ? "DSE" : exchange.trim().toUpperCase();

        data.put("Symbol", symClean);
        data.put("Exchange", exClean);
        data.put("DATA_SOURCE", String.format("DB Tables (%s PUBLIC: ltp, bbo, mktdepth)", exClean));

        // 1. LTP & Limits from dse_md_ltp / cse_md_ltp
        Map<String, PublicSymbolLtp> ltpMap = fetchPublicBoardLtp(exClean);
        PublicSymbolLtp ltpRec = ltpMap.get(symClean);
        if (ltpRec != null) {
            data.put("LTP", String.format(Locale.US, "%.2f", ltpRec.ltp));
            data.put("YCP", String.format(Locale.US, "%.2f", ltpRec.ycp));
            data.put("High", String.format(Locale.US, "%.2f", ltpRec.high));
            data.put("Low", String.format(Locale.US, "%.2f", ltpRec.low));
            data.put("Open", String.format(Locale.US, "%.2f", ltpRec.open));
            data.put("Close", String.format(Locale.US, "%.2f", ltpRec.close));
            data.put("Total_Volume", String.valueOf(ltpRec.totalQty));
            data.put("SH", String.format(Locale.US, "%.2f", ltpRec.circuitUp));
            data.put("SL", String.format(Locale.US, "%.2f", ltpRec.circuitDown));
            data.put("CH", String.format(Locale.US, "%.2f", ltpRec.circuitUp));
            data.put("CL", String.format(Locale.US, "%.2f", ltpRec.circuitDown));
        }

        // 2. Bid/Ask Price (BBO) from dse_md_bbo / cse_md_bbo
        Map<String, BboRecord> bboMap = fetchPublicBoardBbo(exClean);
        BboRecord bboRec = bboMap.get(symClean);
        if (bboRec != null) {
            data.put("Best_Bid_Price", String.format(Locale.US, "%.2f", bboRec.bidPrice));
            data.put("Best_Bid_Qty", String.valueOf(bboRec.bidQty));
            data.put("Best_Ask_Price", String.format(Locale.US, "%.2f", bboRec.askPrice));
            data.put("Best_Ask_Qty", String.valueOf(bboRec.askQty));
        }

        // 3. Multi-level Market Depth from dse_md_mktdepth / cse_md_mktdepth
        DepthRecord depthRec = fetchSymbolDepth(exClean, symClean);
        if (depthRec != null) {
            for (int i = 1; i <= 5; i++) {
                if (i <= depthRec.bidLevels.size()) {
                    DepthLevel bl = depthRec.bidLevels.get(i - 1);
                    data.put("Bid_L" + i + "_Price", String.format(Locale.US, "%.2f", bl.price));
                    data.put("Bid_L" + i + "_Qty", String.valueOf(bl.qty));
                    data.put("Bid_L" + i + "_Ordr", String.valueOf(bl.orders));
                } else {
                    data.put("Bid_L" + i + "_Price", "-");
                    data.put("Bid_L" + i + "_Qty", "-");
                    data.put("Bid_L" + i + "_Ordr", "-");
                }

                if (i <= depthRec.askLevels.size()) {
                    DepthLevel al = depthRec.askLevels.get(i - 1);
                    data.put("Ask_L" + i + "_Price", String.format(Locale.US, "%.2f", al.price));
                    data.put("Ask_L" + i + "_Qty", String.valueOf(al.qty));
                    data.put("Ask_L" + i + "_Ordr", String.valueOf(al.orders));
                } else {
                    data.put("Ask_L" + i + "_Price", "-");
                    data.put("Ask_L" + i + "_Qty", "-");
                    data.put("Ask_L" + i + "_Ordr", "-");
                }
            }
        }

        return data;
    }

    public static List<String> getPublicSymbols(String exchange) {
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        List<String> symbols = new ArrayList<>(data.keySet());
        Collections.sort(symbols);
        return symbols;
    }

    public static double getSymbolLtp(String symbol, String exchange, double fallback) {
        if (symbol == null) return fallback;
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        PublicSymbolLtp record = data.get(symbol.trim().toUpperCase());
        return (record != null && record.ltp > 0) ? record.ltp : fallback;
    }

    public static boolean isPublicBoardSymbol(String symbol, String exchange) {
        if (symbol == null) return false;
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        return data.containsKey(symbol.trim().toUpperCase());
    }

    public static void printPublicBoardSummary(String exchange, int limit) {
        Map<String, PublicSymbolLtp> ltpMap = fetchPublicBoardLtp(exchange);
        Map<String, BboRecord> bboMap = fetchPublicBoardBbo(exchange);
        String ex = (exchange == null) ? "DSE" : exchange.toUpperCase();

        System.out.println("\n" + "=".repeat(115));
        System.out.printf("  DATABASE TABLES: %s ONLY PUBLIC BOARD MARKET DATA (board = 'PUBLIC')%n", ex);
        System.out.printf("    - LTP:        %s_md_ltp%n", ex.toLowerCase());
        System.out.printf("    - Bid/Ask:    %s_md_bbo%n", ex.toLowerCase());
        System.out.printf("    - MktDepth:   %s_md_mktdepth%n", ex.toLowerCase());
        System.out.printf("  URI: %s%n", MARKETDATA_DB_URI);
        System.out.printf("  Target: %s:%d/%s | Total Public Symbols: %d%n", DB_HOST, DB_PORT, DB_NAME, ltpMap.size());
        System.out.println("=".repeat(115));
        System.out.printf("%-4s | %-12s | %-26s | %10s | %18s | %18s | %15s%n",
                "#", "Symbol", "Company Name", "LTP (BDT)", "Best Bid (Qty)", "Best Ask (Qty)", "SH/SL Bounds");
        System.out.println("-".repeat(115));

        List<String> keys = new ArrayList<>(ltpMap.keySet());
        Collections.sort(keys);
        int max = Math.min(limit, keys.size());
        for (int i = 0; i < max; i++) {
            String sym = keys.get(i);
            PublicSymbolLtp r = ltpMap.get(sym);
            BboRecord b = bboMap.get(sym);

            String name = (r.companyName != null && r.companyName.length() > 26)
                    ? r.companyName.substring(0, 23) + "..."
                    : (r.companyName != null ? r.companyName : r.symbol);

            String bidStr = (b != null && b.bidPrice > 0) ? String.format(Locale.US, "%.2f (%d)", b.bidPrice, b.bidQty) : "-";
            String askStr = (b != null && b.askPrice > 0) ? String.format(Locale.US, "%.2f (%d)", b.askPrice, b.askQty) : "-";
            String boundsStr = String.format(Locale.US, "%.1f / %.1f", r.circuitUp, r.circuitDown);

            System.out.printf("%-4d | %-12s | %-26s | %10.2f | %18s | %18s | %15s%n",
                    (i + 1), r.symbol, name, r.ltp, bidStr, askStr, boundsStr);
        }
        if (keys.size() > limit) {
            System.out.printf("... and %d more Public Board symbols collected from DB tables.%n", keys.size() - limit);
        }
        System.out.println("=".repeat(115) + "\n");
    }

    private static void saveToJsonCache(Map<String, PublicSymbolLtp> cache) {
        String[] jsonPaths = {
            "OMS_WEB_Python/public_board_ltp.json",
            "D:\\Anitgravity\\OMS-WEB\\OMS_WEB_Python\\public_board_ltp.json",
            "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\OMS_WEB_Python\\public_board_ltp.json"
        };
        for (String p : jsonPaths) {
            File jf = new File(p);
            File parent = jf.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(jf), java.nio.charset.StandardCharsets.UTF_8))) {
                pw.println("{");
                int idx = 0;
                int total = cache.size();
                for (Map.Entry<String, PublicSymbolLtp> e : cache.entrySet()) {
                    PublicSymbolLtp r = e.getValue();
                    String cName = (r.companyName != null) ? r.companyName.replace("\"", "\\\"") : r.symbol;
                    pw.printf(Locale.US, "  \"%s\": {%n", r.symbol);
                    pw.printf(Locale.US, "    \"symbol\": \"%s\",%n", r.symbol);
                    pw.printf(Locale.US, "    \"company_name\": \"%s\",%n", cName);
                    pw.printf(Locale.US, "    \"board\": \"PUBLIC\",%n");
                    pw.printf(Locale.US, "    \"ltp\": %.2f,%n", r.ltp);
                    pw.printf(Locale.US, "    \"ycp\": %.2f,%n", r.ycp);
                    pw.printf(Locale.US, "    \"circuit_up\": %.2f,%n", r.circuitUp);
                    pw.printf(Locale.US, "    \"circuit_down\": %.2f,%n", r.circuitDown);
                    pw.printf(Locale.US, "    \"floor_price\": %.2f,%n", r.floorPrice);
                    pw.printf(Locale.US, "    \"open\": %.2f,%n", r.open);
                    pw.printf(Locale.US, "    \"high\": %.2f,%n", r.high);
                    pw.printf(Locale.US, "    \"low\": %.2f,%n", r.low);
                    pw.printf(Locale.US, "    \"close\": %.2f,%n", r.close);
                    pw.printf(Locale.US, "    \"total_qty\": %d,%n", r.totalQty);
                    pw.printf(Locale.US, "    \"last_update\": \"%s\"%n", r.lastUpdate != null ? r.lastUpdate : "");
                    pw.printf("  }%s%n", (idx == total - 1 ? "" : ","));
                    idx++;
                }
                pw.println("}");
                break;
            } catch (Exception ignored) {}
        }
    }

    private static String findPsqlExecutable() {
        String[] candidates = {
            "C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe",
            "C:\\Program Files\\PostgreSQL\\15\\bin\\psql.exe",
            "C:\\Program Files\\PostgreSQL\\14\\bin\\psql.exe",
            "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
            "psql"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) return c;
        }
        return "psql";
    }

    private static void loadFromJsonCache(Map<String, PublicSymbolLtp> cache) {
        String[] jsonPaths = {
            "OMS_WEB_Python/public_board_ltp.json",
            "D:\\Anitgravity\\OMS-WEB\\OMS_WEB_Python\\public_board_ltp.json",
            "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\OMS_WEB_Python\\public_board_ltp.json"
        };
        for (String p : jsonPaths) {
            File jf = new File(p);
            if (jf.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(jf))) {
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    parseSimpleJson(sb.toString(), cache);
                    if (!cache.isEmpty()) break;
                } catch (Exception ignored) {}
            }
        }
    }

    private static void parseSimpleJson(String json, Map<String, PublicSymbolLtp> cache) {
        int idx = 0;
        while ((idx = json.indexOf("\"symbol\":", idx)) != -1) {
            int blockStart = json.lastIndexOf("{", idx);
            int blockEnd = json.indexOf("}", idx);
            if (blockStart != -1 && blockEnd != -1) {
                String block = json.substring(blockStart, blockEnd + 1);
                String sym = extractJsonField(block, "symbol");
                String name = extractJsonField(block, "company_name");
                double ltp = parseDouble(extractJsonField(block, "ltp"));
                double ycp = parseDouble(extractJsonField(block, "ycp"));
                double up = parseDouble(extractJsonField(block, "circuit_up"));
                double down = parseDouble(extractJsonField(block, "circuit_down"));
                double floor = parseDouble(extractJsonField(block, "floor_price"));
                double open = parseDouble(extractJsonField(block, "open"));
                double high = parseDouble(extractJsonField(block, "high"));
                double low = parseDouble(extractJsonField(block, "low"));
                double close = parseDouble(extractJsonField(block, "close"));
                long qty = parseLong(extractJsonField(block, "total_qty"));
                String lastUp = extractJsonField(block, "last_update");
                String sec = extractJsonField(block, "sector");
                String sinstr = extractJsonField(block, "symbol_instr");

                if (!sym.isEmpty() && ltp > 0 && isStrictlyPublicEquity(sym, name, sec, sinstr)) {
                    cache.put(sym, new PublicSymbolLtp(sym, name, ltp, ycp, up, down, floor, open, high, low, close, qty, lastUp));
                }
                idx = blockEnd + 1;
            } else {
                idx += 9;
            }
        }
    }

    private static String extractJsonField(String block, String key) {
        String search = "\"" + key + "\":";
        int pos = block.indexOf(search);
        if (pos == -1) return "";
        int valStart = pos + search.length();
        while (valStart < block.length() && (block.charAt(valStart) == ' ' || block.charAt(valStart) == '\"')) {
            valStart++;
        }
        int valEnd = valStart;
        boolean inQuotes = (pos + search.length() < block.length() && block.charAt(pos + search.length()) == '\"')
                || (valStart > 0 && block.charAt(valStart - 1) == '\"');
        while (valEnd < block.length()) {
            char c = block.charAt(valEnd);
            if (inQuotes) {
                if (c == '\"') break;
            } else {
                if (c == ',' || c == '}' || c == '\n' || c == '\r') break;
            }
            valEnd++;
        }
        return block.substring(valStart, valEnd).trim();
    }

    private static double parseDouble(String s) {
        try {
            if (s == null) return 0.0;
            return Double.parseDouble(s.replaceAll("[^0-9.-]", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static long parseLong(String s) {
        try {
            if (s == null) return 0L;
            return Long.parseLong(s.replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static void main(String[] args) {
        String ex = args.length > 0 ? args[0] : "DSE";
        if ("COLLECT".equalsIgnoreCase(ex)) {
            String targetEx = args.length > 1 ? args[1] : "DSE";
            collectLtp(targetEx);
        } else if (args.length > 1) {
            String sym = args[1];
            Map<String, String> snap = getCompleteMarketSnapshot(sym, ex);
            System.out.printf("%n--- Complete Market Depth for %s (%s) from DB Tables ---%n", sym, ex);
            for (Map.Entry<String, String> entry : snap.entrySet()) {
                System.out.printf("  %-20s: %s%n", entry.getKey(), entry.getValue());
            }
        } else {
            printPublicBoardSummary(ex, 25);
        }
    }
}
