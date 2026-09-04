import java.time.Duration;
import java.util.Scanner;

import org.openqa.selenium.By;
//import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ClientPortfolio {

    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String side = sc.nextLine().trim().toUpperCase();
        String instrument = sc.nextLine();
        String clientCode = sc.nextLine();
        verifyPortfolio(driver, wait, sc, instrument, side, clientCode);
        /*System.out.println("\n========== PORTFOLIO TRADE ==========");

        // ---------- Choose BUY or SELL ----------
        System.out.println("1. BUY trade + portfolio verify");
        System.out.println("2. SELL trade + portfolio verify");
        System.out.print("Please Enter your choice: ");
        int choice = Integer.parseInt(sc.nextLine());

        String side = choice == 2 ? "SELL" : "BUY";

        // ---------- Confirm the side ----------
        System.out.println("\nPlace " + side + " Order window...");
        WebElement sideBtn = driver.findElement(By.xpath("//div[@title='Place " + side + " Order']"));
        sideBtn.click();
        Thread.sleep(300);

        // ---------- Fill order fields ----------
        System.out.print("Enter Symbol: ");
        String instrument = sc.nextLine();

        System.out.print("Enter Client Code (default 10009): ");
        String clientCode = sc.nextLine();
        if (clientCode.trim().isEmpty()) clientCode = "10009";

        System.out.print("Enter Qty (default 10): ");
        String qty = sc.nextLine();
        if (qty.trim().isEmpty()) qty = "10";

        fillOrderFields(driver, wait, sc, instrument, qty, clientCode);
        enterRateAndSubmit(driver, wait, sc, instrument, side);

        // ---------- Confirm order ----------
        confirmOrder(driver, wait, sc, side);

        // ---------- Verify portfolio for this symbol ----------
        verifyPortfolio(driver, wait, sc, instrument, side, clientCode);

        System.out.println("\n========== DONE ==========");
    }

    // ---------- SHARED HELPERS ----------

    // Fills client code + symbol + qty
    private static void fillOrderFields(WebDriver driver, WebDriverWait wait, Scanner sc,
                                        String instrument, String qty, String clientCode)
            throws InterruptedException {
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

    // Manual rate input by default; press ENTER to auto-fetch from LTP
    private static void enterRateAndSubmit(WebDriver driver, WebDriverWait wait, Scanner sc,
                                           String instrument, String side) throws InterruptedException {

        WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
        String rateValue = rateInput.getDomProperty("value");
        if (rateValue == null || rateValue.trim().isEmpty()) {
            rateValue = rateInput.getDomAttribute("value");
        }

        String rate;
        if (rateValue != null && !rateValue.trim().isEmpty()) {
            rate = rateValue;
            System.out.println("Rate already present: " + rate + " -> going straight to " + side + " button.");
        } else {
            System.out.print("Enter " + side + " Rate manually (press ENTER to auto-fetch from LTP): ");
            rate = sc.nextLine();
            if (rate.trim().isEmpty()) {
                double ltp = fetchLTP(driver, wait, instrument);
                double autoRate = side.equalsIgnoreCase("BUY") ? ltp - 0.1 : ltp + 0.1;
                rate = String.format("%.2f", autoRate);
                System.out.println("Auto-fetched LTP: " + ltp + " -> Rate: " + rate);
            } else {
                System.out.println("Manual " + side + " Rate entered: " + rate);
            }
            rateInput.clear();
        }

        rateInput.sendKeys(rate);
        submitOrder(driver, side);
        System.out.println("Submitted " + side + " @ " + rate);
    }

    private static boolean confirmOrder(WebDriver driver, WebDriverWait wait, Scanner sc,
                                        String side) throws InterruptedException {
        System.out.print("Confirm " + side + " order? (1=YES / 0=NO): ");
        if (!sc.nextLine().trim().equals("1")) {
            System.out.println(side + " order cancelled.");
            Thread.sleep(1000);
            return false;
        }
        try {
            WebElement yes = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[@id='cnfirmConfirmBtn']")));
            yes.click();
        } catch (Exception ex) {
            WebElement yes = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[normalize-space()='YES']")));
            yes.click();
        }
        System.out.println(side + " order confirmed!");
        Thread.sleep(2000);
        return true;
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

    private static double fetchLTP(WebDriver driver, WebDriverWait wait, String instrument) {
        try {
            WebElement symbolRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tr[contains(@class, '" + instrument.replace(".", "").toUpperCase() + "')]")));
            WebElement ltpCell = symbolRow.findElement(By.cssSelector("td.mw_table_ltp div"));
            return Double.parseDouble(ltpCell.getText().replace(",", "").trim());
        } catch (Exception e) {
            System.out.println("Could not fetch LTP: " + e.getMessage());
            return 0.0;
        }*/
    }

    // ---------- PORTFOLIO VERIFICATION ----------
    private static void verifyPortfolio(WebDriver driver, WebDriverWait wait, Scanner sc,
                                        String instrument, String side, String clientCode)
            throws InterruptedException {

        System.out.println("\n========== PORTFOLIO VERIFICATION ==========");

        // --- Open Portfolio ---
        try {
            WebElement portfolioTab = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//span[normalize-space()='Portfolio']")));
            portfolioTab.click();
            Thread.sleep(1000);
            System.out.println("Opened Portfolio for client: " + clientCode);
        } catch (Exception e) {
            System.out.println("Could not open Portfolio: " + e.getMessage());
            return;
        }

        // --- Find the symbol row in the portfolio table ---
        String symbolKey = instrument.replace(".", "").toUpperCase();
        WebElement symbolRow = null;
        try {
            symbolRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tr[contains(., '" + symbolKey + "')]")));
        } catch (Exception e) {
            System.out.println("Symbol " + symbolKey + " NOT found in portfolio: " + e.getMessage());
        }

        if (symbolRow != null) {
            String totalQty   = readCell(symbolRow, 1);
            String saleable   = readCell(symbolRow, 2);
            String avgCost    = readCell(symbolRow, 3);
            String totalCost  = readCell(symbolRow, 4);
            String mktRate    = readCell(symbolRow, 5);
            String mktValue   = readCell(symbolRow, 6);
            String gain       = readCell(symbolRow, 7);
            String pGain      = readCell(symbolRow, 8);
            String percentage = readCell(symbolRow, 9);

            System.out.println("\n--- Portfolio data for " + symbolKey + " ---");
            System.out.println("Symbol      : " + symbolKey);
            System.out.println("Total Qty   : " + totalQty);
            System.out.println("Saleable    : " + saleable);
            System.out.println("Avg Cost    : " + avgCost);
            System.out.println("Total Cost  : " + totalCost);
            System.out.println("Mkt Rate    : " + mktRate);
            System.out.println("Mkt Value   : " + mktValue);
            System.out.println("Gain        : " + gain);
            System.out.println("%Gain       : " + pGain);
            System.out.println("Percentage  : " + percentage);

            if (side.equalsIgnoreCase("BUY")) {
                System.out.println("\n[BUY checks] Symbol added, Total Qty = " + totalQty);
            } else if (side.equalsIgnoreCase("SELL")) {
                System.out.println("\n[SELL checks] Saleable reduced = " + saleable);
            }
        }

        // --- Cash balance check ---
        System.out.print("Enter pre-trade cash limit (or press ENTER if unknown): ");
        String preCash = sc.nextLine();
        try {
            WebElement cash = driver.findElement(By.xpath("//span[contains(@class,'_cash')]"));
            String cashText = cash.getText().trim();
            System.out.println("Current Cash limit: " + cashText);
            if (!preCash.trim().isEmpty()) {
                double before = Double.parseDouble(preCash.replace(",", "").trim());
                double after = Double.parseDouble(cashText.replace(",", "").trim());
                if (side.equalsIgnoreCase("BUY")) {
                    System.out.println("Cash: " + (after < before ? "REDUCED (correct)" : "NOT reduced - CHECK"));
                } else if (side.equalsIgnoreCase("SELL")) {
                    System.out.println("Cash: " + (after > before ? "INCREASED (correct)" : "NOT increased - CHECK"));
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read cash balance: " + e.getMessage());
        }

        Thread.sleep(500);

        // --- Back to Trade ---
        try {
            driver.findElement(By.xpath("//i[@class='fa fa-exchange ']")).click();
            Thread.sleep(500);
        } catch (Exception e) {
            System.out.println("Could not return to Trade page.");
        }
    }

    private static String readCell(WebElement row, int column) {
        try {
            return row.findElement(By.xpath("./td[" + column + "]")).getText().trim();
        } catch (Exception e) {
            return "";
        }
    }
}