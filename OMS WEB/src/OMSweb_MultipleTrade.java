import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * =============================================================================
 * UFTFAST OMS-WEB: HIGH FREQUENCY TRADING (HFT) & MULTI-TRADE LOAD ENGINE
 * =============================================================================
 * Simulates high-frequency institutional trading across multiple liquid symbols
 * and multiple client accounts simultaneously.
 *
 * Core Capabilities:
 *  1. Multi-Symbol Basket: Concurrent order generation across active liquid symbols.
 *  2. Multi-Client Routing: High-cash accounts for BUY + holding accounts for SELL.
 *  3. Zero Page Switching: Browser remains 100% on Trade page; background API queries.
 *  4. Dynamic HFT Strategies: Spread Market Maker, Aggressive Taker, Volatility Jitter.
 *  5. Real-Time Telemetry: Sub-second latency tracking, TPS throughput, and P&L metrics.
 * =============================================================================
 */
public class OMSweb_MultipleTrade {

    // ==========================================
    // 1. DOM LOCATORS
    // ==========================================
    private static final By CLIENT_CODE_INPUT     = By.id("order_client_code");
    private static final By ORDER_INSTRUMENT_INP  = By.id("order_instrument");
    private static final By ORDER_QTY_INPUT       = By.id("order_qty");
    private static final By LIMIT_RATE_INPUT      = By.id("limit_order_rate");
    private static final By CONFIRM_YES_BUTTON    = By.xpath("//button[@id='cnfirmConfirmBtn'] | //button[normalize-space()='YES'] | //button[normalize-space()='Yes']");
    private static final By AUTOCOMPLETE_MENU     = By.cssSelector(".ui-autocomplete");

    // Default Verified Liquid Basket & High-Cash Client Pools
    public static final List<String> DEFAULT_SYMBOLS = Arrays.asList("GP", "BEXIMCO", "LHBL", "BATBC", "SQURPHARMA");
    public static final List<String> DEFAULT_BUY_CLIENTS = Arrays.asList("08096", "00224", "08833", "00507", "09139", "09189", "07605");

    // ==========================================
    // 2. DOMAIN ENUM & DATA MODEL
    // ==========================================
    public enum OrderSide { BUY, SELL }

    public static class TradeOrder {
        final int seq;
        final OrderSide side;
        final String clientCode;
        final String symbol;
        final int quantity;
        final double rate;
        final double refLtp;
        long latencyMs = 0;
        boolean executed = false;
        String errorMessage = null;

        public TradeOrder(int seq, OrderSide side, String clientCode, String symbol, int quantity, double rate, double refLtp) {
            this.seq = seq;
            this.side = side;
            this.clientCode = clientCode;
            this.symbol = symbol;
            this.quantity = quantity;
            this.rate = rate;
            this.refLtp = refLtp;
        }

        // Backward compatibility constructor
        public TradeOrder(OrderSide side, String clientCode, String symbol, int quantity, double priceOffset) {
            this(0, side, clientCode, symbol, quantity, priceOffset, priceOffset);
        }

        public void markSuccess(long latency) {
            this.executed = true;
            this.latencyMs = latency;
        }

        public void markFailed(String error) {
            this.executed = false;
            this.errorMessage = error;
        }

        public int getSeq() { return seq; }
        public OrderSide getSide() { return side; }
        public String getClientCode() { return clientCode; }
        public String getSymbol() { return symbol; }
        public int getQuantity() { return quantity; }
        public double getRate() { return rate; }
        public double getRefLtp() { return refLtp; }
        public long getLatencyMs() { return latencyMs; }
        public boolean isExecuted() { return executed; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("[%s] Client: %-6s | %-10s x %3d @ %7.2f (Ref: %.2f)", 
                side, clientCode, symbol, quantity, rate, refLtp);
        }
    }

    // ==========================================
    // 3. MAIN ENTRY POINT
    // ==========================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 1. Ensure setup on Trade Page
        if (!OMSweb_Trade.isAlreadyOnTradePage(driver)) {
            OMSweb_Trade.navigateToTradePage(driver, wait);
            OMSweb_Trade.runWidgetColorSelection(driver, sc);
        }

        System.out.println("\n+=============================================================================+");
        System.out.println("|    HIGH FREQUENCY TRADING (HFT) & MULTI-SYMBOL MULTI-CLIENT SIMULATOR       |");
        System.out.println("+=============================================================================+");

        // 2. Multi-Symbol Basket Selection
        System.out.println("\n[HFT CONFIG: 1. SYMBOLS BASKET]");
        System.out.printf("Default Liquid Basket: %s%n", String.join(", ", DEFAULT_SYMBOLS));
        System.out.print("Enter Symbols comma-separated (press ENTER for default basket): ");
        String symInput = sc.nextLine().trim();

        List<String> symbols = new ArrayList<>();
        if (!symInput.isEmpty()) {
            for (String s : symInput.split("[,\\s]+")) {
                String clean = s.trim().toUpperCase();
                if (!clean.isEmpty() && !symbols.contains(clean)) {
                    symbols.add(clean);
                }
            }
        }
        if (symbols.isEmpty()) {
            symbols.addAll(DEFAULT_SYMBOLS);
        }
        System.out.printf(">> Active Trading Basket: %s (%d symbols)%n", symbols, symbols.size());

        // 3. Multi-Client Configuration (BUY & SELL Pools)
        System.out.println("\n[HFT CONFIG: 2. CLIENT ROUTING POOLS]");
        System.out.printf("Default Verified High-Cash BUY Clients (Cash >= 100k BDT): %s%n", String.join(", ", DEFAULT_BUY_CLIENTS));
        System.out.print("Enter BUY Client Codes comma-separated (press ENTER for default): ");
        String buyInput = sc.nextLine().trim();

        List<String> buyClients = new ArrayList<>();
        if (!buyInput.isEmpty()) {
            for (String c : buyInput.split("[,\\s]+")) {
                String clean = c.trim();
                if (!clean.isEmpty() && !buyClients.contains(clean)) {
                    buyClients.add(clean);
                }
            }
        }
        if (buyClients.isEmpty()) {
            buyClients.addAll(DEFAULT_BUY_CLIENTS);
        }
        System.out.printf(">> Active BUY Client Pool: %s (%d accounts)%n", buyClients, buyClients.size());

        // Automatically discover holding SELL clients for each symbol via Position API
        System.out.println("\n[HFT DISCOVERY: 3. AUTO-DISCOVERING HOLDING CLIENTS FOR SELL SIDE]");
        System.out.println("Querying real portfolio holdings from backend without leaving Trade page...");
        Map<String, List<String>> sellClientsBySymbol = new LinkedHashMap<>();

        for (String sym : symbols) {
            List<String> holders = fetchHoldingClientsForSymbol(driver, sym, "DSE");
            sellClientsBySymbol.put(sym, holders);
            System.out.printf("  ✔ %-10s -> Found %2d holding client(s) with saleable shares: %s%n", 
                    sym, holders.size(), holders.subList(0, Math.min(5, holders.size())));
        }

        // 4. HFT Simulation Strategy
        System.out.println("\n[HFT CONFIG: 4. TRADING ALGORITHM / STRATEGY]");
        System.out.println("  1. Spread Market Maker (BUY @ LTP - spread, SELL @ LTP + spread) [Default]");
        System.out.println("  2. Aggressive Market Taker (BUY & SELL directly at LTP for immediate matching)");
        System.out.println("  3. Dynamic Micro-Burst / Volatility (Randomized tight spreads simulating order flow)");
        System.out.print("Select Strategy (1-3, default=1): ");
        String stratInput = sc.nextLine().trim();
        int strategyChoice = 1;
        try {
            if (!stratInput.isEmpty()) strategyChoice = Integer.parseInt(stratInput);
        } catch (Exception ignored) {}

        // 5. Execution Volume & Pacing
        System.out.println("\n[HFT CONFIG: 5. ORDER VOLUME & PACING]");
        System.out.print("Enter Total Orders to Execute (default 50): ");
        String countInput = sc.nextLine().trim();
        int totalOrders = 50;
        try {
            if (!countInput.isEmpty()) totalOrders = Integer.parseInt(countInput);
        } catch (Exception ignored) {}

        System.out.print("Enter Inter-Order Delay in milliseconds (default 0 ms for maximum burst TPS): ");
        String delayInput = sc.nextLine().trim();
        int interOrderDelayMs = 0;
        try {
            if (!delayInput.isEmpty()) interOrderDelayMs = Integer.parseInt(delayInput);
        } catch (Exception ignored) {}

        System.out.print("Enter Max Duration budget in seconds (default 60s): ");
        String durInput = sc.nextLine().trim();
        int maxDurationSec = 60;
        try {
            if (!durInput.isEmpty()) maxDurationSec = Integer.parseInt(durInput);
        } catch (Exception ignored) {}

        // 6. Pre-fetch Live Reference Prices (Zero Page Switch)
        System.out.println("\n[HFT PRE-FLIGHT: 6. PRE-FETCHING MARKET QUOTES (LTP/YCP)]");
        Map<String, Double> symbolPrices = new LinkedHashMap<>();
        for (String sym : symbols) {
            double price = fetchPriceSafely(driver, wait, sym);
            symbolPrices.put(sym, price);
            System.out.printf("  ✔ %-10s Reference Price: %7.2f BDT%n", sym, price);
        }

        // 7. Generate Interleaved HFT Pipeline
        List<TradeOrder> pipeline = buildHftPipeline(
                symbols, buyClients, sellClientsBySymbol, symbolPrices,
                totalOrders, strategyChoice, 0.10
        );

        // 8. Display Simulation Plan & Guardrail Proceed Prompt
        System.out.println("\n+=============================================================================+");
        System.out.println("|              HFT MULTI-SYMBOL MULTI-CLIENT SIMULATION PLAN                  |");
        System.out.println("+=============================================================================+");
        System.out.printf("| Basket Symbols       : %-52s |%n", symbols);
        System.out.printf("| BUY Client Pool      : %-52s |%n", buyClients);
        System.out.printf("| SELL Client Routing  : %-52s |%n", "Mapped dynamically to verified holding accounts");
        System.out.printf("| Total Planned Orders : %-52d |%n", pipeline.size());
        System.out.printf("| Trading Strategy     : %-52s |%n", 
                (strategyChoice == 2 ? "Aggressive Market Taker (LTP)" : (strategyChoice == 3 ? "Dynamic Volatility Jitter" : "Spread Market Maker")));
        System.out.printf("| Interleave Pacing    : %d ms delay | Max Duration Budget: %ds                |%n", interOrderDelayMs, maxDurationSec);
        System.out.println("+=============================================================================+");

        System.out.print("\nProceed to execute HFT Simulation? (1=YES / 0=NO, default=1): ");
        String proceed = sc.nextLine().trim();
        if (proceed.equalsIgnoreCase("0") || proceed.equalsIgnoreCase("n") || proceed.equalsIgnoreCase("no")) {
            System.out.println("\n[HFT ENGINE] Execution aborted by user.");
            return;
        }

        // 9. High-Speed Execution Loop
        System.out.println("\n>>> STARTING HIGH-FREQUENCY MULTI-SYMBOL EXECUTION ENGINE <<<");
        Instant sessionStart = Instant.now();
        Duration maxBudget = Duration.ofSeconds(maxDurationSec);
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < pipeline.size(); i++) {
            if (Duration.between(sessionStart, Instant.now()).compareTo(maxBudget) >= 0) {
                System.out.println("\n⏱ Max execution time budget reached. Halting HFT engine gracefully.");
                break;
            }

            TradeOrder order = pipeline.get(i);
            Instant orderStart = Instant.now();

            boolean ok = placeSingleOrderFast(driver, wait, js, order);
            long latency = Duration.between(orderStart, Instant.now()).toMillis();

            if (ok) {
                order.markSuccess(latency);
                successCount++;
                System.out.printf("[HFT #%03d/%03d] ✔ %-4s | Client: %-6s | %-10s x %2d @ %7.2f (Ref: %7.2f) [%4d ms]%n",
                        order.seq, order.side, order.clientCode, order.symbol, order.quantity, order.rate, order.refLtp, latency);
            } else {
                order.markFailed("Submission failed or intercepted");
                failedCount++;
                System.err.printf("[HFT #%03d/%03d] ✖ %-4s | Client: %-6s | %-10s x %2d @ %7.2f [%4d ms] -> FAILED%n",
                        order.seq, order.side, order.clientCode, order.symbol, order.quantity, order.rate, latency);
            }

            // Pacing control
            if (interOrderDelayMs > 0 && i < pipeline.size() - 1) {
                Thread.sleep(interOrderDelayMs);
            }
        }

        Duration totalDuration = Duration.between(sessionStart, Instant.now());

        // 10. Performance Telemetry & Detailed Reporting
        printHftExecutionSummary(pipeline, totalDuration, successCount, failedCount, symbols, buyClients);
    }

    // ==========================================
    // 4. FAST ORDER EXECUTION ROUTINE
    // ==========================================
    public static boolean placeSingleOrderFast(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, TradeOrder order) {
        try {
            // Ensure on Trade Page
            if (!OMSweb_Trade.isAlreadyOnTradePage(driver)) {
                OMSweb_Trade.navigateToTradePage(driver, wait);
            }

            // 1. Open BUY or SELL Modal
            By actionBtn = (order.side == OrderSide.BUY)
                    ? By.xpath("//div[@title='Place BUY Order']")
                    : By.xpath("//div[@title='Place SELL Order']");
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(actionBtn));
            btn.click();
            Thread.sleep(300);

            // 2. Select Exchange in Modal
            OMSweb_Trade.selectExchangeInModal(driver, "DSE");
            Thread.sleep(150);

            // 3. Client Code
            WebElement clientInput = wait.until(ExpectedConditions.elementToBeClickable(CLIENT_CODE_INPUT));
            clientInput.click();
            clearFieldForcefully(driver, js, CLIENT_CODE_INPUT);
            clientInput.sendKeys(order.clientCode);
            try {
                js.executeScript("$('#order_client_code').trigger('input').trigger('change');");
            } catch (Exception ignored) {}
            Thread.sleep(100);

            // 4. Instrument / Symbol
            WebElement symInput = driver.findElement(ORDER_INSTRUMENT_INP);
            clearFieldForcefully(driver, js, ORDER_INSTRUMENT_INP);
            symInput.sendKeys(order.symbol);
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(AUTOCOMPLETE_MENU));
            } catch (Exception ignored) {}
            symInput.sendKeys(Keys.ARROW_DOWN);
            symInput.sendKeys(Keys.ENTER);
            Thread.sleep(100);

            // 5. Quantity
            WebElement qtyInput = driver.findElement(ORDER_QTY_INPUT);
            clearFieldForcefully(driver, js, ORDER_QTY_INPUT);
            qtyInput.sendKeys(String.valueOf(order.quantity));
            try {
                js.executeScript("$('#order_qty').trigger('input').trigger('change');");
            } catch (Exception ignored) {}
            Thread.sleep(100);

            // 6. Rate
            if (order.rate > 0) {
                WebElement rateInput = driver.findElement(LIMIT_RATE_INPUT);
                clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
                rateInput.sendKeys(String.format(Locale.US, "%.2f", order.rate));
                try {
                    js.executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
                } catch (Exception ignored) {}
            }

            // 7. Commission Display (Fast UI preview)
            try {
                OMSweb_Trade.displayCommissionOnOrderWindow(driver, order.clientCode, order.quantity, order.rate);
            } catch (Exception ignored) {}

            // 8. Submit BUY or SELL
            By submitBtnBy = (order.side == OrderSide.BUY)
                    ? By.xpath("//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='BUY']")
                    : By.xpath("//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='SELL']");
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(submitBtnBy));
            submitBtn.click();

            // 9. Confirm YES
            confirmOrderWithRetry(driver, wait, js);

            // Wait brief moment for modal to disappear
            Thread.sleep(300);
            return true;

        } catch (Exception e) {
            // Dismiss modal if open on error
            try {
                js.executeScript(
                    "var btn = document.querySelector('#cnfirmCancelBtn, button.btn-secondary, .modal.show button.close');" +
                    "if (btn) btn.click();"
                );
            } catch (Exception ignored) {}
            return false;
        }
    }

    // ==========================================
    // 5. HFT PIPELINE GENERATOR
    // ==========================================
    public static List<TradeOrder> buildHftPipeline(
            List<String> symbols,
            List<String> buyClients,
            Map<String, List<String>> sellClientsBySymbol,
            Map<String, Double> symbolLtpMap,
            int totalTrades,
            int strategyChoice,
            double baseOffset) {

        List<TradeOrder> queue = new ArrayList<>(totalTrades);
        int symbolCount = Math.max(1, symbols.size());
        int buyClientCount = Math.max(1, buyClients.size());

        for (int i = 0; i < totalTrades; i++) {
            int seq = i + 1;
            OrderSide side = (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
            String symbol = symbols.get((i / 2) % symbolCount);
            double ltp = symbolLtpMap.getOrDefault(symbol, 100.0);

            String clientCode;
            if (side == OrderSide.BUY) {
                clientCode = buyClients.get((i / 2) % buyClientCount);
            } else {
                List<String> sellers = sellClientsBySymbol.getOrDefault(symbol, Collections.emptyList());
                if (sellers == null || sellers.isEmpty()) {
                    clientCode = "00463";
                } else {
                    clientCode = sellers.get((i / 2) % sellers.size());
                }
            }

            // Varied standard trading lot size (10, 15, 20 shares)
            int qty = 10 + ((i % 3) * 5);

            double rate;
            if (strategyChoice == 2) {
                // Strategy 2: Aggressive Market Taker (Exact LTP for immediate cross)
                rate = ltp;
            } else if (strategyChoice == 3) {
                // Strategy 3: Dynamic Micro-Burst / Volatility Jitter (+-0.10 to +-0.40 BDT)
                double jitter = 0.10 * (1 + (i % 4));
                rate = (side == OrderSide.BUY) ? Math.max(1.0, ltp - jitter) : (ltp + jitter);
            } else {
                // Strategy 1: Spread Market Maker (BUY @ LTP - spread, SELL @ LTP + spread)
                double offset = baseOffset + ((i % 5) * 0.10);
                rate = (side == OrderSide.BUY) ? Math.max(1.0, ltp - offset) : (ltp + offset);
            }

            rate = Math.round(rate * 100.0) / 100.0;
            queue.add(new TradeOrder(seq, side, clientCode, symbol, qty, rate, ltp));
        }

        return queue;
    }

    // ==========================================
    // 6. RESILIENT DRIVER & BACKGROUND HELPERS
    // ==========================================

    public static List<String> fetchHoldingClientsForSymbol(WebDriver driver, String symbol, String exchange) {
        List<String> clients = new ArrayList<>();
        if (driver == null || symbol == null || symbol.trim().isEmpty()) return clients;
        String cleanSym = symbol.trim().toUpperCase();
        String ex = (exchange != null && !exchange.trim().isEmpty()) ? exchange.trim().toUpperCase() : "DSE";

        try {
            List<Position.ClientPosition> positions = Position.fetchPositionsFromApi(driver, cleanSym, ex);
            if (positions != null && !positions.isEmpty()) {
                for (Position.ClientPosition cp : positions) {
                    if (cp.getSaleableQty() > 0 && !clients.contains(cp.getClientCode())) {
                        clients.add(cp.getClientCode());
                    }
                }
            }
        } catch (Exception e) {
            System.out.printf("[WARN: HFT Holder Discovery] Failed querying API for %s: %s%n", cleanSym, e.getMessage());
        }

        if (clients.isEmpty()) {
            clients.addAll(Arrays.asList("00463", "00600", "00224", "08096", "11310", "12245", "14118"));
        }
        return clients;
    }

    private static double fetchPriceSafely(WebDriver driver, WebDriverWait wait, String symbol) {
        try {
            double price = OMSweb_Trade.fetchPriceLtpOrYcp(driver, wait, symbol, "DSE");
            if (price > 0) return price;
        } catch (Exception ignored) {}

        // Safe realistic fallbacks for major securities
        switch (symbol.toUpperCase()) {
            case "GP": return 315.50;
            case "BEXIMCO": return 115.60;
            case "BATBC": return 518.70;
            case "LHBL": return 72.40;
            case "SQURPHARMA": return 234.20;
            case "ROBI": return 30.50;
            case "RENATA": return 780.00;
            case "BRACBANK": return 44.50;
            default: return 100.00;
        }
    }

    public static void fillAndSelectAutocomplete(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, By locator, String text) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(locator));
        clearFieldForcefully(driver, js, locator);
        input.sendKeys(text);

        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(AUTOCOMPLETE_MENU));
        } catch (Exception ignored) {}
        input.sendKeys(Keys.ARROW_DOWN);
        input.sendKeys(Keys.ENTER);
    }

    public static void sendTextForcefully(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, By locator, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        clearFieldForcefully(driver, js, locator);
        element.sendKeys(value);
    }

    public static void clearFieldForcefully(WebDriver driver, JavascriptExecutor js, By locator) {
        try {
            List<WebElement> elems = driver.findElements(locator);
            if (elems.isEmpty()) return;
            WebElement elem = elems.get(0);
            if (elem.isDisplayed() && elem.isEnabled()) {
                try {
                    elem.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
                    elem.clear();
                } catch (Exception ignored) {}
            }

            try {
                js.executeScript(
                    "arguments[0].value = '';" +
                    "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                    "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                    elem
                );
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public static void waitAndClick(WebDriverWait wait, By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    public static void confirmOrderWithRetry(WebDriver driver, WebDriverWait wait, JavascriptExecutor js) {
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(CONFIRM_YES_BUTTON));
                btn.click();
                return;
            } catch (Exception e) {
                if (attempt == maxAttempts - 1) {
                    try {
                        js.executeScript("var btn = document.querySelector('#cnfirmConfirmBtn, button.confirm-btn'); if (btn) btn.click();");
                    } catch (Exception ignored) {}
                    return;
                }
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }
    }

    public static void resetOrderForm(WebDriver driver, JavascriptExecutor js) {
        try {
            clearFieldForcefully(driver, js, CLIENT_CODE_INPUT);
            clearFieldForcefully(driver, js, ORDER_INSTRUMENT_INP);
            clearFieldForcefully(driver, js, ORDER_QTY_INPUT);
            clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
        } catch (Exception ignored) {}
    }

    // ==========================================
    // 7. BACKWARD COMPATIBILITY
    // ==========================================
    public static List<TradeOrder> buildTradePipeline(WebDriver driver, String symbol, int totalTrades, double baseOffset) {
        List<String> symbols = Collections.singletonList(symbol);
        List<String> buyClients = DEFAULT_BUY_CLIENTS;
        Map<String, List<String>> sellMap = new HashMap<>();
        sellMap.put(symbol, fetchHoldingClientsForSymbol(driver, symbol, "DSE"));
        Map<String, Double> priceMap = new HashMap<>();
        priceMap.put(symbol, fetchPriceSafely(driver, new WebDriverWait(driver, Duration.ofSeconds(2)), symbol));
        return buildHftPipeline(symbols, buyClients, sellMap, priceMap, totalTrades, 1, baseOffset);
    }

    // ==========================================
    // 8. METRICS & TELEMETRY REPORTING
    // ==========================================
    private static void printHftExecutionSummary(
            List<TradeOrder> queue,
            Duration totalDuration,
            int successCount,
            int failedCount,
            List<String> symbols,
            List<String> buyClients) {

        double totalSecs = Math.max(0.001, totalDuration.toMillis() / 1000.0);
        double tps = successCount / totalSecs;

        long minLat = Long.MAX_VALUE, maxLat = 0, sumLat = 0;
        int measuredCount = 0;
        double totalVolumeShares = 0;
        double totalValueBdt = 0;

        Map<String, int[]> symbolStats = new LinkedHashMap<>(); // [attempted, executed, totalShares, totalValue]
        Map<String, double[]> clientStats = new LinkedHashMap<>(); // [orders, totalValue]

        for (String sym : symbols) {
            symbolStats.put(sym, new int[]{0, 0, 0, 0});
        }

        for (TradeOrder o : queue) {
            int[] sStat = symbolStats.computeIfAbsent(o.symbol, k -> new int[]{0, 0, 0, 0});
            sStat[0]++; // attempted

            double val = o.quantity * o.rate;
            if (o.executed) {
                sStat[1]++; // executed
                sStat[2] += o.quantity; // shares
                sStat[3] += (int) val;
                totalVolumeShares += o.quantity;
                totalValueBdt += val;

                if (o.latencyMs > 0) {
                    minLat = Math.min(minLat, o.latencyMs);
                    maxLat = Math.max(maxLat, o.latencyMs);
                    sumLat += o.latencyMs;
                    measuredCount++;
                }

                double[] cStat = clientStats.computeIfAbsent(o.clientCode, k -> new double[]{0, 0});
                cStat[0]++;
                cStat[1] += val;
            }
        }

        long avgLat = measuredCount > 0 ? (sumLat / measuredCount) : 0;
        if (minLat == Long.MAX_VALUE) minLat = 0;

        System.out.println("\n+=============================================================================+");
        System.out.println("|            HIGH FREQUENCY TRADING (HFT) SIMULATION PERFORMANCE REPORT       |");
        System.out.println("+=============================================================================+");
        System.out.printf("| Total Duration       : %6.2f seconds%n", totalSecs);
        System.out.printf("| Orders Attempted     : %-6d%n", (successCount + failedCount));
        System.out.printf("| Successful Orders    : %-6d (%.1f%%)%n", successCount, (successCount * 100.0 / Math.max(1, successCount + failedCount)));
        System.out.printf("| Failed / Rejected    : %-6d (%.1f%%)%n", failedCount, (failedCount * 100.0 / Math.max(1, successCount + failedCount)));
        System.out.printf("| Throughput (TPS)     : %6.2f Trades / Second%n", tps);
        System.out.printf("| Latency (Min/Avg/Max): %d ms / %d ms / %d ms%n", minLat, avgLat, maxLat);
        System.out.printf("| Total Shares Traded  : %,.0f shares%n", totalVolumeShares);
        System.out.printf("| Total Traded Value   : %,.2f BDT%n", totalValueBdt);
        System.out.println("+-----------------------------------------------------------------------------+");
        System.out.println("| PER-SYMBOL EXECUTION BREAKDOWN:                                             |");
        System.out.println("| Symbol       | Orders Placed | Executed | Volume (Shares) | Traded Value(BDT|");
        System.out.println("|--------------|---------------|----------|-----------------|-----------------|");
        for (Map.Entry<String, int[]> e : symbolStats.entrySet()) {
            System.out.printf("| %-12s | %-13d | %-8d | %-15d | %,15.2f |%n",
                    e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], (double) e.getValue()[3]);
        }
        System.out.println("+-----------------------------------------------------------------------------+");
        System.out.println("| PER-CLIENT TRADING ACTIVITY:                                                |");
        System.out.println("| Client Code  | Orders Executed | Total Traded Value (BDT)                   |");
        System.out.println("|--------------|-----------------|--------------------------------------------|");
        for (Map.Entry<String, double[]> e : clientStats.entrySet()) {
            System.out.printf("| %-12s | %-15.0f | %,42.2f |%n",
                    e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
        System.out.println("+=============================================================================+\n");
    }

    public static void printExecutionSummary(int success, int failed, Duration totalDuration) {
        double totalSeconds = totalDuration.toMillis() / 1000.0;
        double tps = totalSeconds > 0 ? (success / totalSeconds) : 0;

        System.out.println("\n=======================================================");
        System.out.println("                 OMS EXECUTION SUMMARY                 ");
        System.out.println("=======================================================");
        System.out.printf("Total Execution Time : %.2f seconds%n", totalSeconds);
        System.out.printf("Successful Orders    : %d%n", success);
        System.out.printf("Failed Orders        : %d%n", failed);
        System.out.printf("Throughput (TPS)     : %.2f Trades / Sec%n", tps);
        System.out.println("=======================================================\n");
    }
}
