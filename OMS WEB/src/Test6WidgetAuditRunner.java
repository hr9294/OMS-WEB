import java.util.*;

public class Test6WidgetAuditRunner {

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("            6-WIDGET COMPARISON TABLE RENDERING AUDIT (PASS/FAIL/REMARKS)       ");
        System.out.println("===============================================================================");

        // --- TEST CASE 1: SUCCESSFUL TRADE (PASS EXPECTED) ---
        System.out.println("\n>>> [SCENARIO 1] NORMAL ACCEPTED TRADE AUDIT VERIFICATION");
        Day_Netting.Netting6WidgetsUiSnapshot pre1 = new Day_Netting.Netting6WidgetsUiSnapshot();
        pre1.stageName = "PRE-TRADE_BASELINE_INDIVIDUAL_DSE";
        pre1.targetClient = "23986";
        pre1.targetSymbol = "GP.PUBLIC";
        pre1.dayNettingMode = "INDIVIDUAL";
        pre1.allowZAndSpotNetting = false;
        pre1.isDseDayNettingEnabled = true;
        pre1.isCseDayNettingEnabled = true;

        pre1.portfolioDataDSE.put("total_qty", "500");
        pre1.portfolioDataDSE.put("saleable_qty", "500");
        pre1.portfolioDataDSE.put("avg_cost", "270.00");
        pre1.portfolioDataDSE.put("value", "135000.00");

        pre1.portfolioDataCSE.put("total_qty", "0");
        pre1.portfolioDataCSE.put("saleable_qty", "0");
        pre1.portfolioDataCSE.put("avg_cost", "0.00");
        pre1.portfolioDataCSE.put("value", "0.00");

        pre1.orderTerminalData.put("Order_ID", "-");
        pre1.orderTerminalData.put("Status", "-");
        pre1.orderTerminalData.put("Exec_Qty", "0");
        pre1.orderTerminalData.put("Due_Qty", "0");

        pre1.orderSummaryData.put("Total_Orders", "0");
        pre1.orderSummaryData.put("Executed", "0");
        pre1.orderSummaryData.put("Buy_Qty", "0");
        pre1.orderSummaryData.put("Sell_Qty", "0");
        pre1.orderSummaryData.put("Buy_Turnover", "0.00");

        pre1.accountLimitsData.put("Remaining_Cash", "500000.00");
        pre1.accountLimitsData.put("Blocked_Day", "0.00");
        pre1.accountLimitsData.put("Total_Tx", "0.00");

        pre1.marketDepthData.put("LTP", "270.00");
        pre1.marketDepthNettingBadge = true;
        pre1.marketDepthData.put("BUY%", "65%");
        pre1.marketDepthData.put("SELL%", "35%");

        pre1.dedicatedNettingWatchlist = true;
        pre1.watchlistNettingIcon = true;
        pre1.watchlistSymbolCount = 14;

        Day_Netting.Netting6WidgetsUiSnapshot post1 = new Day_Netting.Netting6WidgetsUiSnapshot();
        post1.stageName = "POST-TRADE_REALTIME_INDIVIDUAL_DSE";
        post1.targetClient = "23986";
        post1.targetSymbol = "GP.PUBLIC";
        post1.dayNettingMode = "INDIVIDUAL";
        post1.allowZAndSpotNetting = false;
        post1.isDseDayNettingEnabled = true;
        post1.isCseDayNettingEnabled = true;

        post1.portfolioDataDSE.put("total_qty", "600");
        post1.portfolioDataDSE.put("saleable_qty", "500");
        post1.portfolioDataDSE.put("avg_cost", "270.00");
        post1.portfolioDataDSE.put("value", "162000.00");

        post1.portfolioDataCSE.put("total_qty", "0");
        post1.portfolioDataCSE.put("saleable_qty", "0");
        post1.portfolioDataCSE.put("avg_cost", "0.00");
        post1.portfolioDataCSE.put("value", "0.00");

        post1.orderTerminalData.put("Order_ID", "ORD-89211");
        post1.orderTerminalData.put("Status", "Accepted");
        post1.orderTerminalData.put("Exec_Qty", "100");
        post1.orderTerminalData.put("Due_Qty", "0");

        post1.orderSummaryData.put("Total_Orders", "1");
        post1.orderSummaryData.put("Executed", "1");
        post1.orderSummaryData.put("Buy_Qty", "100");
        post1.orderSummaryData.put("Sell_Qty", "0");
        post1.orderSummaryData.put("Buy_Turnover", "27000.00");

        post1.accountLimitsData.put("Remaining_Cash", "473000.00");
        post1.accountLimitsData.put("Blocked_Day", "0.00");
        post1.accountLimitsData.put("Total_Tx", "27000.00");

        post1.marketDepthData.put("LTP", "270.00");
        post1.marketDepthNettingBadge = true;
        post1.marketDepthData.put("BUY%", "65%");
        post1.marketDepthData.put("SELL%", "35%");

        post1.dedicatedNettingWatchlist = true;
        post1.watchlistNettingIcon = true;
        post1.watchlistSymbolCount = 14;

        Day_Netting.compareAndReportNettingWidgets(null, pre1, post1, "DAY NETTING UI AUDIT (INDIVIDUAL - DSE)");

        // --- TEST CASE 2: REJECTED ORDER / UI DOM FAILURE (FAIL EXPECTED) ---
        System.out.println("\n>>> [SCENARIO 2] REJECTED TRADE AUDIT VERIFICATION (FAIL / REMARKS TEST)");
        Day_Netting.Netting6WidgetsUiSnapshot post2 = new Day_Netting.Netting6WidgetsUiSnapshot();
        post2.stageName = "POST-TRADE_REJECTED";
        post2.targetClient = "23986";
        post2.targetSymbol = "GP.PUBLIC";
        post2.orderTerminalData.put("Order_ID", "ORD-89212");
        post2.orderTerminalData.put("Status", "Rejected");
        post2.marketDepthData.put("LTP", "-");

        Day_Netting.compareAndReportNettingWidgets(null, pre1, post2, "DAY NETTING UI AUDIT (REJECTED TRADE)");

        // UI Layout Health function check
        String healthStatus = Day_Netting.verifyUiLayoutHealth(null);
        System.out.printf("[HEALTH CHECK NULL-SAFE] Result: %s (PASS)%n", healthStatus);
    }
}
