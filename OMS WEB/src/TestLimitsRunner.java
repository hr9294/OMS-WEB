import java.util.*;

public class TestLimitsRunner {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("                 OMS-WEB AUTOMATED TEST SUITE RUNNER                           ");
        System.out.println("===============================================================================");

        List<TestResult> results = new ArrayList<>();

        // Test Suite 1: Cash Limit Precedence Resolution (Account_Limits)
        try {
            boolean pass = Account_Limits.testResolveCashLimitScenarios();
            results.add(new TestResult("Cash Limit 3-Tier Precedence", pass, 
                pass ? "All 5 hierarchy tiers resolved as specified (Explicit -> Default -> DB Fallback -> Exception)" 
                     : "Hierarchy tier resolution discrepancy detected"));
        } catch (Exception e) {
            results.add(new TestResult("Cash Limit 3-Tier Precedence", false, "Exception: " + e.getMessage()));
        }

        // Test Suite 2: Price Limit Breach & Clamping (OMSweb_Trade)
        try {
            boolean pass = OMSweb_Trade.testPriceLimitBreachClamping();
            results.add(new TestResult("Price Limit Breach & Clamping", pass, 
                pass ? "Upper, Lower, In-Band, and Suggested Price Clamping accurately resolved" 
                     : "Price limit clamping failure"));
        } catch (Exception e) {
            results.add(new TestResult("Price Limit Breach & Clamping", false, "Exception: " + e.getMessage()));
        }

        // Test Suite 3: RMS Pre-Trade Audit (RMS)
        try {
            boolean pass = RMS.testPreTradeRmsAuditScenarios();
            results.add(new TestResult("RMS Pre-Trade Audit Validation", pass, 
                pass ? "Standard DSE approved, CSE Block rejected, Spot without mature balance rejected" 
                     : "Pre-trade RMS approval rule mismatch"));
        } catch (Exception e) {
            results.add(new TestResult("RMS Pre-Trade Audit Validation", false, "Exception: " + e.getMessage()));
        }

        // Test Suite 4: Order Terminal Quantitative Reflection
        try {
            boolean pass = testOrderTerminalQuantitativeReflection();
            results.add(new TestResult("Order Terminal Reflection Audit", pass,
                pass ? "Pre/Post order state delta, filled status, and progress properly audited"
                     : "Order terminal reflection discrepancy"));
        } catch (Exception e) {
            results.add(new TestResult("Order Terminal Reflection Audit", false, "Exception: " + e.getMessage()));
        }

        // Test Suite 5: Portfolio DSE/CSE Extraction & Delta Audit
        try {
            boolean pass = testPortfolioReflectionAudit();
            results.add(new TestResult("Portfolio Extraction & Delta Audit", pass,
                pass ? "Buy delta, total/saleable quantity transitions correctly validated"
                     : "Portfolio delta verification discrepancy"));
        } catch (Exception e) {
            results.add(new TestResult("Portfolio Extraction & Delta Audit", false, "Exception: " + e.getMessage()));
        }

        // Test Suite 6: QA Day Netting Test Suite (Scenarios 1, 2, 3)
        try {
            boolean pass = Day_Netting.testQaDayNettingScenarios();
            results.add(new TestResult("QA Day Netting Scenarios (1, 2, 3)", pass,
                pass ? "Scenario 1 (Both Exchanges), Scenario 2 (Sell > Buy), Scenario 3 (NONE Mode) 100% verified"
                     : "QA Day Netting scenario verification mismatch"));
        } catch (Exception e) {
            results.add(new TestResult("QA Day Netting Scenarios (1, 2, 3)", false, "Exception: " + e.getMessage()));
        }

        // Print Structured Scorecard
        System.out.println("\n+====================================================================================================================+");
        System.out.println("|                                               FINAL TEST SCORECARD                                                 |");
        System.out.println("+----+-------------------------------------+--------+---------------------------------------------------------------+");
        System.out.printf("| %-2s | %-35s | %-6s | %-61s |%n", "#", "Test Suite", "Status", "Remarks");
        System.out.println("+----+-------------------------------------+--------+---------------------------------------------------------------+");
        int count = 1;
        int passedCount = 0;
        for (TestResult tr : results) {
            if (tr.passed) passedCount++;
            System.out.printf("| %-2d | %-35s | %-6s | %-61s |%n", 
                count++, tr.suiteName, tr.passed ? "PASS" : "FAIL", tr.remarks);
        }
        System.out.println("+----+-------------------------------------+--------+---------------------------------------------------------------+");
        System.out.printf("| SUMMARY: %d/%d TESTS PASSED                                                                                  |%n", passedCount, results.size());
        System.out.println("+====================================================================================================================+\n");

        if (passedCount < results.size()) {
            System.exit(1);
        }
    }

    private static boolean testOrderTerminalQuantitativeReflection() {
        System.out.println("\n+=============================================================================+");
        System.out.println("|              ORDER TERMINAL QUANTITATIVE AUDIT UNIT TEST                    |");
        System.out.println("+=============================================================================+");
        Map<String, String> before = new HashMap<>();
        before.put("Order_ID", "ORD-1001");
        before.put("Side", "BUY");
        before.put("Status", "NEW");
        before.put("Qty", "0");
        before.put("Price", "0.0");
        before.put("DATA_SOURCE", "TEST_PRE");

        Map<String, String> after = new HashMap<>();
        after.put("Order_ID", "ORD-1002");
        after.put("Side", "BUY");
        after.put("Status", "FILLED");
        after.put("Exec_Type", "Trade Executed");
        after.put("Qty", "100");
        after.put("Exec_Qty", "100");
        after.put("Due_Qty", "0");
        after.put("Price", "270.0");
        after.put("Avg_Price", "270.0");
        after.put("Progress", "100%");
        after.put("DATA_SOURCE", "TEST_POST");

        Order_Terminal.verify(before, after, "GP", "BUY", 100, 270.0, "DSE");
        return true;
    }

    private static boolean testPortfolioReflectionAudit() {
        System.out.println("\n+=============================================================================+");
        System.out.println("|              PORTFOLIO REFLECTION QUANTITATIVE AUDIT TEST                   |");
        System.out.println("+=============================================================================+");
        Map<String, String> before = new HashMap<>();
        before.put("total_qty", "500");
        before.put("saleable_qty", "500");
        before.put("ledger_balance", "1000000");
        before.put("total_cost", "135000");

        Map<String, String> after = new HashMap<>();
        after.put("total_qty", "600");
        after.put("saleable_qty", "500");
        after.put("ledger_balance", "973000");
        after.put("total_cost", "162000");

        ClientPortfolio.verify(before, after, "GP", "BUY", 100, 270.0, "DSE");
        return true;
    }

    static class TestResult {
        String suiteName;
        boolean passed;
        String remarks;

        TestResult(String suiteName, boolean passed, String remarks) {
            this.suiteName = suiteName;
            this.passed = passed;
            this.remarks = remarks;
        }
    }
}
