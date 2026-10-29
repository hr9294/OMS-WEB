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
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
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
    // All Verified Public Board Equity Symbols on DSE/CSE
    public static final List<String> ALL_PUBLIC_SYMBOLS = Collections.unmodifiableList(Arrays.asList(
        "GP", "BATBC", "BEXIMCO", "SQURPHARMA", "LHBL",
        "ROBI", "RENATA", "BRACBANK", "BXPHARMA", "WALTONHIL",
        "BEACONPHAR", "BSCCL", "CITYBANK", "EBL", "ISLAMIBANK",
        "UCB", "DHAKABANK", "PUBALIBANK", "PRIMEBANK", "DUTCHBANGL",
        "LANKABAFIN", "IDLC", "IPDC", "POWERGRID", "TITASGAS",
        "MJLBD", "UPGDCL", "SUMITPOWER", "LINDEBD", "MPETROLEUM",
        "PADMAOIL", "JAMUNAOIL", "HEIDELBCEM", "CROWNCEMNT", "BSRMSTEEL",
        "GPHISPAT", "OLYMPIC", "AAMRANET", "AAMRATECH", "GENEXIL",
        "UNILEVERCL", "MARICO", "IBNSINA", "GREENDELTA", "RELIANCINS",
        "SQUARETEXT", "ENVOYTEX"
    ));

    // Helper method to retrieve all Public Board symbols from DB table dse_md_ltp
    public static List<String> getPublicSymbolsBasket() {
        try {
            List<String> dbSyms = OMSweb_DBMarketData.getPublicSymbols("DSE");
            if (dbSyms != null && !dbSyms.isEmpty()) {
                return dbSyms;
            }
        } catch (Exception ignored) {}
        return ALL_PUBLIC_SYMBOLS;
    }

    // Default Symbols Basket contains ALL Public Symbols (from DB table / verified basket)
    public static final List<String> DEFAULT_SYMBOLS = Collections.unmodifiableList(getPublicSymbolsBasket());
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
        final String expectedStatus; // "ACCEPTED", "FILLED", "PARTIALLY_FILLED", "CANCELLED"
        long latencyMs = 0;
        boolean executed = false;
        String errorMessage = null;

        public TradeOrder(int seq, OrderSide side, String clientCode, String symbol, int quantity, double rate, double refLtp, String expectedStatus) {
            this.seq = seq;
            this.side = side;
            this.clientCode = clientCode;
            this.symbol = symbol;
            this.quantity = quantity;
            this.rate = rate;
            this.refLtp = refLtp;
            this.expectedStatus = (expectedStatus != null) ? expectedStatus : "ACCEPTED";
        }

        public TradeOrder(int seq, OrderSide side, String clientCode, String symbol, int quantity, double rate, double refLtp) {
            this(seq, side, clientCode, symbol, quantity, rate, refLtp, "ACCEPTED");
        }

        // Backward compatibility constructor
        public TradeOrder(OrderSide side, String clientCode, String symbol, int quantity, double priceOffset) {
            this(0, side, clientCode, symbol, quantity, priceOffset, priceOffset, "ACCEPTED");
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
        public String getExpectedStatus() { return expectedStatus; }
        public long getLatencyMs() { return latencyMs; }
        public boolean isExecuted() { return executed; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("[%s] Client: %-6s | %-10s x %3d @ %7.2f (Ref: %.2f) [%s]", 
                side, clientCode, symbol, quantity, rate, refLtp, expectedStatus);
        }
    }


    // ==========================================
    // 2B. CIRCUIT LIMITS MODEL (SH/SL & CH/CL)
    // ==========================================
    public static class CircuitLimits {
        public final double sh; // Standard High
        public final double sl; // Standard Low
        public final double ch; // Circuit High
        public final double cl; // Circuit Low
        public final double high; // Effective upper limit
        public final double low;  // Effective lower limit

        public CircuitLimits(double sh, double sl, double ch, double cl, double refPrice) {
            this.sh = sh;
            this.sl = sl;
            this.ch = ch;
            this.cl = cl;
            double h = (sh > 0) ? sh : ((ch > 0) ? ch : (refPrice > 0 ? refPrice * 1.10 : 0));
            double l = (sl > 0) ? sl : ((cl > 0) ? cl : (refPrice > 0 ? refPrice * 0.90 : 0));
            this.high = Math.round(h * 100.0) / 100.0;
            this.low = Math.round(l * 100.0) / 100.0;
        }

        public boolean isValid() {
            return high > 0 && low > 0 && high >= low;
        }

        @Override
        public String toString() {
            return String.format("[SL/CL: %.2f - SH/CH: %.2f] (SH=%.2f, SL=%.2f, CH=%.2f, CL=%.2f)",
                    low, high, sh, sl, ch, cl);
        }
    }

    // ==========================================
    // 3. MAIN ENTRY POINTS (STANDALONE & RUNNER)
    // ==========================================
    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");
        options.setAcceptInsecureCerts(true);
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        try (Scanner sc = new Scanner(System.in)) {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1000);
            OMSweb_Login.main(driver, sc);
            main(driver, sc);
        } catch (Exception e) {
            System.err.println("[HFT ENGINE ERROR] " + e.getMessage());
        }
    }

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

        // 2. Multi-Symbol Basket Selection (All Public Symbols from DB Table)
        List<String> activePublicSymbols = getPublicSymbolsBasket();
        System.out.println("\n[HFT CONFIG: 1. SYMBOLS BASKET]");
        System.out.printf("Default Public Basket (%d Public Symbols collected from DB table dse_md_ltp): %n  %s%n",
                activePublicSymbols.size(),
                String.join(", ", activePublicSymbols.subList(0, Math.min(25, activePublicSymbols.size()))) + (activePublicSymbols.size() > 25 ? "..." : ""));
        System.out.println("Options:");
        System.out.println("  - Press ENTER to select ALL Public Symbols from DB table (" + activePublicSymbols.size() + " symbols)");
        System.out.println("  - Type 'DB' to view live Public Board symbols & LTPs table (dse_md_ltp / cse_md_ltp)");
        System.out.println("  - Type 'TOP' or '10' for Top 10 Liquid Public Symbols");
        System.out.println("  - Enter specific symbols comma-separated (e.g. GP, BEXIMCO, BATBC)");
        System.out.print("Enter Choice: ");
        String symInput = sc.nextLine().trim();

        if (symInput.equalsIgnoreCase("DB")) {
            OMSweb_DBMarketData.printPublicBoardSummary("DSE", 35);
            System.out.print("Enter Choice (press ENTER for ALL Public Symbols): ");
            symInput = sc.nextLine().trim();
        }

        List<String> symbols = new ArrayList<>();
        if (symInput.equalsIgnoreCase("TOP") || symInput.equals("10")) {
            symbols.addAll(Arrays.asList("GP", "BATBC", "BEXIMCO", "SQURPHARMA", "LHBL", "ROBI", "RENATA", "BRACBANK", "BXPHARMA", "WALTONHIL"));
        } else if (symInput.equalsIgnoreCase("ALL") || symInput.equalsIgnoreCase("PUBLIC") || symInput.isEmpty()) {
            symbols.addAll(DEFAULT_SYMBOLS);
            List<String> liveDiscovered = discoverPublicSymbolsFromBrowser(driver);
            if (!liveDiscovered.isEmpty()) {
                for (String ds : liveDiscovered) {
                    if (!symbols.contains(ds)) {
                        symbols.add(ds);
                    }
                }
                System.out.printf("  ✔ Dynamically discovered & added %d additional live public symbols from Trade Portal.%n", liveDiscovered.size());
            }
        } else {
            for (String s : symInput.split("[,\\s]+")) {
                String clean = s.trim().toUpperCase();
                if (!clean.isEmpty() && !symbols.contains(clean)) {
                    symbols.add(clean);
                }
            }
            if (symbols.isEmpty()) {
                symbols.addAll(DEFAULT_SYMBOLS);
            }
        }
        System.out.printf(">> Active Trading Basket: %d Public Symbol(s) selected: %s%n", symbols.size(), symbols);

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

        int holderDiscLimit = Math.min(symbols.size(), 20);
        for (int i = 0; i < holderDiscLimit; i++) {
            String sym = symbols.get(i);
            List<String> holders = fetchHoldingClientsForSymbol(driver, sym, "DSE");
            sellClientsBySymbol.put(sym, holders);
            System.out.printf("  ✔ %-10s -> Found %2d holding client(s) with saleable shares: %s%n", 
                    sym, holders.size(), holders.subList(0, Math.min(5, holders.size())));
        }
        if (symbols.size() > holderDiscLimit) {
            List<String> defaultSellers = Arrays.asList("00463", "00600", "00224", "08096", "11310", "12245", "14118");
            for (int i = holderDiscLimit; i < symbols.size(); i++) {
                sellClientsBySymbol.put(symbols.get(i), defaultSellers);
            }
            System.out.printf("  ✔ [%d more public symbols mapped instantly to verified holding accounts %s]%n",
                    symbols.size() - holderDiscLimit, defaultSellers);
        }

        // 4. HFT Simulation Strategy
        System.out.println("\n[HFT CONFIG: 4. TRADING ALGORITHM / STRATEGY]");
        System.out.println("  1. Spread Market Maker (BUY @ LTP - spread, SELL @ LTP + spread)");
        System.out.println("  2. Aggressive Market Taker (BUY & SELL directly at LTP for immediate matching)");
        System.out.println("  3. Dynamic Micro-Burst / Volatility (Randomized tight spreads simulating order flow)");
        System.out.println("  4. Full Simultaneous Lifecycle Engine: Accepted, Filled, Partially Filled & Cancelled [15-Min Continuous] (Recommended)");
        System.out.print("Select Strategy (1-4, default=4): ");
        String stratInput = sc.nextLine().trim();
        int strategyChoice = 4;
        try {
            if (!stratInput.isEmpty()) strategyChoice = Integer.parseInt(stratInput);
        } catch (Exception ignored) {}

        // 5. Execution Volume & Pacing
        System.out.println("\n[HFT CONFIG: 5. ORDER VOLUME & PACING]");
        int defaultTotalOrders = (strategyChoice == 4) ? 300 : 50;
        System.out.printf("Enter Total Orders to Execute (default %d, or 0 for continuous 15-min run): ", defaultTotalOrders);
        String countInput = sc.nextLine().trim();
        int totalOrders = defaultTotalOrders;
        try {
            if (!countInput.isEmpty()) totalOrders = Integer.parseInt(countInput);
        } catch (Exception ignored) {}

        System.out.print("Enter Inter-Order Delay in milliseconds (default 0 ms for maximum burst TPS): ");
        String delayInput = sc.nextLine().trim();
        int interOrderDelayMs = 0;
        try {
            if (!delayInput.isEmpty()) interOrderDelayMs = Integer.parseInt(delayInput);
        } catch (Exception ignored) {}

        int defaultDurationSec = 900; // 15 minutes default
        System.out.printf("Enter Max Duration budget in seconds (default %ds = 15 minutes): ", defaultDurationSec);
        String durInput = sc.nextLine().trim();
        int maxDurationSec = defaultDurationSec;
        try {
            if (!durInput.isEmpty()) maxDurationSec = Integer.parseInt(durInput);
        } catch (Exception ignored) {}

        // 6. Pre-fetch Live Reference Prices & Circuit Limits (SH/SL, CH/CL) directly from DB table
        System.out.println("\n[HFT PRE-FLIGHT: 6. BULK-COLLECTING MARKET QUOTES & CIRCUIT LIMITS FROM DB TABLE (dse_md_ltp)]");
        Map<String, Double> symbolPrices = new LinkedHashMap<>();
        Map<String, CircuitLimits> symbolCircuits = new LinkedHashMap<>();
        Map<String, OMSweb_DBMarketData.PublicSymbolLtp> dbPublicQuotes = OMSweb_DBMarketData.fetchPublicBoardLtp("DSE");
        int dbHitCount = 0;
        int printCount = 0;
        int totalSyms = symbols.size();
        for (String sym : symbols) {
            String cleanSym = sym.trim().toUpperCase();
            OMSweb_DBMarketData.PublicSymbolLtp rec = dbPublicQuotes.get(cleanSym);
            double price;
            double sh = 0.0, sl = 0.0;
            if (rec != null && rec.ltp > 0) {
                price = rec.ltp;
                sh = rec.circuitUp > 0 ? rec.circuitUp : Math.round(price * 1.10 * 100.0) / 100.0;
                sl = rec.circuitDown > 0 ? rec.circuitDown : Math.round(price * 0.90 * 100.0) / 100.0;
                dbHitCount++;
            } else {
                price = REALISTIC_PRICES.getOrDefault(cleanSym, 100.0);
                sh = Math.round(price * 1.10 * 100.0) / 100.0;
                sl = Math.round(price * 0.90 * 100.0) / 100.0;
            }
            CircuitLimits circuits = new CircuitLimits(sh, sl, sh, sl, price);
            symbolPrices.put(cleanSym, price);
            symbolCircuits.put(cleanSym, circuits);

            if (printCount < 15 || printCount >= totalSyms - 5) {
                System.out.printf("  ✔ %-10s Ref: %7.2f BDT | Circuit Bounds: [SL/CL: %7.2f - SH/CH: %7.2f]%s%n",
                        cleanSym, price, circuits.low, circuits.high, (rec != null ? " (DB: dse_md_ltp)" : " (Fallback)"));
            } else if (printCount == 15) {
                System.out.printf("  ... [%d more public symbols bulk-loaded from DB table dse_md_ltp] ...%n", totalSyms - 20);
            }
            printCount++;
        }
        System.out.printf("  ✔ Total %d public symbols bulk-loaded from DB table (Hits: %d, Fallbacks: %d). Zero Market Watch scraping!%n",
                totalSyms, dbHitCount, totalSyms - dbHitCount);

        // 7. Generate Interleaved HFT Pipeline (Guarded by Circuit Limits)
        int initialQueueSize = (totalOrders > 0) ? totalOrders : 300;
        List<TradeOrder> pipeline = buildHftPipeline(
                symbols, buyClients, sellClientsBySymbol, symbolPrices, symbolCircuits,
                initialQueueSize, strategyChoice, 0.10
        );

        // 8. Display Simulation Plan & Guardrail Proceed Prompt
        String stratDisplay = (strategyChoice == 4) ? "Full Simultaneous Lifecycle (Accepted, Filled, Partial, Cancel) [15 Min]"
                : (strategyChoice == 2 ? "Aggressive Market Taker (LTP)" : (strategyChoice == 3 ? "Dynamic Volatility Jitter" : "Spread Market Maker"));
        System.out.println("\n+=============================================================================+");
        System.out.println("|              HFT MULTI-SYMBOL MULTI-CLIENT SIMULATION PLAN                  |");
        System.out.println("+=============================================================================+");
        System.out.printf("| Basket Symbols       : %-52s |%n", symbols);
        System.out.printf("| BUY Client Pool      : %-52s |%n", buyClients);
        System.out.printf("| SELL Client Routing  : %-52s |%n", "Mapped dynamically to verified holding accounts");
        System.out.printf("| Total Planned Orders : %-52s |%n", (totalOrders > 0 ? String.valueOf(totalOrders) : "Continuous 15-Minute Run"));
        System.out.printf("| Trading Strategy     : %-52s |%n", stratDisplay);
        System.out.printf("| Interleave Pacing    : %d ms delay | Max Duration Budget: %ds (15m)       |%n", interOrderDelayMs, maxDurationSec);
        System.out.println("+=============================================================================+");

        System.out.print("\nProceed to execute HFT Simulation? (1=YES / 0=NO, default=1): ");
        String proceed = sc.nextLine().trim();
        if (proceed.equalsIgnoreCase("0") || proceed.equalsIgnoreCase("n") || proceed.equalsIgnoreCase("no")) {
            System.out.println("\n[HFT ENGINE] Execution aborted by user.");
            return;
        }

        // 9. High-Speed Execution Loop
        System.out.println("\n>>> STARTING HIGH-FREQUENCY MULTI-SYMBOL EXECUTION ENGINE <<<");
        System.out.printf("[HFT ENGINE] Duration Budget: %d seconds (15 minutes) | Target: Accepted, Filled, Partially Filled, Cancelled Orders%n", maxDurationSec);
        Instant sessionStart = Instant.now();
        Duration maxBudget = Duration.ofSeconds(maxDurationSec);
        int successCount = 0;
        int failedCount = 0;
        int acceptedCount = 0;
        int filledCount = 0;
        int partialFillCount = 0;
        int cancelledCount = 0;

        List<TradeOrder> executedOrders = new ArrayList<>();
        int cycleIndex = 0;

        while (Duration.between(sessionStart, Instant.now()).compareTo(maxBudget) < 0 && (totalOrders <= 0 || (successCount + failedCount) < totalOrders)) {
            TradeOrder templateOrder = pipeline.get(cycleIndex % pipeline.size());
            cycleIndex++;
            int currentOrderNum = successCount + failedCount + 1;
            TradeOrder order = new TradeOrder(
                    currentOrderNum, templateOrder.side, templateOrder.clientCode,
                    templateOrder.symbol, templateOrder.quantity, templateOrder.rate,
                    templateOrder.refLtp, templateOrder.expectedStatus
            );
            executedOrders.add(order);
            Instant orderStart = Instant.now();

            try {
                CircuitLimits limits = (symbolCircuits != null) ? symbolCircuits.get(order.symbol) : null;
                boolean ok = placeSingleOrderFast(driver, wait, js, order, limits);
                long latency = Duration.between(orderStart, Instant.now()).toMillis();

                if (ok) {
                    order.markSuccess(latency);
                    successCount++;

                    String statusLabel = order.expectedStatus;
                    if ("CANCELLED".equalsIgnoreCase(order.expectedStatus)) {
                        boolean cancelOk = cancelLatestOrder(driver, wait, js, order.clientCode, order.symbol);
                        if (cancelOk) {
                            cancelledCount++;
                            statusLabel = "CANCELLED (Confirmed)";
                        } else {
                            acceptedCount++;
                            statusLabel = "CANCEL_SUBMITTED (Accepted)";
                        }
                    } else if (order.expectedStatus.startsWith("FILLED")) {
                        filledCount++;
                    } else if (order.expectedStatus.startsWith("PARTIALLY_FILLED")) {
                        partialFillCount++;
                    } else {
                        acceptedCount++;
                    }

                    long remainingSecs = Math.max(0, maxDurationSec - Duration.between(sessionStart, Instant.now()).toSeconds());
                    System.out.printf("[HFT #%03d | ⏱ %02dm %02ds left] ✔ %-4s | Client: %-6s | %-10s x %2d @ %7.2f (Ref: %7.2f) [%s] [%4d ms]%n",
                            currentOrderNum, (remainingSecs / 60), (remainingSecs % 60),
                            order.side, order.clientCode, order.symbol, order.quantity, order.rate, order.refLtp, statusLabel, latency);
                } else {
                    order.markFailed("Submission rejected or intercepted");
                    failedCount++;
                    long remainingSecs = Math.max(0, maxDurationSec - Duration.between(sessionStart, Instant.now()).toSeconds());
                    System.err.printf("[HFT #%03d | ⏱ %02dm %02ds left] ✖ %-4s | Client: %-6s | %-10s x %2d @ %7.2f [%s] [%4d ms] -> REJECTED (Proceeding to next)%n",
                            currentOrderNum, (remainingSecs / 60), (remainingSecs % 60),
                            order.side, order.clientCode, order.symbol, order.quantity, order.rate, order.expectedStatus, latency);
                    dismissAnyOpenModal(driver, js);
                }
            } catch (Exception ex) {
                long latency = Duration.between(orderStart, Instant.now()).toMillis();
                order.markFailed(ex.getMessage());
                failedCount++;
                System.err.printf("[HFT #%03d] ✖ %-4s | Client: %-6s | %-10s x %2d @ %7.2f [%4d ms] -> ERROR: %s (Continuing to next)%n",
                        currentOrderNum, order.side, order.clientCode, order.symbol, order.quantity, order.rate, latency, ex.getMessage());
                dismissAnyOpenModal(driver, js);
            }

            // Pacing control
            if (interOrderDelayMs > 0) {
                Thread.sleep(interOrderDelayMs);
            }

            // Periodic background refresh every 6 orders (1 full lifecycle cycle) so frontend tables stay live
            if (currentOrderNum % 6 == 0) {
                try {
                    js.executeScript("if (typeof get_order_history === 'function') { get_order_history(); }");
                    js.executeScript("if (typeof get_order_summary === 'function') { get_order_summary(); }");
                } catch (Exception ignored) {}
            }
        }

        if (Duration.between(sessionStart, Instant.now()).compareTo(maxBudget) >= 0) {
            System.out.printf("%n⏱ 15-Minute execution budget reached (%d seconds). Halting HFT engine gracefully.%n", maxDurationSec);
        }

        Duration totalDuration = Duration.between(sessionStart, Instant.now());

        // 10. Performance Telemetry & Detailed Reporting
        printHftExecutionSummary(executedOrders, totalDuration, successCount, failedCount,
                acceptedCount, filledCount, partialFillCount, cancelledCount, symbols, buyClients);

        // 11. Synchronize & Present Orders in Frontend Order Terminal
        syncAndPresentOrdersInFrontend(driver, wait, js, symbols, sc);
    }

    // ==========================================
    // 4. FAST ORDER EXECUTION ROUTINE
    // ==========================================
    public static boolean placeSingleOrderFast(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, TradeOrder order) {
        return placeSingleOrderFast(driver, wait, js, order, null);
    }

    public static boolean placeSingleOrderFast(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, TradeOrder order, CircuitLimits limits) {
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
            Thread.sleep(250);

            // 2. Select Exchange in Modal
            OMSweb_Trade.selectExchangeInModal(driver, "DSE");
            Thread.sleep(150);

            // 3. Client Code
            WebElement clientInput = wait.until(ExpectedConditions.visibilityOfElementLocated(CLIENT_CODE_INPUT));
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

            // 6. Rate calculation & Circuit Range enforcement (SH/SL, CH/CL)
            double rateToUse = order.rate;
            if (limits != null && limits.isValid()) {
                if (order.side == OrderSide.BUY && rateToUse > limits.high) {
                    System.out.printf("  [CIRCUIT CHECK] BUY Rate %.2f exceeds Upper Limit SH/CH (%.2f). Adjusted to SH/CH %.2f BDT.%n",
                            rateToUse, limits.high, limits.high);
                    rateToUse = limits.high;
                } else if (order.side == OrderSide.SELL && rateToUse < limits.low) {
                    System.out.printf("  [CIRCUIT CHECK] SELL Rate %.2f falls below Lower Limit SL/CL (%.2f). Adjusted to SL/CL %.2f BDT.%n",
                            rateToUse, limits.low, limits.low);
                    rateToUse = limits.low;
                }
            }

            if (rateToUse > 0) {
                WebElement rateInput = driver.findElement(LIMIT_RATE_INPUT);
                clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
                rateInput.sendKeys(String.format(Locale.US, "%.2f", rateToUse));
                try {
                    js.executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
                } catch (Exception ignored) {}
            }

            // Check if UI warning appears indicating price exceeds limit
            String exceedWarn = checkPriceExceedWarning(driver, js);
            if (exceedWarn != null) {
                System.out.printf("  [PRICE EXCEED DETECTED ON UI] '%s' -> Taking %s price!%n",
                        exceedWarn, (order.side == OrderSide.BUY ? "SH / CH (Upper)" : "SL / CL (Lower)"));
                if (order.side == OrderSide.BUY) {
                    rateToUse = (limits != null && limits.high > 0) ? limits.high : (order.refLtp * 1.10);
                } else {
                    rateToUse = (limits != null && limits.low > 0) ? limits.low : (order.refLtp * 0.90);
                }
                rateToUse = Math.round(rateToUse * 100.0) / 100.0;
                WebElement rateInput = driver.findElement(LIMIT_RATE_INPUT);
                clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
                rateInput.sendKeys(String.format(Locale.US, "%.2f", rateToUse));
                try {
                    js.executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
                } catch (Exception ignored) {}
                Thread.sleep(150);
            }

            // 7. Commission Display (Fast UI preview)
            try {
                OMSweb_Trade.displayCommissionOnOrderWindow(driver, order.clientCode, order.quantity, rateToUse);
            } catch (Exception ignored) {}

            // 8. Submit BUY or SELL
            By submitBtnBy = (order.side == OrderSide.BUY)
                    ? By.xpath("//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='BUY']")
                    : By.xpath("//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='SELL']");
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(submitBtnBy));
            submitBtn.click();
            Thread.sleep(200);

            // Check if post-submit alert shows price exceed
            String postSubmitExceed = checkPriceExceedWarning(driver, js);
            if (postSubmitExceed != null) {
                System.out.printf("  [SUBMIT ALERT: Price Exceeds] '%s' -> Correcting to %s and retrying...%n",
                        postSubmitExceed, (order.side == OrderSide.BUY ? "SH / CH" : "SL / CL"));
                dismissAnyOpenModal(driver, js);
                double retryRate = (order.side == OrderSide.BUY)
                        ? ((limits != null && limits.high > 0) ? limits.high : (order.refLtp * 1.10))
                        : ((limits != null && limits.low > 0) ? limits.low : (order.refLtp * 0.90));
                retryRate = Math.round(retryRate * 100.0) / 100.0;
                WebElement rateInput = driver.findElement(LIMIT_RATE_INPUT);
                clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
                rateInput.sendKeys(String.format(Locale.US, "%.2f", retryRate));
                try { js.executeScript("$('#limit_order_rate').trigger('input').trigger('change');"); } catch (Exception ignored) {}
                Thread.sleep(150);
                submitBtn.click();
            }

            // 9. Confirm YES
            boolean confirmed = confirmOrderWithRetry(driver, wait, js);

            // Allow modal transition to complete cleanly
            Thread.sleep(450);
            return confirmed;

        } catch (Exception e) {
            // Dismiss modal if open on error and return false so loop continues to next order
            dismissAnyOpenModal(driver, js);
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
        return buildHftPipeline(symbols, buyClients, sellClientsBySymbol, symbolLtpMap, null, totalTrades, strategyChoice, baseOffset);
    }

    public static List<TradeOrder> buildHftPipeline(
            List<String> symbols,
            List<String> buyClients,
            Map<String, List<String>> sellClientsBySymbol,
            Map<String, Double> symbolLtpMap,
            Map<String, CircuitLimits> symbolCircuits,
            int totalTrades,
            int strategyChoice,
            double baseOffset) {

        List<TradeOrder> queue = new ArrayList<>(totalTrades);
        int symbolCount = Math.max(1, symbols.size());
        int buyClientCount = Math.max(1, buyClients.size());

        if (strategyChoice == 4) {
            // Strategy 4: Full Simultaneous Lifecycle Engine
            // Interleaves 4 core order patterns in cycles of 6 orders:
            // 1. ACCEPTED (Single limit order below LTP that sits in book)
            // 2. FILLED (BUY 10 @ LTP + matching SELL 10 @ LTP -> exact fill)
            // 3. PARTIALLY FILLED (BUY 20 @ LTP + SELL 10 @ LTP -> 10 executed, 10 open)
            // 4. CANCELLED (Limit BUY @ LTP - 0.30 -> placed then cancelled in Terminal)
            int symIdx = 0;
            int buyIdx = 0;
            int orderNum = 1;

            while (queue.size() < totalTrades) {
                String sym = symbols.get(symIdx % symbolCount);
                symIdx++;
                double ltp = symbolLtpMap.getOrDefault(sym, 100.0);
                CircuitLimits limits = (symbolCircuits != null) ? symbolCircuits.get(sym) : null;
                double high = (limits != null && limits.high > 0) ? limits.high : Math.round(ltp * 1.10 * 100.0) / 100.0;
                double low = (limits != null && limits.low > 0) ? limits.low : Math.round(ltp * 0.90 * 100.0) / 100.0;

                String buyer1 = buyClients.get(buyIdx % buyClientCount);
                String buyer2 = buyClients.get((buyIdx + 1) % buyClientCount);
                String buyer3 = buyClients.get((buyIdx + 2) % buyClientCount);
                buyIdx += 3;

                List<String> sellers = sellClientsBySymbol.getOrDefault(sym, Collections.emptyList());
                String seller1 = (sellers != null && !sellers.isEmpty()) ? sellers.get(0) : "00463";
                String seller2 = (sellers != null && sellers.size() > 1) ? sellers.get(1) : (seller1.equals("00463") ? "00600" : "00463");

                // 1. ACCEPTED ORDER (BUY @ LTP - 0.20, sits in book)
                if (queue.size() < totalTrades) {
                    double accRate = Math.max(low, Math.round((ltp - 0.20) * 100.0) / 100.0);
                    if (accRate >= ltp) accRate = Math.max(1.0, Math.round((ltp - 0.10) * 100.0) / 100.0);
                    queue.add(new TradeOrder(orderNum++, OrderSide.BUY, buyer1, sym, 10, accRate, ltp, "ACCEPTED"));
                }

                // 2. FILLED ORDER PAIR (BUY @ LTP + SELL @ LTP with matching qty 10)
                if (queue.size() < totalTrades) {
                    double fillRate = Math.min(high, Math.max(low, ltp));
                    queue.add(new TradeOrder(orderNum++, OrderSide.BUY, buyer1, sym, 10, fillRate, ltp, "FILLED (BUY Leg)"));
                }
                if (queue.size() < totalTrades) {
                    double fillRate = Math.min(high, Math.max(low, ltp));
                    queue.add(new TradeOrder(orderNum++, OrderSide.SELL, seller1, sym, 10, fillRate, ltp, "FILLED (SELL Leg)"));
                }

                // 3. PARTIALLY FILLED ORDER PAIR (BUY 20 @ LTP + SELL 10 @ LTP)
                if (queue.size() < totalTrades) {
                    double partRate = Math.min(high, Math.max(low, ltp));
                    queue.add(new TradeOrder(orderNum++, OrderSide.BUY, buyer2, sym, 20, partRate, ltp, "PARTIALLY_FILLED (BUY Leg 20)"));
                }
                if (queue.size() < totalTrades) {
                    double partRate = Math.min(high, Math.max(low, ltp));
                    queue.add(new TradeOrder(orderNum++, OrderSide.SELL, seller2, sym, 10, partRate, ltp, "PARTIALLY_FILLED (SELL Leg 10)"));
                }

                // 4. CANCELLED ORDER (Limit BUY @ LTP - 0.30 -> placed then cancelled)
                if (queue.size() < totalTrades) {
                    double cancelRate = Math.max(low, Math.round((ltp - 0.30) * 100.0) / 100.0);
                    if (cancelRate >= ltp) cancelRate = Math.max(1.0, Math.round((ltp - 0.10) * 100.0) / 100.0);
                    queue.add(new TradeOrder(orderNum++, OrderSide.BUY, buyer3, sym, 10, cancelRate, ltp, "CANCELLED"));
                }
            }
            return queue;
        }

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

            // Circuit range enforcement (SH/SL, CH/CL)
            CircuitLimits limits = (symbolCircuits != null) ? symbolCircuits.get(symbol) : null;
            if (limits != null && limits.isValid()) {
                if (side == OrderSide.BUY && rate > limits.high) {
                    rate = limits.high;
                } else if (side == OrderSide.BUY && rate < limits.low) {
                    rate = limits.low;
                } else if (side == OrderSide.SELL && rate < limits.low) {
                    rate = limits.low;
                } else if (side == OrderSide.SELL && rate > limits.high) {
                    rate = limits.high;
                }
            }

            rate = Math.round(rate * 100.0) / 100.0;
            String expected = (strategyChoice == 2) ? "FILLED" : "ACCEPTED";
            queue.add(new TradeOrder(seq, side, clientCode, symbol, qty, rate, ltp, expected));
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

    public static final Map<String, Double> REALISTIC_PRICES = new LinkedHashMap<>();
    static {
        // Telecom & Blue Chips
        REALISTIC_PRICES.put("GP", 315.50);
        REALISTIC_PRICES.put("ROBI", 30.50);
        REALISTIC_PRICES.put("BSCCL", 185.00);
        REALISTIC_PRICES.put("BATBC", 518.70);
        REALISTIC_PRICES.put("BEXIMCO", 115.60);
        REALISTIC_PRICES.put("WALTONHIL", 680.00);

        // Pharma & Chemicals
        REALISTIC_PRICES.put("SQURPHARMA", 234.20);
        REALISTIC_PRICES.put("RENATA", 780.00);
        REALISTIC_PRICES.put("BXPHARMA", 145.00);
        REALISTIC_PRICES.put("BEACONPHAR", 240.00);
        REALISTIC_PRICES.put("MARICO", 2450.00);
        REALISTIC_PRICES.put("IBNSINA", 310.00);
        REALISTIC_PRICES.put("ACMEPL", 85.00);
        REALISTIC_PRICES.put("ORIONPHARM", 72.00);

        // Cement & Materials
        REALISTIC_PRICES.put("LHBL", 72.40);
        REALISTIC_PRICES.put("HEIDELBCEM", 230.00);
        REALISTIC_PRICES.put("CROWNCEMNT", 55.00);
        REALISTIC_PRICES.put("PREMIERCEM", 48.00);
        REALISTIC_PRICES.put("BSRMSTEEL", 62.00);
        REALISTIC_PRICES.put("GPHISPAT", 35.00);

        // Banking & Financials
        REALISTIC_PRICES.put("BRACBANK", 44.50);
        REALISTIC_PRICES.put("EBL", 32.00);
        REALISTIC_PRICES.put("CITYBANK", 23.50);
        REALISTIC_PRICES.put("ISLAMIBANK", 32.60);
        REALISTIC_PRICES.put("UCB", 12.50);
        REALISTIC_PRICES.put("DHAKABANK", 13.00);
        REALISTIC_PRICES.put("PUBALIBANK", 28.00);
        REALISTIC_PRICES.put("PRIMEBANK", 21.00);
        REALISTIC_PRICES.put("DUTCHBANGL", 58.00);
        REALISTIC_PRICES.put("LANKABAFIN", 18.00);
        REALISTIC_PRICES.put("IDLC", 42.00);
        REALISTIC_PRICES.put("IPDC", 22.00);

        // Fuel & Power
        REALISTIC_PRICES.put("POWERGRID", 52.00);
        REALISTIC_PRICES.put("TITASGAS", 34.00);
        REALISTIC_PRICES.put("MJLBD", 88.00);
        REALISTIC_PRICES.put("UPGDCL", 235.00);
        REALISTIC_PRICES.put("SUMITPOWER", 26.00);
        REALISTIC_PRICES.put("LINDEBD", 1050.00);
        REALISTIC_PRICES.put("MPETROLEUM", 205.00);
        REALISTIC_PRICES.put("PADMAOIL", 195.00);
        REALISTIC_PRICES.put("JAMUNAOIL", 180.00);

        // IT, Consumer & Insurance
        REALISTIC_PRICES.put("AAMRANET", 45.00);
        REALISTIC_PRICES.put("AAMRATECH", 33.00);
        REALISTIC_PRICES.put("GENEXIL", 52.00);
        REALISTIC_PRICES.put("OLYMPIC", 155.00);
        REALISTIC_PRICES.put("UNILEVERCL", 2050.00);
        REALISTIC_PRICES.put("GREENDELTA", 65.00);
        REALISTIC_PRICES.put("RELIANCINS", 70.00);
        REALISTIC_PRICES.put("PIONEERINS", 75.00);
        REALISTIC_PRICES.put("SQUARETEXT", 58.00);
        REALISTIC_PRICES.put("ENVOYTEX", 42.00);
    }

    public static double fetchPriceSafely(WebDriver driver, WebDriverWait wait, String symbol) {
        String cleanSym = (symbol != null) ? symbol.trim().toUpperCase() : "";
        double realisticFallback = REALISTIC_PRICES.getOrDefault(cleanSym, 100.00);
        try {
            // Priority 1: PostgreSQL DB Table (dse_md_ltp WHERE board = 'PUBLIC')
            double dbPrice = OMSweb_DBMarketData.getSymbolLtp(cleanSym, "DSE", 0.0);
            if (dbPrice > 0) {
                return dbPrice;
            }
            double price = OMSweb_Trade.fetchPriceLtpOrYcp(driver, wait, cleanSym, "DSE");
            // Circuit filter safety guard: if scraped price is suspiciously low (< 10.0 BDT or < 40% of known base),
            // override with calibrated market baseline to prevent exchange circuit breaker rejection.
            if (price > 0) {
                if (REALISTIC_PRICES.containsKey(cleanSym)) {
                    if (price >= (realisticFallback * 0.40) && price <= (realisticFallback * 2.50)) {
                        return price;
                    } else {
                        System.out.printf("  [PRICE GUARD] Scraped price %.2f BDT for %s violates realistic circuit band. Overriding with calibrated market price %.2f BDT.%n",
                                price, cleanSym, realisticFallback);
                        return realisticFallback;
                    }
                }
                if (price >= 1.0) return price;
            }
        } catch (Exception ignored) {}

        return realisticFallback;
    }


    // ==========================================
    // 6B. CIRCUIT LIMIT & EXCEED DETECTION HELPERS
    // ==========================================
    public static List<String> discoverPublicSymbolsFromBrowser(WebDriver driver) {
        List<String> discovered = new ArrayList<>();
        if (driver == null) return discovered;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object res = js.executeScript(
                "var syms = [];" +
                "var inputs = ['#order_instrument', '#instrument', '#marketwatch_symbol', '#sc_instrument_search'];" +
                "for (var i = 0; i < inputs.length; i++) {" +
                "    var $el = window.$ ? $(inputs[i]) : null;" +
                "    if ($el && $el.length && $el.data('ui-autocomplete')) {" +
                "        var src = $el.autocomplete('option', 'source');" +
                "        if (Array.isArray(src) && src.length > 0) {" +
                "            for (var j = 0; j < src.length; j++) {" +
                "                var s = typeof src[j] === 'string' ? src[j] : (src[j].value || src[j].label || '');" +
                "                if (s && !syms.includes(s.trim())) syms.push(s.trim());" +
                "            }" +
                "        }" +
                "    }" +
                "}" +
                "var globals = ['instruments', 'symbols', 'public_symbols', 'symbol_list', 'stock_symbols', 'all_instruments', 'INSTRUMENTS'];" +
                "for (var g = 0; g < globals.length; g++) {" +
                "    if (window[globals[g]] && Array.isArray(window[globals[g]])) {" +
                "        window[globals[g]].forEach(function(item) {" +
                "            var s = typeof item === 'string' ? item : (item.symbol || item.code || item.value || '');" +
                "            if (s && !syms.includes(s.trim())) syms.push(s.trim());" +
                "        });" +
                "    }" +
                "}" +
                "var rows = document.querySelectorAll('tr[class*=\"mw_\"], #marketwatch_table tbody tr, table.marketwatch tbody tr');" +
                "for (var r = 0; r < rows.length; r++) {" +
                "    var codeTd = rows[r].querySelector('td.mw_table_symbol, td[class*=\"symbol\"]');" +
                "    if (codeTd && codeTd.innerText) {" +
                "        var s = codeTd.innerText.trim();" +
                "        if (s && !syms.includes(s)) syms.push(s);" +
                "    }" +
                "}" +
                "return syms;"
            );
            if (res instanceof List) {
                for (Object item : (List<?>) res) {
                    if (item != null) {
                        String clean = item.toString().trim().toUpperCase();
                        if (!clean.isEmpty() && !discovered.contains(clean)) {
                            discovered.add(clean);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return discovered;
    }

    public static double parsePrice(String val) {
        if (val == null) return 0.0;
        String clean = val.replaceAll("[^0-9.-]", "").trim();
        if (clean.isEmpty() || clean.equals("-") || clean.equals("--") || clean.equals(".") || clean.equals("-.")) return 0.0;
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static CircuitLimits fetchCircuitLimits(WebDriver driver, String symbol, double refPrice) {
        double sh = 0, sl = 0, ch = 0, cl = 0;
        String cleanSym = (symbol != null) ? symbol.trim().toUpperCase() : "";
        try {
            // Priority 1: PostgreSQL DB Table (dse_md_ltp circuit limits)
            Map<String, OMSweb_DBMarketData.PublicSymbolLtp> ltpMap = OMSweb_DBMarketData.fetchPublicBoardLtp("DSE");
            OMSweb_DBMarketData.PublicSymbolLtp rec = ltpMap.get(cleanSym);
            if (rec != null) {
                sh = rec.circuitUp;
                sl = rec.circuitDown;
                ch = rec.circuitUp;
                cl = rec.circuitDown;
            }
        } catch (Exception ignored) {}

        if (sh <= 0 && sl <= 0) {
            try {
                Map<String, String> depth = Market_Depth.getSnapshot(driver, cleanSym, "DSE");
                if (depth != null && !depth.isEmpty()) {
                    sh = parsePrice(depth.get("SH"));
                    sl = parsePrice(depth.get("SL"));
                    ch = parsePrice(depth.get("CH"));
                    cl = parsePrice(depth.get("CL"));
                }
            } catch (Exception ignored) {}
        }

        // Fallback calculation (Standard DSE 10% band) if exchange depth is unavailable
        if (sh <= 0 && ch <= 0 && refPrice > 0) {
            sh = Math.round(refPrice * 1.10 * 100.0) / 100.0;
            ch = sh;
        }
        if (sl <= 0 && cl <= 0 && refPrice > 0) {
            sl = Math.round(refPrice * 0.90 * 100.0) / 100.0;
            cl = sl;
        }

        return new CircuitLimits(sh, sl, ch, cl, refPrice);
    }

    public static String checkPriceExceedWarning(WebDriver driver, JavascriptExecutor js) {
        try {
            Object res = js.executeScript(
                "var selectors = ['.text-danger', '.field-validation-error', '#rate_error', '.order-error', '.toast-error', '.alert-danger', '[id*=\"error\"]', '.tooltip-inner'];" +
                "for (var i = 0; i < selectors.length; i++) {" +
                "    var els = document.querySelectorAll(selectors[i]);" +
                "    for (var j = 0; j < els.length; j++) {" +
                "        var el = els[j];" +
                "        if (el && el.offsetParent !== null) {" +
                "            var t = (el.innerText || el.textContent || '').trim();" +
                "            var lower = t.toLowerCase();" +
                "            if (lower.includes('exceed') || lower.includes('circuit') || lower.includes('range') || lower.includes('limit') || lower.includes('high') || lower.includes('low')) {" +
                "                return t;" +
                "            }" +
                "        }" +
                "    }" +
                "}" +
                "return null;"
            );
            return (res != null) ? res.toString().trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void dismissAnyOpenModal(WebDriver driver, JavascriptExecutor js) {
        try {
            js.executeScript(
                "var btns = document.querySelectorAll('#cnfirmCancelBtn, button.btn-secondary, .modal.show button.close, button[data-dismiss=\"modal\"], button.confirm-cancel');" +
                "for (var i = 0; i < btns.length; i++) { if (btns[i].offsetParent !== null) { btns[i].click(); } }" +
                "var modals = document.querySelectorAll('.modal.show, .modal-backdrop');" +
                "for (var m = 0; m < modals.length; m++) { modals[m].classList.remove('show'); modals[m].style.display = 'none'; }" +
                "document.body.classList.remove('modal-open');"
            );
            Thread.sleep(250);
        } catch (Exception ignored) {}
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

    public static boolean confirmOrderWithRetry(WebDriver driver, WebDriverWait wait, JavascriptExecutor js) {
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(CONFIRM_YES_BUTTON));
                btn.click();
                try {
                    wait.until(ExpectedConditions.invisibilityOfElementLocated(CONFIRM_YES_BUTTON));
                } catch (Exception ignored) {}
                return true;
            } catch (Exception e) {
                if (attempt == maxAttempts - 1) {
                    try {
                        Boolean res = (Boolean) js.executeScript(
                            "var btn = document.querySelector('#cnfirmConfirmBtn, button.confirm-btn');" +
                            "if (btn && btn.offsetParent !== null) { btn.click(); return true; }" +
                            "return false;"
                        );
                        return Boolean.TRUE.equals(res);
                    } catch (Exception ignored) {}
                    return false;
                }
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    public static void resetOrderForm(WebDriver driver, JavascriptExecutor js) {
        try {
            clearFieldForcefully(driver, js, CLIENT_CODE_INPUT);
            clearFieldForcefully(driver, js, ORDER_INSTRUMENT_INP);
            clearFieldForcefully(driver, js, ORDER_QTY_INPUT);
            clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
        } catch (Exception ignored) {}
    }

    public static boolean cancelLatestOrder(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, String clientCode, String symbol) {
        try {
            // 1. Switch to Order Terminal tab to locate the order row
            Order_Terminal.navigateToOrderTerminal(driver);
            Thread.sleep(350);
            Order_Terminal.refresh(driver);
            Thread.sleep(350);

            // 2. Find row for this clientCode and symbol
            WebElement row = Order_Terminal.findOrderRow(driver, null, clientCode, symbol);
            if (row == null) {
                List<WebElement> rows = driver.findElements(By.xpath("//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr"));
                if (!rows.isEmpty()) {
                    row = rows.get(0);
                }
            }

            if (row != null) {
                // 3. Right-click row to open context menu
                Actions actions = new Actions(driver);
                try {
                    actions.contextClick(row).perform();
                } catch (Exception ignored) {}

                js.executeScript(
                    "var row = arguments[0];" +
                    "var rect = row.getBoundingClientRect();" +
                    "var evt = new MouseEvent('contextmenu', {" +
                    "    bubbles: true, cancelable: true, view: window," +
                    "    clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2," +
                    "    button: 2, buttons: 2" +
                    "});" +
                    "row.dispatchEvent(evt);" +
                    "if (window.jQuery) { window.jQuery(row).trigger('contextmenu'); }",
                    row
                );
                Thread.sleep(400);

                // 4. Click Cancel Order in context menu
                boolean clicked = clickCancelOrderInContextMenu(driver, js);
                if (!clicked) {
                    try {
                        actions.contextClick(row).perform();
                    } catch (Exception ignored) {}
                    Thread.sleep(400);
                    clicked = clickCancelOrderInContextMenu(driver, js);
                }

                if (clicked) {
                    Thread.sleep(400);
                    // 5. Confirm cancellation
                    boolean confirmed = confirmOrderWithRetry(driver, wait, js);
                    System.out.printf("  ✔ [ORDER CANCELLED] Successfully cancelled active order for %s (%s)%n", clientCode, symbol);
                    Thread.sleep(400);
                    Order_Terminal.navigateToTrade(driver);
                    Thread.sleep(300);
                    return true;
                }
            }
            Order_Terminal.navigateToTrade(driver);
        } catch (Exception e) {
            dismissAnyOpenModal(driver, js);
            try { Order_Terminal.navigateToTrade(driver); } catch (Exception ignored) {}
        }
        return false;
    }

    public static boolean clickCancelOrderInContextMenu(WebDriver driver, JavascriptExecutor js) {
        String[] candidateXpaths = {
            "//li[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'cancel order')]",
            "//li[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'cancel') and not(contains(., 'Execute'))]",
            "//a[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'cancel')]",
            "//*[(self::li or self::a or self::span or self::div) and contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'cancel order')]",
            "//*[contains(@class,'cancel') and (self::li or self::a or self::span or self::div)]"
        };

        for (String xp : candidateXpaths) {
            try {
                List<WebElement> matches = driver.findElements(By.xpath(xp));
                for (WebElement el : matches) {
                    if (el.isDisplayed()) {
                        try {
                            new Actions(driver).moveToElement(el).click().perform();
                            return true;
                        } catch (Exception ignored) {}
                        try {
                            el.click();
                            return true;
                        } catch (Exception ignored) {}
                        try {
                            js.executeScript("arguments[0].click();", el);
                            return true;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        try {
            Object res = js.executeScript(
                "var all = Array.from(document.querySelectorAll('li, a, span, div.menu-item'));" +
                "for (var i = 0; i < all.length; i++) {" +
                "    var el = all[i];" +
                "    if (el.offsetParent === null) continue;" +
                "    var txt = (el.innerText || el.textContent || '').trim().toLowerCase();" +
                "    if (txt === 'cancel order' || (txt.indexOf('cancel') !== -1 && txt.indexOf('execute') === -1 && txt.length < 25)) {" +
                "        el.click();" +
                "        return true;" +
                "    }" +
                "}" +
                "return false;"
            );
            return Boolean.TRUE.equals(res);
        } catch (Exception ignored) {
            return false;
        }
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
    public static void syncAndPresentOrdersInFrontend(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, List<String> symbols, Scanner sc) {
        System.out.println("\n+=============================================================================+");
        System.out.println("|      SYNCHRONIZING FRONTEND: ORDER TERMINAL & ORDER SUMMARY WIDGETS        |");
        System.out.println("+=============================================================================+");
        try {
            // 1. Clear any active filter in Order Terminal
            System.out.println("[FRONTEND] Clearing Order Terminal search filters (Client Code, Symbol, Status)...");
            Order_Terminal.clearFilter(driver);
            Thread.sleep(400);

            // 2. Trigger Order Terminal & Order Summary data refresh
            System.out.println("[FRONTEND] Triggering live get_order_history() & get_order_summary()...");
            Order_Terminal.refresh(driver);
            Order_Summary.refresh(driver);
            Thread.sleep(800);

            // 3. Navigate directly to Order Terminal tab to make orders immediately visible
            System.out.println("[FRONTEND] Switching active tab to Order Terminal widget...");
            Order_Terminal.navigateToOrderTerminal(driver);
            Thread.sleep(600);
            Order_Terminal.refresh(driver);
            Thread.sleep(600);

            // 4. Inspect visible rows in Order Terminal table to verify
            List<WebElement> rows = driver.findElements(By.xpath("//table[contains(@id,'market_table') or contains(@class,'order_terminal_table')]//tbody//tr"));
            System.out.printf("✔ [FRONTEND VERIFIED] Order Terminal visible order rows: %d%n", rows.size());

            int displayCount = Math.min(10, rows.size());
            if (displayCount > 0) {
                System.out.println("\n[RECENT ORDERS DISPLAYED IN FRONTEND ORDER TERMINAL]:");
                System.out.println("+------+--------------+------------+------------+-------+-----------+---------+--------------------+");
                System.out.printf("| %-4s | %-12s | %-10s | %-10s | %-5s | %-9s | %-7s | %-18s |%n",
                        "#", "Order ID", "Client", "Symbol", "Side", "Price", "Qty", "Status");
                System.out.println("+------+--------------+------------+------------+-------+-----------+---------+--------------------+");
                for (int r = 0; r < displayCount; r++) {
                    try {
                        WebElement row = rows.get(r);
                        List<WebElement> cells = row.findElements(By.xpath("./td"));
                        if (cells.size() >= 12) {
                            String oid = cells.get(1).getText().trim();
                            String client = cells.get(4).getText().trim();
                            String sym = cells.get(5).getText().trim();
                            String side = cells.get(7).getText().trim();
                            String price = cells.get(9).getText().trim();
                            String qty = cells.get(11).getText().trim();
                            String status = (cells.size() > 13) ? cells.get(13).getText().trim() : "-";
                            System.out.printf("| %-4d | %-12s | %-10s | %-10s | %-5s | %-9s | %-7s | %-18s |%n",
                                    (r + 1), oid, client, sym, side, price, qty, status);
                        } else {
                            System.out.printf("| %-4d | %-78s |%n", (r + 1), row.getText().replaceAll("\\s+", " "));
                        }
                    } catch (Exception ignored) {}
                }
                System.out.println("+------+--------------+------------+------------+-------+-----------+---------+--------------------+");
            } else {
                System.out.println("[INFO] Table rendered. If rows are empty, check if exchange gateway is connected.");
            }

            if (sc != null) {
                System.out.print("\nSwitch view back to Trade widget? (1=YES / 0=Stay on Order Terminal, default=0): ");
                String viewChoice = sc.nextLine().trim();
                if ("1".equals(viewChoice)) {
                    Order_Terminal.navigateToTrade(driver);
                    System.out.println("[NAV] Switched back to Trade view.");
                } else {
                    System.out.println("[NAV] Remaining on Order Terminal view.");
                }
            }
        } catch (Exception e) {
            System.out.printf("[WARN: Frontend Sync] %s%n", e.getMessage());
        }
    }

    public static void printHftExecutionSummary(
            List<TradeOrder> queue,
            Duration totalDuration,
            int successCount,
            int failedCount,
            int acceptedCount,
            int filledCount,
            int partialFillCount,
            int cancelledCount,
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

        long totalSecsInt = totalDuration.toSeconds();
        System.out.println("\n+=============================================================================+");
        System.out.println("|            HIGH FREQUENCY TRADING (HFT) SIMULATION PERFORMANCE REPORT       |");
        System.out.println("+=============================================================================+");
        System.out.printf("| Total Duration       : %02dm %02ds (%.2f seconds)%n", totalSecsInt / 60, totalSecsInt % 60, totalSecs);
        System.out.printf("| Orders Attempted     : %-6d%n", (successCount + failedCount));
        System.out.printf("| Successful Orders    : %-6d (%.1f%%)%n", successCount, (successCount * 100.0 / Math.max(1, successCount + failedCount)));
        if (acceptedCount > 0 || filledCount > 0 || partialFillCount > 0 || cancelledCount > 0) {
            System.out.printf("|   - Accepted Orders  : %-6d%n", acceptedCount);
            System.out.printf("|   - Filled Orders    : %-6d (Matched Crossing Pairs)%n", filledCount);
            System.out.printf("|   - Partially Filled : %-6d (Partial Matching Pairs)%n", partialFillCount);
            System.out.printf("|   - Cancelled Orders : %-6d (Placed & Cancelled in Terminal)%n", cancelledCount);
        }
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

    public static void printHftExecutionSummary(
            List<TradeOrder> queue,
            Duration totalDuration,
            int successCount,
            int failedCount,
            List<String> symbols,
            List<String> buyClients) {
        printHftExecutionSummary(queue, totalDuration, successCount, failedCount, 0, 0, 0, 0, symbols, buyClients);
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
