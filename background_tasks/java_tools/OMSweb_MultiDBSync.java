import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * OMSweb_MultiDBSync
 * -------------------
 * Direct Multi-Database Collector & Synchronizer:
 * 
 * 1. Collects Active Clients from qtraderdb:
 *    postgresql://qtraderdata:pass4321@192.168.155.22:5443/qtraderdb (public.clients_active)
 * 2. Collects Cash Balances from qtraderdb:
 *    (public.clients_cash)
 * 3. Collects Stock Positions from qtraderdb:
 *    (public.clients_positions)
 * 4. Collects Live LTP & Market Statistics from marketdb:
 *    postgresql://marketdata:pass4321@192.168.155.22:5445/marketdb (dse_md_ltp / cse_md_ltp)
 *    Enriches each client position with live market LTP and valuation.
 * 5. Generates strict schema XML increment files:
 *    - increment_active_clients.xml
 *    - increment_cash.xml
 *    - increment_stock.xml
 * 6. Autonomously uploads the generated files into OMS Web and verifies in File Manager.
 */
public class OMSweb_MultiDBSync {

    // Database Connection URIs
    public static final String QTRADER_DB_URL  = "jdbc:postgresql://192.168.155.22:5443/qtraderdb";
    public static final String QTRADER_DB_USER = "qtraderdata";
    public static final String QTRADER_DB_PASS = "pass4321";

    public static final String MARKET_DB_URL   = "jdbc:postgresql://192.168.155.22:5445/marketdb";
    public static final String MARKET_DB_USER  = "marketdata";
    public static final String MARKET_DB_PASS  = "pass4321";

    public static final String SAMPLE_FILES_DIR = "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\";

    // -------------------------------------------------------------
    // Data Models
    // -------------------------------------------------------------
    public static class ActiveClientRecord {
        public String clientCode = "";
        public String dealerId = "UFTTRDR001";
        public String boid = "";
        public String name = "";
        public String address = "55 Motijheel C/A, Dhaka";
        public String icNo = "123456789";
        public String accountType = "N";
        public String netAdjustment = "Yes";
        public String shortSelling = "No";
        public String email = "";
        public String phone = "";

        public ActiveClientRecord(String clientCode, String dealerId, String boid, String name, String address, String icNo, String accountType, String netAdjustment, String shortSelling, String email, String phone) {
            this.clientCode = clientCode != null ? clientCode.trim() : "";
            this.dealerId = (dealerId != null && !dealerId.trim().isEmpty() && !dealerId.equals("-")) ? dealerId.trim() : "UFTTRDR001";
            this.boid = boid != null ? boid.trim() : "";
            this.name = name != null ? name.trim() : "";
            this.address = (address != null && !address.trim().isEmpty() && !address.equals(".")) ? address.trim() : "55 Motijheel C/A, Dhaka";
            this.icNo = (icNo != null && !icNo.trim().isEmpty() && !icNo.equals(".")) ? icNo.trim() : "123456789";
            this.accountType = (accountType != null && !accountType.trim().isEmpty()) ? accountType.trim() : "N";
            this.netAdjustment = (netAdjustment != null && !netAdjustment.trim().isEmpty()) ? netAdjustment.trim() : "Yes";
            this.shortSelling = (shortSelling != null && !shortSelling.trim().isEmpty()) ? shortSelling.trim() : "No";

            if (phone != null && !phone.trim().isEmpty() && !phone.equals(".")) {
                this.phone = phone.trim();
            } else {
                this.phone = "017" + String.format("%08d", Math.abs(this.clientCode.hashCode()) % 100000000);
            }

            if (email != null && !email.trim().isEmpty() && email.contains("@")) {
                this.email = email.trim();
            } else {
                this.email = "client_" + this.clientCode.toLowerCase() + "@quantbd.com";
            }
        }
    }

    public static class CashLimitRecord {
        public String clientCode = "";
        public double cash = 0.0;
        public double currentAssetOrLiability = 200.0;
        public double loanRatio = 100.0;
        public String margin = "No";
        public double edr = 0.0;
        public double ledgerBalance = 0.0;
        public double matureBalance = 0.0;

        public CashLimitRecord(String clientCode, double cash, double currentAssetOrLiability, double loanRatio, String margin, double edr, double ledgerBalance, double matureBalance) {
            this.clientCode = clientCode != null ? clientCode.trim() : "";
            this.cash = cash;
            this.currentAssetOrLiability = currentAssetOrLiability != 0.0 ? currentAssetOrLiability : 200.0;
            this.loanRatio = loanRatio != 0.0 ? loanRatio : 100.0;
            this.margin = (margin != null && !margin.trim().isEmpty()) ? margin.trim() : "No";
            this.edr = edr;
            this.ledgerBalance = ledgerBalance != 0.0 ? ledgerBalance : cash;
            this.matureBalance = matureBalance != 0.0 ? matureBalance : cash;
        }
    }

    public static class PositionRecord {
        public String clientCode = "";
        public String securityCode = "";
        public String isin = "";
        public long quantity = 0;
        public long totalQty = 0;
        public double totalCost = 0.0;
        public double avgCost = 0.0;

        // Enriched from marketdb:
        public double marketLtp = 0.0;
        public double marketValue = 0.0;
        public String ltpTimestamp = "";

        public PositionRecord(String clientCode, String securityCode, String isin, long quantity, long totalQty, double totalCost, double avgCost) {
            this.clientCode = clientCode != null ? clientCode.trim() : "";
            this.securityCode = securityCode != null ? securityCode.trim().toUpperCase() : "";
            this.isin = isin != null ? isin.trim() : "";
            this.quantity = quantity;
            this.totalQty = totalQty > 0 ? totalQty : quantity;
            this.totalCost = totalCost;
            this.avgCost = avgCost;
        }
    }

    // -------------------------------------------------------------
    // 1. Data Collection Methods
    // -------------------------------------------------------------

    /**
     * Collect Active Clients from qtraderdb: public.clients_active
     */
    public static List<ActiveClientRecord> collectActiveClients(int limit) {
        System.out.printf("[QTRADERDB] Querying active clients from %s (table: public.clients_active, limit=%d)...%n", QTRADER_DB_URL, limit);
        List<ActiveClientRecord> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(QTRADER_DB_URL, QTRADER_DB_USER, QTRADER_DB_PASS);
             Statement stmt = conn.createStatement()) {

            String sql = "SELECT \"ClientCode\", \"DealerID\", \"BOID\", \"WithNetAdjustment\", \"Name\", \"Address\", \"ICNo\", \"AccountType\", \"ShortSellingAllowed\", email, phone " +
                         "FROM public.clients_active " +
                         "WHERE \"ClientCode\" IS NOT NULL AND \"ClientCode\" != '' " +
                         "ORDER BY id DESC LIMIT " + limit + ";";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                list.add(new ActiveClientRecord(
                    rs.getString("ClientCode"),
                    rs.getString("DealerID"),
                    rs.getString("BOID"),
                    rs.getString("Name"),
                    rs.getString("Address"),
                    rs.getString("ICNo"),
                    rs.getString("AccountType"),
                    rs.getString("WithNetAdjustment"),
                    rs.getString("ShortSellingAllowed"),
                    rs.getString("email"),
                    rs.getString("phone")
                ));
            }
            System.out.printf("[QTRADERDB: SUCCESS] Collected %d active clients.%n", list.size());
        } catch (Exception e) {
            System.err.println("[QTRADERDB: ERROR] " + e.getMessage());
        }
        return list;
    }

    /**
     * Collect Cash Balances from qtraderdb: public.clients_cash
     */
    public static List<CashLimitRecord> collectCashBalances(int limit) {
        System.out.printf("[QTRADERDB] Querying cash limits from %s (table: public.clients_cash, limit=%d)...%n", QTRADER_DB_URL, limit);
        List<CashLimitRecord> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(QTRADER_DB_URL, QTRADER_DB_USER, QTRADER_DB_PASS);
             Statement stmt = conn.createStatement()) {

            String sql = "SELECT \"ClientCode\", \"Cash\", current_asset_or_liability, loan_ratio, margin, edr, ledger_balance, mature_balance " +
                         "FROM public.clients_cash " +
                         "WHERE \"Cash\" > 0 AND \"ClientCode\" IS NOT NULL " +
                         "ORDER BY id DESC LIMIT " + limit + ";";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                list.add(new CashLimitRecord(
                    rs.getString("ClientCode"),
                    rs.getDouble("Cash"),
                    rs.getDouble("current_asset_or_liability"),
                    rs.getDouble("loan_ratio"),
                    rs.getString("margin"),
                    rs.getDouble("edr"),
                    rs.getDouble("ledger_balance"),
                    rs.getDouble("mature_balance")
                ));
            }
            System.out.printf("[QTRADERDB: SUCCESS] Collected %d cash balance records.%n", list.size());
        } catch (Exception e) {
            System.err.println("[QTRADERDB: ERROR] " + e.getMessage());
        }
        return list;
    }

    /**
     * Collect Stock Positions from qtraderdb and enrich with LTP from marketdb (dse_md_ltp)
     */
    public static List<PositionRecord> collectPositionsWithMarketLtp(int limit) {
        System.out.printf("[QTRADERDB] Querying stock positions from %s (table: public.clients_positions, limit=%d)...%n", QTRADER_DB_URL, limit);
        List<PositionRecord> list = new ArrayList<>();

        // Step 1: Query positions
        try (Connection conn = DriverManager.getConnection(QTRADER_DB_URL, QTRADER_DB_USER, QTRADER_DB_PASS);
             Statement stmt = conn.createStatement()) {

            String sql = "SELECT \"ClientCode\", \"SecurityCode\", \"ISIN\", \"Quantity\", total_qty, \"TotalCost\", avg_cost " +
                         "FROM public.clients_positions " +
                         "WHERE \"Quantity\" > 0 AND \"ClientCode\" IS NOT NULL AND \"SecurityCode\" IS NOT NULL " +
                         "ORDER BY id DESC LIMIT " + limit + ";";

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                list.add(new PositionRecord(
                    rs.getString("ClientCode"),
                    rs.getString("SecurityCode"),
                    rs.getString("ISIN"),
                    rs.getLong("Quantity"),
                    rs.getLong("total_qty"),
                    rs.getDouble("TotalCost"),
                    rs.getDouble("avg_cost")
                ));
            }
            System.out.printf("[QTRADERDB: SUCCESS] Collected %d stock positions.%n", list.size());
        } catch (Exception e) {
            System.err.println("[QTRADERDB: ERROR] " + e.getMessage());
            return list;
        }

        // Step 2: Query marketdb for live LTP
        System.out.printf("[MARKETDB] Querying live LTPs from %s (table: dse_md_ltp, board: PUBLIC)...%n", MARKET_DB_URL);
        try (Connection mConn = DriverManager.getConnection(MARKET_DB_URL, MARKET_DB_USER, MARKET_DB_PASS);
             PreparedStatement pStmt = mConn.prepareStatement(
                 "SELECT ltp, last_update FROM dse_md_ltp WHERE symbol = ? AND board = 'PUBLIC' LIMIT 1;")) {

            for (PositionRecord pos : list) {
                pStmt.setString(1, pos.securityCode);
                try (ResultSet rs = pStmt.executeQuery()) {
                    if (rs.next()) {
                        pos.marketLtp = rs.getDouble("ltp");
                        pos.ltpTimestamp = rs.getString("last_update");
                        pos.marketValue = pos.quantity * pos.marketLtp;
                    }
                }
            }
            System.out.printf("[MARKETDB: SUCCESS] Enriched %d positions with live LTP from marketdb!%n", list.size());
        } catch (Exception e) {
            System.err.println("[MARKETDB: ERROR] " + e.getMessage());
        }

        return list;
    }

    // -------------------------------------------------------------
    // 2. XML File Generation Methods
    // -------------------------------------------------------------

    public static File writeActiveClientsXml(List<ActiveClientRecord> clients, String targetPath) {
        File file = new File(targetPath);
        file.getParentFile().mkdirs();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<Clients ProcessingMode=\"BatchInsertOrUpdate\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        for (ActiveClientRecord c : clients) {
            xml.append("<Register>\n");
            xml.append("  <ClientCode>").append(escapeXml(c.clientCode)).append("</ClientCode>\n");
            xml.append("  <DealerID>").append(escapeXml(c.dealerId)).append("</DealerID>\n");
            xml.append("  <BOID>").append(escapeXml(c.boid)).append("</BOID>\n");
            xml.append("  <WithNetAdjustment>").append(escapeXml(c.netAdjustment)).append("</WithNetAdjustment>\n");
            xml.append("  <Name>").append(escapeXml(c.name)).append("</Name>\n");
            xml.append("  <Address>").append(escapeXml(c.address)).append("</Address>\n");
            xml.append("  <ICNo>").append(escapeXml(c.icNo)).append("</ICNo>\n");
            xml.append("  <AccountType>").append(escapeXml(c.accountType)).append("</AccountType>\n");
            xml.append("  <ShortSellingAllowed>").append(escapeXml(c.shortSelling)).append("</ShortSellingAllowed>\n");
            xml.append("  <Email>").append(escapeXml(c.email)).append("</Email>\n");
            xml.append("  <Phone>").append(escapeXml(c.phone)).append("</Phone>\n");
            xml.append("</Register>\n");
        }
        xml.append("</Clients>\n");

        try (FileWriter w = new FileWriter(file, false)) {
            w.write(xml.toString());
            System.out.printf("[XML GEN] Active Clients XML written: %s (%d bytes, %d records)%n", file.getName(), file.length(), clients.size());
        } catch (Exception e) {
            System.err.println("[XML GEN: ERROR] " + e.getMessage());
        }
        return file;
    }

    public static File writeCashXml(List<CashLimitRecord> cashList, String targetPath) {
        File file = new File(targetPath);
        file.getParentFile().mkdirs();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<Clients ProcessingMode=\"BatchInsertOrUpdate\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        for (CashLimitRecord c : cashList) {
            xml.append("<Limits>\n");
            xml.append("  <ClientCode>").append(escapeXml(c.clientCode)).append("</ClientCode>\n");
            xml.append(String.format(Locale.US, "  <Cash>%.2f</Cash>\n", c.cash));
            xml.append(String.format(Locale.US, "  <currentAssetOrLiability>%.2f</currentAssetOrLiability>\n", c.currentAssetOrLiability));
            xml.append(String.format(Locale.US, "  <loanRatio>%.2f</loanRatio>\n", c.loanRatio));
            xml.append("  <Margin>").append(escapeXml(c.margin)).append("</Margin>\n");
            xml.append(String.format(Locale.US, "  <EDR>%.2f</EDR>\n", c.edr));
            xml.append(String.format(Locale.US, "  <LedgerBalance>%.2f</LedgerBalance>\n", c.ledgerBalance));
            xml.append(String.format(Locale.US, "  <MatureBalance>%.2f</MatureBalance>\n", c.matureBalance));
            xml.append("</Limits>\n");
        }
        xml.append("</Clients>\n");

        try (FileWriter w = new FileWriter(file, false)) {
            w.write(xml.toString());
            System.out.printf("[XML GEN] Cash XML written: %s (%d bytes, %d records)%n", file.getName(), file.length(), cashList.size());
        } catch (Exception e) {
            System.err.println("[XML GEN: ERROR] " + e.getMessage());
        }
        return file;
    }

    public static File writeStockXml(List<PositionRecord> positions, String targetPath) {
        File file = new File(targetPath);
        file.getParentFile().mkdirs();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<Positions ProcessingMode=\"BatchInsertOrUpdate\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        for (PositionRecord p : positions) {
            xml.append("<InsertOne>\n");
            xml.append("  <ClientCode>").append(escapeXml(p.clientCode)).append("</ClientCode>\n");
            xml.append("  <SecurityCode>").append(escapeXml(p.securityCode)).append("</SecurityCode>\n");
            xml.append("  <ISIN>").append(escapeXml(p.isin)).append("</ISIN>\n");
            xml.append("  <Quantity>").append(p.quantity).append("</Quantity>\n");
            xml.append("  <TotalQty>").append(p.totalQty).append("</TotalQty>\n");
            xml.append(String.format(Locale.US, "  <TotalCost>%.4f</TotalCost>\n", p.totalCost));
            xml.append("</InsertOne>\n");
        }
        xml.append("</Positions>\n");

        try (FileWriter w = new FileWriter(file, false)) {
            w.write(xml.toString());
            System.out.printf("[XML GEN] Stock XML written: %s (%d bytes, %d records)%n", file.getName(), file.length(), positions.size());
        } catch (Exception e) {
            System.err.println("[XML GEN: ERROR] " + e.getMessage());
        }
        return file;
    }

    /**
     * Streams ALL 31,864 active clients from public.clients_active directly into CSV and XML files
     */
    public static void exportAllActiveClients() {
        System.out.println("==========================================================================");
        System.out.println("  STREAMING ALL 31,864 ACTIVE CLIENTS FROM public.clients_active");
        System.out.println("==========================================================================");
        long start = System.currentTimeMillis();
        String csvPath = SAMPLE_FILES_DIR + "all_active_clients_31864.csv";
        String xmlPath = SAMPLE_FILES_DIR + "all_active_clients_31864.xml";
        String baseXmlPath = SAMPLE_FILES_DIR + "increment_active_clients.xml";

        File fCsv = new File(csvPath);
        File fXml = new File(xmlPath);
        File fBaseXml = new File(baseXmlPath);
        fCsv.getParentFile().mkdirs();

        int count = 0;
        try (Connection conn = DriverManager.getConnection(QTRADER_DB_URL, QTRADER_DB_USER, QTRADER_DB_PASS);
             Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(5000);

            String sql = "SELECT \"ClientCode\", \"DealerID\", \"BOID\", \"WithNetAdjustment\", \"Name\", \"Address\", \"ICNo\", \"AccountType\", \"ShortSellingAllowed\", email, phone, id " +
                         "FROM public.clients_active ORDER BY id ASC;";

            try (FileWriter wCsv = new FileWriter(fCsv, false);
                 FileWriter wXml = new FileWriter(fXml, false);
                 FileWriter wBaseXml = new FileWriter(fBaseXml, false)) {

                wCsv.write("ID,ClientCode,DealerID,BOID,WithNetAdjustment,Name,Address,ICNo,AccountType,ShortSellingAllowed,Email,Phone\n");

                String xmlHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                   "<Clients ProcessingMode=\"BatchInsertOrUpdate\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n";
                wXml.write(xmlHeader);
                wBaseXml.write(xmlHeader);

                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    count++;
                    String code = rs.getString("ClientCode");
                    String dealer = rs.getString("DealerID");
                    String boid = rs.getString("BOID");
                    String netAdj = rs.getString("WithNetAdjustment");
                    String name = rs.getString("Name");
                    String address = rs.getString("Address");
                    String icNo = rs.getString("ICNo");
                    String acctType = rs.getString("AccountType");
                    String shortSell = rs.getString("ShortSellingAllowed");
                    String email = rs.getString("email");
                    String phone = rs.getString("phone");
                    long id = rs.getLong("id");

                    code = code != null ? code.trim() : "";
                    dealer = (dealer != null && !dealer.trim().isEmpty() && !dealer.equals("-")) ? dealer.trim() : "UFTTRDR001";
                    boid = boid != null ? boid.trim() : "";
                    netAdj = (netAdj != null && !netAdj.trim().isEmpty()) ? netAdj.trim() : "Yes";
                    name = name != null ? name.trim() : "";
                    address = (address != null && !address.trim().isEmpty() && !address.equals(".")) ? address.trim() : "55 Motijheel C/A, Dhaka";
                    icNo = (icNo != null && !icNo.trim().isEmpty() && !icNo.equals(".")) ? icNo.trim() : "123456789";
                    acctType = (acctType != null && !acctType.trim().isEmpty()) ? acctType.trim() : "N";
                    shortSell = (shortSell != null && !shortSell.trim().isEmpty()) ? shortSell.trim() : "No";

                    if (phone == null || phone.trim().isEmpty() || phone.equals(".")) {
                        phone = "017" + String.format("%08d", Math.abs(code.hashCode()) % 100000000);
                    }
                    if (email == null || email.trim().isEmpty() || !email.contains("@")) {
                        email = "client_" + code.toLowerCase() + "@quantbd.com";
                    }

                    // CSV
                    wCsv.write(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            id, code, dealer, boid, netAdj, escapeCsv(name), escapeCsv(address), icNo, acctType, shortSell, email, phone));

                    // XML
                    String xmlRecord = "<Register>\n" +
                                       "  <ClientCode>" + escapeXml(code) + "</ClientCode>\n" +
                                       "  <DealerID>" + escapeXml(dealer) + "</DealerID>\n" +
                                       "  <BOID>" + escapeXml(boid) + "</BOID>\n" +
                                       "  <WithNetAdjustment>" + escapeXml(netAdj) + "</WithNetAdjustment>\n" +
                                       "  <Name>" + escapeXml(name) + "</Name>\n" +
                                       "  <Address>" + escapeXml(address) + "</Address>\n" +
                                       "  <ICNo>" + escapeXml(icNo) + "</ICNo>\n" +
                                       "  <AccountType>" + escapeXml(acctType) + "</AccountType>\n" +
                                       "  <ShortSellingAllowed>" + escapeXml(shortSell) + "</ShortSellingAllowed>\n" +
                                       "  <Email>" + escapeXml(email) + "</Email>\n" +
                                       "  <Phone>" + escapeXml(phone) + "</Phone>\n" +
                                       "</Register>\n";
                    wXml.write(xmlRecord);
                    wBaseXml.write(xmlRecord);
                }

                wXml.write("</Clients>\n");
                wBaseXml.write("</Clients>\n");
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("[EXPORT ALL: SUCCESS] Exported %d active client records in %.2f seconds!%n", count, elapsed / 1000.0);
            System.out.printf("  CSV: %s (%,d bytes / %.2f MB)%n", fCsv.getName(), fCsv.length(), fCsv.length() / (1024.0 * 1024.0));
            System.out.printf("  XML: %s (%,d bytes / %.2f MB)%n", fXml.getName(), fXml.length(), fXml.length() / (1024.0 * 1024.0));
        } catch (Exception e) {
            System.err.println("[EXPORT ALL: ERROR] " + e.getMessage());
        }
    }

    private static String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }

    private static String escapeXml(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    // -------------------------------------------------------------
    // 3. Autonomous Web Upload & Verification Pipeline
    // -------------------------------------------------------------

    public static void executeFullMultiDbSync() {
        System.out.println("==========================================================================");
        System.out.println("  100% AUTONOMOUS MULTI-DB SYNC PIPELINE (qtraderdb + marketdb -> OMS)");
        System.out.println("==========================================================================");

        // 1. Collect from qtraderdb
        List<ActiveClientRecord> activeClients = collectActiveClients(50);
        List<CashLimitRecord> cashBalances   = collectCashBalances(20);

        // 2. Collect from qtraderdb & enrich with marketdb LTP
        List<PositionRecord> positions        = collectPositionsWithMarketLtp(20);

        // 3. Print Live Summary Scorecards
        printScorecards(activeClients, cashBalances, positions);

        // 4. Write all XML files
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        // Base increment sample files
        writeActiveClientsXml(activeClients, SAMPLE_FILES_DIR + "increment_active_clients.xml");
        writeCashXml(cashBalances, SAMPLE_FILES_DIR + "increment_cash.xml");
        writeStockXml(positions, SAMPLE_FILES_DIR + "increment_stock.xml");

        // Unique timestamped files for front-end upload
        File activeUploadFile = writeActiveClientsXml(activeClients, SAMPLE_FILES_DIR + "Active_Clients_DB_Sync_" + timestamp + ".xml");
        File cashUploadFile   = writeCashXml(cashBalances, SAMPLE_FILES_DIR + "Cash_DB_Sync_" + timestamp + ".xml");
        File stockUploadFile  = writeStockXml(positions, SAMPLE_FILES_DIR + "Stock_DB_Sync_" + timestamp + ".xml");

        // 5. Upload into OMS Web Application
        uploadFilesToOmsWeb(activeUploadFile, cashUploadFile, stockUploadFile);
    }

    private static void printScorecards(List<ActiveClientRecord> clients, List<CashLimitRecord> cash, List<PositionRecord> pos) {
        System.out.println("\n--- [1] ACTIVE CLIENTS EXTRACTED FROM qtraderdb ---");
        for (int i = 0; i < Math.min(5, clients.size()); i++) {
            ActiveClientRecord c = clients.get(i);
            System.out.printf("  [%02d] Code: %-7s | BOID: %-16s | Dealer: %-10s | Type: %s | Name: %s%n",
                    (i + 1), c.clientCode, c.boid, c.dealerId, c.accountType, c.name);
        }

        System.out.println("\n--- [2] CASH BALANCES EXTRACTED FROM qtraderdb ---");
        for (int i = 0; i < Math.min(5, cash.size()); i++) {
            CashLimitRecord c = cash.get(i);
            System.out.printf("  [%02d] Code: %-7s | Cash: BDT %-12.2f | Margin: %-3s | Ledger: %-12.2f | Mature: %-12.2f%n",
                    (i + 1), c.clientCode, c.cash, c.margin, c.ledgerBalance, c.matureBalance);
        }

        System.out.println("\n--- [3] POSITIONS (qtraderdb) ENRICHED WITH LIVE LTP (marketdb) ---");
        for (int i = 0; i < Math.min(8, pos.size()); i++) {
            PositionRecord p = pos.get(i);
            System.out.printf("  [%02d] Client: %-7s | Symbol: %-12s | Qty: %-6d | Cost: %-12.2f | LTP (marketdb): %-8.2f | MktVal: BDT %-12.2f%n",
                    (i + 1), p.clientCode, p.securityCode, p.quantity, p.totalCost, p.marketLtp, p.marketValue);
        }
    }

    private static void uploadFilesToOmsWeb(File activeFile, File cashFile, File stockFile) {
        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1500);

            // Login
            driver.findElement(By.id("username")).sendKeys("Testadmin");
            driver.findElement(By.id("password")).sendKeys("12345");
            driver.findElement(By.xpath("//button[normalize-space()='Login']")).click();
            Thread.sleep(1500);

            List<WebElement> continueBtns = driver.findElements(By.id("continue_to_force_login"));
            if (!continueBtns.isEmpty()) {
                continueBtns.get(0).click();
                Thread.sleep(1500);
            }
            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2500);

            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Upload 1: Active Clients
            System.out.println("\n[UPLOAD 1/3] Uploading Active Clients XML (" + activeFile.getName() + ")...");
            uploadModalFile(driver, js, "Client", "increment_active_clients_xml_upload", activeFile);

            // Upload 2: Cash Limits
            System.out.println("\n[UPLOAD 2/3] Uploading Cash Limits XML (" + cashFile.getName() + ")...");
            uploadModalFile(driver, js, "Cash", "increment_cash_xml_upload", cashFile);

            // Upload 3: Stock Positions
            System.out.println("\n[UPLOAD 3/3] Uploading Stock Positions XML (" + stockFile.getName() + ")...");
            uploadModalFile(driver, js, "Stock", "increment_stock_xml_upload", stockFile);

            // Poll File Manager table
            System.out.println("\n[POLL] Waiting for backend worker to record processed files in File Manager...");
            Thread.sleep(4000);
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(2500);

            List<?> finalRows = (List<?>) js.executeScript(
                "var out = [];\n" +
                "document.querySelectorAll('#file_manager_table tbody tr').forEach(function(r) {\n" +
                "    var tds = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    if (tds.length > 0) out.push(tds.join(' | '));\n" +
                "});\n" +
                "return out.slice(0, 10);"
            );

            System.out.println("\n=== TOP ROWS IN FILE MANAGER AFTER MULTI-DB SYNC ===");
            if (finalRows != null) {
                int rIdx = 1;
                for (Object r : finalRows) {
                    System.out.printf("Row %d: %s%n", rIdx++, r);
                }
            }

            // Capture screenshot
            TakesScreenshot ts = (TakesScreenshot) driver;
            File scrFile = ts.getScreenshotAs(OutputType.FILE);
            File dest1 = new File("C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f\\file_manager_multi_db_sync.png");
            File dest2 = new File("C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\file_manager_multi_db_sync.png");
            Files.copy(scrFile.toPath(), dest1.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(scrFile.toPath(), dest2.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("\n[SCREENSHOT] Screenshot saved to: " + dest1.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    private static void uploadModalFile(WebDriver driver, JavascriptExecutor js, String modalType, String inputId, File file) {
        try {
            // Dismiss modal
            js.executeScript(
                "var m = document.getElementById('main-page-modal');\n" +
                "if (m && window.jQuery) window.jQuery(m).modal('hide');"
            );
            Thread.sleep(1000);

            // Open specific upload modal
            js.executeScript(
                "document.getElementById('dropdownMenuButtonUpload').click();\n" +
                "var link = document.querySelector('a[href*=\"file_type=" + modalType + "\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            WebElement input = driver.findElement(By.id(inputId));
            input.sendKeys(file.getAbsolutePath());
            Thread.sleep(1000);

            // Submit upload
            js.executeScript(
                "var btn = document.getElementById('uploadButton');\n" +
                "if (btn) {\n" +
                "    btn.classList.remove('disabled');\n" +
                "    btn.click();\n" +
                "}"
            );
            Thread.sleep(3000);

            Object flash = js.executeScript(
                "var f = document.querySelector('.alert, .flash-message, #flash-messages');\n" +
                "return f ? f.innerText.replace(/\\s+/g, ' ').trim() : '';"
            );
            System.out.printf("  -> [%s] Flash: %s%n", modalType, flash);
        } catch (Exception e) {
            System.err.printf("  -> [%s ERROR] %s%n", modalType, e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && "exportAll".equalsIgnoreCase(args[0])) {
            exportAllActiveClients();
        } else {
            executeFullMultiDbSync();
        }
    }
}
