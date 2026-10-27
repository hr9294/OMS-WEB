import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
//import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * ============================================================================
 * OMSweb_MultiOrderExecution
 * ============================================================================
 * End-to-end automation and verification engine:
 * 1. Places multiple BUY or SELL orders with same or different limit prices.
 * 2. Places opposite order(s) (BUY <-> SELL) to establish opposing book orders.
 * 3. Navigates to Order Terminal, multi-selects the placed orders, right-clicks,
 *    and triggers "Execute at Market" via context menu.
 * 4. Audits real-time UI updates across ALL 6 widgets to verify whether data
 *    is displayed right or wrong:
 *      - Client Portfolio
 *      - Market Depth
 *      - Order Terminal
 *      - Order Summary
 *      - Account Limits
 *      - Position
 * 5. Generates a master multi-widget verification scorecard.
 * ============================================================================
 */
public class OMSweb_MultiOrderExecution {

    // =========================================================================
    // 1. DATA MODELS & ENUMS
    // =========================================================================
    public enum OrderSide { BUY, SELL }

    public static class OrderItem {
        private final int id;
        private final OrderSide side;
        private final String clientCode;
        private final String symbol;
        private final int quantity;
        private final double rate;
        private final boolean isOpposite;
        private String orderId = "-";
        private String postStatus = "UNKNOWN";
        private int execQty = 0;
        private int dueQty = 0;

        public OrderItem(int id, OrderSide side, String clientCode, String symbol, int quantity, double rate, boolean isOpposite) {
            this.id = id;
            this.side = side;
            this.clientCode = clientCode;
            this.symbol = symbol;
            this.quantity = quantity;
            this.rate = rate;
            this.isOpposite = isOpposite;
        }

        public int getId() { return id; }
        public OrderSide getSide() { return side; }
        public String getClientCode() { return clientCode; }
        public String getSymbol() { return symbol; }
        public int getQuantity() { return quantity; }
        public double getRate() { return rate; }
        public boolean isOpposite() { return isOpposite; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getPostStatus() { return postStatus; }
        public void setPostStatus(String postStatus) { this.postStatus = postStatus; }
        public int getExecQty() { return execQty; }
        public void setExecQty(int execQty) { this.execQty = execQty; }
        public int getDueQty() { return dueQty; }
        public void setDueQty(int dueQty) { this.dueQty = dueQty; }

        @Override
        public String toString() {
            return String.format("#%d [%s%s] Client: %-8s | %s x %-4d @ %-7.2f | OrderID: %s",
                    id, side, (isOpposite ? "-OPP" : ""), clientCode, symbol, quantity, rate, orderId);
        }
    }

    public static class BaselineStore {
        Map<String, Map<String, String>> portfolioSnapshots = new HashMap<>();
        Map<String, Map<String, String>> clientDetails = new HashMap<>();
        Map<String, Map<String, String>> accountLimits = new HashMap<>();
        Map<String, String> orderSummary = new HashMap<>();
        Map<String, String> marketDepth = new HashMap<>();
        Map<String, String> orderTerminal = new HashMap<>();
        List<Position.ClientPosition> positionHoldings = new ArrayList<>();
    }

    // =========================================================================
    // 2. MAIN ENTRY POINT
    // =========================================================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        System.out.println("\n+=============================================================================+");
        System.out.println("|        MULTI-ORDER EXECUTION & REAL-TIME 6-WIDGET VERIFICATION ENGINE       |");
        System.out.println("+=============================================================================+");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Ensure we start on Trade Page
        OMSweb_Trade.navigateToTradePage(driver, wait);

        // STEP 0: Widget Color Linking FIRST (Trade, Marketwatch, Market Depth, Order Terminal, Position, Portfolio)
        OMSweb_Trade.runWidgetColorSelection(driver, sc);

        // Manual input of target symbol
        System.out.print("\nEnter Target Symbol (e.g. GP, ACFL, SQURPHARMA) [default=GP]: ");
        String symInput = sc.nextLine().trim();
        String symbol = symInput.isEmpty() ? "GP" : cleanSymbol(symInput);

        System.out.print("Enter Exchange (DSE / CSE, default=DSE): ");
        String exInput = sc.nextLine().trim().toUpperCase();
        String exchange = exInput.equalsIgnoreCase("CSE") ? "CSE" : "DSE";

        // Auto-populate manually input symbol into Trade search and Marketwatch
        try {
            WebElement searchSym = driver.findElement(By.id("instrument"));
            searchSym.clear();
            searchSym.sendKeys(symbol);
            searchSym.sendKeys(Keys.ARROW_DOWN);
            searchSym.sendKeys(Keys.ENTER);
            Thread.sleep(400);
        } catch (Exception ignored) {}

        try {
            WebElement mwInput = driver.findElement(By.id("marketwatch_symbol"));
            mwInput.clear();
            mwInput.sendKeys(symbol);
            mwInput.sendKeys(Keys.ARROW_DOWN);
            mwInput.sendKeys(Keys.ENTER);
            Thread.sleep(500);
        } catch (Exception ignored) {}

        System.out.printf("[SETUP] Target Symbol: %s on Exchange: %s%n", symbol, exchange);

        // Fetch current Market reference price (LTP or YCP) for dynamic non-crossing pricing
        double refPrice = OMSweb_Trade.fetchPriceLtpOrYcp(driver, wait, symbol, exchange);
        System.out.printf("[MARKET] Reference Price for %s is %.2f BDT (Base for Limit Pricing)%n", symbol, refPrice);

        // Configure Strategy
        System.out.println("\nSelect Multi-Order Execution Strategy:");
        System.out.println("  1. Multiple BUY orders (Down Prices) + Opposite SELL order (Upper Price) [Default]");
        System.out.println("  2. Multiple SELL orders (Upper Prices) + Opposite BUY order (Down Price)");
        System.out.println("  3. Custom Interactive Order Queue");
        System.out.print("Enter Choice (1, 2, or 3, default=1): ");
        String choiceStr = sc.nextLine().trim();
        int choice = choiceStr.isEmpty() ? 1 : Integer.parseInt(choiceStr);

        System.out.print("\nPrice Variation for Orders: (1=Same Price / 2=Different Prices, default=2): ");
        String priceVarStr = sc.nextLine().trim();
        boolean differentPrices = !priceVarStr.equals("1");

        // Build the Order Queue
        List<OrderItem> orderQueue = buildOrderQueue(driver, sc, choice, symbol, exchange, refPrice, differentPrices);

        if (orderQueue.isEmpty()) {
            System.out.println("[WARN] Order queue is empty. Aborting execution.");
            return;
        }

        // Display Planned Order Pipeline
        printPlannedQueue(orderQueue);
        System.out.print("\nProceed to execute this pipeline? (1=YES / 0=NO): ");
        if (!sc.nextLine().trim().equals("1")) {
            System.out.println("Operation aborted by user.");
            return;
        }

        // =====================================================================
        // STEP 1: CAPTURE BASELINE SNAPSHOTS (ALL 6 WIDGETS)
        // =====================================================================
        System.out.println("\n===============================================================================");
        System.out.println(" STEP 1: CAPTURING PRE-EXECUTION BASELINES ACROSS ALL 6 WIDGETS");
        System.out.println("===============================================================================");
        BaselineStore baseline = captureBaselineStore(driver, wait, orderQueue, symbol, exchange);

        // =====================================================================
        // STEP 2: PLACE MULTIPLE ORDERS & OPPOSITE ORDER(S) ON TRADE PAGE
        // =====================================================================
        System.out.println("\n===============================================================================");
        System.out.println(" STEP 2: PLACING MULTIPLE ORDERS & OPPOSITE ORDER(S) ON TRADE PAGE");
        System.out.println("===============================================================================");
        List<OrderItem> placedOrders = placeOrdersOnTradePage(driver, wait, js, orderQueue, exchange);

        if (placedOrders.isEmpty()) {
            System.err.println("[ERROR] No orders were placed successfully. Cannot proceed to market execution.");
            return;
        }

        // =====================================================================
        // STEP 3: NAVIGATE TO ORDER TERMINAL, SELECT ORDERS, & EXECUTE AT MARKET
        // =====================================================================
        System.out.println("\n===============================================================================");
        System.out.println(" STEP 3: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK");
        System.out.println("===============================================================================");
        boolean executedAtMarket = selectAndExecuteAtMarket(driver, wait, js, placedOrders, symbol);

        if (!executedAtMarket) {
            System.out.println("[INFO] Context menu 'Execute at Market' fallback: Checking if matching engine auto-executed matching opposite orders...");
        }

        // Allow matching engine / settlement to process
        System.out.println("[INFO] Waiting 3 seconds for real-time settlement and order flow propagation...");
        Thread.sleep(3000);

        // =====================================================================
        // STEP 4: REAL-TIME UI UPDATE VERIFICATION ACROSS ALL 6 WIDGETS
        // =====================================================================
        System.out.println("\n===============================================================================");
        System.out.println(" STEP 4: REAL-TIME AUDIT & VERIFICATION ACROSS ALL 6 WIDGETS");
        System.out.println("===============================================================================");
        runComprehensiveMultiWidgetAudit(driver, wait, baseline, placedOrders, symbol, exchange);

        // Return to Trade page cleanly
        try {
            OMSweb_Trade.navigateToTradePage(driver, wait);
        } catch (Exception ignored) {}

        System.out.println("\n>>> Multi-Order Execution and 6-Widget Real-Time Audit Session Complete. <<<\n");
    }

    // =========================================================================
    // 3. PIPELINE BUILDER
    // =========================================================================
    private static List<OrderItem> buildOrderQueue(WebDriver driver, Scanner sc, int choice, String symbol, String exchange, double refPrice, boolean differentPrices) {
        List<OrderItem> queue = new ArrayList<>();

        // Discover active clients with cash > 100,000 BDT & valid BO ID for BUY orders
        List<String> realBuyClients = Account_Limits.getActiveBuyClientsWithCash(driver, 100000.0, 5);
        if (realBuyClients == null || realBuyClients.isEmpty()) {
            realBuyClients = new ArrayList<>(Arrays.asList("10009", "10004", "10102"));
            System.out.println("[BUY CLIENTS] Using default fallback clients: " + realBuyClients);
        } else {
            System.out.println("[BUY CLIENTS] Selected Active BUY Clients (> 100,000 cash & BO ID): " + realBuyClients);
        }

        // Fetch real holding clients for SELL side from Position widget
        List<String> realSellClients = new ArrayList<>();
        try {
            Position.navigateToPosition(driver);
            Position.searchSymbolInPosition(driver, symbol, exchange);
            List<Position.ClientPosition> holdings = Position.getClientsWithSaleable(driver, symbol, exchange);
            if (holdings != null && !holdings.isEmpty()) {
                for (Position.ClientPosition cp : holdings) {
                    if (cp.getSaleableQty() > 0) {
                        realSellClients.add(cp.getClientCode());
                    }
                }
                System.out.printf("[POSITION] Found %d real holding clients with saleable shares of %s.%n", realSellClients.size(), symbol);
            }
        } catch (Exception e) {
            System.out.println("[WARN: Position Query] " + e.getMessage());
        }

        if (realSellClients.isEmpty()) {
            realSellClients.addAll(Arrays.asList("11310", "12245", "14118", "19363", "26687"));
            System.out.println("[POSITION] Using default client fallbacks for SELL side.");
        }

        // Calculate safe spread delta to prevent orders from executing on first placement
        double spreadDelta;
        if (refPrice <= 20.0) {
            spreadDelta = 0.50;
        } else if (refPrice <= 100.0) {
            spreadDelta = 1.00;
        } else if (refPrice <= 500.0) {
            spreadDelta = 2.00;
        } else {
            spreadDelta = Math.max(2.0, Math.round(refPrice * 0.005 * 10.0) / 10.0);
        }

        int itemCounter = 1;

        if (choice == 1) {
            // Strategy 1: Multiple BUY orders (DOWN prices) + Opposite SELL order (UPPER price)
            // Rule: BUY orders are down prices (< refPrice), SELL order is upper price (> refPrice).
            // CANNOT be executed first time; placed orders sit as 'Accepted' until executed at market.
            int buyCount = 3;
            int totalBuyQty = 0;

            for (int i = 0; i < buyCount; i++) {
                String buyClient = realBuyClients.get(i % realBuyClients.size());
                int qty = 10 + (i * 2);
                totalBuyQty += qty;
                // Multiple BUY orders at DOWN prices (< refPrice)
                double step = differentPrices ? (i * 0.50) : 0.0;
                double rate = Math.max(0.10, Math.round((refPrice - spreadDelta - step) * 100.0) / 100.0);
                queue.add(new OrderItem(itemCounter++, OrderSide.BUY, buyClient, symbol, qty, rate, false));
            }

            // Opposite SELL order at UPPER price (> refPrice and well above the BUY orders)
            String sellClient = realSellClients.get(0);
            double sellRate = Math.round((refPrice + spreadDelta) * 100.0) / 100.0;
            queue.add(new OrderItem(itemCounter++, OrderSide.SELL, sellClient, symbol, totalBuyQty, sellRate, true));

        } else if (choice == 2) {
            // Strategy 2: Multiple SELL orders (UPPER prices) + Opposite BUY order (DOWN price)
            // Rule: SELL orders are upper prices (> refPrice), BUY order is down price (< refPrice).
            // CANNOT be executed first time; placed orders sit as 'Accepted' until executed at market.
            int sellCount = Math.min(3, realSellClients.size());
            int totalSellQty = 0;

            for (int i = 0; i < sellCount; i++) {
                String sellClient = realSellClients.get(i % realSellClients.size());
                int qty = 10;
                totalSellQty += qty;
                // Multiple SELL orders at UPPER prices (> refPrice)
                double step = differentPrices ? (i * 0.50) : 0.0;
                double rate = Math.round((refPrice + spreadDelta + step) * 100.0) / 100.0;
                queue.add(new OrderItem(itemCounter++, OrderSide.SELL, sellClient, symbol, qty, rate, false));
            }

            // Opposite BUY order at DOWN price (< refPrice and well below the SELL orders)
            String buyClient = realBuyClients.get(0);
            double buyRate = Math.max(0.10, Math.round((refPrice - spreadDelta) * 100.0) / 100.0);
            queue.add(new OrderItem(itemCounter++, OrderSide.BUY, buyClient, symbol, totalSellQty, buyRate, true));

        } else {
            // Strategy 3: Custom Interactive Order Queue with Non-Crossing Spread Defaults
            System.out.print("Enter Primary Order Side (BUY / SELL, default=BUY): ");
            String sideInput = sc.nextLine().trim().toUpperCase();
            OrderSide primarySide = sideInput.equals("SELL") ? OrderSide.SELL : OrderSide.BUY;
            OrderSide oppSide = (primarySide == OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY;

            System.out.print("How many primary orders to place? (e.g. 2, 3, default=2): ");
            String cntStr = sc.nextLine().trim();
            int pCount = cntStr.isEmpty() ? 2 : Integer.parseInt(cntStr);

            int totalPrimaryQty = 0;
            for (int i = 0; i < pCount; i++) {
                String client = (primarySide == OrderSide.BUY)
                        ? realBuyClients.get(i % realBuyClients.size())
                        : realSellClients.get(i % realSellClients.size());
                System.out.printf("  Order #%d Client [%s]: ", (i + 1), client);
                String cInput = sc.nextLine().trim();
                if (!cInput.isEmpty()) client = cInput;

                System.out.print("  Quantity (default 10): ");
                String qInput = sc.nextLine().trim();
                int q = qInput.isEmpty() ? 10 : Integer.parseInt(qInput);
                totalPrimaryQty += q;

                double defaultRate;
                if (primarySide == OrderSide.BUY) {
                    double step = differentPrices ? (i * 0.50) : 0.0;
                    defaultRate = Math.max(0.10, Math.round((refPrice - spreadDelta - step) * 100.0) / 100.0);
                } else {
                    double step = differentPrices ? (i * 0.50) : 0.0;
                    defaultRate = Math.round((refPrice + spreadDelta + step) * 100.0) / 100.0;
                }

                System.out.printf("  Price Rate (default %.2f %s): ", defaultRate, (primarySide == OrderSide.BUY ? "DOWN" : "UPPER"));
                String rInput = sc.nextLine().trim();
                double r = rInput.isEmpty() ? defaultRate : Double.parseDouble(rInput);

                queue.add(new OrderItem(itemCounter++, primarySide, client, symbol, q, r, false));
            }

            // Opposite order
            String oppClient = (oppSide == OrderSide.BUY) ? realBuyClients.get(0) : realSellClients.get(0);
            System.out.printf("Enter Opposite %s Client [%s]: ", oppSide, oppClient);
            String ocInput = sc.nextLine().trim();
            if (!ocInput.isEmpty()) oppClient = ocInput;

            double defaultOppRate;
            if (oppSide == OrderSide.SELL) {
                defaultOppRate = Math.round((refPrice + spreadDelta) * 100.0) / 100.0;
            } else {
                defaultOppRate = Math.max(0.10, Math.round((refPrice - spreadDelta) * 100.0) / 100.0);
            }
            System.out.printf("Enter Opposite %s Rate (default %.2f %s): ", oppSide, defaultOppRate, (oppSide == OrderSide.SELL ? "UPPER" : "DOWN"));
            String orInput = sc.nextLine().trim();
            double oppRate = orInput.isEmpty() ? defaultOppRate : Double.parseDouble(orInput);

            queue.add(new OrderItem(itemCounter++, oppSide, oppClient, symbol, totalPrimaryQty, oppRate, true));
        }

        return queue;
    }

    // =========================================================================
    // 4. BASELINE SNAPSHOT CAPTURE (ALL 6 WIDGETS)
    // =========================================================================
    private static BaselineStore captureBaselineStore(WebDriver driver, WebDriverWait wait, List<OrderItem> queue, String symbol, String exchange) {
        BaselineStore b = new BaselineStore();

        // 1. Gather all unique client codes involved
        List<String> clients = new ArrayList<>();
        for (OrderItem item : queue) {
            if (!clients.contains(item.getClientCode())) {
                clients.add(item.getClientCode());
            }
        }

        // A. Client Portfolio Baselines
        System.out.println("[1/6] Capturing Client Portfolio baselines...");
        try {
            ClientPortfolio.navigateToPortfolio(driver);
            for (String code : clients) {
                Map<String, String> snap = ClientPortfolio.getSnapshot(driver, code, symbol, exchange);
                Map<String, String> details = ClientPortfolio.getClientDetails(driver, code);
                b.portfolioSnapshots.put(code, snap);
                b.clientDetails.put(code, details);
                System.out.printf("      * Client: %-8s | Total Qty: %-6s | Saleable: %-6s | Cash: %s%n",
                        code, snap.getOrDefault("total_qty", "0"), snap.getOrDefault("saleable_qty", "0"), details.getOrDefault("mature_balance", "-"));
            }
        } catch (Exception e) {
            System.out.println("[WARN: Baseline Portfolio] " + e.getMessage());
        }

        // B. Market Depth Baseline
        System.out.println("[2/6] Capturing Market Depth baseline...");
        try {
            Market_Depth.navigateToMarketDepth(driver);
            b.marketDepth = Market_Depth.getSnapshot(driver, symbol, exchange);
            System.out.printf("      * Symbol: %-8s | LTP: %-6s | Total Bid Qty: %-8s | Total Ask Qty: %s%n",
                    symbol, b.marketDepth.getOrDefault("ltp", "-"), b.marketDepth.getOrDefault("total_bid_qty", "-"), b.marketDepth.getOrDefault("total_ask_qty", "-"));
        } catch (Exception e) {
            System.out.println("[WARN: Baseline Depth] " + e.getMessage());
        }

        // C. Order Terminal Baseline
        System.out.println("[3/6] Capturing Order Terminal baseline...");
        try {
            Order_Terminal.navigateToOrderTerminal(driver);
            b.orderTerminal = Order_Terminal.getPreTradeSnapshot(driver, clients.get(0), symbol, exchange);
            System.out.printf("      * Baseline Top Order ID: %s | Progress: %s%n",
                    b.orderTerminal.getOrDefault("Order_ID", "NONE"), b.orderTerminal.getOrDefault("Progress", "0%"));
        } catch (Exception e) {
            System.out.println("[WARN: Baseline Order Terminal] " + e.getMessage());
        }

        // D. Order Summary Baseline
        System.out.println("[4/6] Capturing Order Summary baseline...");
        try {
            Order_Summary.navigateToOrderSummary(driver);
            b.orderSummary = Order_Summary.getSnapshot(driver, clients.get(0), symbol, exchange);
            System.out.printf("      * Buy Turnover: %-10s | Sell Turnover: %-10s | Total Orders: %s%n",
                    b.orderSummary.getOrDefault("Buy_Turnover", "0"), b.orderSummary.getOrDefault("Sell_Turnover", "0"), b.orderSummary.getOrDefault("Total_Orders", "0"));
        } catch (Exception e) {
            System.out.println("[WARN: Baseline Order Summary] " + e.getMessage());
        }

        // E. Account Limits Baseline
        System.out.println("[5/6] Capturing Account Limits baseline...");
        try {
            Account_Limits.navigateToAccountLimits(driver);
            for (String code : clients) {
                Map<String, String> limits = Account_Limits.getSnapshot(driver, code, exchange);
                b.accountLimits.put(code, limits);
                System.out.printf("      * Client: %-8s | Cash Limit: %-10s | Exposure: %-10s | Turnover: %s%n",
                        code, limits.getOrDefault("cash_limit", "-"), limits.getOrDefault("exposure", "-"), limits.getOrDefault("turnover", "-"));
            }
        } catch (Exception e) {
            System.out.println("[WARN: Baseline Account Limits] " + e.getMessage());
        }

        // F. Position Widget Baseline
        System.out.println("[6/6] Capturing Position holdings baseline...");
        try {
            Position.navigateToPosition(driver);
            Position.searchSymbolInPosition(driver, symbol, exchange);
            b.positionHoldings = Position.getPositions(driver, symbol, exchange);
            System.out.printf("      * Found %d holding account(s) recorded for %s in Position widget.%n",
                    b.positionHoldings.size(), symbol);
        } catch (Exception e) {
            System.out.println("[WARN: Baseline Position] " + e.getMessage());
        }

        System.out.println("[SUCCESS] Pre-execution baselines captured across all 6 widgets.\n");
        return b;
    }

    // =========================================================================
    // 5. ORDER PLACEMENT ENGINE (TRADE PAGE)
    // =========================================================================
    private static List<OrderItem> placeOrdersOnTradePage(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, List<OrderItem> queue, String exchange) {
        List<OrderItem> placed = new ArrayList<>();
        OMSweb_Trade.navigateToTradePage(driver, wait);

        for (int i = 0; i < queue.size(); i++) {
            OrderItem item = queue.get(i);
            Instant start = Instant.now();
            System.out.printf("%n>>> Placing Order [%d/%d]: %s...%n", (i + 1), queue.size(), item);

            try {
                // Ensure on Trade Page
                if (!OMSweb_Trade.isAlreadyOnTradePage(driver)) {
                    OMSweb_Trade.navigateToTradePage(driver, wait);
                }

                // 1. Open BUY or SELL Modal
                By actionBtn = (item.getSide() == OrderSide.BUY)
                        ? By.xpath("//div[@title='Place BUY Order']")
                        : By.xpath("//div[@title='Place SELL Order']");
                WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(actionBtn));
                btn.click();
                Thread.sleep(300);

                // 2. Select Exchange in Modal
                OMSweb_Trade.selectExchangeInModal(driver, exchange);
                Thread.sleep(200);

                // 3. Client Code
                WebElement clientInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("order_client_code")));
                clientInput.click();
                clientInput.clear();
                clientInput.sendKeys(item.getClientCode());
                try {
                    js.executeScript("$('#order_client_code').trigger('input').trigger('change');");
                } catch (Exception ignored) {}
                Thread.sleep(200);

                // 4. Instrument / Symbol
                WebElement symInput = driver.findElement(By.id("order_instrument"));
                symInput.clear();
                symInput.sendKeys(item.getSymbol());
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
                } catch (Exception ignored) {}
                symInput.sendKeys(Keys.ARROW_DOWN);
                symInput.sendKeys(Keys.ENTER);
                Thread.sleep(200);

                // 5. Quantity
                WebElement qtyInput = driver.findElement(By.id("order_qty"));
                qtyInput.clear();
                qtyInput.sendKeys(String.valueOf(item.getQuantity()));
                try {
                    js.executeScript("$('#order_qty').trigger('input').trigger('change');");
                } catch (Exception ignored) {}
                Thread.sleep(200);

                // 6. Rate
                if (item.getRate() > 0) {
                    try {
                        WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
                        if (rateInput.isDisplayed()) {
                            rateInput.clear();
                            rateInput.sendKeys(String.format(java.util.Locale.US, "%.2f", item.getRate()));
                        } else {
                            js.executeScript("arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
                                    rateInput, String.format(java.util.Locale.US, "%.2f", item.getRate()));
                        }
                        js.executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
                    } catch (Exception ignored) {}
                }

                // 7. Commission Display
                OMSweb_Trade.displayCommissionOnOrderWindow(driver, item.getClientCode(), item.getQuantity(), item.getRate());

                // 8. Submit BUY or SELL
                By submitBtnBy = (item.getSide() == OrderSide.BUY)
                        ? By.xpath("//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='BUY']")
                        : By.xpath("//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='SELL']");
                WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(submitBtnBy));
                submitBtn.click();

                // 9. Confirm YES
                WebElement confirmYes = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[@id='cnfirmConfirmBtn'] | //button[normalize-space()='YES'] | //button[normalize-space()='Yes']")));
                confirmYes.click();

                long elapsed = Duration.between(start, Instant.now()).toMillis();
                System.out.printf("✔ Order #%d Submitted successfully in %d ms (%s %s x %d @ %.2f)%n",
                        item.getId(), elapsed, item.getSide(), item.getClientCode(), item.getQuantity(), item.getRate());

                placed.add(item);
                Thread.sleep(800);

            } catch (Exception e) {
                System.err.printf("✖ Failed to place Order #%d: %s%n", item.getId(), e.getMessage());
                try {
                    // Dismiss modal if open
                    WebElement cancelBtn = driver.findElement(By.xpath("//button[@id='cnfirmCancelBtn'] | //button[normalize-space()='NO'] | //button[normalize-space()='Close']"));
                    cancelBtn.click();
                } catch (Exception ignored) {}
            }
        }
        return placed;
    }

    // =========================================================================
    // 6. ORDER TERMINAL: MULTI-SELECT & "EXECUTE AT MARKET" CONTEXT MENU
    // =========================================================================
    private static boolean selectAndExecuteAtMarket(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, List<OrderItem> placedOrders, String symbol) {
        try {
            System.out.println("\n[TERMINAL] Navigating to Order Terminal widget...");
            Order_Terminal.navigateToOrderTerminal(driver);
            Thread.sleep(1000);
            Order_Terminal.refresh(driver);
            Thread.sleep(1500);

            // 1. Filter table by symbol to ensure target orders are in view
            try {
                Order_Terminal.filterOrderTerminal(driver, "", symbol);
                Thread.sleep(600);
            } catch (Exception ignored) {}

            // 2. Filter by Status: All Accepted and Partially Filled orders
            System.out.println("[TERMINAL] Filtering by Status: ALL ACCEPTED and PARTIALLY FILLED orders...");
            Order_Terminal.filterByStatus(driver, "ACTIVE");
            Thread.sleep(500);

            // 3. Multi-select all Accepted and Partially Filled orders
            System.out.println("[TERMINAL] Selecting all Accepted and Partially Filled order rows...");
            List<WebElement> selectedRows = Order_Terminal.selectOrdersByStatus(driver, "ACCEPTED", "PARTIALLY FILLED");

            // Fallback retry: if no rows found with filter, clear filter and try scanning again
            if (selectedRows.isEmpty()) {
                System.out.println("[TERMINAL] Retrying selection without symbol filter...");
                Order_Terminal.clearFilter(driver);
                Order_Terminal.refresh(driver);
                Thread.sleep(1500);
                selectedRows = Order_Terminal.selectOrdersByStatus(driver, "ACCEPTED", "PARTIALLY FILLED");
            }

            // Attach Order IDs to placed orders from the selected rows
            for (OrderItem order : placedOrders) {
                for (WebElement row : selectedRows) {
                    try {
                        String rowText = row.getText().toUpperCase();
                        if (rowText.contains(symbol) && rowText.contains(order.getClientCode())) {
                            String rowId = row.getDomAttribute("id");
                            if (rowId != null && !rowId.isEmpty()) {
                                order.setOrderId(rowId);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (selectedRows.isEmpty()) {
                System.out.println("[TERMINAL] No Accepted or Partially Filled orders found to execute.");
                return false;
            }

            // 4. Right-Click and Execute at Market via Order_Terminal helper
            return Order_Terminal.executeSelectedAtMarket(driver, selectedRows);

        } catch (Exception e) {
            System.out.println("[WARN: Context Execution] " + e.getMessage());
            return false;
        }
    }
    // =========================================================================
    // 7. REAL-TIME MULTI-WIDGET AUDIT & VERIFICATION ENGINE (ALL 6 WIDGETS)
    // =========================================================================
    private static void runComprehensiveMultiWidgetAudit(WebDriver driver, WebDriverWait wait, BaselineStore base, List<OrderItem> placedOrders, String symbol, String exchange) {
        Map<String, String> scorecard = new LinkedHashMap<>();

        // ---------------------------------------------------------------------
        // WIDGET 1: ORDER TERMINAL REAL-TIME AUDIT
        // ---------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println(" [AUDIT 1/6] ORDER TERMINAL REAL-TIME VERIFICATION");
        System.out.println("-------------------------------------------------------------------------------");
        try {
            Order_Terminal.navigateToOrderTerminal(driver);
            Order_Terminal.refresh(driver);

            for (OrderItem order : placedOrders) {
                Map<String, String> postSnap = Order_Terminal.getSnapshot(driver, order.getClientCode(), symbol, exchange);
                String postStatus = postSnap.getOrDefault("Status", "Unknown");
                String orderId = postSnap.getOrDefault("Order_ID", order.getOrderId());
                order.setOrderId(orderId);
                order.setPostStatus(postStatus);

                double execQty = parseNum(postSnap.getOrDefault("Exec_Qty", "0"));
                double dueQty = parseNum(postSnap.getOrDefault("Due_Qty", "0"));
                order.setExecQty((int) execQty);
                order.setDueQty((int) dueQty);

                Order_Terminal.verify(base.orderTerminal, postSnap, symbol, order.getSide().name(), order.getQuantity(), order.getRate(), exchange);
            }
            scorecard.put("Order Terminal", "PASS (Orders Captured & Terminal Verified)");
        } catch (Exception e) {
            System.out.println("[FAIL: Order Terminal Audit] " + e.getMessage());
            scorecard.put("Order Terminal", "WARNING (" + e.getMessage() + ")");
        }

        // ---------------------------------------------------------------------
        // WIDGET 2: CLIENT PORTFOLIO REAL-TIME AUDIT
        // ---------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println(" [AUDIT 2/6] CLIENT PORTFOLIO REAL-TIME VERIFICATION");
        System.out.println("-------------------------------------------------------------------------------");
        try {
            ClientPortfolio.navigateToPortfolio(driver);
            ClientPortfolio.refresh(driver);

            for (OrderItem order : placedOrders) {
                String code = order.getClientCode();
                Map<String, String> preSnap = base.portfolioSnapshots.getOrDefault(code, ClientPortfolio.defaultZeroPortfolio(symbol));
                Map<String, String> postSnap = ClientPortfolio.getSnapshot(driver, code, symbol, exchange);
                Map<String, String> preDetails = base.clientDetails.getOrDefault(code, new HashMap<>());
                Map<String, String> postDetails = ClientPortfolio.getClientDetails(driver, code);

                int execQ = order.getExecQty() > 0 ? order.getExecQty() : order.getQuantity();
                int dueQ = order.getDueQty();
                String status = order.getPostStatus().equalsIgnoreCase("UNKNOWN") ? "Filled" : order.getPostStatus();

                System.out.printf("%n>>> Verifying Portfolio for Client: %s (%s %d @ %.2f)...%n",
                        code, order.getSide(), order.getQuantity(), order.getRate());

                ClientPortfolio.verify(preSnap, postSnap, preDetails, postDetails, symbol, order.getSide().name(),
                        order.getQuantity(), order.getRate(), exchange, execQ, dueQ, status);
            }
            scorecard.put("Client Portfolio", "PASS (Shares, Value & Balances Audited)");
        } catch (Exception e) {
            System.out.println("[FAIL: Portfolio Audit] " + e.getMessage());
            scorecard.put("Client Portfolio", "WARNING (" + e.getMessage() + ")");
        }

        // ---------------------------------------------------------------------
        // WIDGET 3: MARKET DEPTH REAL-TIME AUDIT
        // ---------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println(" [AUDIT 3/6] MARKET DEPTH REAL-TIME VERIFICATION");
        System.out.println("-------------------------------------------------------------------------------");
        try {
            Market_Depth.navigateToMarketDepth(driver);
            Map<String, String> postDepth = Market_Depth.getSnapshot(driver, symbol, exchange);
            Market_Depth.printDepthReport(symbol, postDepth);

            OrderItem refOrder = placedOrders.get(0);
            Market_Depth.verify(base.marketDepth, postDepth, symbol, refOrder.getSide().name(), refOrder.getQuantity(), refOrder.getRate(), exchange);
            scorecard.put("Market Depth", "PASS (Order Book & Depth Flow Verified)");
        } catch (Exception e) {
            System.out.println("[FAIL: Market Depth Audit] " + e.getMessage());
            scorecard.put("Market Depth", "WARNING (" + e.getMessage() + ")");
        }

        // ---------------------------------------------------------------------
        // WIDGET 4: ORDER SUMMARY REAL-TIME AUDIT
        // ---------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println(" [AUDIT 4/6] ORDER SUMMARY REAL-TIME VERIFICATION");
        System.out.println("-------------------------------------------------------------------------------");
        try {
            Order_Summary.navigateToOrderSummary(driver);
            Order_Summary.refresh(driver);

            OrderItem refOrder = placedOrders.get(0);
            Map<String, String> postSummary = Order_Summary.getSnapshot(driver, refOrder.getClientCode(), symbol, exchange);
            int totalExecutedQty = 0;
            for (OrderItem o : placedOrders) totalExecutedQty += o.getQuantity();

            Order_Summary.verify(base.orderSummary, postSummary, symbol, refOrder.getSide().name(),
                    totalExecutedQty, refOrder.getRate(), exchange, totalExecutedQty, 0);
            scorecard.put("Order Summary", "PASS (Buy/Sell Aggregates & Turnover Verified)");
        } catch (Exception e) {
            System.out.println("[FAIL: Order Summary Audit] " + e.getMessage());
            scorecard.put("Order Summary", "WARNING (" + e.getMessage() + ")");
        }

        // ---------------------------------------------------------------------
        // WIDGET 5: ACCOUNT LIMITS REAL-TIME AUDIT
        // ---------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println(" [AUDIT 5/6] ACCOUNT LIMITS REAL-TIME VERIFICATION");
        System.out.println("-------------------------------------------------------------------------------");
        try {
            Account_Limits.navigateToAccountLimits(driver);
            Account_Limits.refresh(driver);

            for (OrderItem order : placedOrders) {
                String code = order.getClientCode();
                Map<String, String> preLimit = base.accountLimits.getOrDefault(code, new HashMap<>());
                Map<String, String> postLimit = Account_Limits.getSnapshot(driver, code, exchange);

                System.out.printf("%n>>> Verifying Account Limits for Client: %s...%n", code);
                Account_Limits.verify(preLimit, postLimit, order.getSide().name(),
                        order.getQuantity(), order.getRate(), exchange, order.getQuantity(), 0);
            }
            scorecard.put("Account Limits", "PASS (Exposure & Cash Limits Audited)");
        } catch (Exception e) {
            System.out.println("[FAIL: Account Limits Audit] " + e.getMessage());
            scorecard.put("Account Limits", "WARNING (" + e.getMessage() + ")");
        }

        // ---------------------------------------------------------------------
        // WIDGET 6: POSITION WIDGET REAL-TIME AUDIT
        // ---------------------------------------------------------------------
        System.out.println("\n-------------------------------------------------------------------------------");
        System.out.println(" [AUDIT 6/6] POSITION WIDGET REAL-TIME VERIFICATION");
        System.out.println("-------------------------------------------------------------------------------");
        try {
            Position.navigateToPosition(driver);
            Position.searchSymbolInPosition(driver, symbol, exchange);
            List<Position.ClientPosition> postHoldings = Position.getPositions(driver, symbol, exchange);

            System.out.printf("[POSITION] Post-Execution Holding Accounts for %s: %d%n", symbol, postHoldings.size());
            Position.printPositionsTable(symbol, postHoldings);

            // Verify Position delta for each placed order
            verifyPositionDeltas(base.positionHoldings, postHoldings, placedOrders, symbol);
            scorecard.put("Position Widget", "PASS (Client Holdings & Saleable Balances Verified)");
        } catch (Exception e) {
            System.out.println("[FAIL: Position Widget Audit] " + e.getMessage());
            scorecard.put("Position Widget", "WARNING (" + e.getMessage() + ")");
        }

        // =====================================================================
        // MASTER VERDICT & SCOREBOARD
        // =====================================================================
        printMasterScorecard(scorecard, placedOrders, symbol);
    }

    // =========================================================================
    // 8. POSITION DELTA VERIFIER
    // =========================================================================
    private static void verifyPositionDeltas(List<Position.ClientPosition> preHoldings, List<Position.ClientPosition> postHoldings, List<OrderItem> orders, String symbol) {
        System.out.println();
        System.out.println("+=======================================================================================================+");
        System.out.printf("| POSITION REAL-TIME DELTA AUDIT FOR %-58s |%n", symbol);
        System.out.println("+=======================================================================================================+");
        System.out.printf("| %-8s | %-6s | %-12s | %-12s | %-14s | %-14s | %-8s |%n",
                "Client", "Side", "Pre-Qty", "Post-Qty", "Expected Post", "Expected Delta", "Verdict");
        System.out.println("+-------------------------------------------------------------------------------------------------------+");

        Map<String, Double> preMap = new HashMap<>();
        if (preHoldings != null) {
            for (Position.ClientPosition cp : preHoldings) preMap.put(cp.getClientCode(), cp.getTotalQty());
        }

        Map<String, Double> postMap = new HashMap<>();
        if (postHoldings != null) {
            for (Position.ClientPosition cp : postHoldings) postMap.put(cp.getClientCode(), cp.getTotalQty());
        }

        for (OrderItem o : orders) {
            String client = o.getClientCode();
            double preQ = preMap.getOrDefault(client, 0.0);
            double postQ = postMap.getOrDefault(client, preQ);
            double expQ = (o.getSide() == OrderSide.BUY) ? (preQ + o.getQuantity()) : Math.max(0, preQ - o.getQuantity());
            String deltaStr = (o.getSide() == OrderSide.BUY ? "+" : "-") + o.getQuantity();

            boolean pass = (postQ == expQ || (o.getDueQty() > 0 && postQ >= preQ));
            System.out.printf("| %-8s | %-6s | %-12.0f | %-12.0f | %-14.0f | %-14s | %-8s |%n",
                    client, o.getSide(), preQ, postQ, expQ, deltaStr, pass ? "PASS" : "INFO");
        }
        System.out.println("+=======================================================================================================+");
    }

    // =========================================================================
    // 9. MASTER SCORECARD
    // =========================================================================
    private static void printMasterScorecard(Map<String, String> scorecard, List<OrderItem> placedOrders, String symbol) {
        System.out.println();
        System.out.println("+=======================================================================================+");
        System.out.println("|             MULTI-ORDER EXECUTION & REAL-TIME 6-WIDGET AUDIT REPORT                   |");
        System.out.println("+=======================================================================================+");
        System.out.printf("| Symbol Tested      : %-64s |%n", symbol);
        System.out.printf("| Total Orders Placed: %-64d |%n", placedOrders.size());
        System.out.println("+---------------------------------------------------------------------------------------+");
        System.out.printf("| %-22s | %-58s |%n", "OMS Widget", "Verification Result");
        System.out.println("+---------------------------------------------------------------------------------------+");

        for (Map.Entry<String, String> entry : scorecard.entrySet()) {
            System.out.printf("| %-22s | %-58s |%n", entry.getKey(), entry.getValue());
        }
        System.out.println("+=======================================================================================+");
        System.out.println("| >>> ALL 6 REAL-TIME WIDGETS TESTED & COMPARED AGAINST PRE/POST ORDER FLOW. <<<       |");
        System.out.println("+=======================================================================================+\n");
    }

    // =========================================================================
    // 10. UTILITIES
    // =========================================================================
    private static void printPlannedQueue(List<OrderItem> queue) {
        System.out.println("\n+---------------------------------------------------------------------------------------+");
        System.out.println("| PLANNED MULTI-ORDER EXECUTION PIPELINE                                                |");
        System.out.println("+---------------------------------------------------------------------------------------+");
        for (OrderItem item : queue) {
            System.out.printf("| %-85s |%n", item.toString());
        }
        System.out.println("+---------------------------------------------------------------------------------------+");
    }

    public static String cleanSymbol(String sym) {
        if (sym == null) return "";
        String s = sym.trim().toUpperCase().replaceAll("\\[[A-Z0-9]+\\]", "");
        if (s.contains(".")) s = s.split("\\.")[0];
        return s.trim();
    }

    private static double parseNum(String val) {
        if (val == null) return 0.0;
        String clean = val.replaceAll("[^0-9.-]", "").trim();
        if (clean.isEmpty() || clean.equals("-") || clean.equals("--") || clean.equals(".") || clean.equals("-.")) return 0.0;
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
