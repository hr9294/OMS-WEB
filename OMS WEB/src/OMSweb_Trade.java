import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

		// Navigate to Trade Page initially if not already on it
		if (!isAlreadyOnTradePage(driver)) {
			navigateToTradePage(driver, wait);
			runWidgetColorSelection(driver, sc);
		}

		// ---------- Manual input symbol into search + marketwatch ----------
		System.out.print("\nEnter Symbol (default GP): ");
		String instrument = sc.nextLine().trim();
		if (instrument.isEmpty() || instrument.matches("\\d+")) {
			if (instrument.matches("\\d+")) {
				System.out.println("[WARN] Entered '" + instrument + "' is numeric. Defaulting to GP.");
			}
			instrument = "GP";
		}

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

			// ---------- 1. Choose Buy or Sell FIRST (1=BUY, 2=SELL) ----------
			System.out.println("\nFor BUY, Please Enter: 1");
			System.out.println("For SELL, Please Enter: 2");
			System.out.print("Please Enter your choice: ");
			String sideInput = sc.nextLine().trim();
			int int_side = 1;
			try {
				int_side = Integer.parseInt(sideInput);
			} catch (Exception e) {
				int_side = 1;
			}
			String sideText = int_side == 2 ? "SELL" : "BUY";

			// ---------- 2. Select Exchange / Priority Order Mode AFTER Side is chosen ----------
			System.out.println("\nSelect Exchange / Order Mode:");
			System.out.println("1. DSE (default)");
			System.out.println("2. CSE");
			System.out.println("3. Priority Order");
			System.out.print("Please Enter your choice (1-3, or DSE/CSE/Priority, default 1): ");
			String exInput = sc.nextLine().trim();

			String exchange = "DSE";
			boolean isPriorityOrder = false;

			if (exInput.equalsIgnoreCase("3") || exInput.equalsIgnoreCase("Priority") || exInput.equalsIgnoreCase("P") || exInput.equalsIgnoreCase("Priority Order")) {
				exchange = "Priority";
				isPriorityOrder = true;
			} else if (exInput.equalsIgnoreCase("2") || exInput.equalsIgnoreCase("CSE")) {
				exchange = "CSE";
			} else {
				exchange = "DSE";
			}

			// For widgets & APIs, Priority order queries the underlying market exchange (DSE)
			String snapshotExchange = isPriorityOrder ? "DSE" : exchange;
			System.out.printf("[ORDER MODE] Side: %s | Exchange/Mode: %s (Snapshot Exchange: %s)%n",
					sideText, exchange, snapshotExchange);

			// ---------- 3. AFTER Buy/Sell & Exchange selection, THEN call for Client Code ----------
			List<String> clientCodes = new ArrayList<>();
			Map<String, Position.ClientPosition> positionMap = new HashMap<>();

			if (int_side == 1) { // BUY
				System.out.print("\nEnter Client Code (default 23986): ");
				String input = sc.nextLine().trim();
				if (input.isEmpty()) {
					clientCodes.add("23986");
				} else {
					for (String code : input.split("[,\\s]+")) {
						if (!code.trim().isEmpty()) {
							clientCodes.add(code.trim());
						}
					}
					if (clientCodes.isEmpty()) clientCodes.add("23986");
				}
				System.out.println("[BUY CLIENTS] Selected Client Code(s): " + clientCodes);
			} else { // SELL
				System.out.print("\nEnter Client Code (default from Position): ");
				String input = sc.nextLine().trim();
				if (!input.isEmpty()) {
					for (String code : input.split("[,\\s]+")) {
						if (!code.trim().isEmpty()) {
							clientCodes.add(code.trim());
						}
					}
				}

				// Otherwise input from position
				if (clientCodes.isEmpty()) {
					System.out.println("\n[POSITION] Querying holding clients from Position widget for " + instrument + "...");
					List<Position.ClientPosition> picked = Position.promptOrSelectClientsForSell(driver, sc, instrument, snapshotExchange, "23986");
					if (picked != null && !picked.isEmpty()) {
						for (Position.ClientPosition cp : picked) {
							clientCodes.add(cp.getClientCode());
							positionMap.put(cp.getClientCode(), cp);
						}
					} else {
						clientCodes.add("23986");
					}
				}
				System.out.println("[SELL CLIENTS] Selected Client Code(s): " + clientCodes);
			}

			// Ensure we are on Trade Page
			if (!isAlreadyOnTradePage(driver)) {
				navigateToTradePage(driver, wait);
			}

			// ---------- 4. Qty & Rate Parameters ----------
			String defaultQty = "10";
			if (sideText.equals("SELL") && clientCodes.size() == 1 && positionMap.containsKey(clientCodes.get(0))) {
				Position.ClientPosition singleSell = positionMap.get(clientCodes.get(0));
				if (singleSell != null && singleSell.getSaleableQty() > 0) {
					System.out.printf("Enter Qty (Max Saleable for %s: %,.0f | default 10): ",
							singleSell.getClientCode(), singleSell.getSaleableQty());
				} else {
					System.out.print("Enter Qty (or press Enter for default 10): ");
				}
			} else {
				System.out.print("Enter Qty (or press Enter for default 10): ");
			}
			String qtyStr = sc.nextLine().trim();
			if (qtyStr.isEmpty()) qtyStr = defaultQty;
			int tradeQty = Integer.parseInt(qtyStr);

			// Handle Rate
			double executedRate = 0.0;
			if (isPriorityOrder) {
				System.out.println("\n[PRIORITY ORDER] Mode active: orders execute at Market Rate across exchanges.");
				double ltp = fetchLTP(driver, wait, instrument);
				if (ltp > 0) {
					executedRate = ltp;
					System.out.printf("[PRIORITY ORDER] Auto-detected Market Rate (LTP): %.2f%n", executedRate);
				} else {
					System.out.print("Enter Estimated Market Rate for reporting (press ENTER for 0.0): ");
					String rInput = sc.nextLine().trim();
					if (!rInput.isEmpty()) {
						try { executedRate = Double.parseDouble(rInput); } catch (Exception ignored) {}
					}
				}
			} else {
				executedRate = handleRate(driver, wait, sc, instrument, sideText);
				if (executedRate <= 0.0) {
					executedRate = getEnteredRate(driver);
				}
			}

			// ---------- 5. Execute Trade(s) for the Client Code(s) ----------
			for (int idx = 0; idx < clientCodes.size(); idx++) {
				String clientCode = clientCodes.get(idx);
				if (clientCodes.size() > 1) {
					System.out.printf("%n=======================================================");
					System.out.printf("%n>>> [%d/%d] EXECUTING %s FOR CLIENT: %s (%s x %d @ %.2f)%n",
							(idx + 1), clientCodes.size(), sideText, clientCode, instrument, tradeQty, executedRate);
					System.out.println("=======================================================");
				}

				// Check saleable balance warning for SELL
				Position.ClientPosition sellInfo = positionMap.get(clientCode);
				if (sideText.equals("SELL") && sellInfo != null && sellInfo.getSaleableQty() > 0 && tradeQty > sellInfo.getSaleableQty()) {
					System.out.printf("[WARN] Entered qty %d exceeds client %s's saleable balance (%,.0f)! Order may be rejected.%n",
							tradeQty, clientCode, sellInfo.getSaleableQty());
				}

				// Client Financial Details via API
				Map<String, String> clientInfoBefore = ClientPortfolio.getClientDetails(driver, clientCode);
				if (!clientInfoBefore.isEmpty()) {
					System.out.printf("[CLIENT INFO: %s] %s (BO: %s) | Cash Limit: %s | Mature: %s | Commission: %s%n",
							clientCode,
							clientInfoBefore.getOrDefault("client_name", "-"),
							clientInfoBefore.getOrDefault("bo_id", "-"),
							clientInfoBefore.getOrDefault("cash_limit", "-"),
							clientInfoBefore.getOrDefault("mature_balance", "-"),
							clientInfoBefore.getOrDefault("commission", "-"));
				}

				// [STEP 1] CAPTURE PRE-TRADE SNAPSHOT (DSE/CSE API -> UI FALLBACK)
				System.out.printf("%n[PORTFOLIO] Capturing PRE-TRADE Snapshot for Client: %s...%n", clientCode);
				navigateToPortfolioPage(driver, wait);
				Map<String, String> beforeSnapshot = ClientPortfolio.getSnapshot(driver, clientCode, instrument, snapshotExchange);
				System.out.println("[PORTFOLIO] Pre-Trade Snapshot Captured (Source: " + beforeSnapshot.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");

				Order_Summary.navigateToOrderSummary(driver);
				Map<String, String> preOrderSummary = Order_Summary.getSnapshot(driver, clientCode, instrument, snapshotExchange);
				System.out.println("[ORDER SUMMARY] Pre-Trade Snapshot Captured (Source: " + preOrderSummary.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");

				Account_Limits.navigateToAccountLimits(driver);
				Map<String, String> preAccountLimits = Account_Limits.getSnapshot(driver, clientCode, snapshotExchange);
				System.out.println("[ACCOUNT LIMITS] Pre-Trade Snapshot Captured (Source: " + preAccountLimits.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");

				Market_Depth.navigateToMarketDepth(driver);
				Map<String, String> preDepth = Market_Depth.getSnapshot(driver, instrument, snapshotExchange);
				System.out.println("[MARKET DEPTH] Pre-Trade Snapshot Captured (Source: " + preDepth.getOrDefault("DATA_SOURCE", "UNKNOWN") + ")");
				if (clientCodes.size() == 1) {
					Market_Depth.printDepthReport(instrument, preDepth);
				}

				Order_Terminal.navigateToOrderTerminal(driver);
				Map<String, String> preOrderTerminal = Order_Terminal.getPreTradeSnapshot(driver, clientCode, instrument, snapshotExchange);
				System.out.println("[ORDER TERMINAL] Pre-Trade Snapshot Captured (Source: " + preOrderTerminal.getOrDefault("DATA_SOURCE", "UNKNOWN") + ", Progress: 0%)");

				// Return back to Trade Page if needed
				if (!isAlreadyOnTradePage(driver)) {
					System.out.println("[TRADE] Returning to Trade Page...");
					restoreTradePageState(driver, instrument, clientCode);
				}

				// Open BUY or SELL modal
				if (int_side == 1) {
					WebElement buy = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@title='Place BUY Order']")));
					buy.click();
				} else {
					WebElement sell = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@title='Place SELL Order']")));
					sell.click();
				}
				Thread.sleep(300);

				// Select Exchange / Priority in Modal
				selectExchangeInModal(driver, exchange);
				Thread.sleep(200);

				// Fill Client Code
				WebElement order_client_code = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("order_client_code")));
				order_client_code.click();
				order_client_code.clear();
				order_client_code.sendKeys(clientCode);
				try {
					((JavascriptExecutor) driver).executeScript("$('#order_client_code').trigger('change');");
				} catch (Exception ignored) {}
				System.out.println("Client Code entered: " + clientCode);
				Thread.sleep(200);

				// Fill Symbol
				WebElement symbolInput = driver.findElement(By.id("order_instrument"));
				symbolInput.clear();
				symbolInput.sendKeys(instrument);
				wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
				symbolInput.sendKeys(Keys.ARROW_DOWN);
				symbolInput.sendKeys(Keys.ENTER);
				((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", symbolInput);
				System.out.println("Order Symbol selected: " + instrument);

				// Fill Qty
				WebElement qtyElem = driver.findElement(By.id("order_qty"));
				qtyElem.clear();
				qtyElem.sendKeys(String.valueOf(tradeQty));
				try {
					((JavascriptExecutor) driver).executeScript("$('#order_qty').trigger('input').trigger('change');");
				} catch (Exception ignored) {}
				System.out.println("Qty entered: " + tradeQty);

				// Fill Rate (only for limit orders)
				if (!isPriorityOrder && executedRate > 0) {
					try {
						WebElement rateInput = driver.findElement(By.id("limit_order_rate"));
						if (rateInput.isDisplayed()) {
							rateInput.clear();
							rateInput.sendKeys(String.valueOf(executedRate));
						} else {
							((JavascriptExecutor) driver).executeScript(
									"arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
									rateInput, String.valueOf(executedRate));
						}
						((JavascriptExecutor) driver).executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
					} catch (Exception ignored) {}
				} else if (isPriorityOrder && executedRate <= 0.0) {
					double mRate = getEnteredRate(driver);
					if (mRate > 0.0) executedRate = mRate;
				}

				// Display Commission on Buy and Sell window when client has commission
				displayCommissionOnOrderWindow(driver, clientCode, tradeQty, executedRate);

				// Submit Order
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

				// Confirm Order
				if (!confirmOrder(driver, wait, sc)) {
					System.out.printf("Order cancelled for client %s.%n", clientCode);
					continue; // skip verification for cancelled order & move to next client
				}

				// [STEP 2] CAPTURE POST-TRADE SNAPSHOT & VERIFICATION
				System.out.printf("%n[AUDIT] Capturing POST-TRADE Snapshot & Verification for Client: %s...%n", clientCode);

				// 1. Visit Order Terminal first to detect order status, executed qty, and due qty
				Order_Terminal.navigateToOrderTerminal(driver);
				Order_Terminal.refresh(driver);
				Map<String, String> postOrderTerminal = Order_Terminal.getSnapshot(driver, clientCode, instrument, snapshotExchange);

				int executedQty = tradeQty;
				int dueQty = 0;
				String orderStatus = postOrderTerminal.getOrDefault("Status", "Filled");
				try {
					double eq = Double.parseDouble(postOrderTerminal.getOrDefault("Exec_Qty", "0").replaceAll("[^0-9.]", ""));
					double dq = Double.parseDouble(postOrderTerminal.getOrDefault("Due_Qty", "0").replaceAll("[^0-9.]", ""));
					if (eq > 0 || dq > 0) {
						executedQty = (int) eq;
						dueQty = (int) dq;
					}
				} catch (Exception ignored) {}

				// 2. Capture Portfolio Snapshot & Client Info
				navigateToPortfolioPage(driver, wait);
				ClientPortfolio.refresh(driver);

				Map<String, String> afterSnapshot = ClientPortfolio.getSnapshot(driver, clientCode, instrument, snapshotExchange);
				Map<String, String> clientInfoAfter = ClientPortfolio.getClientDetails(driver, clientCode);

				// Fallback: If Order Terminal didn't show Exec_Qty, check actual portfolio delta
				if (executedQty == tradeQty && afterSnapshot != null && beforeSnapshot != null) {
					try {
						double oldQ = Double.parseDouble(beforeSnapshot.getOrDefault("total_qty", "0").replaceAll("[^0-9.]", ""));
						double newQ = Double.parseDouble(afterSnapshot.getOrDefault("total_qty", "0").replaceAll("[^0-9.]", ""));
						double diffQ = Math.abs(newQ - oldQ);
						if (diffQ > 0 && diffQ < tradeQty) {
							executedQty = (int) diffQ;
							dueQty = tradeQty - executedQty;
							orderStatus = "Partially Filled";
						}
					} catch (Exception ignored) {}
				}

				// Compare & Display Comprehensive Table with Portfolio & Cash Audit
				ClientPortfolio.verify(beforeSnapshot, afterSnapshot, clientInfoBefore, clientInfoAfter, instrument, sideText, tradeQty, executedRate, exchange, executedQty, dueQty, orderStatus);

				// Verify Order Summary
				Order_Summary.navigateToOrderSummary(driver);
				Order_Summary.refresh(driver);
				Map<String, String> postOrderSummary = Order_Summary.getSnapshot(driver, clientCode, instrument, snapshotExchange);
				Order_Summary.verify(preOrderSummary, postOrderSummary, instrument, sideText, tradeQty, executedRate, exchange, executedQty, dueQty);

				// Account Limits
				Account_Limits.navigateToAccountLimits(driver);
				Account_Limits.refresh(driver);
				Map<String, String> postAccountLimits = Account_Limits.getSnapshot(driver, clientCode, snapshotExchange);
				Account_Limits.verify(preAccountLimits, postAccountLimits, sideText, tradeQty, executedRate, exchange, executedQty, dueQty);

				// Audit Market Depth
				Market_Depth.navigateToMarketDepth(driver);
				Map<String, String> postDepth = Market_Depth.getSnapshot(driver, instrument, snapshotExchange);
				Market_Depth.verify(preDepth, postDepth, instrument, sideText, tradeQty, executedRate, exchange);

				// Audit Order Terminal
				Order_Terminal.verify(preOrderTerminal, postOrderTerminal, instrument, sideText, tradeQty, executedRate, exchange);

				// Click newly placed order row in Order Terminal
				Order_Terminal.clickOrderRow(driver, postOrderTerminal.get("Order_ID"), clientCode, instrument);
				// Return to Trade Page if needed
				if (!isAlreadyOnTradePage(driver)) {
					System.out.println("[TRADE] Returning to Trade Page...");
					navigateToTradePage(driver, wait);
				}
			}
			// ---------- After confirmation, ask to select an Order Status type ----------
			System.out.print("\nDo you want to select an Order Status type now? (1=YES / 0=NO, then ENTER): ");
			if (sc.nextLine().trim().equals("1")) {
				selectOrderStatus(driver, wait, sc, instrument);
			}

			// ---------- Ask if user wants to trade again ----------
			System.out.print("Do you want to trade again? (1=YES / 0=NO, default=0): ");
			String tradeAgainInput = sc.nextLine().trim();
			if (!tradeAgainInput.equals("1") && !tradeAgainInput.equalsIgnoreCase("y") && !tradeAgainInput.equalsIgnoreCase("yes")) {
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
	    public static String cleanSymbol(String sym) {
        if (sym == null) return "";
        String s = sym.trim().toUpperCase();
        s = s.replaceAll("\\[[A-Z0-9]+\\]", "");
        if (s.contains(".")) {
            s = s.split("\\.")[0];
        }
        return s.trim();
    }

	public static void restoreTradePageState(WebDriver driver, String symbol, String clientCode) {
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
			navigateToTradePage(driver, wait);

			// 1. Populate Target Symbol into Search & Marketwatch & Order Entry
			if (symbol != null && !symbol.trim().isEmpty()) {
				String sym = cleanSymbol(symbol);
				try {
					WebElement searchSym = driver.findElement(By.id("instrument"));
					searchSym.clear();
					searchSym.sendKeys(sym);
					searchSym.sendKeys(Keys.ARROW_DOWN);
					searchSym.sendKeys(Keys.ENTER);
					Thread.sleep(200);
				} catch (Exception ignored) {}

				try {
					WebElement mwInput = driver.findElement(By.id("marketwatch_symbol"));
					mwInput.clear();
					mwInput.sendKeys(sym);
					mwInput.sendKeys(Keys.ARROW_DOWN);
					mwInput.sendKeys(Keys.ENTER);
					Thread.sleep(200);
				} catch (Exception ignored) {}

				try {
					WebElement orderInst = driver.findElement(By.id("order_instrument"));
					orderInst.clear();
					orderInst.sendKeys(sym);
					orderInst.sendKeys(Keys.ARROW_DOWN);
					orderInst.sendKeys(Keys.ENTER);
				} catch (Exception ignored) {}
			}

			// 2. Populate Client Code into Order Entry
			if (clientCode != null && !clientCode.trim().isEmpty()) {
				try {
					WebElement clientInp = driver.findElement(By.id("order_client_code"));
					clientInp.clear();
					clientInp.sendKeys(clientCode.trim());
					clientInp.sendKeys(Keys.ARROW_DOWN);
					clientInp.sendKeys(Keys.ENTER);
					Thread.sleep(200);
				} catch (Exception ignored) {}
			}

			System.out.printf("[TRADE PAGE RESTORED] Symbol: %s | Client Code: %s%n", symbol, clientCode);
		} catch (Exception e) {
			System.out.println("[WARN: Restore Trade Page] " + e.getMessage());
		}
	}

	public static void navigateToTradePage(WebDriver driver, WebDriverWait wait) {
		try {
			// Automatically dismiss any password breach or system modal before navigation
			OMSweb_Login.pressOkAutomatically(driver);

			// Check if already on Trade portal (marketwatch_symbol or order_instrument visible)
			if (isAlreadyOnTradePage(driver)) {
				System.out.println("[NAV] Already on Trade page.");
				return;
			}

			JavascriptExecutor js = (JavascriptExecutor) driver;
			String userLabel = "User";
			try {
				Object uname = js.executeScript(
					"var el = document.querySelector('.user-name, .username, #username_display, .user-profile span, .profile-name, .user-info');" +
					"return el ? (el.innerText || el.textContent).trim() : null;"
				);
				if (uname != null && !uname.toString().trim().isEmpty()) {
					userLabel = uname.toString().trim();
				}
			} catch (Exception ignored) {}
			System.out.printf("[NAV] %s detected on Dashboard. Navigating to Trade Portal...%n", userLabel);
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
			if (val == null || val.isEmpty()) val = rateInput.getDomAttribute("value");
			if (val != null && !val.trim().isEmpty()) {
				return Double.parseDouble(val.replaceAll("[^0-9.]", ""));
			}
		} catch (Exception ignoredLimit) {}

		try {
			WebElement marketInput = driver.findElement(By.id("market_order_rate"));
			String mVal = marketInput.getDomProperty("value");
			if (mVal == null || mVal.isEmpty()) mVal = marketInput.getDomAttribute("value");
			if (mVal != null && !mVal.trim().isEmpty()) {
				return Double.parseDouble(mVal.replaceAll("[^0-9.]", ""));
			}
		} catch (Exception ignoredMarket) {}

		return 0.0;
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

		System.out.print("Confirm cancellation? (1=YES / 0=NO, default=1): ");
		String cancelAns = sc.nextLine().trim();
		if (!cancelAns.equals("0") && !cancelAns.equalsIgnoreCase("n") && !cancelAns.equalsIgnoreCase("no")) {
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
		try {
			if (rateInput.isDisplayed()) {
				rateInput.clear();
				rateInput.sendKeys(String.format(java.util.Locale.US, "%.2f", outOfCircuitRate));
			} else {
				((JavascriptExecutor) driver).executeScript(
						"arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
						rateInput, String.format(java.util.Locale.US, "%.2f", outOfCircuitRate));
			}
		} catch (Exception e) {
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
					rateInput, String.format(java.util.Locale.US, "%.2f", outOfCircuitRate));
		}
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

	// ---------- MODAL EXCHANGE SELECTION HELPER ----------
	public static void selectExchangeInModal(WebDriver driver, String exchange) {
		try {
			String xcgId;
			if ("PRIORITY".equalsIgnoreCase(exchange)) {
				xcgId = "xcg1";
			} else if ("CSE".equalsIgnoreCase(exchange)) {
				xcgId = "xcg3";
			} else {
				xcgId = "xcg2"; // DSE
			}

			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript(
					"var el = document.getElementById(arguments[0]);" +
					"if (el) {" +
					"    el.checked = true;" +
					"    el.click();" +
					"    if (typeof switch_exchange === 'function') switch_exchange(el);" +
					"    var lbl = document.querySelector('label[for=' + arguments[0] + ']');" +
					"    if (lbl) lbl.click();" +
					"    var hidden = document.getElementById('exchange');" +
					"    if (hidden) hidden.value = el.value;" +
					"}",
					xcgId);
			System.out.println("[MODAL] Selected Exchange / Mode: " + exchange + " (" + xcgId + ")");
		} catch (Exception e) {
			System.out.println("[MODAL] Note selecting exchange: " + e.getMessage());
		}
	}

	// ---------- DOM PROPERTY HELPERS (Selenium 4 compliant, replacing deprecated getAttribute) ----------
	private static String getElementValue(WebElement el) {
		if (el == null) return "";
		try {
			String val = el.getDomProperty("value");
			if (val == null || val.trim().isEmpty()) {
				val = el.getDomAttribute("value");
			}
			return val != null ? val.trim() : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static String getElementValueSafe(WebDriver driver, By by) {
		try {
			WebElement el = driver.findElement(by);
			return getElementValue(el);
		} catch (Exception e) {
			return "";
		}
	}

	// ---------- DISPLAY COMMISSION ON BUY & SELL WINDOW ----------
	public static void displayCommissionOnOrderWindow(WebDriver driver, String clientCode, int qty, double rate) {
		if (driver == null) return;
		try {
			if (clientCode == null) clientCode = "";
			String cleanCode = clientCode.trim();
			boolean isZeroComm = cleanCode.equals("00239") || cleanCode.equals("239");

			// If qty or rate were not provided, attempt reading from order modal DOM inputs
			if (qty <= 0) {
				try {
					WebElement qEl = driver.findElement(By.id("order_qty"));
					String qVal = getElementValue(qEl);
					if (!qVal.isEmpty()) {
						qty = Integer.parseInt(qVal.replaceAll("[^0-9]", ""));
					}
				} catch (Exception ignored) {}
			}
			if (rate <= 0.0) {
				try {
					WebElement rEl = driver.findElement(By.id("limit_order_rate"));
					String rVal = getElementValue(rEl);
					if (!rVal.isEmpty()) {
						rate = Double.parseDouble(rVal.replaceAll("[^0-9.]", ""));
					}
				} catch (Exception ignored) {}
			}

			// Determine client commission rate
			double commRate = 0.0;
			if (!isZeroComm && !cleanCode.isEmpty()) {
				// 1. Try DOM hidden input #stock_commission_value
				try {
					WebElement scv = driver.findElement(By.id("stock_commission_value"));
					String v = getElementValue(scv);
					if (!v.isEmpty()) {
						double parsed = Double.parseDouble(v);
						if (parsed > 0) {
							commRate = (parsed < 0.05) ? (parsed * 100.0) : parsed;
						}
					}
				} catch (Exception ignored) {}

				// 2. If not found in DOM, fetch client details via API
				if (commRate <= 0.0) {
					try {
						Map<String, String> details = ClientPortfolio.getClientDetails(driver, cleanCode);
						if (details != null && details.containsKey("commission")) {
							String cStr = details.get("commission").replaceAll("[^0-9.]", "");
							if (!cStr.isEmpty()) {
								commRate = Double.parseDouble(cStr);
							}
						}
					} catch (Exception ignored) {}
				}
			}

			if (commRate <= 0.0) {
				isZeroComm = true;
			}

			double orderValue = (qty > 0 && rate > 0.0) ? (qty * rate) : 0.0;
			double commAmount = (commRate > 0.0 && orderValue > 0.0) ? (orderValue * (commRate / 100.0)) : 0.0;

			// Update DOM via JavascriptExecutor: unblur and style #stock_commission, update values
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript(
					"var commRate = arguments[0];" +
					"var commAmount = arguments[1];" +
					"var orderVal = arguments[2];" +
					"var isZero = arguments[3];" +
					"var sc = document.getElementById('stock_commission');" +
					"var scv = document.getElementById('stock_commission_value');" +
					"var sv = document.getElementById('stock_value');" +
					"var ov = document.getElementById('order_value');" +
					"if (typeof blurTimerCommission !== 'undefined' && blurTimerCommission) { clearTimeout(blurTimerCommission); }" +
					"if (sc) {" +
					"    sc.classList.remove('blurred');" +
					"    sc.style.setProperty('filter', 'none', 'important');" +
					"    sc.style.setProperty('-webkit-filter', 'none', 'important');" +
					"    sc.style.setProperty('opacity', '1', 'important');" +
					"    if (!isZero && commRate > 0) {" +
					"        sc.style.setProperty('display', 'inline-block', 'important');" +
					"        sc.style.setProperty('visibility', 'visible', 'important');" +
					"        sc.style.setProperty('color', '#FFB800', 'important');" +
					"        sc.style.setProperty('font-weight', 'bold', 'important');" +
					"        sc.style.setProperty('font-size', '13px', 'important');" +
					"        if (commAmount > 0) {" +
					"            sc.innerText = ' (Comm: ' + commAmount.toFixed(2) + ' BDT @ ' + commRate.toFixed(2) + '%)';" +
					"            sc.title = 'Client Commission: ' + commAmount.toFixed(2) + ' BDT (' + commRate.toFixed(2) + '%)';" +
					"        } else {" +
					"            sc.innerText = ' (Comm: ' + commRate.toFixed(2) + '%)';" +
					"            sc.title = 'Client Commission Rate: ' + commRate.toFixed(2) + '%';" +
					"        }" +
					"    } else {" +
					"        sc.innerText = '';" +
					"        sc.style.setProperty('display', 'none', 'important');" +
					"    }" +
					"}" +
					"if (scv) {" +
					"    scv.value = (!isZero && commRate > 0) ? (commRate / 100.0).toString() : '0.00';" +
					"}" +
					"if (sv && orderVal > 0) {" +
					"    var totalVal = (!isZero && commRate > 0) ? (orderVal + commAmount) : orderVal;" +
					"    sv.innerHTML = \"<span style='color: #00DB86;'><b>\" + Number(totalVal).toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2}) + \"</b></span>\";" +
					"}" +
					"if (ov && orderVal > 0) {" +
					"    ov.value = ((!isZero && commRate > 0) ? (orderVal + commAmount) : orderVal).toFixed(2);" +
					"}" +
					"if (!window._comm_listener_attached) {" +
					"    window._comm_listener_attached = true;" +
					"    $(document).on('input change keyup', '#order_qty, #limit_order_rate', function() {" +
					"        var _sc = document.getElementById('stock_commission');" +
					"        var _scv = document.getElementById('stock_commission_value');" +
					"        if (_sc && _scv) {" +
					"            var cR = parseFloat(_scv.value) || 0;" +
					"            var q = parseFloat($('#order_qty').val()) || 0;" +
					"            var r = parseFloat($('#limit_order_rate').val()) || 0;" +
					"            if (cR > 0 && q > 0 && r > 0) {" +
					"                var cAmt = (q * r) * cR;" +
					"                var pct = cR < 0.05 ? (cR * 100) : cR;" +
					"                _sc.classList.remove('blurred');" +
					"                _sc.style.setProperty('filter', 'none', 'important');" +
					"                _sc.style.setProperty('-webkit-filter', 'none', 'important');" +
					"                _sc.style.setProperty('opacity', '1', 'important');" +
					"                _sc.style.setProperty('display', 'inline-block', 'important');" +
					"                _sc.style.setProperty('color', '#FFB800', 'important');" +
					"                _sc.style.setProperty('font-weight', 'bold', 'important');" +
					"                _sc.innerText = ' (Comm: ' + cAmt.toFixed(2) + ' BDT @ ' + pct.toFixed(2) + '%)';" +
					"            } else if (cR <= 0) {" +
					"                _sc.innerText = '';" +
					"                _sc.style.setProperty('display', 'none', 'important');" +
					"            }" +
					"        }" +
					"    });" +
					"}",
					commRate, commAmount, orderValue, isZeroComm);

			if (!isZeroComm && commRate > 0) {
				System.out.printf("[BUY/SELL WINDOW] Displayed Commission for Client %s: %.2f BDT (%.2f%%) | Order Value: %,.2f BDT%n",
						cleanCode, commAmount, commRate, orderValue);
			} else {
				System.out.printf("[BUY/SELL WINDOW] Client %s has zero commission (Commission not shown on order window)%n", cleanCode);
			}
		} catch (Exception e) {
			System.out.println("[WARN: displayCommissionOnOrderWindow] " + e.getMessage());
		}
	}

	// ---------- SHARED HELPERS ----------
	private static void fillOrderFields(WebDriver driver, WebDriverWait wait, String instrument, String qty,
			String clientCode) throws InterruptedException {
		WebElement client = driver.findElement(By.id("order_client_code"));
		client.click();
		client.clear();
		client.sendKeys(clientCode);
		try {
			((JavascriptExecutor) driver).executeScript("$('#order_client_code').trigger('change');");
		} catch (Exception ignored) {}

		WebElement symbol = driver.findElement(By.id("order_instrument"));
		symbol.clear();
		symbol.sendKeys(instrument);
		wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-autocomplete-input")));
		symbol.sendKeys(Keys.ARROW_DOWN);
		symbol.sendKeys(Keys.ENTER);

		WebElement qtyInput = driver.findElement(By.id("order_qty"));
		qtyInput.clear();
		qtyInput.sendKeys(qty);
		try {
			((JavascriptExecutor) driver).executeScript("$('#order_qty').trigger('input').trigger('change');");
		} catch (Exception ignored) {}
		System.out.println("Fields filled: client=" + clientCode + ", symbol=" + instrument + ", qty=" + qty);

		int q = 0;
		try { q = Integer.parseInt(qty.trim()); } catch (Exception ignored) {}
		displayCommissionOnOrderWindow(driver, clientCode, q, 0.0);
	}

	private static double handleRate(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument, String side)
			throws InterruptedException {
		WebElement rateInput = null;
		try {
			rateInput = driver.findElement(By.id("limit_order_rate"));
		} catch (Exception ignored) {}

		System.out.print("Enter " + side + " Rate manually (press ENTER to auto-fetch from LTP): ");
		String rate = sc.nextLine().trim();
		double parsedRate = 0.0;
		if (rate.isEmpty()) {
			double ltp = fetchLTP(driver, wait, instrument);
			double autoRate = side.equalsIgnoreCase("BUY") ? ltp - 0.1 : ltp + 0.1;
			rate = String.format(java.util.Locale.US, "%.2f", autoRate);
			parsedRate = autoRate;
			System.out.println("Auto-fetched LTP: " + ltp + " -> Rate: " + rate);
		} else {
			try {
				parsedRate = Double.parseDouble(rate.replaceAll("[^0-9.]", ""));
			} catch (Exception ignored) {}
			System.out.println("Manual " + side + " Rate entered: " + rate);
		}

		if (rateInput != null) {
			try {
				if (rateInput.isDisplayed()) {
					rateInput.clear();
					rateInput.sendKeys(rate);
				} else {
					((JavascriptExecutor) driver).executeScript(
							"arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
							rateInput, rate);
				}
			} catch (Exception e) {
				try {
					((JavascriptExecutor) driver).executeScript(
							"arguments[0].value = arguments[1]; try { $(arguments[0]).trigger('input').trigger('change'); } catch(e){}",
							rateInput, rate);
				} catch (Exception ignored) {}
			}
			try {
				((JavascriptExecutor) driver).executeScript("$('#limit_order_rate').trigger('input').trigger('change');");
			} catch (Exception ignored) {}
		}
		return parsedRate;
	}

	private static void enterRateAndSubmit(WebDriver driver, WebDriverWait wait, Scanner sc, String instrument,
			String side) throws InterruptedException {
		handleRate(driver, wait, sc, instrument, side);
		try {
			String clientCode = getElementValueSafe(driver, By.id("order_client_code"));
			String qtyStr = getElementValueSafe(driver, By.id("order_qty"));
			String rateStr = getElementValueSafe(driver, By.id("limit_order_rate"));
			int q = (!qtyStr.isEmpty()) ? Integer.parseInt(qtyStr.replaceAll("[^0-9]", "")) : 0;
			double r = (!rateStr.isEmpty()) ? Double.parseDouble(rateStr.replaceAll("[^0-9.]", "")) : 0.0;
			displayCommissionOnOrderWindow(driver, clientCode, q, r);
		} catch (Exception ignored) {}
		submitOrder(driver, side);
		System.out.println("Submitted " + side + " order.");
	}

	private static double parseNumSafe(String val) {
		if (val == null) return 0.0;
		String clean = val.replaceAll("[^0-9.-]", "").trim();
		if (clean.isEmpty() || clean.equals("-") || clean.equals("--") || clean.equals(".") || clean.equals("-.")) return 0.0;
		try {
			return Double.parseDouble(clean);
		} catch (Exception e) {
			return 0.0;
		}
	}

	/**
	 * Centralized market reference price fetcher:
	 * Fetches LTP (Last Traded Price) with automatic fallback to YCP (Yesterday's Closing Price)
	 * or Close Price. Queries Market Depth API (Admin Live / QuantBD token / DOM) and Marketwatch table.
	 */
	public static double fetchPriceLtpOrYcp(WebDriver driver, WebDriverWait wait, String instrument, String exchange) {
		String cleanSym = cleanSymbol(instrument);
		String ex = (exchange != null && !exchange.isEmpty()) ? exchange : "DSE";

		// Tier 0: Direct PostgreSQL Database Table (dse_md_ltp / cse_md_ltp WHERE board = 'PUBLIC')
		try {
			double dbPrice = OMSweb_DBMarketData.getSymbolLtp(cleanSym, ex, 0.0);
			if (dbPrice > 0) {
				System.out.printf("[MARKET PRICE: DB Table] Resolved Public Board LTP for %s: %.2f BDT (%s_md_ltp)%n",
						cleanSym, dbPrice, ex.toLowerCase());
				return dbPrice;
			}
		} catch (Exception ignored) {}

		// Tier 1: Market Depth Snapshot API (Queries Admin Shared API / QuantBD Token API / DOM)
		try {
			Map<String, String> depth = Market_Depth.getSnapshot(driver, cleanSym, ex);
			if (depth != null && !depth.isEmpty()) {
				double ltp = parseNumSafe(depth.get("Last"));
				if (ltp > 0) {
					System.out.printf("[MARKET PRICE] Fetched LTP for %s: %.2f BDT (Source: %s)%n",
							cleanSym, ltp, depth.getOrDefault("DATA_SOURCE", "Market Depth API"));
					return ltp;
				}
				double ycp = parseNumSafe(depth.get("YCP"));
				if (ycp > 0) {
					System.out.printf("[MARKET PRICE] LTP is 0/unavailable. Fallback to YCP for %s: %.2f BDT (Source: %s)%n",
							cleanSym, ycp, depth.getOrDefault("DATA_SOURCE", "Market Depth API"));
					return ycp;
				}
				double close = parseNumSafe(depth.get("Close"));
				if (close > 0) {
					System.out.printf("[MARKET PRICE] LTP/YCP unavailable. Fallback to Close price for %s: %.2f BDT (Source: %s)%n",
							cleanSym, close, depth.getOrDefault("DATA_SOURCE", "Market Depth API"));
					return close;
				}
			}
		} catch (Exception e) {
			System.out.println("[WARN: Depth Price Query] " + e.getMessage());
		}

		// Tier 2: Search in Marketwatch on UI & Scrape Table
		try {
			WebElement mwInput = null;
			try {
				mwInput = driver.findElement(By.id("marketwatch_symbol"));
			} catch (Exception ignored) {
				try {
					mwInput = driver.findElement(By.id("instrument"));
				} catch (Exception ignored2) {}
			}
			if (mwInput != null && mwInput.isDisplayed()) {
				mwInput.clear();
				mwInput.sendKeys(cleanSym);
				mwInput.sendKeys(Keys.ARROW_DOWN);
				mwInput.sendKeys(Keys.ENTER);
				Thread.sleep(600);
			}

			By rowXPath = By.xpath("//tr[contains(@class, '" + cleanSym.replace(".", "").toUpperCase() + "')]");
			WebElement row = wait.until(ExpectedConditions.visibilityOfElementLocated(rowXPath));

			// Check LTP cell
			try {
				WebElement ltpCell = row.findElement(By.cssSelector("td.mw_table_ltp div, td.mw_table_ltp, td[class*='ltp']"));
				double ltp = parseNumSafe(ltpCell.getText());
				if (ltp > 0) {
					System.out.printf("[MARKET PRICE] Fetched LTP from Marketwatch table for %s: %.2f BDT%n", cleanSym, ltp);
					return ltp;
				}
			} catch (Exception ignored) {}

			// Check YCP / Close cell
			try {
				WebElement ycpCell = row.findElement(By.cssSelector("td.mw_table_ycp div, td.mw_table_ycp, td[class*='ycp'], td.mw_table_close div, td.mw_table_close, td[class*='close']"));
				double ycp = parseNumSafe(ycpCell.getText());
				if (ycp > 0) {
					System.out.printf("[MARKET PRICE] LTP is 0. Fallback to YCP from Marketwatch table for %s: %.2f BDT%n", cleanSym, ycp);
					return ycp;
				}
			} catch (Exception ignored) {}
		} catch (Exception ignored) {}

		// Tier 3: Direct API fetch fallback
		try {
			String adminUrl = String.format("/shared/getmktdepth/?mkt_group=PUBLIC&mkt_symbol=%s&exchange=%s", cleanSym, ex);
			JavascriptExecutor js = (JavascriptExecutor) driver;
			Object res = js.executeAsyncScript(
				"var callback = arguments[arguments.length - 1];" +
				"fetch('" + adminUrl + "').then(function(r){ return r.json(); }).then(function(d){ callback(JSON.stringify(d)); }).catch(function(e){ callback(''); });"
			);
			if (res != null) {
				String json = res.toString();
				Map<String, String> parsed = Market_Depth.parseMarketDepthJson(json, cleanSym, ex);
				double ltp = parseNumSafe(parsed.get("Last"));
				if (ltp > 0) {
					System.out.printf("[MARKET PRICE] Fetched LTP from Direct API for %s: %.2f BDT%n", cleanSym, ltp);
					return ltp;
				}
				double ycp = parseNumSafe(parsed.get("YCP"));
				if (ycp > 0) {
					System.out.printf("[MARKET PRICE] Fetched YCP from Direct API for %s: %.2f BDT%n", cleanSym, ycp);
					return ycp;
				}
			}
		} catch (Exception ignored) {}

		// Tier 4: Fallback to realistic calibrated market price
		double realisticFallback = 100.0;
		switch (cleanSym.toUpperCase()) {
			case "GP": realisticFallback = 315.50; break;
			case "BEXIMCO": realisticFallback = 115.60; break;
			case "BATBC": realisticFallback = 518.70; break;
			case "LHBL": realisticFallback = 72.40; break;
			case "SQURPHARMA": realisticFallback = 234.20; break;
			case "ROBI": realisticFallback = 30.50; break;
			case "RENATA": realisticFallback = 780.00; break;
			case "BRACBANK": realisticFallback = 44.50; break;
			default: realisticFallback = 100.0; break;
		}
		System.out.printf("[WARN: Price Fetch] Could not fetch LTP or YCP from market. Defaulting reference price for %s to %.2f BDT.%n", cleanSym, realisticFallback);
		return realisticFallback;
	}

	public static double fetchPriceLtpOrYcp(WebDriver driver, WebDriverWait wait, String instrument) {
		return fetchPriceLtpOrYcp(driver, wait, instrument, "DSE");
	}

	public static double fetchLTP(WebDriver driver, WebDriverWait wait, String instrument, String exchange) {
		return fetchPriceLtpOrYcp(driver, wait, instrument, exchange);
	}

	public static double fetchLTP(WebDriver driver, WebDriverWait wait, String instrument) {
		return fetchPriceLtpOrYcp(driver, wait, instrument, "DSE");
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
		System.out.print("Confirm order? (1=YES / 0=NO, default=1): ");
		String confirmAns = sc.nextLine().trim();
		if (!confirmAns.equals("0") && !confirmAns.equalsIgnoreCase("n") && !confirmAns.equalsIgnoreCase("no")) {
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
	public static final String[] COLOR_NAMES = { "Turquoise", "Yellow", "Orange", "Green", "Red", "Pink" };
	public static final String[] COLOR_HEXES = { "#42EADDFF", "#FEE715FF", "#F95700ff", "#06d001", "#EB0506", "#FF3EA5FF" };

	public static void runWidgetColorSelection(WebDriver driver, Scanner sc) throws InterruptedException {
		System.out.println("\n=== Widget Color Link ===");
		System.out.println("1.Turquoise 2.Yellow 3.Orange");
		System.out.println("4.Green 5.Red 6.Pink");
		System.out.print("Enter choice (1-6, or Enter to skip): ");
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
		applyColorToAllWidgets(driver, chosenColorName, targetHex);
		Thread.sleep(500);
	}

	public static boolean applyColorToAllWidgets(WebDriver driver, int colorChoice) {
		if (colorChoice < 1 || colorChoice > 6) return false;
		return applyColorToAllWidgets(driver, COLOR_NAMES[colorChoice - 1], COLOR_HEXES[colorChoice - 1]);
	}

	public static boolean applyColorToAllWidgets(WebDriver driver, String chosenColorName, String targetHex) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		String script =
			"var targetHex = arguments[0];" +
			"var chooseColors = Array.from(document.querySelectorAll('.chooseColor'));" +
			"var results = [];" +
			"chooseColors.forEach(function(chooseDiv, index) {" +
			"    var stack = chooseDiv.closest('.lm_stack');" +
			"    var posTab = stack ? stack.querySelector('.lm_tab[title*=Position], .lm_tab .lm_title') : null;" +
			"    var activeTab = stack ? stack.querySelector('.lm_tab.lm_active .lm_title, .lm_tab .lm_title') : null;" +
			"    var title = activeTab ? activeTab.innerText.trim() : (posTab ? 'Position' : ('Widget ' + (index + 1)));" +
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
			return successCount > 0;
		} else {
			System.out.println("[WIDGET COLOR] No widgets found on page.");
			return false;
		}
	}
}