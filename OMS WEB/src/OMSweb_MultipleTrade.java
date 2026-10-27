import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OMSweb_MultipleTrade {

    // ==========================================
    // 1. DOM LOCATORS
    // ==========================================
    private static final By TRADE_PAGE_NAV        = By.xpath("//i[contains(@class, 'fa-exchange')]");
    private static final By SEARCH_INSTRUMENT_INP = By.id("instrument");
    private static final By MARKETWATCH_INPUT     = By.id("marketwatch_symbol");
    private static final By ORDER_WINDOW          = By.id("order-window");
    private static final By CLIENT_CODE_INPUT     = By.id("order_client_code");
    private static final By ORDER_INSTRUMENT_INP  = By.id("order_instrument");
    private static final By ORDER_QTY_INPUT       = By.id("order_qty");
    private static final By LIMIT_RATE_INPUT      = By.id("limit_order_rate");
    private static final By CONFIRM_YES_BUTTON    = By.xpath("//button[normalize-space()='YES']");
    private static final By AUTOCOMPLETE_MENU     = By.cssSelector(".ui-autocomplete");

    // ==========================================
    // 2. DOMAIN ENUM & DATA MODEL
    // ==========================================
    public enum OrderSide { BUY, SELL }

    public static class TradeOrder {
        final OrderSide side;
        final String clientCode;
        final String symbol;
        final int quantity;
        final double priceOffset;

        public TradeOrder(OrderSide side, String clientCode, String symbol, int quantity, double priceOffset) {
            this.side = side;
            this.clientCode = clientCode;
            this.symbol = symbol;
            this.quantity = quantity;
            this.priceOffset = priceOffset;
        }

        @Override
        public String toString() {
            return String.format("[%s] Client: %s | %s x %d (Offset: %.2f)", 
                side, clientCode, symbol, quantity, priceOffset);
        }
    }

    // ==========================================
    // 3. MAIN ENTRY POINT
    // ==========================================
    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(2));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 1. Navigate & Setup
        waitAndClick(wait, TRADE_PAGE_NAV);

        System.out.println("\n=======================================================");
        System.out.println("       HIGH-SPEED MULTI-TRADE & LOAD TESTING ENGINE    ");
        System.out.println("=======================================================");

        System.out.print("Search Symbol (default GP): ");
        String instrument = sc.nextLine().trim();
        if (instrument.isEmpty()) instrument = "GP";

        fillAndSelectAutocomplete(driver, wait, js, SEARCH_INSTRUMENT_INP, instrument);
        fillAndSelectAutocomplete(driver, wait, js, MARKETWATCH_INPUT, instrument);

        // 2. Load Testing Configuration & Pipeline Generation
        System.out.print("Enter Total Trades to Execute (default 500): ");
        String tradesInput = sc.nextLine().trim();
        int totalTrades = 500;
        try {
            if (!tradesInput.isEmpty()) totalTrades = Integer.parseInt(tradesInput);
        } catch (Exception ignored) {}

        System.out.print("Enter Max Duration budget in seconds (default 60s): ");
        String durInput = sc.nextLine().trim();
        int durationSecs = 60;
        try {
            if (!durInput.isEmpty()) durationSecs = Integer.parseInt(durInput);
        } catch (Exception ignored) {}
        Duration maxDuration = Duration.ofSeconds(durationSecs);

        double baseOffset = 0.1;
        System.out.printf("[LOAD TEST ENGINE] Configuring pipeline for %d trades of %s (Max Duration: %ds)...%n",
                totalTrades, instrument, durationSecs);

        List<TradeOrder> tradeQueue = buildTradePipeline(driver, instrument, totalTrades, baseOffset);

        // 3. High-Speed Execution Loop
        Instant sessionStart = Instant.now();
        int executedCount = 0;
        int failedCount = 0;

        System.out.println("\n>>> Starting Trade Engine [Max Duration: " + maxDuration.getSeconds() + "s] <<<");

        for (int i = 0; i < tradeQueue.size(); i++) {
            // Guard clause: Total time budget
            if (Duration.between(sessionStart, Instant.now()).compareTo(maxDuration) >= 0) {
                System.out.println("⏱ Max execution duration reached. Halting engine.");
                break;
            }

            TradeOrder order = tradeQueue.get(i);
            Instant tradeStart = Instant.now();

            try {
                // A. Open Order Modal
                By actionBtn = By.xpath(String.format("//div[@title='Place %s Order']", order.side.name()));
                waitAndClick(wait, actionBtn);

                // B. Client Code & Focus
                sendTextForcefully(driver, wait, js, CLIENT_CODE_INPUT, order.clientCode);
                waitAndClick(wait, ORDER_WINDOW);

                // C. Instrument Selection
                fillAndSelectAutocomplete(driver, wait, js, ORDER_INSTRUMENT_INP, order.symbol);

                // D. Quantity
                sendTextForcefully(driver, wait, js, ORDER_QTY_INPUT, String.valueOf(order.quantity));

                // E. Real-time LTP Extraction & Dynamic Price Calculation
                double ltp = OMSweb_Trade.fetchLTP(driver, wait, order.symbol);
                double limitRate = (order.side == OrderSide.BUY) 
                    ? (ltp - order.priceOffset) 
                    : (ltp + order.priceOffset);

                sendTextForcefully(driver, wait, js, LIMIT_RATE_INPUT, String.format("%.2f", limitRate));

                // Display Commission on Order Window if client has commission
                OMSweb_Trade.displayCommissionOnOrderWindow(driver, order.clientCode, order.quantity, limitRate);

                // F. Submit BUY / SELL
                By submitBtn = By.xpath(String.format(
                    "//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='%s']", 
                    order.side.name()
                ));
                waitAndClick(wait, submitBtn);

                // G. Confirmation Modal with Retry & JS Fallback
                confirmOrderWithRetry(driver, wait, js);

                executedCount++;
                long latency = Duration.between(tradeStart, Instant.now()).toMillis();
                System.out.printf("✔ [#%d] Placed: %s @ %.2f (LTP: %.2f) [%d ms]%n", 
                    executedCount, order, limitRate, ltp, latency);

                // H. Reset Form on BUY to prevent input residue
                if (order.side == OrderSide.BUY) {
                    resetOrderForm(driver, js);
                }

            } catch (Exception ex) {
                failedCount++;
                System.err.printf("✖ [Trade #%d Failed] %s - %s%n", (i + 1), order.side, ex.getMessage());
                resetOrderForm(driver, js); // Safe recovery
            }
        }

        // 4. Telemetry & Performance Summary
        printExecutionSummary(executedCount, failedCount, Duration.between(sessionStart, Instant.now()));
    }

    // ==========================================
    // 4. RESILIENT DRIVER UTILITIES
    // ==========================================

    private static void fillAndSelectAutocomplete(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, By locator, String text) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(locator));
        clearFieldForcefully(driver, js, locator);
        input.sendKeys(text);

        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(AUTOCOMPLETE_MENU));
        } catch (TimeoutException ignored) {
            // Dropdown may use a custom DOM class; proceed to key selection
        }
        input.sendKeys(Keys.ARROW_DOWN);
        input.sendKeys(Keys.ENTER);
    }

    private static void sendTextForcefully(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, By locator, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        clearFieldForcefully(driver, js, locator);
        element.sendKeys(value);
    }

    /**
     * Completely avoids getAttribute() calls while ensuring the input
     * is thoroughly wiped across React, Vue, Angular, or standard forms.
     */
    private static void clearFieldForcefully(WebDriver driver, JavascriptExecutor js, By locator) {
        try {
            WebElement elem = driver.findElement(locator);
            elem.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
            elem.clear();

            // Direct JS wipe with event dispatching (no getAttribute needed)
            js.executeScript(
                "arguments[0].value = '';" +
                "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                elem
            );
        } catch (NoSuchElementException ignored) {}
    }

    private static void waitAndClick(WebDriverWait wait, By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    private static void confirmOrderWithRetry(WebDriver driver, WebDriverWait wait, JavascriptExecutor js) {
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(CONFIRM_YES_BUTTON));
                btn.click();
                return;
            } catch (ElementClickInterceptedException | StaleElementReferenceException e) {
                if (attempt == maxAttempts - 1) {
                    WebElement btn = driver.findElement(CONFIRM_YES_BUTTON);
                    js.executeScript("arguments[0].click();", btn);
                    return;
                }
            }
        }
    }

    private static void resetOrderForm(WebDriver driver, JavascriptExecutor js) {
        clearFieldForcefully(driver, js, CLIENT_CODE_INPUT);
        clearFieldForcefully(driver, js, ORDER_INSTRUMENT_INP);
        clearFieldForcefully(driver, js, ORDER_QTY_INPUT);
        clearFieldForcefully(driver, js, LIMIT_RATE_INPUT);
    }

    // ==========================================
    // 5. DATA PIPELINE BUILDER
    // ==========================================
    private static List<TradeOrder> buildTradePipeline(WebDriver driver, String symbol, int totalTrades, double baseOffset) {
        String[] buyClients = {"10009", "10004", "10102", "10340", "10351"};
        int[] buyQtys = {10, 12, 15, 18, 20};

        String[] sellClients = null;
        if (driver != null) {
            try {
                Position.navigateToPosition(driver);
                Position.searchSymbolInPosition(driver, symbol, "DSE");
                List<Position.ClientPosition> holdingClients = Position.getClientsWithSaleable(driver, symbol, "DSE");
                if (holdingClients != null && !holdingClients.isEmpty()) {
                    sellClients = holdingClients.stream().map(Position.ClientPosition::getClientCode).toArray(String[]::new);
                    System.out.printf("[POSITION] Retrieved %d real client(s) with saleable %s from Position widget for multi-trade SELL side.%n",
                            sellClients.length, symbol);
                }
            } catch (Exception e) {
                System.out.println("[WARN: Position Multi-Trade] " + e.getMessage());
            }
            try {
                OMSweb_Trade.navigateToTradePage(driver, new WebDriverWait(driver, Duration.ofSeconds(6)));
            } catch (Exception ignored) {}
        }

        if (sellClients == null || sellClients.length == 0) {
            sellClients = new String[]{"11310", "12245", "14118", "19363", "26687"};
            System.out.println("[POSITION] Using default fallback clients for multi-trade SELL side.");
        }

        int[] sellQtys = {10, 12, 15, 18, 20};

        List<TradeOrder> queue = new ArrayList<>(totalTrades);
        for (int i = 0; i < totalTrades; i++) {
            OrderSide side = (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
            int idx = i / 2;
            double offset = baseOffset + (i % 10) * 0.10;

            String client = (side == OrderSide.BUY) 
                ? buyClients[idx % buyClients.length] 
                : sellClients[idx % sellClients.length];

            int qty = (side == OrderSide.BUY) 
                ? buyQtys[idx % buyQtys.length] 
                : sellQtys[idx % sellQtys.length];

            queue.add(new TradeOrder(side, client, symbol, qty, offset));
        }
        return queue;
    }

    // ==========================================
    // 6. METRICS TELEMETRY
    // ==========================================
    private static void printExecutionSummary(int success, int failed, Duration totalDuration) {
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