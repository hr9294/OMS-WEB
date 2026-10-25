import java.time.Duration;
//import java.util.ArrayList;
//import java.util.HashMap;
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

public class OMSweb_Trade {

	public static void main(WebDriver driver, Scanner sc) throws InterruptedException {

		JavascriptExecutor js = (JavascriptExecutor) driver;
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

		// Navigate to Trade Page initially
		navigateToTradePage(driver, wait);
		runWidgetColorSelection(driver, sc);

		// ---------- Auto input symbol into search + marketwatch ----------
		System.out.print("Enter Symbol: ");
		String instrument = sc.nextLine().trim();

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
	private static void selectOrderStatus(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {

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
		case 1:
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

	// =========================================================================
	// NORMAL TRADE FLOW WITH AUTOMATED PORTFOLIO VERIFICATION
	// =========================================================================
	private static void normalTradeFlow(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {

		boolean keepTrading = true;

		while (keepTrading) {

			// ---------- 1. Get Client Code & Exchange FIRST for Snapshot ----------
			System.out.print("\nEnter Client Code (default 23986): ");
			String clientCode = sc.nextLine().trim();
			if (clientCode.isEmpty()) clientCode = "23986";

			System.out.print("Enter Exchange (DSE / CSE, default DSE): ");
			String exchange = sc.nextLine().trim().toUpperCase();
			if (exchange.isEmpty()) exchange = "DSE";

			// ---------- Client Financial Details via API ----------
			Map<String, String> clientInfoBefore = ClientPortfolio.getClientDetails(driver, clientCode);
			if (!clientInfoBefore.isEmpty()) {
				System.out.printf("[CLIENT INFO] %s (BO: %s) | Cash Limit: %s | Mature: %s | Commission: %s%n",
						clientInfoBefore.getOrDefault("client_name", "-"),
						clientInfoBefore.getOrDefault("bo_id", "-"),
						clientInfoBefore.getOrDefault("cash_limit", "-"),
						clientInfoBefore.getOrDefault("mature_balance", "-"),
						clientInfoBefore.getOrDefault("commission", "-"));
			}

			// =====================================================================
			// [STEP 1] CAPTURE PRE-TRADE SNAPSHOT (DSE/CSE API -> UI FALLBACK)
			// =====================================================================
			System.out.println("\n[PORTFOLIO] Capturing PRE-TRADE Snapshot...");
			navigateToPortfolioPage(driver, wait);
			Map<String, String> beforeSnapshot = ClientPortfolio.getSnapshot(driver, clientCode, instrument, exchange);
			System.out.println("[PORTFOLIO] Pre-Trade Snapshot Captured (Source: " + beforeSnapshot.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");
			
			Order_Summary.navigateToOrderSummary(driver);
			Map<String, String> preOrderSummary = Order_Summary.getSnapshot(driver, clientCode, instrument, exchange);
			System.out.println("[ORDER SUMMARY] Pre-Trade Snapshot Captured (Source: " + preOrderSummary.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");
			
			Account_Limits.navigateToAccountLimits(driver);
			Map<String, String> preAccountLimits = Account_Limits.getSnapshot(driver, clientCode, exchange);
			System.out.println("[ACCOUNT LIMITS] Pre-Trade Snapshot Captured (Source: " + preAccountLimits.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");
			
			Market_Depth.navigateToMarketDepth(driver);
			Map<String, String> preDepth = Market_Depth.getSnapshot(driver, instrument, exchange);
			System.out.println("[MARKET DEPTH] Pre-Trade Snapshot Captured (Source: " + preDepth.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");
			Market_Depth.printDepthReport(instrument, preDepth);

			Order_Terminal.navigateToOrderTerminal(driver);
			Map<String, String> preOrderTerminal = Order_Terminal.getSnapshot(driver, clientCode, instrument, exchange);
			System.out.println("[ORDER TERMINAL] Pre-Trade Snapshot Captured (Source: " + preOrderTerminal.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");
			// Return back to Trade Page if needed
			if (!isAlreadyOnTradePage(driver)) {
				System.out.println("[TRADE] Returning to Trade Page...");
				navigateToTradePage(driver, wait);
			}
			// =====================================================================

			// ---------- Choose Buy or Sell (1=BUY, 2=SELL) ----------
			System.out.println("\nFor BUY, Please Enter: 1");
			System.out.println("For SELL, Please Enter: 2");
			System.out.print("Please Enter your choice: ");
			int int_side = Integer.parseInt(sc.nextLine());
			String sideText = int_side == 1 ? "BUY" : "SELL";

			if (int_side == 1) {
				WebElement buy = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@title='Place BUY Order']")));
				buy.click();
				System.out.println("Buy action performed.");
			} else if (int_side == 2) {
				WebElement sell = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@title='Place SELL Order']")));
				sell.click();
				System.out.println("Sell action performed.");
			} else {
				System.out.println("Wrong value. Starting over.");
				continue;
			}
			Thread.sleep(300);

			// ---------- Fill Client Code ----------
			WebElement order_client_code = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("order_client_code")));
			order_client_code.click();
			order_client_code.clear();
			order_client_code.sendKeys(clientCode);
			System.out.println("Client Code entered: " + clientCode);
			Thread.sleep(200);

			// ---------- Fill Symbol ----------
			WebElement symbolInput = driver.findElement(By.id("order_instrument"));
			symbolInput.clear();
			symbolInput.sendKeys(instrument);
			wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
			symbolInput.sendKeys(Keys.ARROW_DOWN);
			symbolInput.sendKeys(Keys.ENTER);
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", symbolInput);
			System.out.println("Order Symbol selected: " + instrument);

			// ---------- Fill Qty ----------
			System.out.print("Enter Qty (or press Enter for default 10): ");
			String qty = sc.nextLine().trim();
			if (qty.isEmpty()) qty = "10";
			WebElement qtyInput = driver.findElement(By.id("order_qty"));
			qtyInput.clear();
			qtyInput.sendKeys(qty);
			System.out.println("Qty entered: " + qty);

			// ---------- Handle Rate ----------
			handleRate(driver, wait, sc, instrument, sideText);
			double executedRate = getEnteredRate(driver);

			// ---------- Submit Order ----------
			WebElement header = driver.findElement(By.xpath("//div[@class='wb-title']"));
			if (header.getText().toLowerCase().contains("buy")) {
				driver.findElement(By.xpath(
						"//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='BUY']"))
						.click();
			} else {
				driver.findElement(By.xpath(
						"//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']"))
						.click();
			}
			System.out.println("Clicked " + sideText + " button.");

			// ---------- Confirm Order ----------
			if (!confirmOrder(driver, wait, sc)) {
				continue; // order cancelled -> skip portfolio check & trade again
			}

			// =====================================================================
			//  CAPTURE POST-TRADE SNAPSHOT & VERIFICATION
			// =====================================================================
			System.out.println("\n[PORTFOLIO] Capturing POST-TRADE Snapshot & Verification...");
			navigateToPortfolioPage(driver, wait);
			ClientPortfolio.refresh(driver);

			Map<String, String> afterSnapshot = ClientPortfolio.getSnapshot(driver, clientCode, instrument, exchange);

			// Fetch updated Client Cash via API right after trade
			Map<String, String> clientInfoAfter = ClientPortfolio.getClientDetails(driver, clientCode);

			// Compare & Display Comprehensive Table with Portfolio & Cash Audit
			int tradeQty = Integer.parseInt(qty);
			ClientPortfolio.verify(beforeSnapshot, afterSnapshot, clientInfoBefore, clientInfoAfter, instrument, sideText, tradeQty, executedRate, exchange);

			//  Verify Order Summary
			Order_Summary.navigateToOrderSummary(driver);
			Order_Summary.refresh(driver);
			Map<String, String> postOrderSummary = Order_Summary.getSnapshot(driver, clientCode, instrument, exchange);
			Order_Summary.verify(preOrderSummary, postOrderSummary, instrument, sideText, tradeQty, executedRate, exchange);
			
			// Account Limits
			Account_Limits.navigateToAccountLimits(driver);
			Account_Limits.refresh(driver);
			Map<String, String> postAccountLimits = Account_Limits.getSnapshot(driver, clientCode, exchange);
			Account_Limits.verify(preAccountLimits, postAccountLimits, sideText, tradeQty, executedRate, exchange);
			
			// Audit Market Depth
			Market_Depth.navigateToMarketDepth(driver);
			Map<String, String> postDepth = Market_Depth.getSnapshot(driver, instrument, exchange);
			Market_Depth.verify(preDepth, postDepth, instrument, sideText, tradeQty, executedRate, exchange);

			// Audit Order Terminal
			Order_Terminal.navigateToOrderTerminal(driver);
			Order_Terminal.refresh(driver);
			Map<String, String> postOrderTerminal = Order_Terminal.getSnapshot(driver, clientCode, instrument, exchange);
			Order_Terminal.verify(preOrderTerminal, postOrderTerminal, instrument, sideText, tradeQty, executedRate, exchange);

			// Click that newly placed order in Order Terminal table
			Order_Terminal.clickOrderRow(driver, postOrderTerminal.get("Order_ID"), clientCode, instrument);

			// Return to Trade Page if needed
			if (!isAlreadyOnTradePage(driver)) {
				System.out.println("[TRADE] Returning to Trade Page...");
				navigateToTradePage(driver, wait);
			}
			// =====================================================================

			// ---------- After confirmation, ask to select an Order Status type ----------
			System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
			if (sc.nextLine().trim().equals("1")) {
				selectOrderStatus(driver, wait, sc, instrument);
			}

			// ---------- Ask if user wants to trade again ----------
			System.out.print("Do you want to trade again? (1=YES / 0=NO): ");
			if (Integer.parseInt(sc.nextLine()) != 1) {
				keepTrading = false;
				System.out.println("Exiting trade.");
			} else {
				runWidgetColorSelection(driver, sc);
			}
		}
	}

	// =========================================================================
	// NAVIGATION HELPERS (TRADE <-> PORTFOLIO)
	// =========================================================================
	public static void navigateToTradePage(WebDriver driver, WebDriverWait wait) {
		try {
			// Check if already on Trade portal (marketwatch_symbol or order_instrument visible)
			if (isAlreadyOnTradePage(driver)) {
				System.out.println("[NAV] Already on Trade page.");
				return;
			}

			System.out.println("[NAV] Admin user detected on Dashboard. Navigating to Trade Portal...");
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript(
				"var tradeLink = document.querySelector('a[data-link=\"live_orders/trade_portal\"]');" +
				"if (tradeLink) {" +
				"    tradeLink.click();" +
				"} else {" +
				"    var links = document.querySelectorAll('.nav-item a');" +
				"    for (var i = 0; i < links.length; i++) {" +
				"        if (links[i].getAttribute('title') === 'Trade' || links[i].innerText.trim() === 'Trade') {" +
				"            links[i].click();" +
				"            break;" +
				"        }" +
				"    }" +
				"}"
			);

			// Wait for trade portal elements to be visible
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("marketwatch_symbol")));
			Thread.sleep(800);
			System.out.println("[NAV] Trade Portal loaded successfully.");
		} catch (Exception e) {
			// Fallback: standard Selenium click
			try {
				WebElement tradepage = wait.until(ExpectedConditions.elementToBeClickable(
						By.xpath("//a[@data-link='live_orders/trade_portal'] | //a[@title='Trade'] | //a[@data-original-title='Trade'] | //i[contains(@class,'fa-exchange')]/ancestor::a[1] | //i[contains(@class,'fa-exchange')] | //a[normalize-space()='Trade']")));
				tradepage.click();
				Thread.sleep(800);
			} catch (Exception ex) {
				System.out.println("[NAV] Trade portal navigation note: " + ex.getMessage());
			}
		}
	}

	public static boolean isAlreadyOnTradePage(WebDriver driver) {
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;
			Object res = js.executeScript(
				"var el = document.getElementById('marketwatch_symbol') || document.getElementById('order_instrument');" +
				"return !!(el && el.offsetParent !== null);"
			);
			return Boolean.TRUE.equals(res);
		} catch (Exception e) {
			return false;
		}
	}

	public static void navigateToPortfolioPage(WebDriver driver) {
		navigateToPortfolioPage(driver, null);
	}

	public static void navigateToPortfolioPage(WebDriver driver, WebDriverWait wait) {
		try {
			// Fast non-blocking check for Portfolio tab or widget title (supports GoldenLayout tabs)
			JavascriptExecutor js = (JavascriptExecutor) driver;
			Boolean clicked = (Boolean) js.executeScript(
				"var tab = document.querySelector('li.lm_tab[title=\"Portfolio\"], div[title=\"Portfolio\"], a[title=\"Portfolio\"]');" +
				"if (tab && tab.offsetParent !== null) { tab.click(); return true; }" +
				"var spans = Array.from(document.querySelectorAll('li.lm_tab span, a.nav-link, a'));" +
				"var pTab = spans.find(function(s) { return s.innerText && s.innerText.trim() === 'Portfolio'; });" +
				"if (pTab && pTab.offsetParent !== null) { pTab.click(); return true; }" +
				"return false;"
			);
			if (Boolean.TRUE.equals(clicked)) {
				Thread.sleep(300);
				System.out.println("[NAV] Switched to Portfolio tab.");
			}
		} catch (Exception ignored) {
		}
	}

	private static double getEnteredRate(WebDriver driver) {
		try {
			WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
			String val = rateInput.getDomProperty("value");
			if (val == null || val.isEmpty()) val = rateInput.getDomProperty("value");
			return Double.parseDouble(val.replaceAll("[^0-9.]", ""));
		} catch (Exception e) {
			return 0.0;
		}
	}

	// ---------- PARTIALLY FILLED ----------
	private static void placePartiallyFilledOrder(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {
		if (!isAlreadyOnTradePage(driver)) {
			navigateToTradePage(driver, wait);
		}

		System.out.print("Enter BUY qty for the partially-filled test: ");
		String buyQty = sc.nextLine();
		System.out.print("Enter SELL qty (should be LOWER than " + buyQty + "): ");
		String sellQty = sc.nextLine();

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

		// Click order in Order Terminal
		Order_Terminal.navigateToOrderTerminal(driver);
		Order_Terminal.refresh(driver);
		Order_Terminal.clickOrderRow(driver, null, sellClientCode, instrument);

		System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
		if (sc.nextLine().trim().equals("1")) {
			selectOrderStatus(driver, wait, sc, instrument);
		}
	}

	// ---------- FILLED ----------
	private static void placeFilledOrder(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {
		if (!isAlreadyOnTradePage(driver)) {
			navigateToTradePage(driver, wait);
		}

		System.out.print("Enter qty for the filled test: ");
		String qty = sc.nextLine();

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

		// Click order in Order Terminal
		Thread.sleep(1000);
		Order_Terminal.navigateToOrderTerminal(driver);
		Order_Terminal.refresh(driver);
		Order_Terminal.clickOrderRow(driver, null, sellClientCode, instrument);

		System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
		if (sc.nextLine().trim().equals("1")) {
			selectOrderStatus(driver, wait, sc, instrument);
		}
	}

	// ---------- CANCELLED ----------
	private static void placeCancelledOrder(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {
		if (!isAlreadyOnTradePage(driver)) {
			navigateToTradePage(driver, wait);
		}

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

		fillOrderFields(driver, wait, instrument, qty, clientCode);
		enterRateAndSubmit(driver, wait, sc, instrument, side);
		confirmOrder(driver, wait, sc);

		Thread.sleep(1000);
		Order_Terminal.navigateToOrderTerminal(driver);
		Order_Terminal.refresh(driver);
		WebElement firstRow = Order_Terminal.findOrderRow(driver, null, clientCode, instrument);

		if (firstRow != null) {
			Order_Terminal.clickOrderRow(driver, null, clientCode, instrument);
			new org.openqa.selenium.interactions.Actions(driver).contextClick(firstRow).perform();
			Thread.sleep(500);
		} else {
			System.out.println("[WARN] Order row could not be found to cancel.");
		}

		String[] cancelXpaths = { "//li[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
				"//a[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
				"//div[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
				"//span[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]",
				"//*[contains(@class,'cancel')]",
				"//*[contains(translate(normalize-space(),'CANCEL','cancel'),'cancel')]" };

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
			} catch (Exception ignored) {}
		}

		if (!cancelClicked) {
			System.out.println("Could not find the Cancel option in the context menu.");
			new org.openqa.selenium.interactions.Actions(driver).sendKeys(Keys.ESCAPE).perform();
			return;
		}

		System.out.print("Confirm cancellation? (1=YES / 0=NO): ");
		if (Integer.parseInt(sc.nextLine()) == 1) {
			try {
				WebElement yesCancel = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='YES']")));
				yesCancel.click();
				System.out.println("Cancellation confirmed.");
			} catch (Exception e) {
				System.out.println("No cancellation confirmation appeared.");
			}
		}
		Thread.sleep(1000);
	}

	// ---------- REJECTED ----------
	private static void placeRejectedOrder(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {
		if (!isAlreadyOnTradePage(driver)) {
			navigateToTradePage(driver, wait);
		}

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

		double ltp = fetchLTP(driver, wait, instrument);
		double outOfCircuitRate = side.equalsIgnoreCase("BUY") ? ltp + 500 : ltp - 500;
		WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
		rateInput.clear();
		rateInput.sendKeys(String.format("%.2f", outOfCircuitRate));
		System.out.println("Set out-of-circuit rate: " + String.format("%.2f", outOfCircuitRate));

		submitOrder(driver, side);
		confirmOrder(driver, wait, sc);
		System.out.println("Order submitted at out-of-circuit price -> expected REJECTED.");

		// Click order in Order Terminal
		Thread.sleep(1000);
		Order_Terminal.navigateToOrderTerminal(driver);
		Order_Terminal.refresh(driver);
		Order_Terminal.clickOrderRow(driver, null, clientCode, instrument);

		System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
		if (sc.nextLine().trim().equals("1")) {
			selectOrderStatus(driver, wait, sc, instrument);
		}
	}

	// ---------- EXPIRED ----------
	private static void placeExpiredOrder(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument)
			throws InterruptedException {
		if (!isAlreadyOnTradePage(driver)) {
			navigateToTradePage(driver, wait);
		}

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

		WebElement advanceOption = driver.findElement(By.xpath("//label[@for='trade_mode_checkbox']"));
		advanceOption.click();
		Thread.sleep(300);

		WebElement iocOption = driver
				.findElement(By.xpath("//select[@id='order_validity']//option[@value='IOC'][normalize-space()='IOC']"));
		iocOption.click();
		System.out.println("Selected IOC order validity.");

		enterRateAndSubmit(driver, wait, sc, instrument, side);
		confirmOrder(driver, wait, sc);
		System.out.println("IOC order placed -> expected EXPIRED if not filled immediately.");

		// Click order in Order Terminal
		Thread.sleep(1000);
		Order_Terminal.navigateToOrderTerminal(driver);
		Order_Terminal.refresh(driver);
		Order_Terminal.clickOrderRow(driver, null, clientCode, instrument);

		System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
		if (sc.nextLine().trim().equals("1")) {
			selectOrderStatus(driver, wait, sc, instrument);
		}
	}

	// ---------- SHARED HELPERS ----------
	private static void fillOrderFields(WebDriver driver, WebDriverWait wait, String instrument, String qty,
			String clientCode) throws InterruptedException {
		WebElement client = driver.findElement(By.id("order_client_code"));
		client.click();
		client.clear();
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

	private static void handleRate(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument, String side)
			throws InterruptedException {
		WebElement rateInput = driver.findElement(By.id("limit_order_rate"));

		String rateValue = rateInput.getDomProperty("value");
		if (rateValue == null || rateValue.trim().isEmpty()) {
			rateValue = rateInput.getDomAttribute("value");
		}

		if (rateValue != null && !rateValue.trim().isEmpty()) {
			System.out.println("Rate already present: " + rateValue + " -> going straight to " + side + " button.");
			return;
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

	private static void enterRateAndSubmit(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument,
			String side) throws InterruptedException {
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
			driver.findElement(By.xpath(
					"//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='BUY']"))
					.click();
		} else {
			driver.findElement(By.xpath(
					"//div[@class='col pl-0 pr-0 td-btn mt-2 mb-2 form-inline form-submit-btn-holder text-center']//button[@type='button'][normalize-space()='SELL']"))
					.click();
		}
		Thread.sleep(300);
	}

	private static boolean confirmOrder(WebDriver driver, WebDriverWait wait, Scanner sc) throws InterruptedException {
		System.out.print("Confirm order? (1=YES / 0=NO): ");
		if (Integer.parseInt(sc.nextLine()) == 1) {
			WebElement yes = wait
					.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@id='cnfirmConfirmBtn']")));
			yes.click();
			System.out.println("Order confirmed!");
			Thread.sleep(2000);
			return true;
		} else {
			WebElement no = wait
					.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@id='cnfirmCancelBtn']")));
			no.click();
			System.out.println("Order cancelled.");
			Thread.sleep(2000);
			return false;
		}
	}

	// ============================================================
	// WIDGET COLOR SELECTION
	// ============================================================
	private static final String[] COLOR_NAMES = { "Turquoise", "Yellow", "Orange", "Green", "Red", "Pink" };
	private static final String[] COLOR_HEXES = { "#42EADDFF", "#FEE715FF", "#F95700ff", "#06d001", "#EB0506", "#FF3EA5FF" };

	private static void runWidgetColorSelection(WebDriver driver, Scanner sc) throws InterruptedException {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		System.out.println("\n=== Widget Color ===");
		System.out.println("1.Turquoise 2.Yellow 3.Orange");
		System.out.println("4.Green 5.Red 6.Pink");
		System.out.print("Enter choice (1-6): ");
		String input = sc.nextLine().trim();
		if (input.isEmpty()) return;
		int colorChoice;
		try {
			colorChoice = Integer.parseInt(input);
		} catch (NumberFormatException e) {
			return;
		}
		if (colorChoice < 1 || colorChoice > 6) return;

		String chosenColorName = COLOR_NAMES[colorChoice - 1];
		String targetHex = COLOR_HEXES[colorChoice - 1];

		System.out.println("\n[WIDGET COLOR] Applying " + chosenColorName + " (" + targetHex + ") to all widgets...");

		String script =
			"var targetHex = arguments[0];" +
			"var chooseColors = Array.from(document.querySelectorAll('.chooseColor'));" +
			"var results = [];" +
			"chooseColors.forEach(function(chooseDiv, index) {" +
			"    var stack = chooseDiv.closest('.lm_stack');" +
			"    var activeTab = stack ? stack.querySelector('.lm_tab.lm_active .lm_title, .lm_tab .lm_title') : null;" +
			"    var title = activeTab ? activeTab.innerText.trim() : ('Widget ' + (index + 1));" +
			"    var items = Array.from(chooseDiv.querySelectorAll('ul.color-card li.color-item'));" +
			"    var targetLi = items.find(function(li) {" +
			"        var colorDiv = li.querySelector('[data-link-color]');" +
			"        return colorDiv && colorDiv.getAttribute('data-link-color').toLowerCase() === targetHex.toLowerCase();" +
			"    });" +
			"    if (targetLi) {" +
			"        if (window.$) {" +
			"            window.$(targetLi).trigger('click');" +
			"        } else {" +
			"            targetLi.click();" +
			"        }" +
			"        var selectedDiv = chooseDiv.querySelector('.selectedColor');" +
			"        results.push({ index: index + 1, title: title, success: true, color: selectedDiv ? selectedDiv.style.backgroundColor : '' });" +
			"    } else {" +
			"        results.push({ index: index + 1, title: title, success: false, color: '' });" +
			"    }" +
			"});" +
			"return results;";

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) js.executeScript(script, targetHex);

		int successCount = 0;
		if (results != null && !results.isEmpty()) {
			for (Map<String, Object> res : results) {
				String title = (String) res.get("title");
				Boolean ok = (Boolean) res.get("success");
				if (Boolean.TRUE.equals(ok)) {
					System.out.printf("  ✓ %-18s -> %s SELECTED%n", title, chosenColorName);
					successCount++;
				} else {
					System.out.printf("  ✗ %-18s -> FAILED%n", title);
				}
			}
			System.out.printf("[WIDGET COLOR] Done: %d/%d widgets colored with %s.%n", successCount, results.size(), chosenColorName);
		} else {
			System.out.println("[WIDGET COLOR] No widgets found on page.");
		}
		Thread.sleep(500);
	}
}