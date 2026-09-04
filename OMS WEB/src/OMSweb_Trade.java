import java.time.Duration;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OMSweb_Trade {

    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {

        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to Trade Page
        WebElement tradepage = driver.findElement(By.xpath("//i[@class='fa fa-exchange ']"));
        tradepage.click();
        Thread.sleep(300);

        // ---------- Auto input symbol into search + marketwatch ----------
        System.out.print("Enter Symbol: ");
        String instrument = sc.nextLine();

        WebElement searchSymbol = driver.findElement(By.id("instrument"));
        searchSymbol.clear();
        searchSymbol.sendKeys(instrument);
        searchSymbol.sendKeys(Keys.ARROW_DOWN);
        searchSymbol.sendKeys(Keys.ENTER);
        js.executeScript("arguments[0].scrollIntoView(true);", searchSymbol);
        Thread.sleep(500);

        WebElement marketWatchInput = driver.findElement(By.id("marketwatch_symbol"));
        marketWatchInput.clear();
        marketWatchInput.sendKeys(instrument);
        marketWatchInput.sendKeys(Keys.ARROW_DOWN);
        marketWatchInput.sendKeys(Keys.ENTER);
        js.executeScript("arguments[0].scrollIntoView(true);", marketWatchInput);
        Thread.sleep(500);

        // ---------- Optional: ask if user wants to select an Order Status type ----------
        System.out.print("\nDo you want to select an Order Status type? (1=YES / 0=NO, then ENTER): ");
        String wantStatus = sc.nextLine();

        if (wantStatus.trim().equals("1")) {
            selectOrderStatus(driver, wait, sc, instrument);
        } else {
            normalTradeFlow(driver, wait, sc, instrument);
        }
    }

    // ---------- STATUS SELECTION MENU ----------
    private static void selectOrderStatus(WebDriver driver, WebDriverWait wait, Scanner sc,
                                          String instrument) throws InterruptedException {

        System.out.println("\n=== Select Order Status Type ===");
        System.out.println("1. Accepted (normal order)");
        System.out.println("2. Partially Filled (buy then lower-qty sell, diff client)");
        System.out.println("3. Filled (both sides, diff client code)");
        System.out.println("4. Cancelled (right-click cancel)");
        System.out.println("5. Rejected (out of circuit price)");
        System.out.println("6. Expired (IOC order)");
        System.out.print("Please Enter your choice: ");
        int statusChoice = Integer.parseInt(sc.nextLine());

        switch (statusChoice) {

            case 1: // ACCEPTED = normal order (redirects back to buy/sell via loop)
                System.out.println("\n[Accepted] Placing normal order...");
                normalTradeFlow(driver, wait, sc, instrument);
                break;

            case 2:
                System.out.println("\n[Partially Filled] Buy then lower-qty sell...");
                placePartiallyFilledOrder(driver, wait, sc, instrument);
                break;

            case 3:
                System.out.println("\n[Filled] Placing both BUY & SELL (diff client code)...");
                placeFilledOrder(driver, wait, sc, instrument);
                break;

            case 4:
                System.out.println("\n[Cancelled] Placing order then cancelling...");
                placeCancelledOrder(driver, wait, sc, instrument);
                break;

            case 5:
                System.out.println("\n[Rejected] Placing order at out-of-circuit price...");
                placeRejectedOrder(driver, wait, sc, instrument);
                break;

            case 6:
                System.out.println("\n[Expired] Placing IOC order...");
                placeExpiredOrder(driver, wait, sc, instrument);
                break;

            default:
                System.out.println("Invalid status choice.");
                break;
        }
    }

    // ---------- NORMAL SINGLE TRADE FLOW (with Buy/Sell loop; redirects on Accepted) ----------
    private static void normalTradeFlow(WebDriver driver, WebDriverWait wait, Scanner sc,
                                        String instrument) throws InterruptedException {

        boolean keepTrading = true;

        while (keepTrading) {

            // ---------- Choose Buy or Sell (1=BUY, 2=SELL) ----------
            System.out.println("\nFor BUY, Please Enter: 1");
            System.out.println("For SELL, Please Enter: 2");
            System.out.print("Please Enter your choice: ");
            int int_side = Integer.parseInt(sc.nextLine());

            if (int_side == 1) {
                WebElement buy = driver.findElement(By.xpath("//div[@title='Place BUY Order']"));
                buy.click();
                System.out.println("Buy action performed.");
            } else if (int_side == 2) {
                WebElement sell = driver.findElement(By.xpath("//div[@title='Place SELL Order']"));
                sell.click();
                System.out.println("Sell action performed.");
            } else {
                System.out.println("Wrong value. Starting over.");
                continue;
            }
            Thread.sleep(200);

            // ---------- Auto input Client Code ----------
            System.out.print("Enter Client Code (or press Enter for default 10009): ");
            String clientCode = sc.nextLine();
            if (clientCode.trim().isEmpty()) {
                clientCode = "10009";
            }
            WebElement order_client_code = driver.findElement(By.id("order_client_code"));
            order_client_code.click();
            order_client_code.sendKeys(clientCode);
            System.out.println("Client Code entered: " + clientCode);
            Thread.sleep(200);

            // ---------- Auto input Symbol in order window ----------
            WebElement symbolInput = driver.findElement(By.id("order_instrument"));
            symbolInput.clear();
            symbolInput.sendKeys(instrument);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
            symbolInput.sendKeys(Keys.ARROW_DOWN);
            symbolInput.sendKeys(Keys.ENTER);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", symbolInput);
            System.out.println("Order Symbol selected: " + instrument);

            // ---------- Auto input Qty ----------
            System.out.print("Enter Qty (or press Enter for default 10): ");
            String qty = sc.nextLine();
            if (qty.trim().isEmpty()) {
                qty = "10";
            }
            WebElement qtyInput = driver.findElement(By.id("order_qty"));
            qtyInput.clear();
            qtyInput.sendKeys(qty);
            System.out.println("Qty entered: " + qty);

            
            
            // ---------- Handle Rate ----------
            String sideText = int_side == 1 ? "BUY" : "SELL";
            handleRate(driver, wait, sc, instrument, sideText);

            // ---------- Buy/Sell submit based on header ----------
            WebElement header = driver.findElement(By.xpath("//div[@class='wb-title']"));
            if (header.getText().toLowerCase().contains("buy")) {
                driver.findElement(By.xpath("//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='BUY']")).click();
            } else {
                driver.findElement(By.xpath("//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']")).click();
            }
            System.out.println("Clicked " + sideText + " button.");

            // ---------- Confirm ----------
            
            if (!confirmOrder(driver, wait, sc)) {
                continue; // order cancelled -> skip status & trade-again prompt
            }

            // ---------- After confirmation, ask to select an Order Status type ----------
            System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
            if (sc.nextLine().trim().equals("1")) {
                selectOrderStatus(driver, wait, sc, instrument);
            }

            // ---------- Ask if user wants to trade again (redirects to Buy/Sell) ----------
            System.out.print("Do you want to trade again? (1=YES / 0=NO): ");
            if (Integer.parseInt(sc.nextLine()) != 1) {
                keepTrading = false;
                System.out.println("Exiting trade.");
            }
        }
    }

    // ---------- PARTIALLY FILLED: buy then lower-qty sell with diff client code ----------
    private static void placePartiallyFilledOrder(WebDriver driver, WebDriverWait wait, Scanner sc,
                                                  String instrument) throws InterruptedException {
        System.out.print("Enter BUY qty for the partially-filled test: ");
        String buyQty = sc.nextLine();
        System.out.print("Enter SELL qty (should be LOWER than " + buyQty + "): ");
        String sellQty = sc.nextLine();

        // Place BUY order
        WebElement buyBtn = driver.findElement(By.xpath("//div[@title='Place BUY Order']"));
        buyBtn.click();
        Thread.sleep(200);
        System.out.print("Enter BUY client code (default 10009): ");
        String buyClientCode = sc.nextLine();
        if (buyClientCode.trim().isEmpty()) buyClientCode = "10009";
        fillOrderFields(driver, wait, instrument, buyQty, buyClientCode);
        enterRateAndSubmit(driver, wait, sc, instrument, "BUY");
        confirmOrder(driver, wait, sc);
        Thread.sleep(1000);

        // Place SELL order - lower qty, different client code
        WebElement sellBtn = driver.findElement(By.xpath("//div[@title='Place SELL Order']"));
        sellBtn.click();
        Thread.sleep(200);
        System.out.print("Enter SELL client code (different from buy, default 11310): ");
        String sellClientCode = sc.nextLine();
        if (sellClientCode.trim().isEmpty()) sellClientCode = "11310";
        fillOrderFields(driver, wait, instrument, sellQty, sellClientCode);
        enterRateAndSubmit(driver, wait, sc, instrument, "SELL");
        confirmOrder(driver, wait, sc);
        Thread.sleep(1000);
        System.out.println("Partially filled test: BUY " + buyQty + " / SELL " + sellQty + " (sell lower).");
    
        System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
        if (sc.nextLine().trim().equals("1")) {
            selectOrderStatus(driver, wait, sc, instrument);
        }
    }

    // ---------- FILLED: both sides, only client code differs ----------
    private static void placeFilledOrder(WebDriver driver, WebDriverWait wait, Scanner sc,
                                         String instrument) throws InterruptedException {
        System.out.print("Enter qty for the filled test: ");
        String qty = sc.nextLine();

        // BUY side
        WebElement buyBtn = driver.findElement(By.xpath("//div[@title='Place BUY Order']"));
        buyBtn.click();
        Thread.sleep(200);
        System.out.print("Enter BUY client code (default 10009): ");
        String buyClientCode = sc.nextLine();
        if (buyClientCode.trim().isEmpty()) buyClientCode = "10009";
        fillOrderFields(driver, wait, instrument, qty, buyClientCode);
        enterRateAndSubmit(driver, wait, sc, instrument, "BUY");
        confirmOrder(driver, wait, sc);
        Thread.sleep(2000);
        // SELL side - identical fields, different client code
        WebElement sellBtn = driver.findElement(By.xpath("//div[@title='Place SELL Order']"));
        sellBtn.click();
        Thread.sleep(200);
        System.out.print("Enter SELL client code (default 11310): ");
        String sellClientCode = sc.nextLine();
        if (sellClientCode.trim().isEmpty()) sellClientCode = "11310";
        fillOrderFields(driver, wait, instrument, qty, sellClientCode);
        enterRateAndSubmit(driver, wait, sc, instrument, "SELL");
        confirmOrder(driver, wait, sc);

        System.out.println("Filled test: BUY & SELL placed with different client codes.");
        
        System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
        if (sc.nextLine().trim().equals("1")) {
            selectOrderStatus(driver, wait, sc, instrument);
        }
    }

    // ---------- CANCELLED: place order then right-click cancel ----------
    private static void placeCancelledOrder(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
            throws InterruptedException {
        System.out.print("Enter side for cancel test (1=BUY / 2=SELL, default 1): ");
        String sideChoice = sc.nextLine();
        String side = sideChoice.trim().equals("2") ? "SELL" : "BUY";
        System.out.print("Enter qty (default 10): ");
        String qty = sc.nextLine();
        if (qty.trim().isEmpty()) qty = "10";
        System.out.print("Enter client code (default 10009): ");
        String clientCode = sc.nextLine();
        if (clientCode.trim().isEmpty()) clientCode = "10009";

        WebElement sideBtn = driver.findElement(By.xpath("//div[@title='Place " + side + " Order']"));
        sideBtn.click();
        Thread.sleep(200);

        // fillOrderFields prompts for symbol and returns it
         
        fillOrderFields(driver, wait, instrument, qty, clientCode);
        enterRateAndSubmit(driver, wait, sc,  instrument, side);
        confirmOrder(driver, wait, sc);

        // Wait for the order table to load
        Thread.sleep(1000);

        // Locate the FIRST row in the order terminal table (row 1)
        WebElement firstRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath("/html[1]/body[1]/div[3]/div[2]/div[1]/div[1]/div[1]/div[3]/div[1]/div[2]/div[1]/div[1]/div[3]/div[1]/table[1]/tbody[1]/tr[1]")));

        // Check/select the first row (click to select it)
        firstRow.click();
        Thread.sleep(300);
        System.out.println("First order row selected.");

        // Right-click on the first row to open the context menu
        new org.openqa.selenium.interactions.Actions(driver)
            .contextClick(firstRow)
            .perform();
        Thread.sleep(500);

        // Click the "Cancel" option in the context menu - try multiple locators
        String[] cancelXpaths = {
            "//li[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
            "//a[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
            "//div[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
            "//span[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
            "//*[contains(@class,'cancel')]",
            "//*[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]"
        };

        boolean cancelClicked = false;
        for (String xp : cancelXpaths) {
            try {
                WebElement cancelOption = driver.findElement(By.xpath(xp));
                if (cancelOption.isDisplayed()) {
                    cancelOption.click();
                    System.out.println("Cancel option clicked.");
                    cancelClicked = true;
                    break;
                }
            } catch (Exception ignored) {
                // try next locator
            }
        }

        if (!cancelClicked) {
            System.out.println("Could not find the Cancel option in the context menu.");
            new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform();
            return;
        }

        // Confirm the cancellation
        System.out.print("Confirm cancellation? (1=YES / 0=NO): ");
        if (Integer.parseInt(sc.nextLine()) == 1) {
            try {
                WebElement yesCancel = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[normalize-space()='YES']")));
                yesCancel.click();
                System.out.println("Cancellation confirmed.");
            } catch (Exception e) {
                System.out.println("No cancellation confirmation appeared.");
            }
        }
        Thread.sleep(1000);
    }
     

    // ---------- REJECTED: out of circuit price ----------
    private static void placeRejectedOrder(WebDriver driver, WebDriverWait wait, Scanner sc,
                                           String instrument) throws InterruptedException {
        System.out.print("Enter side (1=BUY / 2=SELL, default 1): ");
        String sideChoice = sc.nextLine();
        String side = sideChoice.trim().equals("2") ? "SELL" : "BUY";
        System.out.print("Enter client code (default 10009): ");
        String clientCode = sc.nextLine();
        if (clientCode.trim().isEmpty()) clientCode = "10009";
        System.out.print("Enter qty (default 10): ");
        String qty = sc.nextLine();
        if (qty.trim().isEmpty()) qty = "10";

        WebElement sideBtn = driver.findElement(By.xpath("//div[@title='Place " + side + " Order']"));
        sideBtn.click();
        Thread.sleep(200);
        fillOrderFields(driver, wait, instrument, qty, clientCode);

        // Fetch LTP then set an extreme price outside the circuit band
        double ltp = fetchLTP(driver, wait, instrument);
        double outOfCircuitRate = side.equalsIgnoreCase("BUY") ? ltp + 500 : ltp - 500;
        WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
        rateInput.clear();
        rateInput.sendKeys(String.format("%.2f", outOfCircuitRate));
        System.out.println("Set out-of-circuit rate: " + String.format("%.2f", outOfCircuitRate));

        submitOrder(driver, side);
        confirmOrder(driver, wait, sc);
        System.out.println("Order submitted at out-of-circuit price -> expected REJECTED.");
        
        System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
        if (sc.nextLine().trim().equals("1")) {
            selectOrderStatus(driver, wait, sc, instrument);
        }
    }

    // ---------- EXPIRED: IOC order ----------
    private static void placeExpiredOrder(WebDriver driver, WebDriverWait wait, Scanner sc,
                                          String instrument) throws InterruptedException {
        System.out.print("Enter side (1=BUY / 2=SELL, default 1): ");
        String sideChoice = sc.nextLine();
        String side = sideChoice.trim().equals("2") ? "SELL" : "BUY";
        System.out.print("Enter client code (default 10009): ");
        String clientCode = sc.nextLine();
        if (clientCode.trim().isEmpty()) clientCode = "10009";
        System.out.print("Enter qty (default 10): ");
        String qty = sc.nextLine();
        if (qty.trim().isEmpty()) qty = "10";

        WebElement sideBtn = driver.findElement(By.xpath("//div[@title='Place " + side + " Order']"));
        sideBtn.click();
        Thread.sleep(200);
        fillOrderFields(driver, wait, instrument, qty, clientCode);

        // Open "Advance options" and select IOC from the order validity section
        WebElement advanceOption = driver.findElement(By.xpath("//label[@for='trade_mode_checkbox']"));
        advanceOption.click();
        Thread.sleep(300);

        WebElement iocOption = driver.findElement(By.xpath("//select[@id='order_validity']//option[@value='IOC'][normalize-space()='IOC']"));
        iocOption.click();
        System.out.println("Selected IOC order validity.");

        enterRateAndSubmit(driver, wait, sc, instrument, side);
        confirmOrder(driver, wait, sc);
        System.out.println("IOC order placed -> expected EXPIRED if not filled immediately.");
        
        System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
        if (sc.nextLine().trim().equals("1")) {
            selectOrderStatus(driver, wait, sc, instrument);
        } 
    }

    // ---------- SHARED HELPERS ----------

    private static void fillOrderFields(WebDriver driver, WebDriverWait wait, String instrument,
                                        String qty, String clientCode) throws InterruptedException {
        WebElement client = driver.findElement(By.id("order_client_code"));
        client.click();
        client.sendKeys(clientCode);

        WebElement symbol = driver.findElement(By.id("order_instrument"));
        symbol.clear();
        symbol.sendKeys(instrument);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
        symbol.sendKeys(Keys.ARROW_DOWN);
        symbol.sendKeys(Keys.ENTER);

        WebElement qtyInput = driver.findElement(By.id("order_qty"));
        qtyInput.clear();
        qtyInput.sendKeys(qty);
        System.out.println("Fields filled: client=" + clientCode + ", symbol=" + instrument + ", qty=" + qty);
    }

    // Enters the rate for the current order: manual input by default, auto-fetch on ENTER
    private static void handleRate(WebDriver driver, WebDriverWait wait, Scanner sc,
                                   String instrument, String side) throws InterruptedException {
        WebElement rateInput = driver.findElement(By.id("limit_order_rate"));

        // Check if a rate is already present
        String rateValue = rateInput.getDomProperty("value");
        if (rateValue == null || rateValue.trim().isEmpty()) {
            rateValue = rateInput.getDomAttribute("value");
        }

        if (rateValue != null && !rateValue.trim().isEmpty()) {
            System.out.println("Rate already present: " + rateValue + " -> going straight to " + side + " button.");
            return; // keep whatever is there, no need to overwrite
        }

        System.out.print("Enter " + side + " Rate manually (press ENTER to auto-fetch from LTP): ");
        String rate = sc.nextLine();
        if (rate.trim().isEmpty()) {
            double ltp = fetchLTP(driver, wait, instrument);
            double autoRate = side.equalsIgnoreCase("BUY") ? ltp - 0.1 : ltp + 0.1;
            rate = String.format("%.2f", autoRate);
            System.out.println("Auto-fetched LTP: " + ltp + " -> Rate: " + rate);
        } else {
            System.out.println("Manual " + side + " Rate entered: " + rate);
        }
        rateInput.clear();
        rateInput.sendKeys(rate);
    }
    
    private static void enterRateAndSubmit(WebDriver driver, WebDriverWait wait, Scanner sc,
            String instrument, String side) throws InterruptedException {
        handleRate(driver, wait, sc, instrument, side);
        submitOrder(driver, side);
        System.out.println("Submitted " + side + " order.");
    }

    private static double fetchLTP(WebDriver driver, WebDriverWait wait, String instrument) {
        try {
            WebElement symbolRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tr[contains(@class, '" + instrument.replace(".", "").toUpperCase() + "')]")));
            WebElement ltpCell = symbolRow.findElement(By.cssSelector("td.mw_table_ltp div"));
            return Double.parseDouble(ltpCell.getText().replace(",", "").trim());
        } catch (Exception e) {
            System.out.println("Could not fetch LTP: " + e.getMessage());
            return 0.0;
        }
    }

    private static void submitOrder(WebDriver driver, String side) throws InterruptedException {
        WebElement header = driver.findElement(By.xpath("//div[@class='wb-title']"));
        if (header.getText().toLowerCase().contains("buy")) {
            driver.findElement(By.xpath("//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='BUY']")).click();
        } else {
            driver.findElement(By.xpath("//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']")).click();
        }
        Thread.sleep(300);
    }
    
    // Asks the user to confirm the order; returns true if confirmed, false if cancelled
    private static boolean confirmOrder(WebDriver driver, WebDriverWait wait, Scanner sc) throws InterruptedException {
        System.out.print("Confirm order? (1=YES / 0=NO): ");
        if (Integer.parseInt(sc.nextLine()) == 1) {
            WebElement yes = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[@id='cnfirmConfirmBtn']")));
            yes.click();
            System.out.println("Order confirmed!");
            Thread.sleep(2000);
            return true;
        } else {
            WebElement no = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[@id='cnfirmCancelBtn']")));
            no.click();
            System.out.println("Order cancelled.");
            Thread.sleep(2000);
            return false;
        }
    }
}