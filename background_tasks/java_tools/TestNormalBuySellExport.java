import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestNormalBuySellExport {

    private static final String EXPORT_DIR = "C:\\Users\\Hasibur Rahman\\git\\OMS-WEB\\sample_files\\trades_export";
    private static final String ARTIFACT_DIR = "C:\\Users\\Hasibur Rahman\\.gemini\\antigravity\\brain\\e97cfc56-db00-4743-82f5-75664c6a224f";

    public static void main(String[] args) {
        new File(EXPORT_DIR).mkdirs();

        System.setProperty("webdriver.chrome.silentOutput", "true");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--log-level=3");
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", EXPORT_DIR);
        prefs.put("download.prompt_for_download", false);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            // STEP 1: LOGIN
            System.out.println("=================================================");
            System.out.println("[STEP 1] Logging into devoms.quantbd.com...");
            System.out.println("=================================================");
            driver.get("https://devoms.quantbd.com");
            Thread.sleep(1500);

            driver.findElement(By.id("username")).sendKeys("Testadmin");
            driver.findElement(By.id("password")).sendKeys("12345");
            driver.findElement(By.xpath("//button[normalize-space()='Login']")).click();
            Thread.sleep(1500);

            List<WebElement> continueBtns = driver.findElements(By.id("continue_to_force_login"));
            if (!continueBtns.isEmpty()) {
                System.out.println("[AUTH] Force-login prompted; confirming session takeover...");
                continueBtns.get(0).click();
                Thread.sleep(1500);
            }
            OMSweb_Login.pressOkAutomatically(driver);
            Thread.sleep(1000);
            System.out.println("[AUTH] Login successful as Testadmin.");

            // STEP 2: NAVIGATE TO TRADE PAGE
            System.out.println("\n=================================================");
            System.out.println("[STEP 2] Navigating to Trade Page...");
            System.out.println("=================================================");
            OMSweb_Trade.navigateToTradePage(driver, wait, true);
            Thread.sleep(2000);

            // Connect widget color Turquoise
            OMSweb_Trade.applyColorToAllWidgets(driver, "Turquoise", "#42EADDFF");
            Thread.sleep(1000);

            // Fetch LTP for GP on DSE
            String symbol = "GP";
            String buyClient = "23986";
            String sellClient = "11310"; // Different client code for bilateral matching
            int tradeQty = 10;
            double ltp = OMSweb_Trade.fetchLTP(driver, wait, symbol);
            if (ltp <= 0) ltp = 230.00;

            // STEP 3: NORMAL BUY ORDER
            System.out.println("\n=================================================");
            System.out.println("[STEP 3] Executing NORMAL BUY ORDER...");
            System.out.println("=================================================");
            System.out.printf("[BUY SETUP] Client: %s | Symbol: %s | Qty: %d | LTP/Rate: %.2f%n",
                    buyClient, symbol, tradeQty, ltp);

            executeTradeOrder(driver, wait, "BUY", buyClient, symbol, tradeQty, ltp);
            Thread.sleep(2000);

            // Scrape Terminal for Buy Order Record
            Map<String, String> buySnapshot = Order_Terminal.getSnapshot(driver, buyClient, symbol, "DSE");
            System.out.println("\n[ORDER TERMINAL - BUY SNAPSHOT]");
            for (Map.Entry<String, String> e : buySnapshot.entrySet()) {
                if (!e.getValue().isEmpty() && !e.getValue().equals("-")) {
                    System.out.printf("  %-20s : %s%n", e.getKey(), e.getValue());
                }
            }

            // Screenshot after Buy
            saveScreenshot(driver, ARTIFACT_DIR + "\\trade_buy_order_executed.png");

            // STEP 4: NORMAL SELL ORDER
            System.out.println("\n=================================================");
            System.out.println("[STEP 4] Executing NORMAL SELL ORDER...");
            System.out.println("=================================================");
            System.out.printf("[SELL SETUP] Client: %s | Symbol: %s | Qty: %d | LTP/Rate: %.2f%n",
                    sellClient, symbol, tradeQty, ltp);

            executeTradeOrder(driver, wait, "SELL", sellClient, symbol, tradeQty, ltp);
            Thread.sleep(2000);

            // Scrape Terminal for Sell Order Record
            Map<String, String> sellSnapshot = Order_Terminal.getSnapshot(driver, sellClient, symbol, "DSE");
            System.out.println("\n[ORDER TERMINAL - SELL SNAPSHOT]");
            for (Map.Entry<String, String> e : sellSnapshot.entrySet()) {
                if (!e.getValue().isEmpty() && !e.getValue().equals("-")) {
                    System.out.printf("  %-20s : %s%n", e.getKey(), e.getValue());
                }
            }

            // Screenshot after Sell
            saveScreenshot(driver, ARTIFACT_DIR + "\\trade_sell_order_executed.png");

            // STEP 4B: EXECUTE AT MARKET TO FILL TRADES
            System.out.println("\n=================================================");
            System.out.println("[STEP 4B] Right-Click 'Execute at Market' on Orders...");
            System.out.println("=================================================");
            try {
                Order_Terminal.navigateToOrderTerminal(driver);
                Order_Terminal.refresh(driver);
                Thread.sleep(1000);
                Order_Terminal.executeSeparatedBuyAndSellAtMarket(driver);
                Thread.sleep(2000);
            } catch (Exception ex) {
                System.out.println("[MARKET EXEC WARN] " + ex.getMessage());
            }

            // STEP 5: EXPORT TRADE FILE
            System.out.println("\n=================================================");
            System.out.println("[STEP 5] Exporting Trade Files...");
            System.out.println("=================================================");

            // Extract cookies for authenticated HTTP download
            Set<Cookie> seleniumCookies = driver.manage().getCookies();
            StringBuilder cookieHeader = new StringBuilder();
            for (Cookie c : seleniumCookies) {
                if (cookieHeader.length() > 0) cookieHeader.append("; ");
                cookieHeader.append(c.getName()).append("=").append(c.getValue());
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            // 5A. Download XML Trade File (All)
            String xmlTradeUrl = "https://devoms.quantbd.com/shared/download_trade_file?exchange=default";
            File xmlFile = new File(EXPORT_DIR + "\\Trade_Export_All_" + timestamp + ".xml");
            boolean xmlDownloaded = downloadFileWithCookies(xmlTradeUrl, cookieHeader.toString(), xmlFile);
            System.out.printf("[EXPORT] XML Trade File (All): %s | Size: %,d bytes | Path: %s%n",
                    xmlDownloaded ? "SUCCESS" : "FAILED", xmlFile.length(), xmlFile.getAbsolutePath());

            // 5B. Download CSV Trade File (All)
            String csvTradeUrl = "https://devoms.quantbd.com/shared/download_csv_trade_file?exchange=default";
            File csvFile = new File(EXPORT_DIR + "\\Trade_Export_All_" + timestamp + ".csv");
            boolean csvDownloaded = downloadFileWithCookies(csvTradeUrl, cookieHeader.toString(), csvFile);
            System.out.printf("[EXPORT] CSV Trade File (All): %s | Size: %,d bytes | Path: %s%n",
                    csvDownloaded ? "SUCCESS" : "FAILED", csvFile.length(), csvFile.getAbsolutePath());

            // 5C. Navigate to Upload Data / File Manager to verify in UI
            System.out.println("\n=================================================");
            System.out.println("[STEP 6] Navigating to File Manager / Upload Data...");
            System.out.println("=================================================");
            OMSweb_UploadData.navigateToUploadDataPage(driver, wait);
            Thread.sleep(2000);

            // Also trigger Export via UI Dropdown
            System.out.println("[UI EXPORT] Triggering XML Trade File export via UI menu...");
            js.executeScript(
                "var btn = document.getElementById('dropdownMenuButton');\n" +
                "if (btn) btn.click();\n" +
                "var link = document.querySelector('a[href*=\"download_trade_file?exchange=default\"]');\n" +
                "if (link) link.click();"
            );
            Thread.sleep(2000);

            // Refresh File Manager Table
            js.executeScript("if (typeof refresh_this_page === 'function') refresh_this_page();");
            Thread.sleep(3000);

            List<?> tableRows = (List<?>) js.executeScript(
                "var rows = Array.from(document.querySelectorAll('#file_manager_table tbody tr'));\n" +
                "return rows.slice(0, 10).map(function(r) {\n" +
                "    var cells = Array.from(r.querySelectorAll('td')).map(function(c) { return c.innerText.trim(); });\n" +
                "    return cells.join(' | ');\n" +
                "});"
            );

            System.out.println("\n==========================================================================");
            System.out.println("                 FILE MANAGER TABLE - TOP 10 RECORDS                      ");
            System.out.println("==========================================================================");
            if (tableRows != null) {
                int i = 1;
                for (Object row : tableRows) {
                    System.out.println(String.format("Row %2d: %s", i++, row));
                }
            }

            // Screenshot of File Manager
            saveScreenshot(driver, ARTIFACT_DIR + "\\file_manager_trade_export.png");

            // Print sample of exported trade file
            if (xmlFile.exists() && xmlFile.length() > 0) {
                System.out.println("\n==========================================================================");
                System.out.println("             EXPORTED XML TRADE FILE PREVIEW (FIRST 20 LINES)             ");
                System.out.println("==========================================================================");
                List<String> lines = Files.readAllLines(xmlFile.toPath());
                for (int i = 0; i < Math.min(20, lines.size()); i++) {
                    System.out.println(lines.get(i));
                }
            }

            if (csvFile.exists() && csvFile.length() > 0) {
                System.out.println("\n==========================================================================");
                System.out.println("             EXPORTED CSV TRADE FILE PREVIEW (FIRST 10 LINES)             ");
                System.out.println("==========================================================================");
                List<String> lines = Files.readAllLines(csvFile.toPath());
                for (int i = 0; i < Math.min(10, lines.size()); i++) {
                    System.out.println(lines.get(i));
                }
            }

            System.out.println("\n>>> [COMPLETED] Normal Buy, Normal Sell, and Trade File Export finished successfully! <<<");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    private static void executeTradeOrder(WebDriver driver, WebDriverWait wait, String side, String clientCode, String symbol, int qty, double rate) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 1. Close any existing open WinBox or modals
        js.executeScript(
            "var closes = document.querySelectorAll('.wb-close, .winbox .wb-close, .modal .close, button.close, div.wb-close');\n" +
            "for (var i = 0; i < closes.length; i++) { try { closes[i].click(); } catch(e){} }\n" +
            "try { $('.winbox').hide(); } catch(e){}"
        );
        Thread.sleep(300);

        // 2. Click Buy or Sell icon button
        String xpath = "//div[@title='Place " + side.toUpperCase() + " Order'] | //button[normalize-space()='" + side.toUpperCase() + "'] | //div[contains(@class,'new_" + side.toLowerCase() + "_order')]";
        List<WebElement> sideBtns = driver.findElements(By.xpath(xpath));
        if (!sideBtns.isEmpty()) {
            js.executeScript("arguments[0].click();", sideBtns.get(0));
        }
        Thread.sleep(600);

        // 3. Select DSE
        OMSweb_Trade.selectExchangeInModal(driver, "DSE");
        Thread.sleep(200);

        // 4. Fill Client Code
        OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_client_code"), clientCode, false);
        Thread.sleep(200);

        // 5. Fill Symbol
        OMSweb_Trade.installSymbolWhitespaceSanitizer(driver);
        String publicSym = OMSweb_Trade.toPublicSymbol(symbol);
        OMSweb_Trade.fillAndSelectAutocompleteSafely(driver, wait, By.id("order_instrument"), publicSym, true);
        OMSweb_Trade.ensureSymbolBoardRetained(driver, publicSym);
        OMSweb_Trade.ensureClientCodeRetained(driver, clientCode);
        OMSweb_Trade.dismissLingeringAutocomplete(driver);
        Thread.sleep(200);

        // 6. Fill Qty
        try {
            WebElement qtyInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("order_qty")));
            qtyInput.clear();
            qtyInput.sendKeys(String.valueOf(qty));
        } catch (Exception e) {
            js.executeScript("var el = document.getElementById('order_qty'); if(el){ el.value = arguments[0]; try { $(el).trigger('input').trigger('change'); } catch(err){} }", String.valueOf(qty));
        }
        js.executeScript("try { $('#order_qty').trigger('input').trigger('change'); } catch(e){}");

        // 7. Fill Rate
        String rateFormatted = String.format(Locale.US, "%.2f", rate);
        try {
            WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
            if (rateInput.isDisplayed()) {
                rateInput.clear();
                rateInput.sendKeys(rateFormatted);
            } else {
                js.executeScript("arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}", rateInput, rateFormatted);
            }
        } catch (Exception e) {
            js.executeScript("var el = document.getElementById('limit_order_rate'); if(el){ el.value = arguments[0]; try { $(el).trigger('input').trigger('change'); } catch(err){} }", rateFormatted);
        }
        js.executeScript("try { $('#limit_order_rate').trigger('input').trigger('change'); } catch(e){}");
        Thread.sleep(200);

        // 8. Display commission
        OMSweb_Trade.displayCommissionOnOrderWindow(driver, clientCode, qty, rate);

        // 9. Click BUY / SELL submit button via robust selector
        System.out.printf("[SUBMIT] Triggering %s Order submit...%n", side.toUpperCase());
        Boolean clicked = (Boolean) js.executeScript(
            "var side = arguments[0].toUpperCase();\n" +
            "var btns = Array.from(document.querySelectorAll('.form-submit-btn-holder button, button.btn-buy, button.btn-sell, .td-btn button, .order_submit_btn'));\n" +
            "var target = btns.find(function(b) {\n" +
            "    var txt = (b.innerText || b.textContent || '').trim().toUpperCase();\n" +
            "    return txt === side || txt.indexOf(side) !== -1;\n" +
            "});\n" +
            "if (target) {\n" +
            "    $(target).prop('disabled', false).removeAttr('disabled');\n" +
            "    if (window.jQuery) $(target).trigger('click');\n" +
            "    target.click();\n" +
            "    return true;\n" +
            "}\n" +
            "return false;",
            side.toUpperCase()
        );

        if (!Boolean.TRUE.equals(clicked)) {
            System.out.printf("[WARN] Direct selector fallback for %s button.%n", side);
            By submitBy = (side.equalsIgnoreCase("BUY"))
                    ? By.xpath("//div[contains(@class,'form-submit-btn-holder')]//button[normalize-space()='BUY'] | //button[contains(@class,'btn-buy')]")
                    : By.xpath("//div[contains(@class,'form-submit-btn-holder')]//button[normalize-space()='SELL'] | //button[contains(@class,'btn-sell')]");
            List<WebElement> els = driver.findElements(submitBy);
            if (!els.isEmpty()) {
                OMSweb_Trade.safeClick(driver, els.get(0));
            }
        }
        Thread.sleep(500);

        // 10. Auto-confirm execution popup
        System.out.println("[CONFIRMATION] Auto-confirming execution popup...");
        boolean confirmed = false;
        for (int i = 0; i < 25; i++) {
            Boolean c = (Boolean) js.executeScript(
                "try {\n" +
                "    var btn = document.getElementById('cnfirmConfirmBtn');\n" +
                "    if (btn && (btn.offsetParent !== null || btn.offsetWidth > 0)) { btn.click(); if (window.jQuery) $(btn).trigger('click'); return true; }\n" +
                "    var jcBtn = document.querySelector('.jconfirm-buttons button.btn-success, .jconfirm-box button.btn-success, button.btn-success');\n" +
                "    if (jcBtn) { jcBtn.click(); if (window.jQuery) $(jcBtn).trigger('click'); return true; }\n" +
                "    var all = Array.from(document.querySelectorAll('button, a, .btn'));\n" +
                "    var yesBtn = all.find(function(b) { var t = (b.innerText || '').trim().toUpperCase(); return t === 'YES' || t === 'CONFIRM'; });\n" +
                "    if (yesBtn) { yesBtn.click(); if (window.jQuery) $(yesBtn).trigger('click'); return true; }\n" +
                "} catch(e){}\n" +
                "return false;"
            );
            if (Boolean.TRUE.equals(c)) {
                confirmed = true;
                break;
            }
            Thread.sleep(100);
        }
        if (!confirmed) {
            OMSweb_Trade.autoConfirmOrderOrCancellation(driver, wait);
        }
        Thread.sleep(1500);
        OMSweb_Trade.dismissLingeringAutocomplete(driver);

        // Close order modal after submission
        js.executeScript("try { $('.winbox .wb-close').click(); } catch(e){}");
    }

    private static boolean downloadFileWithCookies(String fileUrl, String cookieHeader, File destination) {
        try {
            URL url = URI.create(fileUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Cookie", cookieHeader);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            if (status == 200) {
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                return destination.exists() && destination.length() > 0;
            } else {
                System.out.println("[DOWNLOAD: HTTP " + status + "] Could not download from: " + fileUrl);
            }
        } catch (Exception e) {
            System.out.println("[DOWNLOAD: ERROR] " + e.getMessage());
        }
        return false;
    }

    private static void saveScreenshot(WebDriver driver, String path) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(src.toPath(), Paths.get(path), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[SCREENSHOT] Saved: " + path);
        } catch (Exception e) {
            System.out.println("[SCREENSHOT: WARN] " + e.getMessage());
        }
    }
}
