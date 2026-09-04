import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OMSweb_MultipleTrade {

    public static void main(WebDriver driver, Scanner sc) throws InterruptedException {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(1));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Navigate to Trade Page
        WebElement tradepage = driver.findElement(By.xpath("//i[@class='fa fa-exchange ']"));
        tradepage.click();
        Thread.sleep(300);

        System.out.println("Search Symbol: ");
        String instrument = sc.nextLine();

        WebElement searchSymbol = driver.findElement(By.id("instrument"));
        searchSymbol.clear();
        searchSymbol.sendKeys(instrument);
        searchSymbol.sendKeys(Keys.ARROW_DOWN);
        searchSymbol.sendKeys(Keys.ENTER);
        js.executeScript("arguments[0].scrollIntoView(true);", searchSymbol);
        Thread.sleep(500);

        // Input into marketwatch_symbol
        WebElement marketWatchInput = driver.findElement(By.id("marketwatch_symbol"));
        marketWatchInput.clear();
        marketWatchInput.sendKeys(instrument);
        marketWatchInput.sendKeys(Keys.ARROW_DOWN);
        marketWatchInput.sendKeys(Keys.ENTER);
        js.executeScript("arguments[0].scrollIntoView(true);", marketWatchInput);
        Thread.sleep(500);

        Instant start = Instant.now();

        int totalTrades = 500;
        double baseOffset = 0.1;

        // BUY
        String[] buyClientCodes = {"10009", "10004", "10102", "10340", "10351"};
        String[] buySymbols = {instrument, instrument, instrument, instrument, instrument};
        String[] buyQtys = {"10", "12", "15", "18", "20"};

        // SELL
        String[] sellClientCodes = {"11310", "12245", "14118", "19363", "26687"};
        String[] sellSymbols = {instrument, instrument, instrument, instrument, instrument};
        String[] sellQtys = {"10", "12", "15", "18", "20"};

        for (int i = 0; i < totalTrades; i++) {
            String action = (i % 2 == 0) ? "BUY" : "SELL";
            int index = i / 2;
            System.out.println("\nStarting trade " + (i + 1) + ": " + action);

            try {
                WebDriverWait wait1 = new WebDriverWait(driver, Duration.ofSeconds(1));
                WebElement actionBtn = wait1.until(
                    ExpectedConditions.elementToBeClickable(
                        By.xpath("//div[@title='Place " + action + " Order']")
                    )
                );
                actionBtn.click();
                System.out.println("Clicked " + action + " window.");
                Thread.sleep(100);

            } catch (Exception e) {
                System.out.println("Failed to click " + action + " button on trade " + (i + 1));
                e.printStackTrace();
                i--;
            }

            String clientCode = action.equals("BUY") ? buyClientCodes[index % buyClientCodes.length] : sellClientCodes[index % sellClientCodes.length];
            String symbol = action.equals("BUY") ? buySymbols[index % buySymbols.length] : sellSymbols[index % sellSymbols.length];
            String qty = action.equals("BUY") ? buyQtys[index % buyQtys.length] : sellQtys[index % sellQtys.length];

            driver.findElement(By.xpath("//input[@id='order_client_code']")).sendKeys(clientCode);
            System.out.println("Selected Client Code: " + clientCode);
            Thread.sleep(100);

            WebElement orderwindow = driver.findElement(By.id("order-window"));
            orderwindow.click();
            Thread.sleep(100);

            WebElement symbolInput = driver.findElement(By.id("order_instrument"));
            symbolInput.clear();
            symbolInput.sendKeys(symbol);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
            symbolInput.sendKeys(Keys.ARROW_DOWN);
            symbolInput.sendKeys(Keys.ENTER);
            System.out.println("Selected Symbol: " + symbol);

            driver.findElement(By.id("order_qty")).sendKeys(qty);
            System.out.println("Entered Qty: " + qty);

            // LTP fetching & limit rate
            try {
                String rowXPath = "//tr[contains(@class, '" + instrument.replace(".", "").toUpperCase() + "')]";
                WebElement symbolRow = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(rowXPath)));

                WebElement ltpCell = symbolRow.findElement(By.cssSelector("td.mw_table_ltp div"));
                String ltpText = ltpCell.getText().replace(",", "").trim();
                double ltp = Double.parseDouble(ltpText);
                System.out.println("Fetched LTP: " + ltp);

                // Dynamic offset
                double offset = baseOffset + (i % 10) * 0.10;
                double limitRate = action.equals("BUY") ? (ltp - offset) : (ltp + offset);

                WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
                rateInput.clear();
                rateInput.sendKeys(String.format("%.2f", limitRate));
                System.out.println("Fetched LTP: " + ltp + " -> Limit Rate: " + String.format("%.2f", limitRate));
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to fetch LTP or set limit rate.");
                continue;
            }

            WebElement header = driver.findElement(By.xpath("//div[@class='wb-title']"));
            if (header.getText().toLowerCase().contains("buy")) {
                driver.findElement(By.xpath("//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='BUY']")).click();
            } else {
                driver.findElement(By.xpath("//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']")).click();
            }

            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    WebDriverWait wait1 = new WebDriverWait(driver, Duration.ofSeconds(1));
                    WebElement yesBtn = wait1.until(
                        ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='YES']"))
                    );
                    yesBtn.click();
                    System.out.println("YES button clicked.");
                    break;
                } catch (ElementClickInterceptedException e) {
                    System.out.println("Click intercepted, retrying... Attempt " + (attempt + 1));
                    Thread.sleep(100);
                    if (attempt == 2) {
                        WebElement yesBtn = driver.findElement(By.xpath("//button[normalize-space()='YES']"));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", yesBtn);
                        System.out.println("YES button clicked via JS fallback.");
                    }
                }
            }

            if (Duration.between(start, Instant.now()).getSeconds() >= 60) {
                System.out.println("Stopped after 60 Seconds.");
                break;
            }

            // Clear fields after a BUY
            if (action.equalsIgnoreCase("BUY")) {
                try {
                    driver.findElement(By.id("order_client_code")).clear();
                    driver.findElement(By.id("order_instrument")).clear();
                    driver.findElement(By.id("order_qty")).clear();
                    driver.findElement(By.id("limit_order_rate")).clear();
                    System.out.println("Order window cleared for next trade.");
                } catch (Exception clearEx) {
                    System.out.println("Failed to clear order window: " + clearEx.getMessage());
                }
            }
        }
    }
}
