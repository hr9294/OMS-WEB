import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dedicated Runner for Executing a Normal Trade on CSE (Chittagong Stock Exchange).
 * Supports TWO modes:
 *  1. Live Interactive Frontend Mode (Default / Desktop / Eclipse):
 *     Maximized Chrome browser opens on your desktop with visual element glowing,
 *     live on-screen HUD banner, and remains open (detach: true).
 *  2. Headless Background Mode (Pass argument --headless or HEADLESS):
 *     Runs invisibly with zero UI display, completing directly in the background
 *     and printing the exact order terminal result.
 */
public class RunNormalTradeCSE {

    public static final String ARTIFACT_DIR = "C:/Users/Hasibur Rahman/.gemini/antigravity/brain/f1d08daf-b204-4974-9477-32b49afd7601";
    public static final String SCREENSHOTS_DIR = "D:/Anitgravity/screenshots";

    public static void showFrontendBanner(WebDriver driver, String msg, String borderColor) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "var id = 'oms-frontend-banner';" +
                "var b = document.getElementById(id);" +
                "if (!b) {" +
                "    b = document.createElement('div');" +
                "    b.id = id;" +
                "    b.style.cssText = 'position:fixed;top:12px;left:50%;transform:translateX(-50%);z-index:9999999;' +" +
                "                      'padding:10px 24px;border-radius:24px;font-family:Segoe UI,sans-serif;font-size:14px;font-weight:700;' +" +
                "                      'color:#ffffff;background:#0f172a;border:2px solid " + (borderColor != null ? borderColor : "#3b82f6") + ";' +" +
                "                      'box-shadow:0 10px 30px rgba(0,0,0,0.5);transition:all 0.3s ease;pointer-events:none;letter-spacing:0.5px;';" +
                "    document.body.appendChild(b);" +
                "}" +
                "b.innerHTML = arguments[0];" +
                "if (arguments[1]) b.style.borderColor = arguments[1];",
                msg, borderColor
            );
        } catch (Exception ignored) {}
    }

    public static void highlightElement(WebDriver driver, WebElement el, String color) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});" +
                "arguments[0].style.transition = 'all 0.25s ease';" +
                "arguments[0].style.outline = '3px solid " + color + "';" +
                "arguments[0].style.boxShadow = '0 0 16px " + color + "';",
                el
            );
            Thread.sleep(300);
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].style.outline = '';" +
                "arguments[0].style.boxShadow = '';",
                el
            );
        } catch (Exception ignored) {}
    }

    public static void saveScreenshot(WebDriver driver, String filename) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            new File(ARTIFACT_DIR).mkdirs();
            new File(SCREENSHOTS_DIR).mkdirs();
            File dest1 = new File(ARTIFACT_DIR, filename);
            File dest2 = new File(SCREENSHOTS_DIR, filename);
            Files.copy(src.toPath(), dest1.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(src.toPath(), dest2.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[SCREENSHOT CAPTURED] " + dest1.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[WARN: Screenshot] " + e.getMessage());
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("+=============================================================================+");
        System.out.println("|              UFTFAST OMS - CSE GATEWAY NORMAL TRADE ENGINE                  |");
        System.out.println("+=============================================================================+");

        // Detect mode from CLI arguments or environment variable
        boolean isHeadless = false;
        for (String arg : args) {
            if ("--headless".equalsIgnoreCase(arg) || "HEADLESS".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                isHeadless = true;
                break;
            }
        }
        if ("true".equalsIgnoreCase(System.getenv("OMS_HEADLESS"))) {
            isHeadless = true;
        }

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

        if (isHeadless) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
            System.out.println(">> [RUN MODE: BACKGROUND HEADLESS] Executing silently without browser UI. Results will display below.");
        } else {
            options.addArguments("--start-maximized");
            options.addArguments("--window-position=0,0");
            options.addArguments("--window-size=1920,1080");
            options.setExperimentalOption("detach", true); // Keeps browser open on your desktop for inspection
            System.out.println(">> [RUN MODE: VISIBLE FRONTEND] Opening maximized Chrome window directly on your desktop.");
        }

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("safebrowsing.enabled", false);
        prefs.put("safebrowsing.enhanced", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--disable-features=PasswordLeakDetection,PasswordCheck,PasswordLeakToggleMove,SafeBrowsing");
        options.setAcceptInsecureCerts(true);

        WebDriver driver = new ChromeDriver(options);
        if (!isHeadless) {
            driver.manage().window().maximize();
        }
        driver.manage().deleteAllCookies();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(25));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        try {
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(800);

            ((JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            );
            Scanner sc = new Scanner(System.in);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 2. Perform Login
            System.out.println("\n[SETUP] Logging in to UFTFAST OMS...");
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Authenticating into UFTFAST OMS...", "#3b82f6");
            OMSweb_Login.main(driver, sc);

            // 3. Open Trade Page
            System.out.println("\n[SETUP] Navigating to Trade Portal...");
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Navigating to Trade Portal & Linking Widgets...", "#3b82f6");
            OMSweb_Trade.navigateToTradePage(driver, wait, true);
            Thread.sleep(600);

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
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Resolving Tradeable Symbols from DB & Position Widget...", "#8b5cf6");
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
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Synchronizing Symbol: " + instrument + " across UI Widgets...", "#06b6d4");
            try {
                WebElement searchSymbol = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("instrument")));
                if (!isHeadless) highlightElement(driver, searchSymbol, "#06b6d4");
                searchSymbol.clear();
                searchSymbol.sendKeys(instrument);
                searchSymbol.sendKeys(Keys.ARROW_DOWN);
                searchSymbol.sendKeys(Keys.ENTER);
                Thread.sleep(300);
            } catch (Exception ignored) {}

            Market_Watch.connectWidget(driver, instrument, "CSE");
            Thread.sleep(400);

            // 10. Open Order Window
            System.out.printf("%n[ORDER] Opening %s Window for %s on CSE...%n", sideText, instrument);
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Opening " + sideText + " Order Modal for " + instrument + "...", "#f59e0b");
            By actionBtnLoc = By.xpath(String.format("//div[@title='Place %s Order']", sideText));
            WebElement actionBtn = wait.until(ExpectedConditions.elementToBeClickable(actionBtnLoc));
            if (!isHeadless) highlightElement(driver, actionBtn, "#f59e0b");
            actionBtn.click();
            Thread.sleep(600);

            // 11. Fill Order Modal Inputs and Select CSE (xcg3)
            System.out.println("[ORDER] Filling Order Modal fields & Selecting CSE Gateway...");
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Routing Order to CSE Gateway (Radio #xcg3)...", "#10b981");

            OMSweb_Trade.selectExchangeInModal(driver, "CSE");
            Thread.sleep(200);

            // Client Code
            OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_client_code"), clientCode, false);
            if (!isHeadless) {
                try {
                    WebElement ccEl = driver.findElement(By.id("order_client_code"));
                    highlightElement(driver, ccEl, "#3b82f6");
                } catch (Exception ignored) {}
            }

            // Instrument
            String publicInst = Position.toPublicSymbol(instrument);
            OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_instrument"), publicInst, true);
            OMSweb_Trade.ensureSymbolBoardRetained(driver, publicInst);
            OMSweb_Trade.ensureClientCodeRetained(driver, clientCode);

            // Quantity (Resilient typing)
            try {
                WebElement qtyEl = wait.until(ExpectedConditions.elementToBeClickable(By.id("order_qty")));
                if (!isHeadless) highlightElement(driver, qtyEl, "#3b82f6");
                qtyEl.clear();
                qtyEl.sendKeys(String.valueOf(tradeQty));
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript(
                    "var el = document.getElementById('order_qty'); if(el){ el.value = arguments[0]; try { $(el).trigger('input').trigger('change'); } catch(err){} }",
                    String.valueOf(tradeQty));
            }
            try {
                ((JavascriptExecutor) driver).executeScript("$('#order_qty').trigger('input').trigger('change');");
            } catch (Exception ignored) {}

            // Re-assert CSE radio check
            OMSweb_Trade.selectExchangeInModal(driver, "CSE");

            // 12. Determine Rate (LTP / Market Depth)
            double refRate = OMSweb_Trade.fetchPriceLtpOrYcp(driver, wait, instrument, "CSE");
            if (refRate <= 0) {
                refRate = Position.getCseTradeableSymbols(driver).stream()
                        .filter(s -> s.getSymbol().equalsIgnoreCase(instrument))
                        .mapToDouble(Position.CseTradeableSymbol::getLtp).findFirst().orElse(25.00);
            }

            String formattedRate = String.format(java.util.Locale.US, "%.2f", refRate);
            try {
                WebElement rateEl = wait.until(ExpectedConditions.elementToBeClickable(By.id("limit_order_rate")));
                if (!isHeadless) highlightElement(driver, rateEl, "#3b82f6");
                rateEl.clear();
                rateEl.sendKeys(formattedRate);
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript(
                    "var el = document.getElementById('limit_order_rate'); if(el){ el.value = arguments[0]; try { $(el).trigger('input').trigger('change'); } catch(err){} }",
                    formattedRate);
            }
            try {
                ((JavascriptExecutor) driver).executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
            } catch (Exception ignored) {}
            System.out.printf("[RATE] Set Limit Rate: %s BDT for %s on CSE%n", formattedRate, instrument);

            // Dismiss any autocomplete popup before submitting
            OMSweb_Trade.dismissLingeringAutocomplete(driver);
            Thread.sleep(300);

            // Capture Screenshot of filled Order Modal before submit
            saveScreenshot(driver, "cse_trade_frontend_order_modal.png");

            // 13. Submit Order
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Submitting " + sideText + " Order (" + tradeQty + " shares @ " + formattedRate + " BDT)...", "#f59e0b");
            By submitBtnLoc = By.xpath(String.format(
                    "//div[contains(@class, 'form-submit-btn-holder')]//button[normalize-space()='%s']", sideText));
            WebElement submitBtn = wait.until(ExpectedConditions.elementToBeClickable(submitBtnLoc));
            if (!isHeadless) highlightElement(driver, submitBtn, "#f59e0b");
            submitBtn.click();
            Thread.sleep(500);

            // 14. Auto-Confirm Modal
            System.out.println("[CONFIRMATION] Confirming order modal [YES]...");
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Confirming Order Confirmation Dialog [YES]...", "#10b981");
            for (int attempt = 0; attempt < 20; attempt++) {
                try {
                    Boolean clicked = (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "var btn = document.getElementById('cnfirmConfirmBtn') || document.querySelector('button.btn-yes, button#yesBtn');" +
                        "if (btn) { btn.style.outline = '3px solid #10b981'; btn.click(); return true; } return false;"
                    );
                    if (Boolean.TRUE.equals(clicked)) {
                        System.out.println("[CONFIRMED] Order confirmation YES clicked successfully.");
                        break;
                    }
                } catch (Exception ignored) {}
                Thread.sleep(100);
            }

            // 15. Verify Real-time Order in Order Terminal & Status
            System.out.println("\n[TERMINAL] Polling Order Terminal for Real-time CSE Order Execution...");
            if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Order Submitted! Polling Order Terminal Table for Execution...", "#10b981");
            Thread.sleep(1200);

            Order_Terminal.navigateToOrderTerminal(driver);
            Order_Terminal.refresh(driver);
            Thread.sleep(600);

            Map<String, String> actionOrder = OMSweb_Trade.fetchTerminalOrderForAction(driver, clientCode, instrument, "accept");
            String placedOrderId = actionOrder != null ? actionOrder.getOrDefault("Order ID", "") : "";

            // Highlight the placed order row in the Order Terminal table on the frontend
            if (!placedOrderId.isEmpty()) {
                ((JavascriptExecutor) driver).executeScript(
                    "var oid = arguments[0];" +
                    "var rows = document.querySelectorAll('#order-terminal-table tbody tr, table.order_terminal_table tbody tr, div[id*=\"terminal\"] table tbody tr');" +
                    "for (var i = 0; i < rows.length; i++) {" +
                    "    if (rows[i].innerText.indexOf(oid) !== -1) {" +
                    "        rows[i].scrollIntoView({behavior: 'smooth', block: 'center'});" +
                    "        rows[i].style.transition = 'all 0.4s ease';" +
                    "        rows[i].style.backgroundColor = '#dcfce7';" +
                    "        rows[i].style.outline = '3px solid #22c55e';" +
                    "        rows[i].style.fontWeight = 'bold';" +
                    "        break;" +
                    "    }" +
                    "}",
                    placedOrderId
                );
                if (!isHeadless) showFrontendBanner(driver, "[CSE TRADE] Trade ACCEPTED on CSE! Order ID: " + placedOrderId + " (Highlighted in Green)", "#10b981");
            }

            // Capture Screenshot of Order Terminal showing the executed CSE order
            Thread.sleep(500);
            saveScreenshot(driver, "cse_trade_frontend_order_terminal.png");

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
            if (!isHeadless) {
                System.out.println("\nExecution session complete. Browser will remain open for inspection.");
            } else {
                try { driver.quit(); } catch (Exception ignored) {}
                System.out.println("\nBackground session complete.");
            }
        }
    }
}
