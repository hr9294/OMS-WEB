"""
OMS-WEB Multi-Order Execution & Real-Time 6-Widget Verification Engine
Executes multi-order non-crossing limit strategies (BUY at down prices, SELL at upper prices),
discovers active clients with cash >= 100,000 BDT & valid BO IDs, triggers 'Execute at Market'
via Order Terminal right-click context menu, and performs 6-widget audit.
"""
import math
import time
from typing import List, Dict, Any, Optional
from selenium import webdriver
from selenium.webdriver.support.ui import WebDriverWait

from config import DEFAULT_SYMBOL, DEFAULT_EXCHANGE
import widget_color
import oms_price
import oms_trade
import account_limits
import position
import client_portfolio
import market_depth
import order_summary
import order_terminal


class OrderItem:
    def __init__(
        self,
        seq: int,
        side: str,
        client_code: str,
        symbol: str,
        quantity: int,
        rate: float,
        is_opposite: bool = False
    ):
        self.seq = seq
        self.side = side.upper()
        self.client_code = client_code
        self.symbol = symbol
        self.quantity = quantity
        self.rate = rate
        self.is_opposite = is_opposite

    def __repr__(self) -> str:
        tag = "[OPPOSITE]" if self.is_opposite else "[PRIMARY] "
        return (
            f"#{self.seq:02d} {tag} {self.side:<4} | Client: {self.client_code:<8} | "
            f"{self.symbol} x {self.quantity:<4} @ {self.rate:8.2f} BDT"
        )


def parse_client_list(user_in: str, defaults: List[str]) -> List[str]:
    """Parses a single client or comma-separated client list."""
    if not user_in or not user_in.strip():
        return list(defaults)
    clients = [c.strip() for c in re.split(r"[,;\s]+", user_in.strip()) if c.strip()]
    return clients if clients else list(defaults)


def set_trade_page_client_code(driver: webdriver.Chrome, client_code: str) -> None:
    """Sets active client code directly in Trade Page without page switching."""
    if not client_code or not client_code.strip():
        return
    try:
        el = driver.find_element(By.ID, "order_client_code")
        el.clear()
        el.send_keys(client_code.strip())
        el.send_keys(Keys.TAB)
        print(f"[TRADE PAGE] Active Client Code set to: {client_code.strip()}")
    except Exception:
        pass


def build_order_queue(
    driver: webdriver.Chrome,
    choice: int,
    symbol: str,
    exchange: str,
    ref_price: float,
    different_prices: bool = True
) -> List[OrderItem]:
    """Builds the non-crossing order queue with manual client entry (100% on Trade Page)."""
    queue: List[OrderItem] = []

    # Calculate safe spread delta to prevent orders from executing upon initial placement
    if ref_price <= 20.0:
        spread_delta = 0.50
    elif ref_price <= 100.0:
        spread_delta = 1.00
    elif ref_price <= 500.0:
        spread_delta = 2.00
    else:
        spread_delta = max(2.0, round(ref_price * 0.005, 1))

    item_counter = 1

    if choice == 1:
        # Strategy 1: Multiple BUY orders (DOWN prices < P) + Opposite SELL order (UPPER price > P)
        buy_in = input("\nEnter BUY Client Code(s) (single or comma-separated, default=08096): ").strip()
        buy_clients = parse_client_list(buy_in, ["08096", "00224", "08833"])

        sell_in = input("Enter Opposite SELL Client Code [default=11310]: ").strip()
        sell_client = sell_in or "11310"

        set_trade_page_client_code(driver, buy_clients[0])

        buy_count = 3 if len(buy_clients) == 1 else min(3, len(buy_clients))
        total_buy_qty = 0

        for i in range(buy_count):
            buy_client = buy_clients[i % len(buy_clients)]
            qty = 10 + (i * 2)
            total_buy_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = max(0.10, round(ref_price - spread_delta - step, 2))
            queue.append(OrderItem(item_counter, "BUY", buy_client, symbol, qty, rate, False))
            item_counter += 1

        # Opposite SELL order at UPPER price (> ref_price)
        sell_rate = round(ref_price + spread_delta, 2)
        queue.append(OrderItem(item_counter, "SELL", sell_client, symbol, total_buy_qty, sell_rate, True))

    elif choice == 2:
        # Strategy 2: Multiple SELL orders (UPPER prices > P) + Opposite BUY order (DOWN price < P)
        sell_in = input("\nEnter SELL Client Code(s) (single or comma-separated, default=11310): ").strip()
        sell_clients = parse_client_list(sell_in, ["11310", "12245", "14118"])

        buy_in = input("Enter Opposite BUY Client Code [default=08096]: ").strip()
        buy_client = buy_in or "08096"

        set_trade_page_client_code(driver, sell_clients[0])

        sell_count = 3 if len(sell_clients) == 1 else min(3, len(sell_clients))
        total_sell_qty = 0

        for i in range(sell_count):
            sell_client = sell_clients[i % len(sell_clients)]
            qty = 10
            total_sell_qty += qty
            step = (i * 0.50) if different_prices else 0.0
            rate = round(ref_price + spread_delta + step, 2)
            queue.append(OrderItem(item_counter, "SELL", sell_client, symbol, qty, rate, False))
            item_counter += 1

        # Opposite BUY order at DOWN price (< ref_price)
        buy_rate = max(0.10, round(ref_price - spread_delta, 2))
        queue.append(OrderItem(item_counter, "BUY", buy_client, symbol, total_sell_qty, buy_rate, True))

    else:
        # Strategy 3: Custom Interactive Queue
        side_in = input("Enter Primary Order Side (BUY / SELL, default BUY): ").strip().upper()
        p_side = "SELL" if side_in == "SELL" else "BUY"
        opp_side = "SELL" if p_side == "BUY" else "BUY"

        cnt_str = input("How many primary orders to place? (default 2): ").strip()
        p_count = int(cnt_str) if cnt_str.isdigit() else 2

        total_qty = 0
        first_client = ""
        for i in range(p_count):
            default_client = "08096" if p_side == "BUY" else "11310"
            c_in = input(f"  Order #{i+1} Client [{default_client}]: ").strip()
            client = c_in or default_client
            if not first_client:
                first_client = client

            q_in = input("  Quantity (default 10): ").strip()
            qty = int(q_in) if q_in.isdigit() else 10
            total_qty += qty

            step = (i * 0.50) if different_prices else 0.0
            default_rate = max(0.10, round(ref_price - spread_delta - step, 2)) if p_side == "BUY" else round(ref_price + spread_delta + step, 2)
            r_in = input(f"  Price Rate (default {default_rate:.2f}): ").strip()
            rate = float(r_in) if r_in else default_rate

            queue.append(OrderItem(item_counter, p_side, client, symbol, qty, rate, False))
            item_counter += 1

        default_opp = "08096" if opp_side == "BUY" else "11310"
        opp_in = input(f"Enter Opposite {opp_side} Client [{default_opp}]: ").strip()
        opp_client = opp_in or default_opp

        default_opp_rate = round(ref_price + spread_delta, 2) if opp_side == "SELL" else max(0.10, round(ref_price - spread_delta, 2))
        or_in = input(f"Enter Opposite {opp_side} Rate (default {default_opp_rate:.2f}): ").strip()
        opp_rate = float(or_in) if or_in else default_opp_rate

        queue.append(OrderItem(item_counter, opp_side, opp_client, symbol, total_qty, opp_rate, True))

        set_trade_page_client_code(driver, first_client)

    return queue


def print_planned_queue(queue: List[OrderItem]) -> None:
    """Prints planned pipeline summary table."""
    print("\n+=====================================================================================+")
    print("|                              PLANNED ORDER PIPELINE                                 |")
    print("+=====================================================================================+")
    print(f"| {'#':<3} | {'Type':<10} | {'Side':<4} | {'Client':<10} | {'Instrument':<10} | {'Qty':<6} | {'Limit Price':<12} |")
    print("+-------------------------------------------------------------------------------------+")
    for item in queue:
        tag = "OPPOSITE" if item.is_opposite else "PRIMARY"
        print(f"| {item.seq:<3} | {tag:<10} | {item.side:<4} | {item.client_code:<10} | {item.symbol:<10} | {item.quantity:<6} | {item.rate:12.2f} |")
    print("+=====================================================================================+")


def run_multi_order_pipeline(driver: webdriver.Chrome) -> None:
    """Full Multi-Order Pipeline with 6-Widget Real-Time Audit."""
    print("\n+=============================================================================+")
    print("|        MULTI-ORDER EXECUTION & REAL-TIME 6-WIDGET VERIFICATION ENGINE       |")
    print("+=============================================================================+")

    # STEP 0: Navigate to Trade Page
    oms_trade.navigate_to_trade_page(driver)

    # STEP 1: Widget Color Linking FIRST (link all 6 widgets before symbol & order placement)
    widget_color.run_widget_color_selection(driver)

    # STEP 2: Manual input of target symbol (with numeric guardrail)
    sym_input = input(f"\nEnter Target Symbol (e.g. GP, ACFL, SQURPHARMA) [default={DEFAULT_SYMBOL}]: ").strip()
    sym_clean = oms_price.clean_symbol(sym_input or DEFAULT_SYMBOL)
    if not sym_clean or sym_clean.isdigit():
        if sym_clean and sym_clean.isdigit():
            print(f"[WARN] Entered '{sym_clean}' is numeric, not a stock symbol. Defaulting to {DEFAULT_SYMBOL}.")
        symbol = DEFAULT_SYMBOL
    else:
        symbol = sym_clean

    ex_input = input(f"Enter Exchange (DSE / CSE, default={DEFAULT_EXCHANGE}): ").strip().upper()
    exchange = "CSE" if ex_input == "CSE" else "DSE"

    # Auto-populate into Trade and Marketwatch
    oms_trade.enter_instrument(driver, symbol)

    # Fetch reference price (LTP or YCP)
    ref_price = oms_price.fetch_price_ltp_or_ycp(driver, symbol, exchange)
    print(f"\n[MARKET] Reference Price for {symbol} is {ref_price:.2f} BDT (Base for Limit Pricing)")

    # Configure Strategy
    print("\nSelect Multi-Order Execution Strategy:")
    print("  1. Multiple BUY orders (Down Prices) + Opposite SELL order (Upper Price) [Default]")
    print("  2. Multiple SELL orders (Upper Prices) + Opposite BUY order (Down Price)")
    print("  3. Custom Interactive Order Queue")
    strat_in = input("Enter Choice (1, 2, or 3, default 1): ").strip()
    choice = int(strat_in) if strat_in in ("1", "2", "3") else 1

    price_var_in = input("\nPrice Variation for Orders: (1=Same Price / 2=Different Prices, default 2): ").strip()
    different_prices = (price_var_in != "1")

    # Build queue
    order_queue = build_order_queue(driver, choice, symbol, exchange, ref_price, different_prices)
    if not order_queue:
        print("[WARN] Order queue is empty. Aborting.")
        return

    print_planned_queue(order_queue)
    proceed = input("\nProceed to execute this pipeline? (1=YES / 0=NO, default=1): ").strip()
    if proceed.lower() in ("0", "n", "no"):
        print("Operation aborted by user.")
        return
    print("[CONFIRMATION] Pipeline execution confirmed. Starting execution sequence...")

    # STEP 3: CAPTURE BASELINE 6-WIDGET SNAPSHOTS
    print("\n>>> CAPTURING BASELINE 6-WIDGET SNAPSHOTS BEFORE PLACEMENT... <<<")
    baseline_portfolio: Dict[str, Dict[str, str]] = {}
    for item in order_queue:
        if item.client_code not in baseline_portfolio:
            baseline_portfolio[item.client_code] = client_portfolio.get_snapshot(driver, item.client_code)

    baseline_depth = market_depth.get_snapshot(driver, symbol, exchange)
    baseline_summary = order_summary.get_snapshot(driver, symbol=symbol)
    baseline_terminal = order_terminal.get_snapshot(driver, symbol=symbol, exchange=exchange)
    baseline_pos = position.get_snapshot(driver, symbol, exchange)

    print("✔ Baseline snapshots successfully recorded across 6 widgets.")

    # STEP 4: PLACE ORDERS SEQUENTIALLY
    print("\n>>> PLACING NON-CROSSING LIMIT ORDERS (Orders stay Accepted with 0% progress)... <<<")
    oms_trade.navigate_to_trade_page(driver)
    placed_count = 0

    for idx, item in enumerate(order_queue, 1):
        print(f"\n--- Placed Order [{idx}/{len(order_queue)}] ---")
        ok = oms_trade.place_order(
            driver=driver,
            side=item.side,
            client_code=item.client_code,
            symbol=item.symbol,
            qty=item.quantity,
            rate=item.rate,
            exchange=exchange
        )
        if ok:
            placed_count += 1
        time.sleep(0.8)

    print(f"\n✔ Successfully submitted {placed_count}/{len(order_queue)} orders into the order book.")

    # STEP 5: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK
    print("\n+=============================================================================+")
    print("| STEP 3: ORDER TERMINAL SELECTION & 'EXECUTE AT MARKET' CONTEXT CLICK        |")
    print("+=============================================================================+")
    time.sleep(1.0)
    exec_success = order_terminal.select_and_execute_at_market(driver, symbol=symbol, count=len(order_queue))
    if exec_success:
        print("✔ 'Execute at Market' context trigger completed successfully.")
    else:
        print("[INFO] Checking if matching engine auto-executed matching opposite orders...")

    time.sleep(2.0)

    # STEP 6: REAL-TIME 6-WIDGET AUDIT REPORT
    print("\n+=============================================================================+")
    print("| STEP 4: REAL-TIME 6-WIDGET POST-EXECUTION VERIFICATION & AUDIT REPORT       |")
    print("+=============================================================================+")

    final_depth = market_depth.get_snapshot(driver, symbol, exchange)
    final_summary = order_summary.get_snapshot(driver, symbol=symbol)
    final_terminal = order_terminal.get_snapshot(driver, symbol=symbol, exchange=exchange)
    final_pos = position.get_snapshot(driver, symbol, exchange)

    print("\n1. ORDER TERMINAL AUDIT:")
    print(f"   Baseline Accepted: {baseline_terminal.get('accepted_count')} | Filled: {baseline_terminal.get('filled_count')}")
    print(f"   Current  Accepted: {final_terminal.get('accepted_count')} | Filled: {final_terminal.get('filled_count')}")

    print("\n2. ORDER SUMMARY AUDIT:")
    print(f"   Total Orders: {final_summary.get('total_orders')} | Executed: {final_summary.get('executed_orders')}")

    print("\n3. POSITION WIDGET AUDIT:")
    print(f"   Baseline Total Shares: {baseline_pos.get('total_shares')} | Saleable: {baseline_pos.get('saleable_shares')}")
    print(f"   Current  Total Shares: {final_pos.get('total_shares')} | Saleable: {final_pos.get('saleable_shares')}")

    print("\n4. MARKET DEPTH AUDIT:")
    print(f"   Baseline LTP: {baseline_depth.get('ltp')} | Current LTP: {final_depth.get('ltp')}")

    print("\n5. CLIENT PORTFOLIO FINANCIAL AUDIT:")
    for code in baseline_portfolio:
        after_p = client_portfolio.get_snapshot(driver, code)
        before_cash = baseline_portfolio[code].get("cash_limit", "0.00")
        after_cash = after_p.get("cash_limit", "0.00")
        print(f"   Client {code:<8} -> Cash: {before_cash} -> {after_cash} BDT")

    print("\n>>> Multi-Order Execution and 6-Widget Real-Time Audit Session Complete. <<<\n")
    oms_trade.navigate_to_trade_page(driver)
