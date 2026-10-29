import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * OMSweb_DBMarketData
 * -------------------
 * Collects Symbols, LTP, YCP, and Circuit Limits directly from the PostgreSQL
 * database table (marketdb -> dse_md_ltp / cse_md_ltp) filtering specifically
 * for the Public Board ('PUBLIC').
 *
 * Configured for UFTFAST OMS / UFTCL UAT environment (192.168.155.22:5445).
 */
public class OMSweb_DBMarketData {

    // Database Connection Parameters
    public static final String DB_HOST     = "192.168.155.22";
    public static final int    DB_PORT     = 5445;
    public static final String DB_NAME     = "marketdb";
    public static final String DB_USER     = "marketdata";
    public static final String DB_PASSWORD = "pass4321";

    // Public Board Symbol & LTP Data Model
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

    // In-memory cache
    private static final Map<String, PublicSymbolLtp> DSE_CACHE = new LinkedHashMap<>();
    private static final Map<String, PublicSymbolLtp> CSE_CACHE = new LinkedHashMap<>();

    /**
     * Collects all Public Board symbols & LTPs from the DB table.
     * Table: dse_md_ltp (or cse_md_ltp) WHERE board = 'PUBLIC'
     */
    public static synchronized Map<String, PublicSymbolLtp> fetchPublicBoardLtp(String exchange) {
        String exClean = (exchange == null || exchange.trim().isEmpty()) ? "DSE" : exchange.trim().toUpperCase();
        Map<String, PublicSymbolLtp> targetCache = exClean.equals("CSE") ? CSE_CACHE : DSE_CACHE;
        if (!targetCache.isEmpty()) {
            return Collections.unmodifiableMap(targetCache);
        }

        String tableLtp = exClean.equals("CSE") ? "cse_md_ltp" : "dse_md_ltp";
        String tableSym = exClean.equals("CSE") ? "cse_md_symbols" : "dse_md_symbols";

        // 1. Try querying via PostgreSQL psql CLI
        try {
            String psqlExe = findPsqlExecutable();
            if (psqlExe != null) {
                String query = String.format(
                        "SELECT l.symbol, COALESCE(s.company_name, l.symbol), l.ltp, l.ycp, " +
                        "l.circuit_up, l.circuit_down, l.floor_price, l.open, l.high, l.low, l.close, l.total_qty, l.last_update " +
                        "FROM %s l LEFT JOIN %s s ON l.symbol = s.symbol AND l.board = s.board " +
                        "WHERE l.board = 'PUBLIC' AND l.ltp IS NOT NULL AND l.ltp > 0 ORDER BY l.symbol ASC;",
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
                            if (!sym.isEmpty()) {
                                String name = p.length > 1 ? p[1].trim() : sym;
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
            }
        } catch (Exception e) {
            System.err.println("[WARN: OMSweb_DBMarketData psql query] " + e.getMessage());
        }

        // 2. Fallback: Parse public_board_ltp.json if generated by Python module
        if (targetCache.isEmpty()) {
            loadFromJsonCache(targetCache);
        }

        return Collections.unmodifiableMap(targetCache);
    }

    /**
     * Returns a sorted List of all Public Board symbols collected from the DB table.
     */
    public static List<String> getPublicSymbols(String exchange) {
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        List<String> symbols = new ArrayList<>(data.keySet());
        Collections.sort(symbols);
        return symbols;
    }

    /**
     * Gets the live LTP for a symbol from the collected DB table data.
     */
    public static double getSymbolLtp(String symbol, String exchange, double fallback) {
        if (symbol == null) return fallback;
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        PublicSymbolLtp record = data.get(symbol.trim().toUpperCase());
        return (record != null && record.ltp > 0) ? record.ltp : fallback;
    }

    /**
     * Checks if a symbol belongs to the Public Board in the database table.
     */
    public static boolean isPublicBoardSymbol(String symbol, String exchange) {
        if (symbol == null) return false;
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        return data.containsKey(symbol.trim().toUpperCase());
    }

    /**
     * Prints a clean formatted summary of Public Board symbols & LTPs collected from DB table.
     */
    public static void printPublicBoardSummary(String exchange, int limit) {
        Map<String, PublicSymbolLtp> data = fetchPublicBoardLtp(exchange);
        String ex = (exchange == null) ? "DSE" : exchange.toUpperCase();
        System.out.println("\n" + "=".repeat(95));
        System.out.printf("  DATABASE TABLE: %s PUBLIC BOARD MARKET DATA (dse_md_ltp WHERE board = 'PUBLIC')%n", ex);
        System.out.printf("  Target: %s:%d/%s | Total Public Symbols Collected: %d%n", DB_HOST, DB_PORT, DB_NAME, data.size());
        System.out.println("=".repeat(95));
        System.out.printf("%-4s | %-12s | %-28s | %10s | %8s | %9s | %9s%n",
                "#", "Symbol", "Company Name", "LTP (BDT)", "YCP", "SH/CH", "SL/CL");
        System.out.println("-".repeat(95));

        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        int max = Math.min(limit, keys.size());
        for (int i = 0; i < max; i++) {
            PublicSymbolLtp r = data.get(keys.get(i));
            String name = (r.companyName != null && r.companyName.length() > 28)
                    ? r.companyName.substring(0, 25) + "..."
                    : (r.companyName != null ? r.companyName : r.symbol);
            System.out.printf("%-4d | %-12s | %-28s | %10.2f | %8.2f | %9.2f | %9.2f%n",
                    (i + 1), r.symbol, name, r.ltp, r.ycp, r.circuitUp, r.circuitDown);
        }
        if (keys.size() > limit) {
            System.out.printf("... and %d more Public Board symbols collected from DB table.%n", keys.size() - limit);
        }
        System.out.println("=".repeat(95) + "\n");
    }

    private static String findPsqlExecutable() {
        String[] candidates = {
            "C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe",
            "C:\\Program Files\\PostgreSQL\\15\\bin\\psql.exe",
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
        // Simple regex/substring parser for the cached records without external JSON libraries
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

                if (!sym.isEmpty() && ltp > 0) {
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
        printPublicBoardSummary(ex, 30);
    }
}
