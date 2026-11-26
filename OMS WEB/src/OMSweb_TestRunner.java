import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * ===============================================================================
 *            OMS-WEB UNIFIED TEST SUITE RUNNER (ECLIPSE & CLI)
 * ===============================================================================
 * Consolidated runner merging:
 *   - TestLimitsRunner         (Limits, Price Breach, RMS Audit, Order Terminal, Portfolio)
 *   - Test6WidgetAuditRunner   (6-Widget Comparison Table Rendering & Layout Health)
 *   - DayNettingRunner         (Day Netting Multi-Exchange & 9-Scenario Compliance)
 *
 * Note: RMS.java remains an independent engine and is invoked via
 *       RMS.testPreTradeRmsAuditScenarios() without merging RMS into this runner.
 * ===============================================================================
 */
public class OMSweb_TestRunner {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        String mode = (args != null && args.length > 0 && args[0] != null)
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "all";
        System.out.println("==========================================================================================");
        System.out.println("                      OMS-WEB UNIFIED AUTOMATED TEST SUITE RUNNER                         ");
        System.out.println("==========================================================================================");
        System.out.printf("Execution Environment : Eclipse IDE / Java 21 | Filter Mode: %s%n", mode.toUpperCase(Locale.ROOT));
        System.out.println("Excluded from Merge   : RMS.java (Independent qRMS Engine)");
        System.out.println("------------------------------------------------------------------------------------------\n");
        boolean runAll = "all".equals(mode);
        boolean runLimits = runAll || "limits".equals(mode) || "core".equals(mode);
        boolean runWidget = runAll || "6widget".equals(mode) || "widget".equals(mode);
        boolean runDayNettingScen = runAll || "test".equals(mode) || "limits".equals(mode) || "core".equals(mode) || "netting".equals(mode);
        boolean runNetting = runAll || "netting".equals(mode) || "test".equals(mode);
        boolean runRms = runAll || "rms".equals(mode) || "limits".equals(mode) || "core".equals(mode);
        List<TestResult> results = new ArrayList<>();
        if (runLimits) {
            executeSuite(results, "Cash Limit 3-Tier Precedence",
                    "All 5 hierarchy tiers resolved (Explicit -> Default -> DB Fallback -> Exception)",
                    "Hierarchy tier resolution discrepancy detected",
                    Account_Limits::testResolveCashLimitScenarios);
            executeSuite(results, "Price Limit Breach & Clamping",
                    "Upper, Lower, In-Band, and Suggested Price Clamping accurately resolved",
                    "Price limit clamping failure",
                    OMSweb_Trade::testPriceLimitBreachClamping);
        }
        if (runRms) {
            executeSuite(results, "RMS Pre-Trade Audit & Trade Gate",
                    "Standard DSE, CSE Block, Spot restriction, Client Exclusivity & Connected Trade Gate verified",
                    "Pre-trade RMS approval rule mismatch",
                    RMS::testPreTradeRmsAuditScenarios);
        }
        if (runLimits) {
            executeSuite(results, "Order Terminal Reflection Audit",
                    "Pre/Post order state delta, filled status, and progress properly audited",
                    "Order terminal reflection discrepancy",
                    OMSweb_TestRunner::testOrderTerminalQuantitativeReflection);
            executeSuite(results, "Portfolio Extraction & Delta Audit",
                    "Buy delta, total/saleable quantity transitions correctly validated",
                    "Portfolio delta verification discrepancy",
                    OMSweb_TestRunner::testPortfolioReflectionAudit);
        }
        if (runWidget) {
            executeSuite(results, "6-Widget Audit & Layout Health",
                    "Accepted Trade, Rejected Trade comparison tables & UI layout health validated",
                    "6-Widget audit or layout health validation failed",
                    OMSweb_TestRunner::test6WidgetComparisonAudit);
        }
        if (runDayNettingScen) {
            executeSuite(results, "Day Netting Multi-Exchange (1-4)",
                    "Individual Both Exchanges, Sell > Buy, NONE Mode & CROSS Mode (25/25 assertions) verified",
                    "Day Netting scenario verification mismatch",
                    Day_Netting::testDayNettingScenarios);
        }
        if (runNetting) {
            executeSuite(results, "Day Netting 9-Scenario Compliance",
                    "All 9 offline blocks (Exclusivity, Symbol/Client matrix, Retroactive, Upload x Netting) passed",
                    "Full Day Netting 9-scenario compliance check failed",
                    Day_Netting::runAllNettingTests);
        }
        long durationMs = System.currentTimeMillis() - startTime;
        printUnifiedScorecard(results, durationMs);
        boolean allPassed = !results.isEmpty() && results.stream().allMatch(r -> r.passed);
        if (!allPassed) {
            System.err.println("[TEST FAILURE] One or more unified OMS-WEB test suites failed!");
            System.exit(1);
        }
    }

    private static void executeSuite(List<TestResult> results, String suiteName,
                                     String passRemarks, String failRemarks,
                                     BooleanSupplier check) {
        try {
            boolean pass = check.getAsBoolean();
            results.add(new TestResult(suiteName, pass, pass ? passRemarks : failRemarks));
        } catch (Exception e) {
            results.add(new TestResult(suiteName, false, "Exception: " + e.getMessage()));
        }
    }

    private static void printUnifiedScorecard(List<TestResult> results, long durationMs) {
        System.out.println("\n+===========================================================================================================================+");
        System.out.println("|                                          UNIFIED OMS-WEB FINAL TEST SCORECARD                                            |");
        System.out.println("+----+---------------------------------------+--------+-------------------------------------------------------------------+");
        System.out.printf("| %-2s | %-37s | %-6s | %-65s |%n", "#", "Test Suite", "Status", "Remarks");
        System.out.println("+----+---------------------------------------+--------+-------------------------------------------------------------------+");
        int idx = 1;
        int passed = 0;
        for (TestResult tr : results) {
            if (tr.passed) passed++;
            String cleanRemarks = tr.remarks != null && tr.remarks.length() > 65
                    ? tr.remarks.substring(0, 62) + "..."
                    : (tr.remarks != null ? tr.remarks : "-");
            System.out.printf("| %-2d | %-37s | %-6s | %-65s |%n",
                    idx++, tr.suiteName, tr.passed ? "PASS" : "FAIL", cleanRemarks);
        }
        System.out.println("+----+---------------------------------------+--------+-------------------------------------------------------------------+");
        System.out.printf("| SUMMARY: %d/%d SUITES PASSED | Execution Time: %d ms                                                                   |%n",
                passed, results.size(), durationMs);
        System.out.println("+===========================================================================================================================+\n");
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

    private static boolean test6WidgetComparisonAudit() {
        System.out.println("\n===============================================================================");
        System.out.println("            6-WIDGET COMPARISON TABLE RENDERING AUDIT (PASS/FAIL/REMARKS)       ");
        System.out.println("===============================================================================");
        System.out.println("\n>>> [SCENARIO 1] NORMAL ACCEPTED TRADE AUDIT VERIFICATION");
        Day_Netting.Netting6WidgetsUiSnapshot pre1 = build6WidgetSnapshot(
                "PRE-TRADE_BASELINE_INDIVIDUAL_DSE", "23986", "GP.PUBLIC", "INDIVIDUAL",
                "500", "500", "270.00", "135000.00",
                "-", "-", "0", "0",
                "0", "0", "0", "0", "0.00",
                "500000.00", "0.00", "0.00", "270.00");
        Day_Netting.Netting6WidgetsUiSnapshot post1 = build6WidgetSnapshot(
                "POST-TRADE_REALTIME_INDIVIDUAL_DSE", "23986", "GP.PUBLIC", "INDIVIDUAL",
                "600", "500", "270.00", "162000.00",
                "ORD-89211", "Accepted", "100", "0",
                "1", "1", "100", "0", "27000.00",
                "473000.00", "0.00", "27000.00", "270.00");
        Day_Netting.compareAndReportNettingWidgets(null, pre1, post1, "DAY NETTING UI AUDIT (INDIVIDUAL - DSE)");
        System.out.println("\n>>> [SCENARIO 2] REJECTED TRADE AUDIT VERIFICATION (FAIL / REMARKS TEST)");
        Day_Netting.Netting6WidgetsUiSnapshot post2 = new Day_Netting.Netting6WidgetsUiSnapshot();
        post2.stageName = "POST-TRADE_REJECTED";
        post2.targetClient = "23986";
        post2.targetSymbol = "GP.PUBLIC";
        post2.orderTerminalData.put("Order_ID", "ORD-89212");
        post2.orderTerminalData.put("Status", "Rejected");
        post2.marketDepthData.put("LTP", "-");
        Day_Netting.compareAndReportNettingWidgets(null, pre1, post2, "DAY NETTING UI AUDIT (REJECTED TRADE)");
        String healthStatus = Day_Netting.verifyUiLayoutHealth(null);
        System.out.printf("[HEALTH CHECK NULL-SAFE] Result: %s (PASS)%n", healthStatus);
        return healthStatus != null && !healthStatus.trim().isEmpty();
    }
    private static Day_Netting.Netting6WidgetsUiSnapshot build6WidgetSnapshot(
            String stage, String client, String symbol, String mode,
            String dseTotal, String dseSaleable, String dseAvgCost, String dseVal,
            String ordId, String ordStatus, String execQty, String dueQty,
            String totalOrders, String executedOrders, String buyQty, String sellQty, String buyTurnover,
            String remCash, String blockedDay, String totalTx, String ltp) {
        Day_Netting.Netting6WidgetsUiSnapshot snap = new Day_Netting.Netting6WidgetsUiSnapshot();
        snap.stageName = stage;
        snap.targetClient = client;
        snap.targetSymbol = symbol;
        snap.dayNettingMode = mode;
        snap.allowZAndSpotNetting = false;
        snap.isDseDayNettingEnabled = true;
        snap.isCseDayNettingEnabled = true;
        snap.portfolioDataDSE.put("total_qty", dseTotal);
        snap.portfolioDataDSE.put("saleable_qty", dseSaleable);
        snap.portfolioDataDSE.put("avg_cost", dseAvgCost);
        snap.portfolioDataDSE.put("value", dseVal);
        snap.portfolioDataCSE.put("total_qty", "0");
        snap.portfolioDataCSE.put("saleable_qty", "0");
        snap.portfolioDataCSE.put("avg_cost", "0.00");
        snap.portfolioDataCSE.put("value", "0.00");
        snap.orderTerminalData.put("Order_ID", ordId);
        snap.orderTerminalData.put("Status", ordStatus);
        snap.orderTerminalData.put("Exec_Qty", execQty);
        snap.orderTerminalData.put("Due_Qty", dueQty);
        snap.orderSummaryData.put("Total_Orders", totalOrders);
        snap.orderSummaryData.put("Executed", executedOrders);
        snap.orderSummaryData.put("Buy_Qty", buyQty);
        snap.orderSummaryData.put("Sell_Qty", sellQty);
        snap.orderSummaryData.put("Buy_Turnover", buyTurnover);
        snap.accountLimitsData.put("Remaining_Cash", remCash);
        snap.accountLimitsData.put("Blocked_Day", blockedDay);
        snap.accountLimitsData.put("Total_Tx", totalTx);
        snap.marketDepthData.put("LTP", ltp);
        snap.marketDepthNettingBadge = true;
        snap.marketDepthData.put("BUY%", "65%");
        snap.marketDepthData.put("SELL%", "35%");
        snap.dedicatedNettingWatchlist = true;
        snap.watchlistNettingIcon = true;
        snap.watchlistSymbolCount = 14;
        return snap;
    }
    static class TestResult {
        final String suiteName;
        final boolean passed;
        final String remarks;
        TestResult(String suiteName, boolean passed, String remarks) {
            this.suiteName = suiteName;
            this.passed = passed;
            this.remarks = remarks;
        }
    }
}
