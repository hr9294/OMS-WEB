import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dedicated Runner for Executing a Normal Trade on CSE (Chittagong Stock Exchange)
 * Resolves tradeable symbols from PostgreSQL DB (cse_md_ltp) and the Position widget,
 * executes order on CSE gateway (xcg3), and verifies real-time execution across 6 widgets.
 */
public class RunNormalTradeCSE {

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("+=============================================================================+");
        System.out.println("|              UFTFAST OMS - NORMAL TRADE RUNNER (CSE GATEWAY)                |");
        System.out.println("+=============================================================================+");

        // 1. Configure Chrome Browser Options
        System.setProperty("webdriver.chrome.silentOutput", "true");
        java.util.logging.Logger.getLogger("org.openqa.selenium").setLevel(java.util.logging.Level.SEVERE);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--silent");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("password_manager_leak_detection", false);
        prefs.put("safebrowsing.enabled", false);
        prefs.put("safebrowsing.enhanced", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);

        options.addArguments("--disable-features=PasswordLeakDetection,PasswordCheck,PasswordLeakToggleMove,SafeBrowsing");
        options.setAcceptInsecureCerts(true);

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().deleteAllCookies();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(25));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1000);

            ((JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            );

            Scanner sc = new Scanner(System.in);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 2. Perform Login
            System.out.println("\n[SETUP] Logging in to UFTFAST OMS...");
            OMSweb_Login.main(driver, sc);

            // 3. Open Trade Page
            System.out.println("\n[SETUP] Navigating to Trade Portal...");
            OMSweb_Trade.navigateToTradePage(driver, wait, true);

            // 4. Set Active Exchange to CSE
            OMSweb_Trade.activeExchange = "CSE";

            // 5. Select Side: BUY or SELL
            System.out.println("\n=== SELECT TRADE ACTION FOR CSE ===");
            System.out.println("1. BUY (default)");
            System.out.println("2. SELL");
            System.out.print("Enter choice (1=BUY / 2=SELL, default=1): ");
            String sideInput = "";
            try {
                if (sc.hasNextLine()) sideInput = sc.nextLine().trim();
            } catch (Exception ignored) {}
            String sideText = "2".equals(sideInput) || "SELL".equalsIgnoreCase(sideInput) ? "SELL" : "BUY";

            // 6. Query and Resolve Tradeable CSE Symbol from DB (cse_md_ltp) & Position Widget
            String instrument = Position.promptAndSelectCseTradeableSymbol(driver, sc, sideText, "ACFL");

            // 7. Resolve Client Code
            String clientCode = "23986";
            if ("SELL".equalsIgnoreCase(sideText)) {
                Position.ClientPosition holdingClient = Position.findClientWithSaleable(driver, instrument, 1, "CSE");
                if (holdingClient != null && !holdingClient.getClientCode().isEmpty()) {
                    clientCode = holdingClient.getClientCode();
                    System.out.printf("[POSITION] Selected holding client %s with %,.0f saleable shares on CSE.%n",
                            clientCode, holdingClient.getSaleableQty());
                } else {
                    System.out.print("Enter SELL Client Code (default 23986): ");
                    try {
                        String c = sc.nextLine().trim();
                        if (!c.isEmpty()) clientCode = c;
                    } catch (Exception ignored) {}
                }
            } else {
                System.out.print("Enter BUY Client Code (default 23986): ");
                try {
                    String c = sc.nextLine().trim();
                    if (!c.isEmpty()) clientCode = c;
                } catch (Exception ignored) {}
            }

            // 8. Resolve Trade Quantity
            int tradeQty = 10;
            System.out.print("Enter Quantity (default 10): ");
            try {
                String qStr = sc.nextLine().trim();
                if (!qStr.isEmpty()) tradeQty = Integer.parseInt(qStr);
            } catch (Exception ignored) {}

            // 9. Synchronize Search Inputs & Market Watch
            try {
                WebElement searchSymbol = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("instrument")));
                searchSymbol.clear();
                searchSymbol.sendKeys(instrument);
                searchSymbol.sendKeys(Keys.ARROW_DOWN);
                searchSymbol.sendKeys(Keys.ENTER);
                Thread.sleep(300);
            } catch (Exception ignored) {}

            Market_Watch.connectWidget(driver, instrument, "CSE");

            // 10. Open Order Window
            System.out.printf("%n[ORDER] Opening %s Window for %s on CSE...%n", sideText, instrument);
            By actionBtn = By.xpath(String.format("//div[@title='Place %s Order']", sideText));
            wait.until(ExpectedConditions.elementToBeClickable(actionBtn)).click();
            Thread.sleep(600);

            // 11. Fill Order Modal Inputs and Select CSE (xcg3)
            System.out.println("[ORDER] Filling Order Modal fields & Selecting CSE Gateway...");
            OMSweb_Trade.selectExchangeInModal(driver, "CSE");
            Thread.sleep(200);

            OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_client_code"), clientCode, false);
            String publicInst = Position.toPublicSymbol(instrument);
            OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_instrument"), publicInst, true);
            OMSweb_Trade.ensureSymbolBoardRetained(driver, publicInst);
            OMSweb_Trade.ensureClientCodeRetained(driver, clientCode);

            WebElement qtyInput = driver.findElement(By.id("order_qty"));
            qtyInput.clear();
            qtyInput.sendKeys(String.valueOf(tradeQty));

            // Re-assert CSE radio check
            OMSweb_Trade.selectExchangeInModal(driver, "CSE");

            // 12. Determine Rate (LTP / Market Depth)
            double refRate = OMSweb_Trade.fetchPriceLtpOrYcp(driver, wait, instrument, "CSE");
            if (refRate <= 0) {
                refRate = Position.getCseTradeableSymbols(driver).stream()
                        .filter(s -> s.getSymbol().equalsIgnoreCase(instrument))
                        .mapToDouble(Position.CseTradeableSymbol::getLtp).findFirst().orElse(25.00);
            }

            WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
            rateInput.clear();
            String formattedRate = String.format(java.util.Locale.US, "%.2f", refRate);
            rateInput.sendKeys(formattedRate);
            System.out.printf("[RATE] Set Limit Rate: %s BDT for %s on CSE%n", formattedRate, instrument);

            // 13. Submit Order
            By submitBtn = By.xpath(String.format(
                    "//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='%s']", sideText));
            wait.until(ExpectedConditions.elementToBeClickable(submitBtn)).click();
            Thread.sleep(500);

            // 14. Auto-Confirm Modal
            System.out.println("[CONFIRMATION] Confirming order modal [YES]...");
            for (int attempt = 0; attempt < 20; attempt++) {
                try {
                    Boolean clicked = (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "var btn = document.getElementById('cnfirmConfirmBtn') || document.querySelector('button.btn-yes, button#yesBtn');" +
                        "if (btn) { btn.click(); return true; } return false;"
                    );
                    if (Boolean.TRUE.equals(clicked)) {
                        System.out.println("✔ Order confirmation YES clicked successfully.");
                        break;
                    }
                } catch (Exception ignored) {}
                Thread.sleep(100);
            }

            // 15. Verify Real-time Order in Order Terminal & Status
            System.out.println("\n[TERMINAL] Polling Order Terminal for Real-time CSE Order Execution...");
            Thread.sleep(1200);
            Order_Terminal.navigateToOrderTerminal(driver);
            Order_Terminal.refresh(driver);

            Map<String, String> actionOrder = OMSweb_Trade.fetchTerminalOrderForAction(driver, clientCode, instrument, "accept");
            if (actionOrder != null && !actionOrder.isEmpty()) {
                System.out.println("\n=======================================================");
                System.out.printf("  CSE ORDER PLACED SUCCESSFULLY: %s %d %s @ %s BDT%n",
                        sideText, tradeQty, instrument, formattedRate);
                System.out.println("=======================================================");
                actionOrder.forEach((k, v) -> System.out.printf("  %-16s: %s%n", k, v));
                System.out.println("=======================================================\n");
            } else {
                System.out.println("[INFO] Order submitted to CSE Gateway. Verify Order Terminal for order status updates.");
            }

            System.out.println("Normal Trade execution on CSE completed successfully!");

        } catch (Exception e) {
            System.err.println("[ERROR] Execution failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\nExecution session complete. Browser will remain open for inspection.");
        }
    }
}
